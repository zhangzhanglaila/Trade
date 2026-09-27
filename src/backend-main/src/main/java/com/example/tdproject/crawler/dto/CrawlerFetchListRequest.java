package com.example.tdproject.crawler.dto;

import lombok.Data;

@Data
public class CrawlerFetchListRequest {
    private String source;
    private Long lastTime;
}
