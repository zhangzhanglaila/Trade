package com.example.tdproject.crawler.controller;

import com.example.tdproject.crawler.dto.CrawlerFetchContentRequest;
import com.example.tdproject.crawler.dto.CrawlerFetchListRequest;
import com.example.tdproject.crawler.dto.CrawlerImportRequest;
import com.example.tdproject.crawler.dto.CrawlerImportResponse;
import com.example.tdproject.crawler.dto.CrawlerTaskCreatedResponse;
import com.example.tdproject.crawler.dto.CrawlerTaskState;
import com.example.tdproject.crawler.service.CrawlerBridgeService;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crawler")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "新闻采集桥接", description = "对接 Python 新闻爬虫服务")
public class CrawlerBridgeController {

    private final CrawlerBridgeService crawlerBridgeService;

    @GetMapping("/sources")
    @Operation(summary = "获取可用采集源")
    public ResponseEntity<Result<List<String>>> getSources() {
        return ResponseEntity.ok(Result.build(crawlerBridgeService.getEnabledSources()));
    }

    @PostMapping("/fetch-list")
    @Operation(summary = "抓取新闻列表")
    public ResponseEntity<Result<CrawlerTaskCreatedResponse>> fetchList(@RequestBody CrawlerFetchListRequest request) {
        return ResponseEntity.ok(Result.build(crawlerBridgeService.fetchList(request)));
    }

    @PostMapping("/fetch-content")
    @Operation(summary = "抓取新闻正文")
    public ResponseEntity<Result<CrawlerTaskCreatedResponse>> fetchContent(@RequestBody CrawlerFetchContentRequest request) {
        return ResponseEntity.ok(Result.build(crawlerBridgeService.fetchContent(request)));
    }

    @PostMapping("/import")
    @Operation(summary = "导入抓取结果到新闻库")
    public ResponseEntity<Result<CrawlerImportResponse>> importResults(@RequestBody CrawlerImportRequest request) {
        return ResponseEntity.ok(Result.build(crawlerBridgeService.importResults(request.getTaskId())));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询采集任务")
    public ResponseEntity<Result<CrawlerTaskState>> getTask(@PathVariable String taskId) {
        return ResponseEntity.ok(Result.build(crawlerBridgeService.getTask(taskId)));
    }

    @PostMapping("/callback/datasource")
    @Operation(summary = "接收列表回调")
    public ResponseEntity<Map<String, Object>> datasourceCallback(@RequestParam("callback_id") String callbackId,
                                                             @RequestBody Map<String, Object> payload) {
        crawlerBridgeService.handleDatasourceCallback(callbackId, payload);
        return ResponseEntity.ok(Map.of("code", 0, "msg", "ok"));
    }

    @PostMapping("/callback/content")
    @Operation(summary = "接收正文回调")
    public ResponseEntity<Map<String, Object>> contentCallback(@RequestParam("callback_id") String callbackId,
                                                          @RequestBody Map<String, Object> payload) {
        crawlerBridgeService.handleContentCallback(callbackId, payload);
        return ResponseEntity.ok(Map.of("code", 0, "msg", "ok"));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception e) {
        log.error("crawler bridge error", e);
        return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
    }
}
