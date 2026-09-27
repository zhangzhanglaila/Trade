package cait.collector.configure.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cait.collector.datasource.google")
public class GoogleDatasourceConfiguration {

    private Boolean enabled;

    private String secretKey;

    private String cx;

    private List<Search> search;

    @Data
    public static class Search {

        private String query;

        private String page;

        private String language;

        private String dateRestrict;

    }
}
