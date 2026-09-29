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
                // 防御：即便路由把它判成预测，槽位也可能不完整（用户只说了「预测一下」
                // 而没给任何实体）。与其把 predict(null) 的异常抛给前端，不如返回一句
                // 能照着补全的提示。槽位补齐本身在 IntentRouter.mergeSlots 里完成。
                if (intent.getPredictSlots() == null) {
                    return Result.build(AiChatResponse.builder()
                            .route(IntentRouter.ROUTE_PREDICT)
                            .answer("已识别为「贸易预测」问题，但没能从提问里取到足够的槽位。"
                                    + "请补充：贸易伙伴（如 哈萨克斯坦）、商品名称、贸易方式"
                                    + "（如 一般贸易）、境内注册地（如 新疆维吾尔自治区），"
                                    + "并说明要预测进口还是出口、单价还是数量。")
                            .predictResult(null)
                            .sources(null)
                            .debugInfo(debug)
                            .build());
                }

                var predict = predictionService.predict(intent.getPredictSlots());
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
}
