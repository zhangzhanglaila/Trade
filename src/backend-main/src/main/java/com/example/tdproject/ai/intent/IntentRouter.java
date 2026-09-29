package com.example.tdproject.ai.intent;

import com.example.tdproject.ai.dto.AiChatRequest;
import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.http.LlmClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRouter {

    public static final String ROUTE_PREDICT = "PREDICT";
    public static final String ROUTE_RAG_NEWS = "RAG_NEWS";

    private final LlmClient llmClient;
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
                    .ragFilters(filters(req))
                    .debug(Map.of("stage", "explicit"))
                    .build();
        }

        // Stage 1: 规则优先
        String ruleRoute = ruleBasedRoute(text);

        // 【修复】规则命中「预测」时，原实现只返回 route=PREDICT 而 predictSlots 为 null，
        // 控制器随即调用 predictionService.predict(null)，用户看到的是一句
        // 「PredictRequest 不能为空」；而本可以走到 Stage 2 的 LLM 槽位抽取，
        // 却因为 Stage 1 提前 return 而永远不会执行。
        // 于是「帮我预测一下哈萨克斯坦铜矿砂…下个月进口单价」这类完全正常的自然语言
        // 提问必然报错 —— 这是问答链路不可用的直接原因之一。
        // 修法：规则命中预测后仍需补齐槽位（请求字段 → LLM 抽取 → 文本关键词 → 默认目标月）。
        if (ROUTE_PREDICT.equals(ruleRoute)) {
            Map<String, Object> llmSlots = extractSlotsByLlm(text);
            Map<String, Object> debug = new HashMap<>();
            debug.put("stage", "rule");
            debug.put("llmSlots", llmSlots != null);
            return IntentResult.builder()
                    .route(ROUTE_PREDICT)
                    .predictSlots(mergeSlots(text, req, llmSlots))
                    .ragFilters(filters(req))
                    .debug(debug)
                    .build();
        }

        if (ROUTE_RAG_NEWS.equals(ruleRoute)) {
            return IntentResult.builder()
                    .route(ruleRoute)
                    .ragFilters(filters(req))
                    .debug(Map.of("stage", "rule"))
                    .build();
        }

        // Stage 2: LLM 兜底（未配置对话模型时直接跳过，避免无谓的异常与等待）
        if (llmClient.isChatConfigured()) {
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
        }

        return IntentResult.builder()
                .route(ROUTE_RAG_NEWS)
                .ragFilters(filters(req))
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

    // =================================================================
    // 槽位补齐
    // =================================================================

    /**
     * 汇总四路来源得到最终预测槽位，优先级由高到低：
     * ① 请求体里显式携带的字段（前端表单 / 调试用）；
     * ② LLM 从自然语言里抽出的实体；
     * ③ 文本关键词兜底（tradeType / target，不依赖外部服务，永远可用）；
     * ④ 目标月份默认取「下个月」—— 用户说「下个月」时 LLM 无法可靠算出具体年月，
     *    故这里用服务器时钟补，避免因 year/month 缺失而直接报错。
     */
    private PredictRequest mergeSlots(String text, AiChatRequest req, Map<String, Object> llm) {
        Map<String, Object> s = llm != null ? llm : Collections.emptyMap();

        String tradeType = firstNotBlank(
                req != null ? req.getTradeType() : null,
                asString(s.get("tradeType")));
        String target = firstNotBlank(
                req != null ? req.getTarget() : null,
                asString(s.get("target")));
        String partner = firstNotBlank(
                req != null ? req.getTradePartnerName() : null,
                asString(s.get("tradePartnerName")));
        String product = firstNotBlank(
                req != null ? req.getProductName() : null,
                asString(s.get("productName")));
        String mode = firstNotBlank(
                req != null ? req.getTradeMode() : null,
                asString(s.get("tradeMode")));
        String register = firstNotBlank(
                req != null ? req.getRegisterName() : null,
                asString(s.get("registerName")));

        Integer year = req != null && req.getYear() != null
                ? req.getYear() : asInt(s.get("year"));
        Integer month = req != null && req.getMonth() != null
                ? req.getMonth() : asInt(s.get("month"));

        // ③ 关键词兜底（仅在上述来源仍为空时生效）
        if (isBlank(tradeType)) {
            if (text.contains("进口")) tradeType = "in";
            else if (text.contains("出口") || text.contains("外销") || text.contains("出海")) tradeType = "out";
        }
        if (isBlank(target)) {
            if (text.contains("单价") || text.contains("价格") || text.contains("价位")) target = "price";
            else if (text.contains("数量") || text.contains("总量") || text.contains("吨")) target = "quantity";
        }

        tradeType = normalizeTradeType(tradeType);
        target = normalizeTarget(target);

        // ④ 目标月份缺省 = 下个月
        if (year == null || month == null) {
            LocalDate next = LocalDate.now().plusMonths(1);
            if (year == null) year = next.getYear();
            if (month == null) month = next.getMonthValue();
        }

        return PredictRequest.builder()
                .tradeType(tradeType)
                .target(target)
                .year(year)
                .month(month)
                .tradePartnerName(partner)
                .productName(product)
                .tradeMode(mode)
                .registerName(register)
                .build();
    }

    /** 把 LLM 可能返回的中文/大写取值归一到 Flask 端约定的小写英文枚举 */
    private String normalizeTradeType(String v) {
        if (v == null) return null;
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (t.contains("进口") || t.equals("in") || t.equals("import")) return "in";
        if (t.contains("出口") || t.equals("out") || t.equals("export")) return "out";
        return t;
    }

    private String normalizeTarget(String v) {
        if (v == null) return null;
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (t.contains("单价") || t.contains("价格") || t.equals("price")) return "price";
        if (t.contains("数量") || t.contains("总量") || t.equals("quantity") || t.equals("qty")) return "quantity";
        return t;
    }

    /** 仅让 LLM 抽槽位（不做路由决策）。未配置对话模型或解析失败时返回 null。 */
    private Map<String, Object> extractSlotsByLlm(String text) {
        if (!llmClient.isChatConfigured()) return null;
        try {
            String system = "你是一个实体抽取器。从用户的中文提问里抽取贸易预测所需的槽位。\n"
                    + "只输出严格 JSON，不要输出多余文本，不要解释。\n"
                    + "JSON 格式：{\"tradeType\":\"in|out\",\"target\":\"price|quantity\","
                    + "\"tradePartnerName\":null,\"productName\":null,\"tradeMode\":null,"
                    + "\"registerName\":null,\"year\":null,\"month\":null}\n"
                    + "规则：\n"
                    + "· tradeType：进口=in，出口=out。\n"
                    + "· target：单价/价格=price，数量=quantity。\n"
                    + "· tradePartnerName 取国家或地区名称，productName 取商品名称，\n"
                    + "  tradeMode 取贸易方式（如「一般贸易」「边境小额贸易」「进料加工贸易」），\n"
                    + "  registerName 取境内注册地（如「新疆维吾尔自治区」「北京市」）。\n"
                    + "· 用户说「下个月」「下月」时 year/month 一律填 null（由系统按下个月补），\n"
                    + "  只有明确说出年月（如「2026年3月」）才填具体数字。\n"
                    + "· 抽取不到就填 null，不要编造。";

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", system));
            messages.add(Map.of("role", "user", "content", text));

            String content = llmClient.chat(messages, Map.of("temperature", 0));
            Map<String, Object> json = objectMapper.readValue(
                    stripCodeFence(content), new TypeReference<Map<String, Object>>() {});
            Object slots = json.get("slots");
            if (slots instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) slots;
                return m;
            }
            return json;
        } catch (Exception e) {
            log.warn("LLM 槽位抽取失败，改用请求字段 + 关键词兜底: {}", e.getMessage());
            return null;
        }
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

        String content = llmClient.chat(messages, Map.of("temperature", 0));

        Map<String, Object> json;
        try {
            json = objectMapper.readValue(
                    stripCodeFence(content), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 LLM 返回 JSON 失败: " + e.getOriginalMessage() + ", raw=" + content, e);
        }
        String route = json.get("route") != null ? String.valueOf(json.get("route")) : null;
        Object slotsObj = json.get("slots");

        Map<String, Object> slots = slotsObj instanceof Map ? (Map<String, Object>) slotsObj : new HashMap<>();

        if (ROUTE_PREDICT.equalsIgnoreCase(route)) {
            return IntentResult.builder()
                    .route(ROUTE_PREDICT)
                    .predictSlots(mergeSlots(text, req, slots))
                    .ragFilters(filters(req))
                    .debug(Map.of("llmRaw", content))
                    .build();
        }

        // 默认走 RAG
        Map<String, Object> ragFilters = new HashMap<>();
        ragFilters.put("country", slots.getOrDefault("country", req != null ? req.getCountry() : null));
        ragFilters.put("year", slots.getOrDefault("year", req != null ? req.getYear() : null));

        return IntentResult.builder()
                .route(ROUTE_RAG_NEWS)
                .ragFilters(ragFilters)
                .debug(Map.of("llmRaw", content))
                .build();
    }

    /**
     * LLM 常把 JSON 包在 ```json ... ``` 里，或前后带说明文字。
     * 直接 readValue 会抛 JsonProcessingException，表现为「意图识别失败，回退 RAG」。
     */
    private String stripCodeFence(String content) {
        if (content == null) return "{}";
        String s = content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            int end = s.lastIndexOf("```");
            if (end >= 0) s = s.substring(0, end);
            s = s.trim();
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a >= 0 && b > a) s = s.substring(a, b + 1);
        return s;
    }

    /**
     * 构造 RAG 过滤条件。
     *
     * <p><b>务必不要用 {@code Map.of(...)}</b>：它不接受 null 值，
     * 而前端默认不传 country/year，用 Map.of 会让每一次问答请求都抛 NullPointerException
     * （表现为接口返回 code=202、message=null，前端直接报「接口调用失败」）。
     * 这正是问答功能此前整体不可用的根因。</p>
     */
    private Map<String, Object> filters(AiChatRequest req) {
        Map<String, Object> m = new HashMap<>(4);
        if (req != null) {
            m.put("country", req.getCountry());
            m.put("year", req.getYear());
        }
        return m;
    }

    private String firstNotBlank(String... vals) {
        for (String v : vals) {
            if (notBlank(v)) return v;
        }
        return null;
    }

    private String asString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
