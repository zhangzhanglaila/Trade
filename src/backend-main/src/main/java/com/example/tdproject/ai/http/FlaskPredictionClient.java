package com.example.tdproject.ai.http;

import com.example.tdproject.ai.config.AiProperties;
import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.dto.PredictResponse;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlaskPredictionClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Map<String, Object> health() {
        String baseUrl = props.getFlask().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Flask baseUrl 未配置：ai.flask.base-url");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("Flask health HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return objectMapper.readValue(resp.body(), new TypeReference<>() {});
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Flask health 请求失败: " + e.getMessage(), e);
        }
    }

    public PredictResponse predict(PredictRequest req) {
        String baseUrl = props.getFlask().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Flask baseUrl 未配置：ai.flask.base-url");
        }

        String endpoint = resolveEndpoint(req.getTradeType(), req.getTarget());

        Map<String, Object> body = new HashMap<>();
        body.put("贸易伙伴名称", req.getTradePartnerName());
        body.put("商品名称", req.getProductName());
        body.put("贸易方式", req.getTradeMode());
        body.put("注册地名称", req.getRegisterName());
        body.put("year", req.getYear());
        body.put("month", req.getMonth());

        Map<String, Object> raw = postJson(baseUrl + endpoint, body);

        Double value = null;
        String unit = raw.get("unit") != null ? String.valueOf(raw.get("unit")) : null;

        // 按 Flask app.py 字段命名解析
        String valueKey = null;
        if ("in".equalsIgnoreCase(req.getTradeType()) && "price".equalsIgnoreCase(req.getTarget())) {
            valueKey = "predicted_in_price";
        } else if ("in".equalsIgnoreCase(req.getTradeType()) && "quantity".equalsIgnoreCase(req.getTarget())) {
            valueKey = "predicted_in_quantity";
        } else if ("out".equalsIgnoreCase(req.getTradeType()) && "price".equalsIgnoreCase(req.getTarget())) {
            valueKey = "predicted_out_price";
        } else if ("out".equalsIgnoreCase(req.getTradeType()) && "quantity".equalsIgnoreCase(req.getTarget())) {
            valueKey = "predicted_out_quantity";
        }

        if (valueKey != null && raw.get(valueKey) != null) {
            try {
                value = Double.valueOf(String.valueOf(raw.get(valueKey)));
            } catch (NumberFormatException ignore) {
            }
        }

        return PredictResponse.builder()
                .tradeType(req.getTradeType())
                .target(req.getTarget())
                .value(value)
                .unit(unit)
                .raw(raw)
                .build();
    }

    private String resolveEndpoint(String tradeType, String target) {
        if (tradeType == null || target == null) {
            throw new IllegalArgumentException("预测参数缺少 tradeType 或 target");
        }

        String t = tradeType.trim().toLowerCase();
        String g = target.trim().toLowerCase();

        if ("in".equals(t) && "price".equals(g)) return "/predict_in_price";
        if ("in".equals(t) && "quantity".equals(g)) return "/predict_in_quantity";
        if ("out".equals(t) && "price".equals(g)) return "/predict_out_price";
        if ("out".equals(t) && "quantity".equals(g)) return "/predict_out_quantity";

        throw new IllegalArgumentException("不支持的预测组合: tradeType=" + tradeType + ", target=" + target);
    }

    private Map<String, Object> postJson(String url, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw buildError(resp.statusCode(), resp.body());
            }
            return objectMapper.readValue(resp.body(), new TypeReference<>() {});
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Flask 请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把 Flask 的非 2xx 响应转成异常。
     *
     * <p>「组合无历史数据」（404）是用户选了库里不存在的组合导致的**业务性失败**，
     * 不是系统错误 —— 直接抛 `Flask HTTP 404: {...一堆 JSON...}` 会让用户看到
     * 无从下手的乱码。这里识别出该情况，抛 {@link IllegalArgumentException}
     * 并带上友好的中文说明；调用方（AiChatController）据此转成引导性回答。
     * 其余错误（500 等）仍保留原始信息，方便排查。</p>
     */
    private RuntimeException buildError(int status, String body) {
        if (status == 404) {
            String err = null;
            try {
                Map<String, Object> m = objectMapper.readValue(body, new TypeReference<>() {});
                if (m.get("error") != null) err = String.valueOf(m.get("error"));
            } catch (Exception ignore) {
                // body 不是 JSON，忽略
            }
            if (err != null && err.contains("没有历史数据")) {
                return new IllegalArgumentException(
                        "该「贸易伙伴 + 商品 + 贸易方式 + 注册地」组合在库中没有历史数据，无法预测。\n"
                                + "请确认商品名称、贸易方式、境内注册地都用了库里的规范写法"
                                + "（例如注册地要用「新疆维吾尔自治区」而非「新疆」）后重试。");
            }
        }
        return new IllegalStateException("Flask HTTP " + status + ": " + body);
    }
}
