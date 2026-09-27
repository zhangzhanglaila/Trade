package cait.collector.web.api.v1;

import cait.collector.common.Constants;
import cait.collector.common.mapper.CrawlTaskMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CrawlTaskEntity;
import cait.collector.messaging.CrawlTaskPublisher;
import cait.collector.messaging.ProcessorTaskPublisher;
import cait.collector.web.model.request.CustomCrawlerCallbackRequest;
import cait.collector.web.model.request.CustomDatasourceCallbackRequest;
import cait.collector.web.model.response.Response;
import cait.collector.worker.crawler.CustomCrawler;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import cait.common.pb.ProcessorPb;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.YitIdHelper;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/crawler/custom")
public class CustomCrawlerCallbackController {

    private final CustomCrawler customCrawler;
    private final RedisTemplate<String, byte[]> byteRedisTemplate;
    private final ProcessorTaskPublisher processorTaskPublisher;
    private final CrawlTaskMapper crawlTaskMapper;

    public CustomCrawlerCallbackController(CustomCrawler customCrawler,
                                           @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate,
                                           ProcessorTaskPublisher processorTaskPublisher,
                                           CrawlTaskMapper crawlTaskMapper
    ) {

        this.customCrawler = customCrawler;
        this.byteRedisTemplate = byteRedisTemplate;
        this.processorTaskPublisher = processorTaskPublisher;
        this.crawlTaskMapper = crawlTaskMapper;
    }

    @PostMapping("/callback")
    public Response<?> callback(@RequestParam("callback_id") String callbackId,
                                @RequestBody CustomCrawlerCallbackRequest request
    ) {
        if (request.getCode() != 0) {
            log.warn("error from callback: {} {}", request.getCode(), request.getMsg());
            return Response.success();
        }

        var key = Constants.Redis.CrawlTaskInfo.formatted(callbackId);
        try {
            var taskPbBytes = byteRedisTemplate.opsForValue().get(key);
            var taskPb = CollectorPb.PageCrawlTask.parseFrom(taskPbBytes);
            var resultMap = request.getData().getResult();
            var convertedResultMap = new HashMap<String, String>(resultMap.size());
            resultMap.forEach((urlHash, content) -> convertedResultMap.put(urlHash, content.getContent()));

            var storageResult = customCrawler.storage(taskPb, convertedResultMap);
            this.updateTaskStatus(taskPb.getId(), EnumValues.CrawlTaskStatus.Finished, null);
            if (taskPb.getCommitPublish()) {
                this.publishProcessTask(storageResult);
            }
        } catch (Exception e) {
            this.updateTaskStatus(request.getData().getTaskId(), EnumValues.CrawlTaskStatus.Failed, e.toString());
            log.warn("error when handle callback", e);
        }

        return Response.success();
    }

    private void updateTaskStatus(String id, int status, String message) {
        try {
            var query = new LambdaUpdateWrapper<CrawlTaskEntity>()
                    .eq(CrawlTaskEntity::getId, id)
                    .set(CrawlTaskEntity::getStatus, status)
                    .set(message != null, CrawlTaskEntity::getMessage, message);

            crawlTaskMapper.update(query);
        } catch (Exception e) {
            log.error("error when update crawl task status", e);
        }
    }

    private void publishProcessTask(Map<String, DataStorage.StorageResult> storageResultMap) {
        var groupedResultMap = storageResultMap.values().stream()
                .collect(Collectors.groupingBy(DataStorage.StorageResult::getStorageType));

        for (var entry : groupedResultMap.entrySet()) {
            String type = entry.getKey();
            List<DataStorage.StorageResult> results = entry.getValue();

            var storageType = switch (type) {
                case "es" -> ProcessorPb.ContentFileStorageType.StorageTypeEs;
                case "fs" -> ProcessorPb.ContentFileStorageType.StorageTypeFs;
                default -> ProcessorPb.ContentFileStorageType.StorageTypeFs;
            };

            ProcessorPb.NewsProcessMessage.Builder messageBuilder = ProcessorPb.NewsProcessMessage.newBuilder()
                    .setId(String.valueOf(YitIdHelper.nextId()))
                    .setStorageType(storageType);

            for (DataStorage.StorageResult result : results) {
                messageBuilder.addContentKeys(result.getDataKey());
            }

            processorTaskPublisher.publishJobProcessingMsg(messageBuilder.build());
        }
    }
}
