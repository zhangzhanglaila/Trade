package com.example.tdproject.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PredictRequest {
    /** in/out */
    private String tradeType;
    /** price/quantity */
    private String target;

    private Integer year;
    private Integer month;

    private String tradePartnerName;
    private String productName;
    private String tradeMode;
    private String registerName;

    /** 额外字段（可用于调试/扩展） */
    private Map<String, Object> extra;
}
