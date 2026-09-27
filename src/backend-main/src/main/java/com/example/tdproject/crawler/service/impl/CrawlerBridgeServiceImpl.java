package com.example.tdproject.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.tdproject.crawler.config.CrawlerProperties;
import com.example.tdproject.crawler.dto.CrawlerFetchContentRequest;
import com.example.tdproject.crawler.dto.CrawlerFetchListRequest;
import com.example.tdproject.crawler.dto.CrawlerImportResponse;
import com.example.tdproject.crawler.dto.CrawlerTaskCreatedResponse;
import com.example.tdproject.crawler.dto.CrawlerTaskState;
import com.example.tdproject.crawler.dto.CrawlerTaskStatus;
import com.example.tdproject.crawler.dto.CrawlerTaskType;
import com.example.tdproject.crawler.service.CrawlerBridgeService;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.service.TNewsCorpusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerBridgeServiceImpl implements CrawlerBridgeService {

    private static final String DATASOURCE_CALLBACK_PATH = "/crawler/callback/datasource";
    private static final String CONTENT_CALLBACK_PATH = "/crawler/callback/content";

    private final CrawlerProperties crawlerProperties;
    private final RestTemplate restTemplate;
    private final TNewsCorpusService newsCorpusService;

    private final Map<String, CrawlerTaskState> taskStore = new ConcurrentHashMap<>();
    private final Map<String, String> callbackMapping = new ConcurrentHashMap<>();

    @Override
    public List<String> getEnabledSources() {
        return crawlerProperties.getEnabledSources();
    }

    @Override
    public CrawlerTaskCreatedResponse fetchList(CrawlerFetchListRequest request) {
        String source = normalizeAndValidateSource(request.getSource());
        String taskId = UUID.randomUUID().toString();
        String callbackId = UUID.randomUUID().toString();

        CrawlerTaskState task = initTask(taskId, callbackId, source, CrawlerTaskType.FETCH_LIST);
        taskStore.put(taskId, task);
        callbackMapping.put(callbackId, taskId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callback_instance", buildCallbackInstance(DATASOURCE_CALLBACK_PATH));
        payload.put("callback_id", callbackId);
        if (request.getLastTime() != null) {
            payload.put("last_time", request.getLastTime());
        }

        postToPython("/api/datasource/async/" + source, payload);
        task.setStatus(CrawlerTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.now());
        return new CrawlerTaskCreatedResponse(taskId, callbackId, task.getStatus().name());
    }

    @Override
    public CrawlerTaskCreatedResponse fetchContent(CrawlerFetchContentRequest request) {
        String source = normalizeAndValidateSource(request.getSource());
        if (CollectionUtils.isEmpty(request.getUrls())) {
            throw new IllegalArgumentException("urls 不能为空");
        }

        List<String> urls = request.getUrls().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("urls 不能为空");
        }

        String taskId = UUID.randomUUID().toString();
        String callbackId = UUID.randomUUID().toString();

        CrawlerTaskState task = initTask(taskId, callbackId, source, CrawlerTaskType.FETCH_CONTENT);
        taskStore.put(taskId, task);
        callbackMapping.put(callbackId, taskId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("task_id", taskId);
        params.put("callback_instance", buildCallbackInstance(CONTENT_CALLBACK_PATH));
        params.put("callback_id", callbackId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("urls", urls);
        payload.put("params", params);

        postToPython("/api/crawl/async/" + source, payload);
        task.setStatus(CrawlerTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.now());
        return new CrawlerTaskCreatedResponse(taskId, callbackId, task.getStatus().name());
    }

    @Override
    public CrawlerTaskState getTask(String taskId) {
        CrawlerTaskState task = taskStore.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    @Override
    public void handleDatasourceCallback(String callbackId, Map<String, Object> payload) {
        CrawlerTaskState task = getTaskByCallbackId(callbackId);
        task.setUpdatedAt(LocalDateTime.now());
        Integer code = asInteger(payload.get("code"));
        if (code != null && code == 0) {
            Map<String, Object> data = asMap(payload.get("data"));
            List<Map<String, Object>> result = asListOfMap(data.get("result"));
            task.setListResult(result);
            task.setStatus(CrawlerTaskStatus.SUCCESS);
            task.setError(null);
            return;
        }
        task.setStatus(CrawlerTaskStatus.FAILED);
        task.setError(asString(payload.get("msg")));
    }

    @Override
    public void handleContentCallback(String callbackId, Map<String, Object> payload) {
        CrawlerTaskState task = getTaskByCallbackId(callbackId);
        task.setUpdatedAt(LocalDateTime.now());
        Integer code = asInteger(payload.get("code"));
        if (code != null && code == 0) {
            Map<String, Object> data = asMap(payload.get("data"));
            Map<String, Map<String, Object>> result = asNestedMap(data.get("result"));
            task.setContentResult(result);
            task.setStatus(CrawlerTaskStatus.SUCCESS);
            task.setError(null);
            return;
        }
        task.setStatus(CrawlerTaskStatus.FAILED);
        task.setError(asString(payload.get("msg")));
    }

    @Override
    public CrawlerImportResponse importResults(String taskId) {
        CrawlerTaskState task = getTask(taskId);
        if (task.getType() != CrawlerTaskType.FETCH_CONTENT) {
            throw new IllegalArgumentException("仅正文抓取任务支持导入");
        }
        if (task.getStatus() != CrawlerTaskStatus.SUCCESS || task.getContentResult() == null) {
            throw new IllegalArgumentException("当前任务暂无可导入结果");
        }

        int imported = 0;
        int skipped = 0;
        for (Map<String, Object> item : task.getContentResult().values()) {
            TNewsCorpus entity = toNewsCorpus(item);
            if (!StringUtils.hasText(entity.getNewsTitle()) || !StringUtils.hasText(entity.getNewsContent())) {
                skipped++;
                continue;
            }
            if (existsDuplicate(entity)) {
                skipped++;
                continue;
            }
            newsCorpusService.saveNewsCorpus(entity);
            imported++;
        }
        return new CrawlerImportResponse(imported, skipped);
    }

    private boolean existsDuplicate(TNewsCorpus entity) {
        QueryWrapper<TNewsCorpus> query = new QueryWrapper<>();
        query.eq("title", entity.getNewsTitle());
        if (entity.getPublishTime() != null) {
            query.eq("publish_time", entity.getPublishTime());
        }
        query.last("limit 1");
        return newsCorpusService.getOne(query, false) != null;
    }

    private TNewsCorpus toNewsCorpus(Map<String, Object> item) {
        TNewsCorpus entity = new TNewsCorpus();
        entity.setNewsTitle(asString(item.get("title")));
        String contentText = asString(item.get("content_text"));
        entity.setNewsContent(StringUtils.hasText(contentText) ? contentText : asString(item.get("content")));
        entity.setPublishTime(parseDate(asString(item.get("date"))));
        return entity;
    }

    private Date parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new Date();
        }

        List<DateTimeFormatter> dateTimeFormatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        );
        for (DateTimeFormatter formatter : dateTimeFormatters) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(raw, formatter);
                return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
            }
            try {
                LocalDate date = LocalDate.parse(raw, formatter);
                return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            return Date.from(Instant.parse(raw));
        } catch (Exception ignored) {
        }
        try {
            return Date.from(OffsetDateTime.parse(raw).toInstant());
        } catch (Exception ignored) {
        }
        try {
            return Date.from(ZonedDateTime.parse(raw).toInstant());
        } catch (Exception ignored) {
        }
        return new Date();
    }

    private CrawlerTaskState initTask(String taskId, String callbackId, String source, CrawlerTaskType type) {
        CrawlerTaskState task = new CrawlerTaskState();
        task.setTaskId(taskId);
        task.setCallbackId(callbackId);
        task.setSource(source);
        task.setType(type);
        task.setStatus(CrawlerTaskStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setListResult(Collections.emptyList());
        task.setContentResult(Collections.emptyMap());
        return task;
    }

    private void postToPython(String path, Map<String, Object> payload) {
        String url = crawlerProperties.getBaseUrl() + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                new ParameterizedTypeReference<>() {}
        );
        Map<String, Object> body = response.getBody();
        Integer code = body == null ? null : asInteger(body.get("code"));
        if (code == null || code != 0) {
            throw new IllegalStateException("Python 爬虫调用失败: " + (body == null ? "empty response" : body.get("msg")));
        }
    }

    private String buildCallbackInstance(String callbackPath) {
        if (StringUtils.hasText(crawlerProperties.getCallbackBaseUrl())) {
            return UriComponentsBuilder.fromHttpUrl(crawlerProperties.getCallbackBaseUrl())
                    .path(callbackPath)
                    .toUriString();
        }
        return callbackPath;
    }

    private String normalizeAndValidateSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("source 不能为空");
        }
        String normalized = source.trim();
        if (!crawlerProperties.getEnabledSources().contains(normalized)) {
            throw new IllegalArgumentException("不支持的数据源: " + source);
        }
        return normalized;
    }

    private CrawlerTaskState getTaskByCallbackId(String callbackId) {
        String taskId = callbackMapping.get(callbackId);
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("未知 callbackId: " + callbackId);
        }
        return getTask(taskId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> asNestedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() instanceof Map<?, ?> item) {
                result.put(String.valueOf(entry.getKey()), (Map<String, Object>) item);
            }
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
