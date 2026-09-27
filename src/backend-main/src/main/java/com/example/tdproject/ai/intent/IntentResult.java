package com.example.tdproject.ai.intent;

import com.example.tdproject.ai.dto.PredictRequest;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IntentResult {
    /** PREDICT / RAG_NEWS */
    private String route;

    /** 识别出的预测槽位（route==PREDICT 时使用） */
    private PredictRequest predictSlots;

    /** 识别出的检索过滤槽位（route==RAG_NEWS 时使用） */
    private Map<String, Object> ragFilters;

    /** 调试信息 */
    private Map<String, Object> debug;
}
