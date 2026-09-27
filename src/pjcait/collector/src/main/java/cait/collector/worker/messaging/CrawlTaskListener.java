package cait.collector.worker.messaging;

import cait.collector.common.Constants;
import cait.collector.common.mapper.CrawlTaskMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CrawlTaskEntity;
import cait.collector.messaging.ProcessorTaskPublisher;
import cait.collector.worker.crawler.CustomCrawler;
import cait.collector.worker.crawler.EasyspiderCrawler;
import cait.collector.worker.crawler.RawHtmlCrawler;
import cait.collector.worker.crawler.SeleniumCrawler;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import cait.common.pb.ProcessorPb;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.YitIdHelper;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CrawlTaskListener {
    private final EasyspiderCrawler easyspiderCrawler;
    private final SeleniumCrawler seleniumCrawler;
    private final RawHtmlCrawler rawHtmlCrawler;
    private final ProcessorTaskPublisher processorTaskPublisher;
    private final CustomCrawler customCrawler;
    private final CrawlTaskMapper crawlTaskMapper;
    private final RedisTemplate<String, byte[]> byteRedisTemplate;

    public CrawlTaskListener(EasyspiderCrawler easyspiderCrawler,
                             SeleniumCrawler seleniumCrawler,
                             RawHtmlCrawler rawHtmlCrawler,
                             ProcessorTaskPublisher processorTaskPublisher,
                             CustomCrawler customCrawler,
                             CrawlTaskMapper crawlTaskMapper, RedisTemplate<String, byte[]> byteRedisTemplate) {

        this.easyspiderCrawler = easyspiderCrawler;
        this.seleniumCrawler = seleniumCrawler;
        this.rawHtmlCrawler = rawHtmlCrawler;
        this.processorTaskPublisher = processorTaskPublisher;
        this.customCrawler = customCrawler;
        this.crawlTaskMapper = crawlTaskMapper;
        this.byteRedisTemplate = byteRedisTemplate;
    }

    @KafkaListener(
            topics = Constants.Kafka.Topics.CrawlJob,
            groupId = Constants.Kafka.Topics.CrawlJob,
            contentTypeConverter = ""
    )
    public void listen(ConsumerRecord<String, byte[]> record) {
        log.info("CrawlJob received: {}", record);
        String id = null;
        try {
            var task = CollectorPb.PageCrawlTask.parseFrom(record.value());
            id = task.getId();
            this.updateTaskStatus(task.getId(), EnumValues.CrawlTaskStatus.Received, null);
            try{
                var key = String.format(Constants.Redis.CrawlTaskInfo, task.getId());
                byteRedisTemplate.opsForValue().set(key, task.toByteArray());
            } catch (Exception e) {
                log.warn("[CollectorListener] error when set task info to redis", e);
            }
            switch (task.getCrawlType()) {
                case CrawlTypePageRaw -> {
                    var storageResult = rawHtmlCrawler.crawl(task);
                    this.updateTaskStatus(task.getId(), EnumValues.CrawlTaskStatus.Finished, null);
                    if (task.getCommitPublish()) {
                        this.publishProcessTask(storageResult);
                    }
                }
                case CrawlTypeSelenium -> {
                    var storageResult = seleniumCrawler.crawl(task);
                    this.updateTaskStatus(task.getId(), EnumValues.CrawlTaskStatus.Finished, null);
                    if (task.getCommitPublish()) {
                        this.publishProcessTask(storageResult);
                    }
                }
                case CrawlTypeEasySpider -> easyspiderCrawler.crawl(task);
                case CrawlTypeCustom -> {
                    var crawlerId = task.getAdditionalInfoOrDefault("crawler_id", "");
                    if (crawlerId.isEmpty()) {
                        log.error("crawler_id not provided for custom crawler");
                        this.updateTaskStatus(
                                task.getId(),
                                EnumValues.CrawlTaskStatus.Failed,
                                "crawler_id not provided for custom crawler"
                        );
                        return;
                    }

                    var crawler = customCrawler.getCrawler(crawlerId);
                    if (crawler == null) {
                        log.warn("crawler id not exists");
                        this.updateTaskStatus(
                                task.getId(),
                                EnumValues.CrawlTaskStatus.Failed,
                                "crawler id not exists: " + crawlerId
                        );
                        return;
                    }

                    customCrawler.request(task, crawler);
//                    this.updateTaskStatus(task.getId(), EnumValues.CrawlTaskStatus.Finished, null);
//                    this.publishProcessTask(storageResult);
                }
                default -> {
                    log.error("unknown crawl type: {}, id: {}", task.getCrawlType(), task.getId());
                    this.updateTaskStatus(task.getId(),
                            EnumValues.CrawlTaskStatus.Failed, "unknown crawl type: %s".formatted(task.getCrawlType())
                    );
                }
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("Invalid CrawlJob message", e);
        } catch (Exception e) {
            if (id != null) {
                this.updateTaskStatus(id, EnumValues.CrawlTaskStatus.Failed, "task failed: %s".formatted(e.toString()));
            }

            log.error("IO error", e);
        }
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
