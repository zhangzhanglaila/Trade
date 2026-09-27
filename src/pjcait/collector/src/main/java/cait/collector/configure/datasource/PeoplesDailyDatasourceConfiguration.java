package cait.collector.configure.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cait.collector.datasource.peoples-daily")
public class PeoplesDailyDatasourceConfiguration {

    private Boolean enabled;

}
