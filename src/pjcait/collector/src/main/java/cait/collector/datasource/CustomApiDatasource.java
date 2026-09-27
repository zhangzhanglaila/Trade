package cait.collector.datasource;

import cait.collector.configure.CustomApiConfigure;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.exception.ServiceException;
import cn.hutool.core.net.url.UrlBuilder;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yitter.idgen.YitIdHelper;
import lombok.Builder;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class CustomApiDatasource {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper jacksonObjectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CustomApiConfigure customApiConfigure;
    private final ConsulClient consulClient;

    public CustomApiDatasource(OkHttpClient okHttpClient,
                               ObjectMapper jacksonObjectMapper,
                               StringRedisTemplate stringRedisTemplate,
                               CustomApiConfigure customApiConfigure, ConsulClient consulClient) {
        this.okHttpClient = okHttpClient;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.customApiConfigure = customApiConfigure;
        this.consulClient = consulClient;
    }

    private static final AtomicInteger LastUseInstance = new AtomicInteger(0);
    private static long LastUpdateTime = 0;
    private static final List<HealthService> CachedServiceList = new ArrayList<>();
    private static final ReentrantReadWriteLock ServiceListLock = new ReentrantReadWriteLock();

    private List<HealthService> getInstances(String serviceName) {
        try {
            if (LastUpdateTime < System.currentTimeMillis() - 60 * 1000) {
                return CachedServiceList;
            }

            if (!ServiceListLock.writeLock().tryLock(5, TimeUnit.SECONDS)) {
                return CachedServiceList;
            }

            var req = HealthServicesRequest.newBuilder()
                    .setPassing(true)
                    .build();
            var resp = consulClient.getHealthServices(serviceName, req);
            var services = resp.getValue();
            CachedServiceList.clear();
            CachedServiceList.addAll(services);
            LastUpdateTime = System.currentTimeMillis();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (ServiceListLock.writeLock().isHeldByCurrentThread()) {
                ServiceListLock.writeLock().unlock();
            }
        }

        return CachedServiceList;
    }

    @Data
    @Builder
    private static class Instance {
        private String serviceName;
        private String instanceId;
        private URL url;
    }

    private Instance pickInstance(String serviceName) {
        var serviceList = getInstances(serviceName);

        ServiceListLock.readLock().lock();
        var idx = LastUseInstance.incrementAndGet();
        if (idx >= serviceList.size()) {
            idx = 0;
        }
        LastUseInstance.set(idx);
        var instance = serviceList.get(idx);
        ServiceListLock.readLock().unlock();

        var service = instance.getService();
        var addr = service.getAddress();
        var port = service.getPort();

        var url = UrlBuilder.of().setScheme("http").setHost(addr).setPort(port).toURL();

        return Instance.builder()
                .serviceName(serviceName)
                .instanceId(service.getId())
                .url(url)
                .build();
    }

    public String request(String url, Map<String, String> params) throws IOException {
        var reqParams = this.makeParams(params);

        var reqUrl = UrlBuilder.of(url);
        if (reqUrl.getScheme().equals("service")) {
            var instance = pickInstance(reqUrl.getHost());
            reqUrl.setScheme("http");
            reqUrl.setHost(instance.getInstanceId());
            reqUrl.setPort(instance.getUrl().getPort());
        }

        var reqJson = jacksonObjectMapper.writeValueAsBytes(reqParams);
        var reqBody = RequestBody.create(reqJson, MediaType.parse("application/json"));
        var req = new Request.Builder()
                .url(reqUrl.toURL())
                .post(reqBody)
                .build();

        try (var resp = okHttpClient.newCall(req).execute()) {
            var respBody = resp.body();
            if (respBody == null) {
                return "";
            }

            var json = respBody.string();
            var respObj = jacksonObjectMapper.readValue(json, Response.class);
            if (respObj.code != 0) {
                throw new ServiceException(
                        ServiceCode.UnknownErr,
                        String.format("unexpected response code: %d != 0, msg: '%s'", respObj.code, respObj.msg)
                );
            }

            return respObj.data;
        }
    }

    private Map<String, String> makeParams(Map<String, String> params) {
        var requestParams = new HashMap<>(params);
        requestParams.put("now", String.valueOf(System.currentTimeMillis()));
        requestParams.put("callback_instance", customApiConfigure.getInstanceId());
        var callbackId = YitIdHelper.nextId();
        requestParams.put("callback_id", String.valueOf(callbackId));

        return requestParams;
    }

    @Data
    public static class Response {

        private int code;

        private String msg;

        private String data;

    }

}
