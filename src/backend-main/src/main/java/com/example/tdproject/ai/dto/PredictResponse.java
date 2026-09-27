package com.example.tdproject.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PredictResponse {
    /** price/quantity */
    private String target;
    /** in/out */
    private String tradeType;

    /** 标准化后的数值（若能解析到） */
    private Double value;

    /** 单位（来自 Flask） */
    private String unit;

    /** Flask 原始返回（包含 warning/error 等） */
    private Map<String, Object> raw;
}
