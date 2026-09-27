package com.example.tdproject.ai.http;

import com.example.tdproject.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 生成 embedding 向量。
     *
     * 注意：千问的具体 HTTP 协议/字段随你们提供的 endpoint 而定；这里按常见格式尽量兼容解析。
     */
    public List<Double> embed(String text) {
        String apiKey = props.getQwen().getApiKey();
        String baseUrl = props.getQwen().getBaseUrl();
        String model = props.getQwen().getEmbedding().getModel();
        String endpoint = getEndpointOrThrow("embedding");

        if (isBlank(baseUrl) || isBlank(apiKey) || isBlank(model)) {
            throw new IllegalStateException("千问 embedding 配置不完整：请检查 ai.qwen.base-url/api-key/embedding.model");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", List.of(text));

        JsonNode root = postJson(baseUrl + endpoint, apiKey, body);

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("千问 embedding 返回缺少 data 数组：" + root.toString());
        }
        JsonNode emb = data.get(0).path("embedding");
        if (!emb.isArray()) {
            throw new IllegalStateException("千问 embedding 返回缺少 data[0].embedding：" + root.toString());
        }

        List<Double> vec = new ArrayList<>(emb.size());
        for (JsonNode v : emb) {
            vec.add(v.asDouble());
        }
        return vec;
    }

    /**
     * 生成式对话。
     * messages: [{role: "system"|"user"|"assistant", content: "..."}]
     */
    public String chat(List<Map<String, String>> messages, Map<String, Object> extraParams) {
        String apiKey = props.getQwen().getApiKey();
        String baseUrl = props.getQwen().getBaseUrl();
        String model = props.getQwen().getChat().getModel();
        String endpoint = getEndpointOrThrow("chat");

        if (isBlank(baseUrl) || isBlank(apiKey) || isBlank(model)) {
            throw new IllegalStateException("千问 chat 配置不完整：请检查 ai.qwen.base-url/api-key/chat.model");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (extraParams != null) {
            body.putAll(extraParams);
        }

        JsonNode root = postJson(baseUrl + endpoint, apiKey, body);

        // 常见：choices[0].message.content
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (!content.isMissingNode()) {
                return content.asText();
            }
        }

        // 兜底：output.text
        JsonNode output = root.path("output");
        if (!output.isMissingNode()) {
            JsonNode text = output.path("text");
            if (!text.isMissingNode()) {
                return text.asText();
            }
        }

        throw new IllegalStateException("无法从千问 chat 返回解析 content：" + root.toString());
    }

    private String getEndpointOrThrow(String key) {
        Map<String, String> endpoints = props.getQwen().getEndpoints();
        if (endpoints == null || isBlank(endpoints.get(key))) {
            throw new IllegalStateException("千问 endpoint 未配置：ai.qwen.endpoints." + key);
        }
        return endpoints.get(key);
    }

    private JsonNode postJson(String url, String apiKey, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("千问 HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return objectMapper.readTree(resp.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("千问请求失败: " + e.getMessage(), e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
