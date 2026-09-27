package cait.collector.easyspider;

import cait.collector.common.Constants;
import cait.collector.configure.EasyspiderConfigure;
import cait.common.pb.CollectorPb;
import cn.hutool.core.net.url.UrlBuilder;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.yitter.idgen.YitIdHelper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

// 用于从注册中心挑选实例并且发送执行请求的class
@Slf4j
@Service
public class EasyspiderTaskInvoker {

    private final EasyspiderConfigure easyspiderConfigure;

    private final OkHttpClient okHttpClient;

    private final ConsulClient consulClient;

    private final ObjectMapper jacksonObjectMapper;

    private final RedisTemplate<String, byte[]> byteRedisTemplate;

    public EasyspiderTaskInvoker(EasyspiderConfigure easyspiderConfigure,
                                 OkHttpClient okHttpClient, ConsulClient consulClient,
                                 ObjectMapper jacksonObjectMapper,
                                 @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate) {

        this.easyspiderConfigure = easyspiderConfigure;
        this.okHttpClient = okHttpClient;
        this.consulClient = consulClient;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.byteRedisTemplate = byteRedisTemplate;
    }

    private static final AtomicInteger LastUseInstance = new AtomicInteger(0);
    private static long LastUpdateTime = 0;
    private static final List<HealthService> CachedServiceList = new ArrayList<>();
    private static final ReentrantReadWriteLock ServiceListLock = new ReentrantReadWriteLock();

    private List<HealthService> getEasyspiderInstances(String serviceName) {
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
    private static class EasyspiderInstance {
        private String serviceName;
        private String instanceId;
        private String url;
    }

    private EasyspiderInstance pickEasyspiderInstance(String serviceName) {
        var serviceList = getEasyspiderInstances(serviceName);

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

        var url = UrlBuilder.of().setScheme("http").setHost(addr).setPort(port).build();

        return EasyspiderInstance.builder()
                .serviceName(serviceName)
                .instanceId(service.getId())
                .url(url)
                .build();
    }

    @Data
    @Builder
    private static class WrapperInvokeRequest {
        private String taskId;

        private Map<String, String> params;

        private String callback;
    }

    @Data
    private static class EsWrapperResponse<T> {
        private int code;

        private String msg;

        private T data;
    }

    @Data
    private static class EsWrapperInvokeResponse {
        private Integer pid;

        private String invokeId;
    }

    private static final Duration CallbackTimeout = Duration.ofDays(5);

    private static final Cache<String, Consumer<EasyspiderCallbackData>> CallbackMap = Caffeine.newBuilder()
            .initialCapacity(15)
            .maximumSize(65535)
            .removalListener((key, value, cause) -> log.info("remove callback '{}' cause '{}'", key, cause))
            .expireAfterAccess(CallbackTimeout)
            .build();

    public CollectorPb.EasyspiderCallbackInfo invokeTask(String taskId, HashMap<String, String> params,
                                   Consumer<EasyspiderCallbackData> callback
    ) throws IOException {
        var callbackId = String.valueOf(YitIdHelper.nextId());
        var callbackUrl = UrlBuilder.of(easyspiderConfigure.getCallbackUrl()).addQuery("id", callbackId).build();
        var invokeReq = WrapperInvokeRequest.builder()
                .taskId(taskId)
                .callback(callbackUrl)
                .params(params)
                .build();

        var reqJson = jacksonObjectMapper.writeValueAsString(invokeReq);
        var reqBody = RequestBody.create(reqJson, MediaType.get("application/json; charset=utf-8"));

        var serviceName = easyspiderConfigure.getWrapperServiceName();
        var instance = pickEasyspiderInstance(serviceName);
        var request = new Request.Builder()
                .url(instance.getUrl())
                .post(reqBody)
                .build();

        EsWrapperResponse<EsWrapperInvokeResponse> invokeResp;
        try (Response response = okHttpClient.newCall(request).execute()) {
            var body = response.body();
            if (body == null) {
                throw new IOException("response body is null");
            }

            var invokeResponseJavaType = jacksonObjectMapper.getTypeFactory()
                    .constructParametricType(EsWrapperResponse.class, EsWrapperInvokeResponse.class);
            invokeResp = jacksonObjectMapper.readValue(body.byteStream(), invokeResponseJavaType);
        }

        if (invokeResp.code != 0) {
            throw new IOException(invokeResp.code + ":" + invokeResp.msg);
        }

        var invokeRespData = invokeResp.data;

        var pb = CollectorPb.EasyspiderCallbackInfo.newBuilder()
                .setCallback(callbackId)
                .setStat(CollectorPb.EasyspiderCallbackStat.EsCbStatCommited)
                .setCreateTime(System.currentTimeMillis())
                .setTimeout(Duration.ofHours(1).toMillis())
                .setEsInvokeId(invokeRespData.getInvokeId())
                .setEsTaskId(taskId)
                .setBlameInstance(instance.getInstanceId())
                .setPidInInstance(invokeRespData.getPid())
                .putAllParams(params)
                .build();

        CallbackMap.put(callbackId, callback);

        try {
            var redisKey = String.format(Constants.Redis.EasySpiderTaskCallbackInfo, callbackId);
            byteRedisTemplate.opsForValue().set(redisKey, pb.toByteArray(), CallbackTimeout);
        } catch (Exception e) {
            log.warn("err when putting callback data to redis", e);
        }

        return pb;
    }

    public void execCallback(EasyspiderCallbackData cbData, String callbackId) {
        var cb = CallbackMap.get(callbackId, k -> null);
        if (cb == null) {
            log.warn("callback '{}' has already be removed", callbackId);
            return;
        }

        try {
            cb.accept(cbData);
        } finally {
            log.info("cleaning callback '{}'", callbackId);
            cleanUpCallbackData(callbackId);
        }
    }

    private void cleanUpCallbackData(String callbackId) {
        CallbackMap.invalidate(callbackId);
        try {
            var redisKey = String.format(Constants.Redis.EasySpiderTaskCallbackInfo, callbackId);
            var pbBytes = byteRedisTemplate.opsForValue().get(redisKey);
            if (pbBytes != null) {
                var pb = CollectorPb.EasyspiderCallbackInfo.parseFrom(pbBytes).toBuilder()
                        .setStat(CollectorPb.EasyspiderCallbackStat.EsCbStatReceived)
                        .setReceiveTime(System.currentTimeMillis())
                        .build();
                byteRedisTemplate.opsForValue().set(redisKey, pb.toByteArray(), CallbackTimeout);
            } else {
                log.info("callback info '{}' not exists", callbackId);
            }
        } catch (Exception e) {
            log.warn("err when cleaning up callback data on redis", e);
        }
    }
}
