package com.example.tdproject.crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    private String baseUrl;

    private String callbackBaseUrl;

    private List<String> enabledSources = new ArrayList<>(List.of("kazinform", "mofcom"));
}
