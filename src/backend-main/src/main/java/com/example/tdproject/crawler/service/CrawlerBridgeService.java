package com.example.tdproject.crawler.service;

import com.example.tdproject.crawler.dto.CrawlerFetchContentRequest;
import com.example.tdproject.crawler.dto.CrawlerFetchListRequest;
import com.example.tdproject.crawler.dto.CrawlerImportResponse;
import com.example.tdproject.crawler.dto.CrawlerTaskCreatedResponse;
import com.example.tdproject.crawler.dto.CrawlerTaskState;

import java.util.List;
import java.util.Map;

public interface CrawlerBridgeService {

    List<String> getEnabledSources();

    CrawlerTaskCreatedResponse fetchList(CrawlerFetchListRequest request);

    CrawlerTaskCreatedResponse fetchContent(CrawlerFetchContentRequest request);

    CrawlerTaskState getTask(String taskId);

    void handleDatasourceCallback(String callbackId, Map<String, Object> payload);

    void handleContentCallback(String callbackId, Map<String, Object> payload);

    CrawlerImportResponse importResults(String taskId);
}
