package com.example.tdproject.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiChatResponse {
    /** PREDICT / DATA_QUERY / RAG_NEWS / CHITCHAT */
    private String route;

    /** 最终回答（RAG 或对预测/查询结果的解释） */
    private String answer;

    /** RAG 召回来源 */
    private List<RagHit> sources;

    /** 预测结果（原样透传 Flask + 规范化字段） */
    private PredictResponse predictResult;

    /** 历史数据查询结果（route=DATA_QUERY 时填充） */
    private DataQueryResult dataQuery;

    /** 引导信息：缺槽位时给出「还缺什么 / 库里实际可选值」 */
    private List<String> hints;

    /** 调试信息（可选） */
    private Map<String, Object> debugInfo;
}
