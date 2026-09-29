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
import java.util.regex.Pattern;

/**
 * 意图路由：把用户的一句话分到四条链路之一。
 *
 * <h3>四条路由</h3>
 * <ul>
 *   <li>{@link #ROUTE_PREDICT} —— 预测<b>未来</b>（「下个月」「未来」「预测…」）；</li>
 *   <li>{@link #ROUTE_DATA_QUERY} —— 查<b>已经发生</b>的历史数据（「某年某月实际是多少」）；</li>
 *   <li>{@link #ROUTE_RAG_NEWS} —— 新闻/背景/原因/有哪些等需要从语料检索回答的问题；</li>
 *   <li>{@link #ROUTE_CHITCHAT} —— 打招呼、闲聊、问能力、与贸易无关的问题。</li>
 *   <li>{@link #ROUTE_SCOPE} —— 问「能访问哪些数据」（有哪些国家 / 数据覆盖到哪）。
 *       只走规则，不进 LLM 四分类。</li>
 * </ul>
 *
 * <h3>修复记录（为什么会有这一版）</h3>
 * <p>上一版只有 PREDICT / RAG_NEWS 两条路，并且：
 * <ol>
 *   <li>PREDICT 的关键词表里有裸词「进口」「出口」「单价」「数量」，且<b>排在检索词之前</b>判断，
 *       于是「哈萨克斯坦主要出口哪些商品」被「出口」两字劫持成预测；</li>
 *   <li>LLM 兜底的提示词只有 PREDICT / RAG_NEWS 两个选项，没有「闲聊/无关」，
 *       于是「你好」也被判成预测 —— 用户的主观感受就是「问什么都答贸易预测」；</li>
 *   <li>根本缺少「查历史数据」这条路，而「1月哈萨克斯坦丝绸出口量」这类问题问的是
 *       已经发生的事实，数据就在库里。</li>
 * </ol>
 * 本版按「闲聊 → 前瞻信号 → 检索信号 → 数据查询信号」的顺序判定，并把 LLM 兜底改为四分类。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRouter {

    public static final String ROUTE_PREDICT = "PREDICT";
    public static final String ROUTE_RAG_NEWS = "RAG_NEWS";
    public static final String ROUTE_DATA_QUERY = "DATA_QUERY";
    public static final String ROUTE_CHITCHAT = "CHITCHAT";

    /**
     * 「能访问哪些数据」。
     *
     * <p>刻意<b>只走规则、不进 LLM 兜底的四分类提示词</b>：这条路由的判定条件足够
     * 明确，改提示词反而会扰动已有四类的分类行为。即便规则漏判，问题落到
     * CHITCHAT，其能力说明里也已带上动态数据范围，不会答非所问。</p>
     */
    public static final String ROUTE_SCOPE = "SCOPE";

    /** 「未来」信号 —— 只有出现这些词才算预测意图。 */
    private static final String[] FORECAST_KEYWORDS = {
            "预测", "预估", "预计", "预判", "推算", "估一下", "下个月", "下月", "未来",
            "会是多少", "将达到", "明年", "forecast", "predict"
    };

    /** 「检索/分析」信号 —— 需要读语料才能回答的问题。 */
    private static final String[] RAG_KEYWORDS = {
            "新闻", "报道", "来源", "发生了什么", "近期", "最近", "摘要", "有哪些", "哪些",
            "主要", "概况", "介绍", "是什么", "什么是", "为什么", "分析", "趋势", "走势",
            "背景", "影响", "政策", "资料", "语料", "检索", "总结", "梳理", "综述", "事件",
            "情况", "原因", "意义", "前景"
    };

    /** 「查询已发生数据」的动词信号。 */
    private static final String[] QUERY_KEYWORDS = {
            "多少", "是多少", "多大", "查一下", "查询", "查查", "统计", "累计", "合计",
            "总额", "一共", "总共", "总值", "达到了", "数据"
    };

    /** 寒暄/能力询问（短句才会被判为闲聊，避免误伤「你好，帮我查…」）。 */
    private static final String[] CHITCHAT_WORDS = {
            "你好", "您好", "hi", "hello", "hey", "在吗", "在不在", "嗨", "哈喽",
            "谢谢", "多谢", "感谢", "好的", "ok", "嗯嗯", "测试", "test"
    };

    /** 能力询问，无论多长都算闲聊。 */
    private static final String[] CAPABILITY_WORDS = {
            "你是谁", "你叫什么", "你能做什么", "你能干什么", "你会什么", "你支持什么",
            "怎么用", "使用说明", "帮助文档"
    };

    /** 「能访问哪些数据」的三类信号词，需同时命中才成路由（见 {@link #isScopeQuestion}）。 */
    private static final String[] SCOPE_WHICH = {
            "哪些", "什么", "多少", "范围", "覆盖", "支持", "包含"
    };
    private static final String[] SCOPE_OBJECT = {
            "数据", "语料", "资料", "国家", "地区", "国别", "贸易伙伴"
    };
    private static final String[] SCOPE_ACCESS = {
            "访问", "能查", "可以查", "获取", "覆盖", "包含", "看得到", "有"
    };

    private static final Pattern YM_YEAR = Pattern.compile("20\\d{2}\\s*年?");
    private static final Pattern YM_MONTH = Pattern.compile("(^|[^0-9])(1[0-2]|0?[1-9])\\s*月");
    private static final Pattern YM_PACKED = Pattern.compile("20\\d{4}");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentResult route(AiChatRequest req) {
        String text = req != null ? req.getText() : null;
        if (text == null) text = "";
        text = text.trim();

        // Stage 0: 用户显式给了完整预测槽位 → 直接走预测
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

        // Stage 1: 规则
        String ruleRoute = ruleBasedRoute(text);
        if (ruleRoute != null) {
            return buildByRule(ruleRoute, text, req);
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

    /** 规则命中后补齐槽位（预测与数据查询共用一套槽位结构）。 */
    private IntentResult buildByRule(String ruleRoute, String text, AiChatRequest req) {
        Map<String, Object> debug = new HashMap<>();
        debug.put("stage", "rule");
        debug.put("ruleRoute", ruleRoute);

        if (ROUTE_RAG_NEWS.equals(ruleRoute) || ROUTE_CHITCHAT.equals(ruleRoute)
                || ROUTE_SCOPE.equals(ruleRoute)) {
            return IntentResult.builder()
                    .route(ruleRoute)
                    .ragFilters(filters(req))
                    .debug(debug)
                    .build();
        }

        // PREDICT / DATA_QUERY 都需要槽位：请求字段 → LLM 抽取 → 文本关键词
        Map<String, Object> llmSlots = extractSlotsByLlm(text);
        debug.put("llmSlots", llmSlots != null);

        // 关键差异：预测的目标月份缺省取「下个月」；查历史数据时缺省必须是「不限」，
        // 否则会把「1月哈萨克斯坦丝绸出口量」这种没有年份的问题强行补成未来的下个月。
        boolean defaultNextMonth = ROUTE_PREDICT.equals(ruleRoute);

        return IntentResult.builder()
                .route(ruleRoute)
                .predictSlots(mergeSlots(text, req, llmSlots, defaultNextMonth))
                .ragFilters(filters(req))
                .debug(debug)
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

    // =================================================================
    // 规则判定
    // =================================================================

    /**
     * 返回四条路由之一，或 null（交给 LLM）。
     *
     * <p>顺序刻意如此，逐条都对应一个此前的误判案例：</p>
     * <ol>
     *   <li><b>闲聊最优先</b> —— 否则「你好」会被后面的词表误伤（实测被判成 PREDICT）；</li>
     *   <li><b>前瞻信号优先</b> —— 「预测未来趋势」里「趋势」也是检索词，但用户要的是预测；</li>
     *   <li><b>数据查询先于检索</b> —— 「最近三个月哈萨克斯坦出口量是多少」含「最近」，
     *       若先判检索会被劫持；而「哈萨克斯坦主要出口哪些商品」虽然含「出口」，
     *       却不含查询动词也没有年月，自然会落到检索；</li>
     *   <li><b>检索</b> —— 归纳/背景/原因类问题。</li>
     * </ol>
     */
    private String ruleBasedRoute(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return ROUTE_CHITCHAT;

        // ① 闲聊 / 能力询问
        if (isChitchat(t)) return ROUTE_CHITCHAT;

        // ①-2 问「能访问哪些数据」
        //     必须早于检索：这类问法常含「哪些 / 什么」，继续往下会被
        //     RAG_KEYWORDS 里的「哪些」劫持成新闻检索（此前就是这么落到 LLM 的）。
        if (isScopeQuestion(t)) return ROUTE_SCOPE;

        // ② 前瞻信号 → 预测
        for (String kw : FORECAST_KEYWORDS) {
            if (t.contains(kw)) return ROUTE_PREDICT;
        }

        // ③ 已发生数据的查询信号 → 历史数据查询
        boolean tradeWord = t.contains("进口") || t.contains("出口") || t.contains("单价")
                || t.contains("数量") || t.contains("金额") || t.contains("贸易额")
                || t.contains("外贸");
        boolean queryWord = containsAny(t, QUERY_KEYWORDS);
        boolean hasYm = containsYm(t);
        if (tradeWord && (queryWord || hasYm)) return ROUTE_DATA_QUERY;
        if (queryWord && hasYm) return ROUTE_DATA_QUERY;

        // ④ 检索/分析信号 → 新闻问答
        for (String kw : RAG_KEYWORDS) {
            if (t.contains(kw)) return ROUTE_RAG_NEWS;
        }

        // ⑤ 兜不出结论 → 交给 LLM
        return null;
    }

    private boolean isChitchat(String t) {
        for (String kw : CAPABILITY_WORDS) {
            if (t.contains(kw)) return true;
        }
        // 短句寒暄：限制长度，避免「你好，帮我预测一下…」被判成闲聊
        if (t.length() <= 12) {
            for (String kw : CHITCHAT_WORDS) {
                if (t.contains(kw)) return true;
            }
        }
        return false;
    }

    /**
     * 是否在问「能访问哪些数据」。
     *
     * <p>三个条件必须<b>同时</b>成立，单看任一个都会大面积误伤：</p>
     * <ul>
     *   <li>「哪些 / 什么」几乎出现在所有检索式问句里；</li>
     *   <li>「数据」也常出现在正常查数据的句子里；</li>
     *   <li>「有」是最高频的字。</li>
     * </ul>
     *
     * <p>实测：「有什么国家的数据可以访问」→命中；「你好」→不命中；
     * 「2025年1月哈萨克斯坦的出口数量是多少」→不命中（没有数据/国家类对象）；
     * 「最近有哪些关于哈萨克斯坦的新闻」→不命中（同上）。</p>
     */
    private boolean isScopeQuestion(String t) {
        return containsAny(t, SCOPE_WHICH)
                && containsAny(t, SCOPE_OBJECT)
                && containsAny(t, SCOPE_ACCESS);
    }

    private boolean containsAny(String t, String[] kws) {
        for (String kw : kws) {
            if (t.contains(kw)) return true;
        }
        return false;
    }

    /** 文本里是否出现了具体年月（2025年 / 2025年1月 / 3月 / 202503）。 */
    private boolean containsYm(String t) {
        if (YM_PACKED.matcher(t).find()) return true;
        if (YM_YEAR.matcher(t).find()) return true;
        return YM_MONTH.matcher(t).find();
    }

    // =================================================================
    // 槽位补齐
    // =================================================================

    /**
     * 汇总四路来源得到最终槽位，优先级由高到低：
     * ① 请求体里显式携带的字段（前端表单 / 调试用）；
     * ② LLM 从自然语言里抽出的实体；
     * ③ 文本关键词兜底（tradeType / target，不依赖外部服务，永远可用）；
     * ④ 目标月份缺省。
     *
     * @param defaultNextMonth 预测时为 true（缺省下个月）；查历史数据时必须为 false，
     *                         否则「1月…出口量」会被补成未来的下个月，永远查不到数据。
     */
    private PredictRequest mergeSlots(String text, AiChatRequest req,
                                      Map<String, Object> llm, boolean defaultNextMonth) {
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
            // 「进出口」或同时提到进口与出口时**不猜**：方向不明就留空，
            // 由控制器告诉用户「请说明是进口还是出口」，好过默认成出口给错数。
            boolean ambiguous = text.contains("进出口")
                    || (text.contains("进口") && text.contains("出口"));
            if (!ambiguous) {
                if (text.contains("进口")) tradeType = "in";
                else if (text.contains("出口") || text.contains("外销") || text.contains("出海")) tradeType = "out";
            }
        }
        if (isBlank(target)) {
            if (text.contains("单价") || text.contains("价格") || text.contains("价位")) target = "price";
            else if (text.contains("数量") || text.contains("总量") || text.contains("吨")) target = "quantity";
        }
        // 文本里的年月兜底（LLM 未抽出时）：① 202503 这种紧凑写法优先，否则「2025」会
        // 把 6 位数字误读成「2025 年」；② 「2025年1月」；③ 只有「1月」时只补月份。
        if (year == null || month == null) {
            java.util.regex.Matcher pk = YM_PACKED.matcher(text);
            if (pk.find()) {
                String v = pk.group();
                if (year == null) year = Integer.parseInt(v.substring(0, 4));
                if (month == null) month = Integer.parseInt(v.substring(4, 6));
            }
        }
        if (year == null) {
            java.util.regex.Matcher m = YM_YEAR.matcher(text);
            if (m.find()) {
                String y = m.group().replaceAll("[^0-9]", "");
                if (y.length() == 4) year = Integer.parseInt(y);
            }
        }
        if (month == null) {
            java.util.regex.Matcher m = YM_MONTH.matcher(text);
            if (m.find()) month = Integer.parseInt(m.group(2));
        }

        tradeType = normalizeTradeType(tradeType);
        target = normalizeTarget(target);

        // ④ 目标月份缺省：预测 → 下个月；数据查询 → 保持 null（= 不限月份，取最近）
        if (defaultNextMonth && (year == null || month == null)) {
            LocalDate next = LocalDate.now().plusMonths(1);
            if (year == null) year = next.getYear();
            if (month == null) month = next.getMonthValue();
        }
        // 只有年份时补全为「该年 1~12 月」由服务侧处理；这里不擅自补月份

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
            String system = "你是一个实体抽取器。从用户的中文提问里抽取贸易数据所需的槽位。\n"
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
                    + "  只有明确说出年月（如「2026年3月」「2025年1月」）才填具体数字。\n"
                    + "  用户只说「1月」而不带年份时，只填 month=1，year 填 null。\n"
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

    /**
     * LLM 兜底分类。<b>必须是四分类</b>：只有 PREDICT / RAG_NEWS 两个选项时，
     * 「你好」这种闲聊会被迫落到其中之一（实测判成了 PREDICT）。
     */
    private IntentResult llmRoute(String text, AiChatRequest req) {
        String system = "你是一个严格的意图分类器。把用户输入分到下列四类之一：\n"
                + "- PREDICT：想预测**未来**的进口/出口单价或数量（出现「下个月」「未来」「预测」等）。\n"
                + "- DATA_QUERY：想查询**已经发生**的历史数据，问某个具体年月实际是多少。\n"
                + "- RAG_NEWS：想了解新闻、背景、原因、趋势、有哪些、是什么等需要读语料才能答的问题。\n"
                + "- CHITCHAT：打招呼、闲聊、问你能做什么，或与中哈贸易数据完全无关的问题（天气、股票等）。\n"
                + "只输出严格 JSON，不要输出多余文本，不要用代码块包裹。\n"
                + "JSON 格式：{\"route\":\"PREDICT|DATA_QUERY|RAG_NEWS|CHITCHAT\",\"slots\":{...}}\n"
                + "route 为 PREDICT 或 DATA_QUERY 时，slots 尽量抽取：\n"
                + "tradeType(in/out)、target(price/quantity)、year、month、tradePartnerName、"
                + "productName、tradeMode、registerName。抽不到填 null，不要编造。\n"
                + "其余 route 的 slots 可为空对象 {}。";

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

        @SuppressWarnings("unchecked")
        Map<String, Object> slots = slotsObj instanceof Map
                ? (Map<String, Object>) slotsObj : new HashMap<>();

        if (route != null) {
            String r = route.trim().toUpperCase(Locale.ROOT);
            if (ROUTE_PREDICT.equals(r)) {
                return IntentResult.builder()
                        .route(ROUTE_PREDICT)
                        .predictSlots(mergeSlots(text, req, slots, true))
                        .ragFilters(filters(req))
                        .debug(Map.of("llmRaw", content))
                        .build();
            }
            if (ROUTE_DATA_QUERY.equals(r)) {
                return IntentResult.builder()
                        .route(ROUTE_DATA_QUERY)
                        .predictSlots(mergeSlots(text, req, slots, false))
                        .ragFilters(filters(req))
                        .debug(Map.of("llmRaw", content))
                        .build();
            }
            if (ROUTE_CHITCHAT.equals(r)) {
                return IntentResult.builder()
                        .route(ROUTE_CHITCHAT)
                        .ragFilters(filters(req))
                        .debug(Map.of("llmRaw", content))
                        .build();
            }
            if (ROUTE_RAG_NEWS.equals(r)) {
                Map<String, Object> ragFilters = new HashMap<>();
                ragFilters.put("country", slots.getOrDefault("country",
                        req != null ? req.getCountry() : null));
                ragFilters.put("year", slots.getOrDefault("year",
                        req != null ? req.getYear() : null));
                return IntentResult.builder()
                        .route(ROUTE_RAG_NEWS)
                        .ragFilters(ragFilters)
                        .debug(Map.of("llmRaw", content))
                        .build();
            }
        }

        // 分类值无法识别 → 按 RAG 处理（最安全：不会凭空给出数值）
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
