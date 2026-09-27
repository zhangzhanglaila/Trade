package com.example.tdproject.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CrawlerImportResponse {
    private Integer importedCount;
    private Integer skippedCount;
}
