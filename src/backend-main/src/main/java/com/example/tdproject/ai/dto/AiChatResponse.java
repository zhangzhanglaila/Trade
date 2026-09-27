package com.example.tdproject.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiChatResponse {
    private String route; // PREDICT / RAG_NEWS

    /** 最终回答（RAG 或对预测结果的解释） */
    private String answer;

    /** RAG 召回来源 */
    private List<RagHit> sources;

    /** 预测结果（原样透传 Flask + 规范化字段） */
    private PredictResponse predictResult;

    /** 调试信息（可选） */
    private Map<String, Object> debugInfo;
}
