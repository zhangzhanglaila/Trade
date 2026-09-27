package cait.collector.configure;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class CustomApiConfigure {
    @Value("${spring.cloud.consul.discovery.instance-id}")
    private String instanceId;
}
