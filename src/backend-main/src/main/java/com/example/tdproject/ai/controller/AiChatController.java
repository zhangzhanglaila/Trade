package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.dto.AiChatRequest;
import com.example.tdproject.ai.dto.AiChatResponse;
import com.example.tdproject.ai.dto.DataQueryResult;
import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.dto.PredictResponse;
import com.example.tdproject.ai.intent.IntentResult;
import com.example.tdproject.ai.intent.IntentRouter;
import com.example.tdproject.ai.predict.PredictionService;
import com.example.tdproject.ai.query.TradeDataQueryService;
import com.example.tdproject.ai.rag.NewsRagService;
import com.example.tdproject.ai.rag.RagAnswer;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 智能问答统一入口。
 *
 * <h3>五条路由</h3>
 * <ul>
 *   <li>{@code DATA_QUERY} —— 查已经发生的历史数据（新增）；</li>
 *   <li>{@code PREDICT} —— 预测未来，槽位不齐时给「可照做」的引导；</li>
 *   <li>{@code RAG_NEWS} —— 语料检索问答；</li>
 *   <li>{@code SCOPE} —— 「能访问哪些数据」，答案从库里现算；</li>
 *   <li>{@code CHITCHAT} —— 闲聊 / 问能力，给出能力说明（含动态数据范围）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final IntentRouter intentRouter;
    private final PredictionService predictionService;
    private final NewsRagService newsRagService;
    private final TradeDataQueryService tradeDataQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 非流式问答（原有入口，行为不变）。
     */
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest req) {
        try {
            IntentResult intent = intentRouter.route(req);
            return Result.build(chatCore(intent, req));
        } catch (IllegalArgumentException e) {
            return Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("/ai/chat 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * SSE 流式问答入口，与 {@link #chat(AiChatRequest)} 共用同一套路由与槽位逻辑。
     *
     * <p>差别只在：预测这条「点发送后要等好几秒」的链路，会把中间进度实时推给前端，
     * 避免用户盯着一个静止的界面误以为卡死。其余分支不慢，直接一次性推最终结果。</p>
     *
     * <p>事件协议（text/event-stream，每条 <code>data:</code> 一行 JSON）：</p>
     * <ul>
     *   <li>{@code {"type":"stage","msg":"..."}} —— 阶段进度；</li>
     *   <li>{@code {"type":"done","data":{...}}} —— 最终结果（AiChatResponse）；</li>
     *   <li>{@code {"type":"error","msg":"..."}} —— 出错。</li>
     * </ul>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest req) {
        // timeout=0 表示不设超时；前端断开时 send 会抛异常，届时 complete 收尾。
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                // 点发送后立刻反馈一句，覆盖「意图分类 + 槽位抽取」这段最长的静默期
                //（实测 DeepSeek 做这一步约 1~2 秒，此前前端完全没有输出）。
                emitStage(emitter, "正在理解你的问题");
                IntentResult intent = intentRouter.route(req);
                String route = intent.getRoute();

                // ---------------- 预测：需要流式进度 ----------------
                if (IntentRouter.ROUTE_PREDICT.equalsIgnoreCase(route)) {
                    var slots = intent.getPredictSlots();
                    List<String> hints = collectPredictHints(slots);
                    // 商品名核对：用户常写简称（「粒面剖层蓝湿牛皮」→ 库里是「全粒面
                    // 未剖层及粒面剖层蓝湿牛皮」）或俗称。能解析到库中规范名/品类时，
                    // 直接替换成规范名继续预测，而不是当成「缺商品名称」拦下来。
                    boolean productUnresolved = false;
                    if (slots != null && !isBlank(slots.getProductName())
                            && !isBlank(slots.getTradeType())) {
                        String resolved = tradeDataQueryService.resolveProductName(
                                slots.getTradeType(), slots.getProductName());
                        if (resolved == null) {
                            productUnresolved = true;
                        } else {
                            slots.setProductName(resolved);
                        }
                    }
                    // 注册地核对：用户常写简称（「新疆」→ 库里是「新疆维吾尔自治区」），
                    // 唯一能解析到规范名时替换继续预测，否则转引导（给全称候选）。
                    boolean registerUnresolved = false;
                    if (slots != null && !isBlank(slots.getRegisterName())
                            && !isBlank(slots.getTradeType())) {
                        String resolvedReg = tradeDataQueryService.resolveRegisterName(
                                slots.getTradeType(), slots.getRegisterName());
                        if (resolvedReg == null) {
                            registerUnresolved = true;
                        } else {
                            slots.setRegisterName(resolvedReg);
                        }
                    }
                    if (!hints.isEmpty() || productUnresolved || registerUnresolved) {
                        // 商品名是俗称（如「牛肉」对应多个规范商品）但其余槽位齐全时，
                        // 尝试聚合预测：逐个规范商品预测数量后加总，避免让用户反复挑。
                        PredictResponse agg = null;
                        if (productUnresolved && !registerUnresolved && hints.isEmpty()) {
                            agg = tryAggregatePredict(slots);
                        }
                        if (agg != null) {
                            emitStage(emitter, "正在整理预测结果");
                            emitDone(emitter, AiChatResponse.builder()
                                    .route(IntentRouter.ROUTE_PREDICT)
                                    .answer(buildAggregatedAnswer(agg, slots))
                                    .predictResult(agg)
                                    .sources(null)
                                    .debugInfo(debugMap(intent))
                                    .build());
                            emitter.complete();
                            return;
                        }
                        if (productUnresolved) hints.add("商品名称");
                        if (registerUnresolved) hints.add("境内注册地（请用全称）");
                        emitDone(emitter, AiChatResponse.builder()
                                .route(IntentRouter.ROUTE_PREDICT)
                                .answer(buildPredictGuidance(slots, hints))
                                .hints(hints)
                                .predictResult(null)
                                .sources(null)
                                .debugInfo(debugMap(intent))
                                .build());
                        emitter.complete();
                        return;
                    }

                    // 槽位齐全，进入真正预测。Flask 纯 CPU 推理 + 反变换是主要耗时，
                    // 分阶段推送，让前端持续有反馈。这里把「正在预测什么」说具体：
                    // 多用户排队时，用户能看到自己这条请求在算哪个商品的数量/单价，
                    // 而不是盯着一句笼统的「正在查询」误以为卡死。
                    String predictWhat = "正在预测「" + slots.getProductName() + "」的"
                            + ("out".equalsIgnoreCase(slots.getTradeType()) ? "出口" : "进口")
                            + ("quantity".equalsIgnoreCase(slots.getTarget()) ? "数量" : "单价");
                    emitStage(emitter, predictWhat);
                    PredictResponse predict;
                    try {
                        predict = predictionService.predict(slots);
                    } catch (IllegalArgumentException e) {
                        // 组合无历史数据等业务性失败：转成友好引导，不把裸报错抛给用户
                        emitDone(emitter, AiChatResponse.builder()
                                .route(IntentRouter.ROUTE_PREDICT)
                                .answer(e.getMessage())
                                .hints(List.of())
                                .predictResult(null)
                                .sources(null)
                                .debugInfo(debugMap(intent))
                                .build());
                        emitter.complete();
                        return;
                    }
                    emitStage(emitter, "正在整理预测结果");

                    emitDone(emitter, AiChatResponse.builder()
                            .route(IntentRouter.ROUTE_PREDICT)
                            .answer(buildPredictAnswer(predict, slots))
                            .predictResult(predict)
                            .sources(null)
                            .debugInfo(debugMap(intent))
                            .build());
                    emitter.complete();
                    return;
                }

                // ---------------- 其余分支：复用核心逻辑，一次性返回 ----------------
                emitDone(emitter, chatCore(intent, req));
                emitter.complete();

            } catch (IllegalArgumentException e) {
                emitError(emitter, e.getMessage());
            } catch (Exception e) {
                log.error("/ai/chat/stream 异常", e);
                emitError(emitter, e.getMessage());
            }
        });
        return emitter;
    }

    // =================================================================
    // 核心分发（chat 与 chatStream 共用）
    // =================================================================

    private Map<String, Object> debugMap(IntentResult intent) {
        Map<String, Object> debug = new HashMap<>();
        debug.put("intent", intent);
        return debug;
    }

    private AiChatResponse chatCore(IntentResult intent, AiChatRequest req) {
        String route = intent.getRoute();
        Map<String, Object> debug = debugMap(intent);

        // ---------------- 预测 ----------------
        if (IntentRouter.ROUTE_PREDICT.equalsIgnoreCase(route)) {
            var slots = intent.getPredictSlots();
            List<String> hints = collectPredictHints(slots);
            // 商品名核对：简称/俗称能解析到库中规范名时直接替换继续预测，
            // 而不是当成「缺商品名称」拦下（详见 chatStream 分支注释）。
            boolean productUnresolved = false;
            if (slots != null && !isBlank(slots.getProductName())
                    && !isBlank(slots.getTradeType())) {
                String resolved = tradeDataQueryService.resolveProductName(
                        slots.getTradeType(), slots.getProductName());
                if (resolved == null) {
                    productUnresolved = true;
                } else {
                    slots.setProductName(resolved);
                }
            }
            // 注册地核对：简称（新疆/广西/内蒙古）解析成规范全称
            boolean registerUnresolved = false;
            if (slots != null && !isBlank(slots.getRegisterName())
                    && !isBlank(slots.getTradeType())) {
                String resolvedReg = tradeDataQueryService.resolveRegisterName(
                        slots.getTradeType(), slots.getRegisterName());
                if (resolvedReg == null) {
                    registerUnresolved = true;
                } else {
                    slots.setRegisterName(resolvedReg);
                }
            }
            if (!hints.isEmpty() || productUnresolved || registerUnresolved) {
                // 商品名是俗称但其余槽位齐全时，尝试聚合预测（逐个规范商品预测数量加总）
                PredictResponse agg = null;
                if (productUnresolved && !registerUnresolved && hints.isEmpty()) {
                    agg = tryAggregatePredict(slots);
                }
                if (agg != null) {
                    return AiChatResponse.builder()
                            .route(IntentRouter.ROUTE_PREDICT)
                            .answer(buildAggregatedAnswer(agg, slots))
                            .predictResult(agg)
                            .sources(null)
                            .debugInfo(debug)
                            .build();
                }
                if (productUnresolved) hints.add("商品名称");
                if (registerUnresolved) hints.add("境内注册地（请用全称）");
                return AiChatResponse.builder()
                        .route(IntentRouter.ROUTE_PREDICT)
                        .answer(buildPredictGuidance(slots, hints))
                        .hints(hints)
                        .predictResult(null)
                        .sources(null)
                        .debugInfo(debug)
                        .build();
            }

            PredictResponse predict;
            try {
                predict = predictionService.predict(slots);
            } catch (IllegalArgumentException e) {
                // 组合无历史数据等业务性失败：转成友好引导，不把裸报错抛给用户
                return AiChatResponse.builder()
                        .route(IntentRouter.ROUTE_PREDICT)
                        .answer(e.getMessage())
                        .hints(List.of())
                        .predictResult(null)
                        .sources(null)
                        .debugInfo(debug)
                        .build();
            }

            return AiChatResponse.builder()
                    .route(IntentRouter.ROUTE_PREDICT)
                    .answer(buildPredictAnswer(predict, slots))
                    .predictResult(predict)
                    .sources(null)
                    .debugInfo(debug)
                    .build();
        }

        // ---------------- 历史数据查询 ----------------
        if (IntentRouter.ROUTE_DATA_QUERY.equalsIgnoreCase(route)) {
            var slots = intent.getPredictSlots();
            String qTradeType = slots != null ? slots.getTradeType() : null;
            DataQueryResult data = tradeDataQueryService.query(
                    qTradeType,
                    slots != null ? slots.getTarget() : null,
                    slots != null ? slots.getYear() : null,
                    slots != null ? slots.getMonth() : null,
                    slots != null ? slots.getTradePartnerName() : null,
                    slots != null ? slots.getProductName() : null,
                    slots != null ? slots.getTradeMode() : null,
                    slots != null ? slots.getRegisterName() : null);

            // ---- 方向自动重试 ----
            boolean emptyHit = data.getRows() != null && data.getRows().isEmpty()
                    && data.getSummary() != null && data.getSummary().startsWith("库里没有");
            boolean textHasDirection = req.getText() != null
                    && (req.getText().contains("进口") || req.getText().contains("出口")
                        || req.getText().contains("外销") || req.getText().contains("出海"));
            if (emptyHit && !textHasDirection && qTradeType != null) {
                String other = "in".equals(qTradeType) ? "out" : "in";
                DataQueryResult retry = tradeDataQueryService.query(
                        other,
                        slots != null ? slots.getTarget() : null,
                        slots != null ? slots.getYear() : null,
                        slots != null ? slots.getMonth() : null,
                        slots != null ? slots.getTradePartnerName() : null,
                        slots != null ? slots.getProductName() : null,
                        slots != null ? slots.getTradeMode() : null,
                        slots != null ? slots.getRegisterName() : null);
                if (retry.getRows() != null && !retry.getRows().isEmpty()) {
                    String dirCn = "out".equals(other) ? "出口" : "进口";
                    data = retry;
                    data.setSummary("（问题未指明进出口方向，已按「" + dirCn
                            + "」查得。）\n\n" + retry.getSummary());
                }
            }

            // 引导轮（商品名未解析 / 查空给了相近建议）带上 hints：前端据此允许
            // 下一轮短补充（如「冻鱼片」「改成2024年」）拼接上一轮问题重新查询，
            // 与预测引导轮的上下文衔接机制保持一致。
            List<String> dataHints = null;
            if (Boolean.TRUE.equals(data.getProductUnresolved())
                    || (data.getSuggestions() != null && !data.getSuggestions().isEmpty())) {
                dataHints = List.of("补充查询条件");
            }

            return AiChatResponse.builder()
                    .route(IntentRouter.ROUTE_DATA_QUERY)
                    .answer(data.getSummary())
                    .dataQuery(data)
                    .hints(dataHints)
                    .sources(null)
                    .predictResult(null)
                    .debugInfo(debug)
                    .build();
        }

        // ---------------- 数据范围 ----------------
        if (IntentRouter.ROUTE_SCOPE.equalsIgnoreCase(route)) {
            return AiChatResponse.builder()
                    .route(IntentRouter.ROUTE_SCOPE)
                    .answer(tradeDataQueryService.scopeAnswer())
                    .sources(null)
                    .predictResult(null)
                    .debugInfo(debug)
                    .build();
        }

        // ---------------- 闲聊 / 能力询问 ----------------
        if (IntentRouter.ROUTE_CHITCHAT.equalsIgnoreCase(route)) {
            return AiChatResponse.builder()
                    .route(IntentRouter.ROUTE_CHITCHAT)
                    .answer(buildCapabilityAnswer())
                    .sources(null)
                    .predictResult(null)
                    .debugInfo(debug)
                    .build();
        }

        // ---------------- 默认：RAG ----------------
        String country = req != null ? req.getCountry() : null;
        Integer year = req != null ? req.getYear() : null;

        RagAnswer rag = newsRagService.answer(req.getText(), country, year);

        return AiChatResponse.builder()
                .route(IntentRouter.ROUTE_RAG_NEWS)
                .answer(rag.getAnswer())
                .sources(rag.getSources())
                .corpusSize(rag.getCorpusSize())
                .contextDocs(rag.getContextDocs())
                .predictResult(null)
                .debugInfo(debug)
                .build();
    }

    // =================================================================
    // SSE 推送辅助
    // =================================================================

    private void emitStage(SseEmitter emitter, String msg) {
        send(emitter, Map.of("type", "stage", "msg", msg == null ? "" : msg));
    }

    private void emitDone(SseEmitter emitter, AiChatResponse data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "done");
        payload.put("data", data);
        send(emitter, payload);
    }

    private void emitError(SseEmitter emitter, String msg) {
        send(emitter, Map.of("type", "error", "msg", msg == null ? "未知错误" : msg));
        try {
            emitter.complete();
        } catch (Exception ignore) {
        }
    }

    private void send(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload),
                    MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 前端断开时 send 会抛异常；这里吞掉，由调用方 complete 收尾即可。
            log.debug("SSE 推送失败（前端可能已断开）: {}", e.getMessage());
        }
    }

    // =================================================================
    // 预测分支
    // =================================================================

    private String buildPredictAnswer(com.example.tdproject.ai.dto.PredictResponse predict,
                                      PredictRequest slots) {
        if (predict == null) return "预测失败";
        if (predict.getValue() == null) {
            return "预测已调用完成，但未能解析到数值结果。原始返回: "
                    + (predict.getRaw() == null ? "{}" : predict.getRaw().toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("预测结果：").append(fmt(predict.getValue()));
        if (predict.getUnit() != null) sb.append(" ").append(predict.getUnit());

        sb.append("\n口径：")
                .append("in".equalsIgnoreCase(slots.getTradeType()) ? "进口" : "出口").append(" / ")
                .append(blank(slots.getTradePartnerName())).append(" / ")
                .append(blank(slots.getProductName())).append(" / ")
                .append(blank(slots.getTradeMode())).append(" / ")
                .append(blank(slots.getRegisterName()))
                .append("，目标月份 ").append(slots.getYear()).append("-")
                .append(slots.getMonth() < 10 ? "0" + slots.getMonth() : slots.getMonth());

        if (predict.getRaw() != null && predict.getRaw().get("warning") != null) {
            sb.append("\n注意：").append(predict.getRaw().get("warning"));
        }

        // 远期月份可靠性警示
        if (slots != null && slots.getYear() != null && slots.getMonth() != null) {
            int targetYm = slots.getYear() * 100 + slots.getMonth();
            int maxYm = tradeDataQueryService.maxDataYm(slots.getTradeType());
            if (maxYm > 0 && targetYm > maxYm + 1) {
                sb.append("\n\n⚠️ 可靠性提醒：你问的是 ").append(slots.getYear()).append("-")
                        .append(String.format("%02d", slots.getMonth()))
                        .append("，但该方向数据只更新到 ")
                        .append(maxYm / 100).append("-")
                        .append(String.format("%02d", maxYm % 100))
                        .append("。当前数值是基于最近历史外推的结果，月份越远越不可靠，请谨慎参考。");
            }
        }
        return sb.toString();
    }

    /**
     * 尝试「品类聚合预测」：用户问「牛肉」这类俗称（库里对应多个规范商品）时，
     * 与其让他反复挑具体商品，不如逐个规范商品预测数量后加总。
     *
     * <p>仅当满足以下全部条件才聚合，否则返回 null 交由调用方走原引导：</p>
     * <ul>
     *   <li>目标是「数量」（单价对不同商品没有可加性）；</li>
     *   <li>商品俗称能召回 1~3 个规范商品（太多如「奶制品」7 个，逐个预测会输出一堆空结果）；</li>
     *   <li>其余槽位已齐全（由调用方保证 hints 为空、注册地未 unresolved）。</li>
     * </ul>
     */
    private PredictResponse tryAggregatePredict(PredictRequest slots) {
        if (slots == null || isBlank(slots.getProductName()) || isBlank(slots.getTradeType())) {
            return null;
        }
        if (!"quantity".equalsIgnoreCase(slots.getTarget())) {
            return null;
        }
        List<String> cands = tradeDataQueryService.suggestProducts(
                slots.getTradeType(), slots.getProductName(), 4);
        if (cands.isEmpty() || cands.size() > 3) {
            return null;
        }
        return predictAggregated(slots, cands);
    }

    /**
     * 对一组规范商品逐个预测数量并加总；无任一商品可预测（组合都无历史数据）时返回 null。
     */
    private PredictResponse predictAggregated(PredictRequest slots, List<String> candidates) {
        double total = 0;
        String unit = null;
        boolean any = false;
        List<Map<String, Object>> details = new ArrayList<>();
        for (String p : candidates) {
            PredictRequest clone = PredictRequest.builder()
                    .tradeType(slots.getTradeType())
                    .target(slots.getTarget())
                    .year(slots.getYear())
                    .month(slots.getMonth())
                    .tradePartnerName(slots.getTradePartnerName())
                    .productName(p)
                    .tradeMode(slots.getTradeMode())
                    .registerName(slots.getRegisterName())
                    .build();
            try {
                PredictResponse r = predictionService.predict(clone);
                if (r != null && r.getValue() != null) {
                    total += r.getValue();
                    if (unit == null) unit = r.getUnit();
                    any = true;
                    Map<String, Object> d = new HashMap<>();
                    d.put("productName", p);
                    d.put("value", r.getValue());
                    d.put("unit", r.getUnit());
                    details.add(d);
                }
            } catch (IllegalArgumentException e) {
                // 该商品在当前四键组合下无历史数据，跳过，继续预测其余商品
            }
        }
        if (!any) return null;

        Map<String, Object> raw = new HashMap<>();
        raw.put("aggregated", true);
        raw.put("category", slots.getProductName());
        raw.put("total", total);
        raw.put("details", details);

        return PredictResponse.builder()
                .tradeType(slots.getTradeType())
                .target(slots.getTarget())
                .value(total)
                .unit(unit)
                .raw(raw)
                .build();
    }

    /** 品类聚合预测的答案：逐商品明细 + 合计 + 口径。 */
    private String buildAggregatedAnswer(PredictResponse predict, PredictRequest slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("「").append(slots.getProductName()).append("」是一个品类，对应以下规范商品，")
                .append("逐个预测后加总如下：\n\n");

        List<?> details = predict.getRaw() != null
                ? (List<?>) predict.getRaw().get("details") : List.of();
        for (Object o : details) {
            Map<?, ?> d = (Map<?, ?>) o;
            sb.append("- ").append(d.get("productName")).append("：")
                    .append(fmt(((Number) d.get("value")).doubleValue()));
            if (d.get("unit") != null) sb.append(" ").append(d.get("unit"));
            sb.append("\n");
        }

        sb.append("\n**合计：").append(fmt(predict.getValue()));
        if (predict.getUnit() != null) sb.append(" ").append(predict.getUnit());
        sb.append("**\n");

        sb.append("\n口径：")
                .append("in".equalsIgnoreCase(slots.getTradeType()) ? "进口" : "出口").append(" / ")
                .append(blank(slots.getTradePartnerName())).append(" / ")
                .append(blank(slots.getProductName())).append("（品类） / ")
                .append(blank(slots.getTradeMode())).append(" / ")
                .append(blank(slots.getRegisterName()))
                .append("，目标月份 ").append(slots.getYear()).append("-")
                .append(slots.getMonth() < 10 ? "0" + slots.getMonth() : slots.getMonth());

        // 远期月份可靠性警示（与单商品预测同口径）
        if (slots != null && slots.getYear() != null && slots.getMonth() != null) {
            int targetYm = slots.getYear() * 100 + slots.getMonth();
            int maxYm = tradeDataQueryService.maxDataYm(slots.getTradeType());
            if (maxYm > 0 && targetYm > maxYm + 1) {
                sb.append("\n\n⚠️ 可靠性提醒：你问的是 ").append(slots.getYear()).append("-")
                        .append(String.format("%02d", slots.getMonth()))
                        .append("，但该方向数据只更新到 ")
                        .append(maxYm / 100).append("-")
                        .append(String.format("%02d", maxYm % 100))
                        .append("。当前数值是基于最近历史外推的结果，月份越远越不可靠，请谨慎参考。");
            }
        }
        return sb.toString();
    }

    /**
     * 收集缺失项，并顺便核对「商品名是否真的存在于库中」。
     *
     * <p>返回空列表表示槽位齐全，可以预测。</p>
     */
    private List<String> collectPredictHints(PredictRequest s) {
        List<String> hints = new ArrayList<>();
        if (s == null) {
            hints.add("全部预测信息");
            return hints;
        }
        if (isBlank(s.getTradeType())) hints.add("进口还是出口");
        if (isBlank(s.getTarget())) hints.add("预测单价还是数量");
        if (isBlank(s.getTradePartnerName())) hints.add("贸易伙伴（如：哈萨克斯坦）");
        if (isBlank(s.getProductName())) hints.add("商品名称");
        if (isBlank(s.getTradeMode())) hints.add("贸易方式（如：一般贸易）");
        if (isBlank(s.getRegisterName())) hints.add("境内注册地（如：新疆维吾尔自治区）");
        if (s.getYear() == null || s.getMonth() == null) hints.add("目标年月");
        return hints;
    }

    /**
     * 预测槽位不全时的引导语。
     */
    private String buildPredictGuidance(PredictRequest s, List<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("我理解你想做「贸易预测」，但信息还不够，没法算出结果。\n\n");

        List<String> known = new ArrayList<>();
        if (s != null) {
            if (!isBlank(s.getTradeType())) known.add(("in".equals(s.getTradeType()) ? "进口" : "出口"));
            if (!isBlank(s.getTarget())) known.add(("price".equals(s.getTarget()) ? "单价" : "数量"));
            if (!isBlank(s.getTradePartnerName())) known.add("贸易伙伴＝" + s.getTradePartnerName());
            if (!isBlank(s.getProductName())) known.add("商品＝" + s.getProductName());
            if (!isBlank(s.getTradeMode())) known.add("贸易方式＝" + s.getTradeMode());
            if (!isBlank(s.getRegisterName())) known.add("注册地＝" + s.getRegisterName());
            if (s.getYear() != null && s.getMonth() != null) {
                known.add("目标月份＝" + s.getYear() + "-" + s.getMonth());
            }
        }
        if (!known.isEmpty()) {
            sb.append("· 已识别：").append(String.join("、", known)).append("\n");
        }
        sb.append("· 还缺：").append(String.join("、", missing)).append("\n");

        // 商品名核对
        if (s != null && !isBlank(s.getProductName()) && !isBlank(s.getTradeType())) {
            List<String> near = tradeDataQueryService.suggestProducts(
                    s.getTradeType(), s.getProductName(), 5);
            if (!near.isEmpty()) {
                sb.append("\n· ⚠️ 库里没有名为「").append(s.getProductName()).append("」的商品。\n")
                        .append("  相近的规范商品名有：\n");
                for (int i = 0; i < near.size(); i++) {
                    sb.append("    ").append(i + 1).append(". ").append(near.get(i)).append("\n");
                }
                sb.append("  请把商品名换成上面的写法再问一次。\n");
            }
        }

        // 注册地核对：用户写简称（新疆/广西/内蒙古）时给全称候选
        if (s != null && !isBlank(s.getRegisterName()) && !isBlank(s.getTradeType())) {
            List<String> nearRegs = tradeDataQueryService.suggestRegisters(
                    s.getTradeType(), s.getRegisterName(), 8);
            if (!nearRegs.isEmpty()) {
                sb.append("\n· ⚠️ 库里注册地用的是全称，没有「").append(s.getRegisterName())
                        .append("」。\n  相近的规范注册地有：\n");
                for (int i = 0; i < nearRegs.size(); i++) {
                    sb.append("    ").append(i + 1).append(". ").append(nearRegs.get(i)).append("\n");
                }
                sb.append("  请把注册地换成上面的写法再问一次。\n");
            }
        }

        // 该商品+伙伴在库中实际存在的维度取值
        if (s != null && !isBlank(s.getProductName()) && !isBlank(s.getTradeType())) {
            List<String> near = tradeDataQueryService.suggestProducts(
                    s.getTradeType(), s.getProductName(), 1);
            if (near.isEmpty()) {
                if (isBlank(s.getTradeMode())) {
                    List<String> modes = tradeDataQueryService.distinctValues(
                            "贸易方式名称", s.getTradeType(), s.getTradePartnerName(),
                            s.getProductName(), 6);
                    if (!modes.isEmpty()) {
                        sb.append("· 该商品在库中的贸易方式有：")
                                .append(String.join("、", modes)).append("\n");
                    }
                }
                if (isBlank(s.getRegisterName())) {
                    List<String> regs = tradeDataQueryService.distinctValues(
                            "注册地名称", s.getTradeType(), s.getTradePartnerName(),
                            s.getProductName(), 6);
                    if (!regs.isEmpty()) {
                        sb.append("· 该商品在库中的境内注册地有：")
                                .append(String.join("、", regs)).append("\n");
                    }
                }
            }
        }

        // 商品名是俗称时，示例句用第一个规范候选顶上
        String exampleProduct = null;
        if (s != null && !isBlank(s.getProductName()) && !isBlank(s.getTradeType())) {
            List<String> top = tradeDataQueryService.suggestProducts(
                    s.getTradeType(), s.getProductName(), 1);
            if (!top.isEmpty()) exampleProduct = top.get(0);
        }

        sb.append("\n")
                .append("可以这样问（把缺的部分补上即可）：\n")
                .append("「").append(exampleFor(s, exampleProduct)).append("」\n\n")
                .append("四种要素一次说清：贸易伙伴 + 商品名称 + 贸易方式 + 境内注册地，"
                        + "并点明进口/出口与单价/数量；目标月份不写默认下个月。");
        return sb.toString();
    }

    /** 用已识别的槽位拼一个尽量具体的示例，避免每次都是同一句模板。 */
    private String exampleFor(PredictRequest s, String productOverride) {
        String partner = s != null && !isBlank(s.getTradePartnerName())
                ? s.getTradePartnerName() : "哈萨克斯坦";
        String product = productOverride != null ? productOverride
                : (s != null && !isBlank(s.getProductName())
                        ? s.getProductName() : "铜矿砂及其精矿");
        String mode = s != null && !isBlank(s.getTradeMode()) ? s.getTradeMode() : "一般贸易";
        String register = s != null && !isBlank(s.getRegisterName())
                ? s.getRegisterName() : "新疆维吾尔自治区";
        String dir = s != null && "out".equalsIgnoreCase(s.getTradeType()) ? "出口" : "进口";
        String what = s != null && "quantity".equalsIgnoreCase(s.getTarget()) ? "数量" : "单价";
        String when = (s != null && s.getYear() != null && s.getMonth() != null)
                ? (s.getYear() + "年" + s.getMonth() + "月") : "下个月";
        return "预测" + partner + product + "、" + mode + "、" + register + "的" + when + dir + what;
    }

    // =================================================================
    // 闲聊分支
    // =================================================================

    private String buildCapabilityAnswer() {
        return "我是中哈贸易智能助手，可以帮你做三类事：\n\n"
                + "1. 查历史数据 —— 例如「2025年1月哈萨克斯坦的出口数量」\n"
                + "2. 预测未来 —— 例如「预测哈萨克斯坦铜矿砂及其精矿、一般贸易、"
                + "新疆维吾尔自治区的下个月进口单价」\n"
                + "3. 新闻问答 —— 例如「最近有哪些关于哈萨克斯坦的新闻」\n\n"
                + "**可访问的数据范围**：" + tradeDataQueryService.scopeSummary() + "\n\n"
                + "预测需要贸易伙伴、商品名称、贸易方式、境内注册地四项齐全，"
                + "缺哪项我会告诉你库里实际有哪些可选值。";
    }

    // =================================================================

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String blank(String s) {
        return s == null || s.trim().isEmpty() ? "（未限定）" : s.trim();
    }

    private String fmt(Double v) {
        if (v == null) return "-";
        if (Math.abs(v) >= 1000) return String.format("%,.2f", v);
        return String.format("%.4f", v);
    }
}
