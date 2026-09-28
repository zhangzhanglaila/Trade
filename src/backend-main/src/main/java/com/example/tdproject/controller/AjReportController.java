package com.example.tdproject.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<Void> redirectToAjReport(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(buildAjReportUrl(request)));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "获取 AJ-Report 入口页所需配置")
    public ResponseEntity<Map<String, Object>> getConfig(HttpServletRequest request) {
        String base = resolveBaseUrl(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("baseUrl", base);
        body.put("entryPath", ajReportEntryPath);
        body.put("targetUrl", base + ajReportEntryPath);
        body.put("screen1Path", screen1Path);
        body.put("screen2Path", screen2Path);
        body.put("screen1Url", base + screen1Path);
        body.put("screen2Url", base + screen2Path);
        body.put("embedEnabled", embedEnabled);
        body.put("frameTitle", frameTitle);
        return ResponseEntity.ok(body);
    }

    private String buildAjReportUrl(HttpServletRequest request) {
        return resolveBaseUrl(request) + ajReportEntryPath;
    }

    /**
     * 大屏是与主后端同机、跑在独立端口（默认 9095）的另一个服务。
     * 如果直接把配置里的 base-url（默认 http://127.0.0.1:9095）返回给前端，
     * 浏览器会去访问**自己机器**的 127.0.0.1:9095，必然打不开。
     * 因此这里用请求里的 host（nginx 已透传 Host 头）替换掉回环地址，
     * 换端口 9095，得到浏览器真正够得着的地址；域名/IP/localhost 都自动适配。
     * 若运维显式把 base-url 配成了非回环地址（独立域名、反代等），则尊重配置。
     */
    private String resolveBaseUrl(HttpServletRequest request) {
        if (!isLoopback(ajReportBaseUrl)) {
            return ajReportBaseUrl;
        }
        String host = request == null ? null : request.getServerName();
        if (host == null || host.isBlank()) {
            return ajReportBaseUrl;
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";   // IPv6 字面量
        }
        return "http://" + host + ":9095";
    }

    private boolean isLoopback(String url) {
        return url != null
                && (url.contains("127.0.0.1") || url.contains("localhost")
                    || url.contains("[::1]"));
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
