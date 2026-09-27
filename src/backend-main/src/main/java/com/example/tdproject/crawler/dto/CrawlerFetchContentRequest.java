package com.example.tdproject.crawler.dto;

import lombok.Data;

import java.util.List;

@Data
public class CrawlerFetchContentRequest {
    private String source;
    private List<String> urls;
}
