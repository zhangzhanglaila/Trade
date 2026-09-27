package com.example.tdproject.ai.intent;

import com.example.tdproject.ai.dto.AiChatRequest;
import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.http.DashScopeClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRouter {

    public static final String ROUTE_PREDICT = "PREDICT";
    public static final String ROUTE_RAG_NEWS = "RAG_NEWS";

    private final DashScopeClient dashScopeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentResult route(AiChatRequest req) {
        String text = req != null ? req.getText() : null;
        if (text == null) text = "";

        // Stage 0: 如果用户显式给了预测槽位，则直接走预测
        if (hasExplicitPredictParams(req)) {
            PredictRequest slots = PredictRequest.builder()
                    .tradeType(req.getTradeType())
                    .target(req.getTarget())
                    .year(req.getYear())
                    .month(req.getMonth())
                    .tradePartnerName(req.getTradePartnerName())
                    .productName(req.getProductName())
                    .tradeMode(req.getTradeMode())
                    .registerName(req.getRegisterName())
                    .build();

            return IntentResult.builder()
                    .route(ROUTE_PREDICT)
                    .predictSlots(slots)
                    .ragFilters(Map.of("country", req.getCountry(), "year", req.getYear()))
                    .debug(Map.of("stage", "explicit"))
                    .build();
        }

        // Stage 1: 规则优先
        String ruleRoute = ruleBasedRoute(text);
        if (ruleRoute != null) {
            return IntentResult.builder()
                    .route(ruleRoute)
                    .ragFilters(Map.of("country", req.getCountry(), "year", req.getYear()))
                    .debug(Map.of("stage", "rule"))
                    .build();
        }

        // Stage 2: LLM 兜底（如未配置千问，则退回 RAG）
        try {
            IntentResult llm = llmRoute(text, req);
            if (llm != null && llm.getRoute() != null) {
                Map<String, Object> debug = new HashMap<>();
                debug.put("stage", "llm");
                if (llm.getDebug() != null) debug.putAll(llm.getDebug());
                llm.setDebug(debug);
                return llm;
            }
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，回退到 RAG: {}", e.getMessage());
        }

        return IntentResult.builder()
                .route(ROUTE_RAG_NEWS)
                .ragFilters(Map.of("country", req.getCountry(), "year", req.getYear()))
                .debug(Map.of("stage", "fallback"))
                .build();
    }

    private boolean hasExplicitPredictParams(AiChatRequest req) {
        if (req == null) return false;
        return notBlank(req.getTradeType())
                && notBlank(req.getTarget())
                && req.getYear() != null
                && req.getMonth() != null
                && notBlank(req.getTradePartnerName())
                && notBlank(req.getProductName())
                && notBlank(req.getTradeMode())
                && notBlank(req.getRegisterName());
    }

    private String ruleBasedRoute(String text) {
        String t = text.toLowerCase(Locale.ROOT);

        // 明显预测关键词
        String[] predictKeywords = {
                "预测", "单价", "数量", "进口", "出口", "下月", "未来", "走势", "估计", "预估", "price", "quantity"
        };
        for (String kw : predictKeywords) {
            if (t.contains(kw)) {
                return ROUTE_PREDICT;
            }
        }

        // 明显检索关键词
        String[] ragKeywords = {
                "新闻", "报道", "来源", "发生了什么", "近期", "摘要", "有哪些", "检索", "资料", "语料"
        };
        for (String kw : ragKeywords) {
            if (t.contains(kw)) {
                return ROUTE_RAG_NEWS;
            }
        }

        return null;
    }

    private IntentResult llmRoute(String text, AiChatRequest req) {
        String system = "你是一个严格的意图分类器。\n"
                + "给定用户输入，请判断应该走预测(PREDICT)还是新闻检索问答(RAG_NEWS)。\n"
                + "只输出严格 JSON，不要输出多余文本。\n"
                + "JSON 格式：{\"route\":\"PREDICT\"|\"RAG_NEWS\",\"slots\":{...}}\n"
                + "如果 route=PREDICT，slots 需要尽量抽取：tradeType(in/out)、target(price/quantity)、year、month、tradePartnerName、productName、tradeMode、registerName。\n"
                + "如果 route=RAG_NEWS，slots 可选包含 country、year。";

        String user = "用户输入：" + text;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user));

        String content = dashScopeClient.chat(messages, Map.of("temperature", 0));

        Map<String, Object> json;
        try {
            json = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 LLM 返回 JSON 失败: " + e.getOriginalMessage() + ", raw=" + content, e);
        }
        String route = json.get("route") != null ? String.valueOf(json.get("route")) : null;
        Object slotsObj = json.get("slots");

        Map<String, Object> slots = slotsObj instanceof Map ? (Map<String, Object>) slotsObj : new HashMap<>();

        if (ROUTE_PREDICT.equalsIgnoreCase(route)) {
            PredictRequest predictSlots = PredictRequest.builder()
                    .tradeType(asString(slots.getOrDefault("tradeType", req.getTradeType())))
                    .target(asString(slots.getOrDefault("target", req.getTarget())))
                    .year(asInt(slots.getOrDefault("year", req.getYear())))
                    .month(asInt(slots.getOrDefault("month", req.getMonth())))
                    .tradePartnerName(asString(slots.getOrDefault("tradePartnerName", req.getTradePartnerName())))
                    .productName(asString(slots.getOrDefault("productName", req.getProductName())))
                    .tradeMode(asString(slots.getOrDefault("tradeMode", req.getTradeMode())))
                    .registerName(asString(slots.getOrDefault("registerName", req.getRegisterName())))
                    .build();

            return IntentResult.builder()
                    .route(ROUTE_PREDICT)
                    .predictSlots(predictSlots)
                    .ragFilters(Map.of("country", req.getCountry(), "year", req.getYear()))
                    .debug(Map.of("llmRaw", content))
                    .build();
        }

        // 默认走 RAG
        Map<String, Object> ragFilters = new HashMap<>();
        ragFilters.put("country", slots.getOrDefault("country", req.getCountry()));
        ragFilters.put("year", slots.getOrDefault("year", req.getYear()));

        return IntentResult.builder()
                .route(ROUTE_RAG_NEWS)
                .ragFilters(ragFilters)
                .debug(Map.of("llmRaw", content))
                .build();
    }

    private String asString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }

    private Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
