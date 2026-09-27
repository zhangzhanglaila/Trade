package cait.collector.messaging;

import cait.collector.common.Constants;
import cait.common.pb.ProcessorPb.NewsProcessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProcessorTaskPublisher {
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public ProcessorTaskPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishJobProcessingMsg(NewsProcessMessage task) {
        var future = kafkaTemplate.send(Constants.Kafka.Topics.ProcessJob, task.toByteArray());
        future.thenAccept(result -> log.info("ProcessJob published: {}", result));
        future.exceptionally(ex -> {
            log.warn("ProcessJob publish exception", ex);
            return null;
        });
    }
}
