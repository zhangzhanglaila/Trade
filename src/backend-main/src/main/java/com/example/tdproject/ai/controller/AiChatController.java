package com.example.tdproject.ai.controller;

import com.example.tdproject.ai.dto.AiChatRequest;
import com.example.tdproject.ai.dto.AiChatResponse;
import com.example.tdproject.ai.intent.IntentResult;
import com.example.tdproject.ai.intent.IntentRouter;
import com.example.tdproject.ai.predict.PredictionService;
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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final IntentRouter intentRouter;
    private final PredictionService predictionService;
    private final NewsRagService newsRagService;

    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@RequestBody AiChatRequest req) {
        try {
            IntentResult intent = intentRouter.route(req);
            String route = intent.getRoute();

            Map<String, Object> debug = new HashMap<>();
            debug.put("intent", intent);

            if (IntentRouter.ROUTE_PREDICT.equalsIgnoreCase(route)) {
                // 防御：即便路由把它判成预测，槽位也可能不完整。此前这种情况会落到
                // predictionService 的校验里抛 IllegalArgumentException，控制器返回
                // code=206 且 answer 为空 —— 前端渲染出一片空白，用户不知道要补什么。
                // 实测输入「帮我预测一下」即是如此。这里改成先检查缺哪些槽位，
                // 缺就直接给一句能照着补全的话，并且仍然带上 route=PREDICT。
                var slots = intent.getPredictSlots();
                String missing = missingPredictFields(slots);
                if (missing != null) {
                    return Result.build(AiChatResponse.builder()
                            .route(IntentRouter.ROUTE_PREDICT)
                            .answer(buildSlotGuidance(missing))
                            .predictResult(null)
                            .sources(null)
                            .debugInfo(debug)
                            .build());
                }

                var predict = predictionService.predict(slots);
                String answer = buildPredictAnswer(predict);

                return Result.build(AiChatResponse.builder()
                        .route(IntentRouter.ROUTE_PREDICT)
                        .answer(answer)
                        .predictResult(predict)
                        .sources(null)
                        .debugInfo(debug)
                        .build());
            }

            // 默认：RAG
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

    private String buildPredictAnswer(com.example.tdproject.ai.dto.PredictResponse predict) {
        if (predict == null) return "预测失败";
        if (predict.getValue() == null) {
            return "预测已调用完成，但未能解析到数值结果。原始返回: " + (predict.getRaw() == null ? "{}" : predict.getRaw().toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("预测结果：");
        sb.append(predict.getValue());
        if (predict.getUnit() != null) {
            sb.append(" ").append(predict.getUnit());
        }

        if (predict.getRaw() != null && predict.getRaw().get("warning") != null) {
            sb.append("\n注意：").append(predict.getRaw().get("warning"));
        }

        return sb.toString();
    }

    /**
     * 返回缺失的槽位名称（中文），全部齐备时返回 null。
     *
     * <p>为什么要在控制器里再查一遍：{@code PredictionService.validate} 只抛一句
     * 笼统的「预测槽位不能为空」，而用户需要知道<b>具体</b>该补哪一项。
     * 另外 year/month 在 IntentRouter 里已经缺省成下个月，正常不会缺。</p>
     */
    private String missingPredictFields(com.example.tdproject.ai.dto.PredictRequest s) {
        if (s == null) {
            return "全部预测信息";
        }
        java.util.List<String> miss = new java.util.ArrayList<>();
        if (isBlank(s.getTradeType())) miss.add("进口还是出口");
        if (isBlank(s.getTarget())) miss.add("预测单价还是数量");
        if (isBlank(s.getTradePartnerName())) miss.add("贸易伙伴（如：哈萨克斯坦）");
        if (isBlank(s.getProductName())) miss.add("商品名称");
        if (isBlank(s.getTradeMode())) miss.add("贸易方式（如：一般贸易）");
        if (isBlank(s.getRegisterName())) miss.add("境内注册地（如：新疆维吾尔自治区）");
        if (s.getYear() == null || s.getMonth() == null) miss.add("目标年月");
        if (miss.isEmpty()) return null;
        return String.join("、", miss);
    }

    private String buildSlotGuidance(String missing) {
        return "已识别为「贸易预测」问题，但还缺少：" + missing + "。\n"
                + "可以这样问：「预测哈萨克斯坦铜矿砂及其精矿、一般贸易、到北京市的"
                + "下个月进口单价」—— 一次把贸易伙伴、商品名称、贸易方式、境内注册地"
                + "说清楚，并点明进口/出口与单价/数量即可。\n"
                + "（目标月份不写默认为下个月。）";
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
