package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.dto.AiChatRequest;
import com.example.tdproject.ai.dto.AiChatResponse;
import com.example.tdproject.ai.dto.DataQueryResult;
import com.example.tdproject.ai.dto.PredictRequest;
import com.example.tdproject.ai.intent.IntentResult;
import com.example.tdproject.ai.intent.IntentRouter;
import com.example.tdproject.ai.predict.PredictionService;
import com.example.tdproject.ai.query.TradeDataQueryService;
import com.example.tdproject.ai.rag.NewsRagService;
import com.example.tdproject.ai.rag.RagAnswer;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能问答统一入口。
 *
 * <h3>四条路由</h3>
 * <ul>
 *   <li>{@code DATA_QUERY} —— 查已经发生的历史数据（新增）；</li>
 *   <li>{@code PREDICT} —— 预测未来，槽位不齐时给「可照做」的引导；</li>
 *   <li>{@code RAG_NEWS} —— 语料检索问答；</li>
 *   <li>{@code CHITCHAT} —— 闲聊 / 问能力，给出能力说明。</li>
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

    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest req) {
        try {
            IntentResult intent = intentRouter.route(req);
            String route = intent.getRoute();

            Map<String, Object> debug = new HashMap<>();
            debug.put("intent", intent);

            // ---------------- 预测 ----------------
            if (IntentRouter.ROUTE_PREDICT.equalsIgnoreCase(route)) {
                var slots = intent.getPredictSlots();
                List<String> hints = collectPredictHints(slots);
                if (!hints.isEmpty()) {
                    // 关键：把「缺什么」和「库里实际有什么」一起给出来。此前只给一段
                    // 固定模板，用户照着补也不知道商品名该写什么，于是反复得到同一句话。
                    return Result.build(AiChatResponse.builder()
                            .route(IntentRouter.ROUTE_PREDICT)
                            .answer(buildPredictGuidance(slots, hints))
                            .hints(hints)
                            .predictResult(null)
                            .sources(null)
                            .debugInfo(debug)
                            .build());
                }

                var predict = predictionService.predict(slots);

                return Result.build(AiChatResponse.builder()
                        .route(IntentRouter.ROUTE_PREDICT)
                        .answer(buildPredictAnswer(predict, slots))
                        .predictResult(predict)
                        .sources(null)
                        .debugInfo(debug)
                        .build());
            }

            // ---------------- 历史数据查询 ----------------
            if (IntentRouter.ROUTE_DATA_QUERY.equalsIgnoreCase(route)) {
                var slots = intent.getPredictSlots();
                DataQueryResult data = tradeDataQueryService.query(
                        slots != null ? slots.getTradeType() : null,
                        slots != null ? slots.getTarget() : null,
                        slots != null ? slots.getYear() : null,
                        slots != null ? slots.getMonth() : null,
                        slots != null ? slots.getTradePartnerName() : null,
                        slots != null ? slots.getProductName() : null,
                        slots != null ? slots.getTradeMode() : null,
                        slots != null ? slots.getRegisterName() : null);

                return Result.build(AiChatResponse.builder()
                        .route(IntentRouter.ROUTE_DATA_QUERY)
                        .answer(data.getSummary())
                        .dataQuery(data)
                        .sources(null)
                        .predictResult(null)
                        .debugInfo(debug)
                        .build());
            }

            // ---------------- 闲聊 / 能力询问 ----------------
            if (IntentRouter.ROUTE_CHITCHAT.equalsIgnoreCase(route)) {
                return Result.build(AiChatResponse.builder()
                        .route(IntentRouter.ROUTE_CHITCHAT)
                        .answer(buildCapabilityAnswer())
                        .sources(null)
                        .predictResult(null)
                        .debugInfo(debug)
                        .build());
            }

            // ---------------- 默认：RAG ----------------
            String country = req != null ? req.getCountry() : null;
            Integer year = req != null ? req.getYear() : null;

            RagAnswer rag = newsRagService.answer(req.getText(), country, year);

            return Result.build(AiChatResponse.builder()
                    .route(IntentRouter.ROUTE_RAG_NEWS)
                    .answer(rag.getAnswer())
                    .sources(rag.getSources())
                    .predictResult(null)
                    .debugInfo(debug)
                    .build());

        } catch (IllegalArgumentException e) {
            return Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("/ai/chat 异常", e);
            return Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage());
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
     *
     * <p>与旧版的区别：旧版无论缺几项、缺哪项，都给同一段「可以这样问：…」，
     * 用户的主观感受就是「所有问题输出都一样」。现在会逐项列出已识别/还缺，
     * 并在商品名不在库中时直接给出规范名候选、在该商品已有维度上给出可选值。</p>
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

        // 商品名核对 —— 这是用户最常写错、也最无从下手的一项
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

        sb.append("\n")
                .append("可以这样问（把缺的部分补上即可）：\n")
                .append("「").append(exampleFor(s)).append("」\n\n")
                .append("四种要素一次说清：贸易伙伴 + 商品名称 + 贸易方式 + 境内注册地，"
                        + "并点明进口/出口与单价/数量；目标月份不写默认下个月。");
        return sb.toString();
    }

    /** 用已识别的槽位拼一个尽量具体的示例，避免每次都是同一句模板。 */
    private String exampleFor(PredictRequest s) {
        String partner = s != null && !isBlank(s.getTradePartnerName())
                ? s.getTradePartnerName() : "哈萨克斯坦";
        String product = s != null && !isBlank(s.getProductName())
                ? s.getProductName() : "铜矿砂及其精矿";
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
                + "说明：历史数据覆盖 2015-01 ~ 2025-03；预测需要贸易伙伴、商品名称、"
                + "贸易方式、境内注册地四项齐全，缺哪项我会告诉你库里实际有哪些可选值。";
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
