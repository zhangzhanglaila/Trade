package com.example.tdproject.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/aj-report")
@Tag(name = "AJ-Report 接入", description = "提供大屏统一入口")
public class AjReportController {

    private final String ajReportBaseUrl;
    private final String ajReportEntryPath;
    private final String screen1Path;
    private final String screen2Path;
    private final boolean embedEnabled;
    private final String frameTitle;

    public AjReportController(
            @Value("${integration.aj-report.base-url}") String ajReportBaseUrl,
            @Value("${integration.aj-report.entry-path}") String ajReportEntryPath,
            @Value("${integration.aj-report.screen1-path:/index.html#/aj/eYmVi5sx}") String screen1Path,
            @Value("${integration.aj-report.screen2-path:/index.html#/aj/9jMigyT7}") String screen2Path,
            @Value("${integration.aj-report.embed-enabled:true}") boolean embedEnabled,
            @Value("${integration.aj-report.frame-title:AJ-Report 大屏入口}") String frameTitle) {
        this.ajReportBaseUrl = trimTrailingSlash(ajReportBaseUrl);
        this.ajReportEntryPath = normalizeEntryPath(ajReportEntryPath);
        this.screen1Path = normalizeEntryPath(screen1Path);
        this.screen2Path = normalizeEntryPath(screen2Path);
        this.embedEnabled = embedEnabled;
        this.frameTitle = frameTitle;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "打开 AJ-Report 内嵌入口页")
    public String index() {
        return "redirect:/aj-report.html";
    }

    @GetMapping("/go")
    @Operation(summary = "跳转到 AJ-Report 独立服务")
    public ResponseEntity<Void> redirectToAjReport() {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(buildAjReportUrl()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "获取 AJ-Report 入口页所需配置")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("baseUrl", ajReportBaseUrl);
        body.put("entryPath", ajReportEntryPath);
        body.put("targetUrl", buildAjReportUrl());
        body.put("screen1Path", screen1Path);
        body.put("screen2Path", screen2Path);
        body.put("screen1Url", buildScreenUrl(screen1Path));
        body.put("screen2Url", buildScreenUrl(screen2Path));
        body.put("embedEnabled", embedEnabled);
        body.put("frameTitle", frameTitle);
        return ResponseEntity.ok(body);
    }

    private String buildAjReportUrl() {
        return ajReportBaseUrl + ajReportEntryPath;
    }

    private String buildScreenUrl(String path) {
        return ajReportBaseUrl + path;
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:9095";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String normalizeEntryPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
