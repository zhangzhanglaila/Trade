package cait.collector.configure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cait.collector.easyspider")
public class EasyspiderConfigure {

    private String wrapperServiceName;

    private String callbackUrl;

}
