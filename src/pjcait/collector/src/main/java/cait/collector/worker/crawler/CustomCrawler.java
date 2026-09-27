package cait.collector.worker.crawler;

import cait.collector.common.Constants;
import cait.collector.common.mapper.CustomCrawlerMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CustomCrawlerEntity;
import cait.collector.configure.CustomApiConfigure;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.exception.ServiceException;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yitter.idgen.YitIdHelper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CustomCrawler {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper jacksonObjectMapper;
    private final CustomCrawlerMapper customCrawlerMapper;
    private final JavaType stringMapJavaType;
    private final DataStorage dataStorage;
    private final CustomApiConfigure customApiConfigure;
    private final RedisTemplate<String, byte[]> byteRedisTemplate;

    public CustomCrawler(OkHttpClient okHttpClient,
                         ObjectMapper jacksonObjectMapper,
                         CustomCrawlerMapper customCrawlerMapper,
                         DataStorage dataStorage,
                         CustomApiConfigure customApiConfigure,
                         @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate) {

        this.okHttpClient = okHttpClient;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.customCrawlerMapper = customCrawlerMapper;
        this.stringMapJavaType = jacksonObjectMapper.getTypeFactory()
                .constructParametricType(HashMap.class, String.class, String.class);
        this.dataStorage = dataStorage;
        this.customApiConfigure = customApiConfigure;
        this.byteRedisTemplate = byteRedisTemplate;
    }

    public CustomCrawlerEntity getCrawler(String id) {
        var query = new LambdaQueryWrapper<CustomCrawlerEntity>()
                .eq(CustomCrawlerEntity::getId, id)
                .eq(CustomCrawlerEntity::getStatus, EnumValues.CustomCrawlerStatus.Enabled)
                .last("limit 1");

        return customCrawlerMapper.selectOne(query);
    }

    @Data
    @Builder
    public static class CrawlRequest {

        private List<String> urls;

        private Map<String, String> params;

    }

    @Data
    public static class Response {

        private int code;

        private String msg;

        private String data;

    }

    public String request(CollectorPb.PageCrawlTask task, CustomCrawlerEntity crawler) throws IOException {
        var api = crawler.getReqUrl();

        var callbackId = task.getId();
        Map<String, String> reqParams = jacksonObjectMapper.readValue(crawler.getParams(), stringMapJavaType);
        reqParams.putAll(task.getAdditionalInfoMap());
        reqParams.put("callback_instance", customApiConfigure.getInstanceId());
        reqParams.put("callback_id", callbackId);
        reqParams.put("task_id", task.getId());

        var reqJson = jacksonObjectMapper.writeValueAsBytes(CrawlRequest.builder()
                .urls(task.getUrlList())
                .params(reqParams)
                .build()
        );

        var reqBody = RequestBody.create(reqJson, MediaType.parse("application/json"));
        var req = new Request.Builder()
                .url(api)
                .post(reqBody)
                .build();

        try (var resp = okHttpClient.newCall(req).execute()) {
            var respBody = resp.body();
            if (respBody == null) {
                return "";
            }

            var respObj = jacksonObjectMapper.readValue(respBody.string(), Response.class);
            if (respObj.code != 0) {
                throw new ServiceException(
                        ServiceCode.UnknownErr,
                        String.format("unexpected response code: %d != 0, msg: '%s'", respObj.code, respObj.msg)
                );
            }

            var key = Constants.Redis.CrawlTaskInfo.formatted(callbackId);
            byteRedisTemplate.opsForValue().set(key, task.toByteArray());
            return respObj.data;
        }
    }

    public Map<String, DataStorage.StorageResult> storage(
            CollectorPb.PageCrawlTask task, Map<String, String> dataMap
    ) throws IOException {
        var resultMap = new HashMap<String, DataStorage.StorageResult>();
        for (var url : task.getUrlList()) {
            var urlHash = DigestUtils.sha256Hex(url);

            var data = dataMap.get(urlHash);
            if (data == null || data.isEmpty()) {
                log.warn("data for url '{}' not found", url);
                continue;
            }

            var result = dataStorage.storage(task.getId(), urlHash, data);
            resultMap.put(urlHash, result);
        }

        return resultMap;
    }
}
