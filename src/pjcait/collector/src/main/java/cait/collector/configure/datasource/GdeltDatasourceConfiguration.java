package cait.collector.configure.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "cait.collector.datasource.gdelt")
public class GdeltDatasourceConfiguration {

    private Boolean enabled;

    private Boolean cleanHtml;

    private List<String> themeFilterKeywords;
    private List<String> themeExcludeKeywords;
    private Float themeKeywordsMinRatio;

    private List<String> locationFilterKeywords;

    private String downloadCacheDir;

}
