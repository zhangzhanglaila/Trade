package cait.collector.configure;

import cait.collector.common.Constants;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Collections;

@Configuration
public class KafkaConfigure {
    @Bean("crawl-job-kf-topic")
    public NewTopic crawlJobTopic() {
        return TopicBuilder
                .name(Constants.Kafka.Topics.CrawlJob)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaTemplate<String, byte[]> bytesTemplate(ProducerFactory<String, byte[]> pf) {
        return new KafkaTemplate<>(pf,
                Collections.singletonMap(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)
        );
    }
}
