package cait.collector.messaging;

import cait.collector.common.Constants;
import cait.collector.common.mapper.CrawlTaskMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CrawlTaskEntity;
import cait.common.pb.CollectorPb.PageCrawlTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CrawlTaskPublisher {
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper jacksonObjectMapper;
    private final CrawlTaskMapper crawlTaskMapper;

    public CrawlTaskPublisher(KafkaTemplate<String, byte[]> kafkaTemplate, ObjectMapper jacksonObjectMapper, CrawlTaskMapper crawlTaskMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.crawlTaskMapper = crawlTaskMapper;
    }

    public void publishCrawlJob(PageCrawlTask task) {
        CrawlTaskEntity crawlTaskEntity = null;
        try {
            crawlTaskEntity = convert(task);
            crawlTaskMapper.insert(crawlTaskEntity);
        } catch (Exception e) {
            log.error("error when record crawl task", e);
        }

        var future = kafkaTemplate.send(Constants.Kafka.Topics.CrawlJob, task.toByteArray());

        final CrawlTaskEntity finalCrawlTaskEntity = crawlTaskEntity;
        future.thenAccept(result -> {
            log.info("CrawlJob published: {}", result);
            try {
                if (finalCrawlTaskEntity != null) {
                    finalCrawlTaskEntity.setStatus(EnumValues.CrawlTaskStatus.Published);
                }
            } catch (Exception e) {
                log.error("error when record crawl task", e);
            }
        });

        future.exceptionally(ex -> {
            log.warn("CrawlJob publish exception", ex);
            try {
                if (finalCrawlTaskEntity != null) {
                    finalCrawlTaskEntity.setStatus(EnumValues.CrawlTaskStatus.Failed);
                    finalCrawlTaskEntity.setMessage(ex.getMessage());
                }
            } catch (Exception e) {
                log.error("error when record crawl task", e);
            }

            return null;
        });
    }

    private CrawlTaskEntity convert(PageCrawlTask task) throws JsonProcessingException {
        var urls = jacksonObjectMapper.writeValueAsString(task.getUrlList().stream().toList());
        var additionalInfo = jacksonObjectMapper.writeValueAsString(task.getAdditionalInfoMap());

        CrawlTaskEntity crawlTaskEntity = new CrawlTaskEntity();
        crawlTaskEntity.setId(task.getId());
        crawlTaskEntity.setCrawlType(task.getCrawlType().getNumber());
        crawlTaskEntity.setSource(task.getSource().getNumber());
        crawlTaskEntity.setUrls(urls);
        crawlTaskEntity.setCleanHtml(task.getCleanHtml());
        crawlTaskEntity.setArchive(task.getArchive());
        crawlTaskEntity.setAdditionalInfo(additionalInfo);
        crawlTaskEntity.setStatus(EnumValues.CrawlTaskStatus.Ready);

        return crawlTaskEntity;
    }
}
