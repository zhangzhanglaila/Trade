package com.example.tdproject.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerTaskCreatedResponse {
    private String taskId;
    private String callbackId;
    private String status;
}
