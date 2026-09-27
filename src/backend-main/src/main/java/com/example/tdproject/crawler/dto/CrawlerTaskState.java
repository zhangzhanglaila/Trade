package com.example.tdproject.crawler.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CrawlerTaskState {
    private String taskId;
    private String callbackId;
    private String source;
    private CrawlerTaskType type;
    private CrawlerTaskStatus status;
    private List<Map<String, Object>> listResult;
    private Map<String, Map<String, Object>> contentResult;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
