package cait.collector.configure;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class CaitCollectorConfiguration {

    @Value("${cait.collector.data-update-job-cron}")
    private String dataUpdateJobCron;

    @Value("${cait.collector.run-mode}")
    private String runMode;

    @Value("${cait.collector.data-store-type}")
    private String dataStorageType;

    @Value("${cait.collector.data-store-location}")
    private String dataStorageLocation;

}
