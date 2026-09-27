package cait.collector.worker.crawler;

import cait.collector.common.Constants;
import cait.collector.easyspider.EasyspiderTaskInvoker;
import cait.collector.messaging.ProcessorTaskPublisher;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import cait.common.pb.ProcessorPb;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.StringJoiner;

@Slf4j
@Service
public class EasyspiderCrawler {

    private final EasyspiderTaskInvoker easyspiderTaskInvoker;
    private final RedisTemplate<String, byte[]> byteRedisTemplate;
    private final DataStorage dataStorage;
    private final ProcessorTaskPublisher processorTaskPublisher;

    public EasyspiderCrawler(EasyspiderTaskInvoker easyspiderTaskInvoker,
                             @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate, DataStorage dataStorage, ProcessorTaskPublisher processorTaskPublisher) {
        this.easyspiderTaskInvoker = easyspiderTaskInvoker;
        this.byteRedisTemplate = byteRedisTemplate;
        this.dataStorage = dataStorage;
        this.processorTaskPublisher = processorTaskPublisher;
    }

    // todo 这里是调用taskInvoker来执行easyspider任务的，生成callbackId，并记录callback信息（类型，crawler或者datasource），
    //  在这里传入url和taskId作为参数，调用taskInvoker，执行任务，url是从datasource那里拿到的，

    // todo 这里是用于接收从easyspider回调回来的页面数据，也就是新闻内容，datasource那边接收的是url列表，
    //  通过回调时提供的callbackId得知类型，进而路由到不同的地方（datasource或者crawlerReceiver）

    public CollectorPb.EasyspiderCallbackInfo crawl(CollectorPb.PageCrawlTask task) throws IOException {

        var urls = task.getUrlList();
        var taskStatusRedisKey = Constants.Redis.CrawlTaskStatus.formatted(task.getId());

        var now = System.currentTimeMillis();
        var extraParams = task.getAdditionalInfoMap();

        var urlListStr = new StringJoiner("\r\n");
        urls.forEach(urlListStr::add);

        var params = new HashMap<String, String>() {{
            put("urlList_0", urlListStr.toString());
            put("now", String.valueOf(now));
            putAll(extraParams);
        }};

        var taskId = extraParams.get("easyspider_task_id");

        return easyspiderTaskInvoker.invokeTask(taskId, params, (data) -> {
            var urlHash = DigestUtils.sha256Hex(data.getUrl());
            var status = CollectorPb.CrawlStatus.newBuilder();

            try {
                var statusData = status.build().toByteArray();
                byteRedisTemplate.opsForHash().put(taskStatusRedisKey, urlHash, statusData);
            } catch (Exception e) {
                log.warn("err when put crawl status to redis", e);
            }

            try {
                var result = data.getResult();
                if (result.isEmpty()) {
                    return;
                }

                var content = result.getFirst();

                var storageResult = dataStorage.storage(task.getId(), urlHash, content);
                // 这里应该要直接publish消息了，
                publish(urlHash, storageResult);

                status.setStatus(CollectorPb.CrawlStatusEnum.CrawlStatFinished);
            } catch (Exception e) {
                log.error(e.getMessage());
                status.setStatus(CollectorPb.CrawlStatusEnum.CrawlStatFailed);
                status.putMessage("task.err", e.toString());
            }

            try {
                var statusData = status.build().toByteArray();
                byteRedisTemplate.opsForHash().put(taskStatusRedisKey, urlHash, statusData);
            } catch (Exception e) {
                log.warn("err when crawl status to redis", e);
            }
        });
    }

    private void publish(String urlHash, DataStorage.StorageResult storageResult) {
        var pbStorageType = switch (storageResult.getStorageType()) {
            case "fs" -> ProcessorPb.ContentFileStorageType.StorageTypeFs;
            case "es" -> ProcessorPb.ContentFileStorageType.StorageTypeEs;
            default -> ProcessorPb.ContentFileStorageType.StorageTypeFs;
        };

        var newsProcessMessage = ProcessorPb.NewsProcessMessage.newBuilder()
                .setId(urlHash)
                .addContentKeys(storageResult.getDataKey())
                .setStorageType(pbStorageType)
                .build();

        processorTaskPublisher.publishJobProcessingMsg(newsProcessMessage);
    }
}
