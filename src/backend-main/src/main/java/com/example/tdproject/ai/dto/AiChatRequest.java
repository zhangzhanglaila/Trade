package com.example.tdproject.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class AiChatRequest {
    /** 用户输入原文 */
    private String text;

    /** 可选：用于 RAG 过滤 */
    private String country;
    private Integer year;

    /** 可选：预测路由槽位（也可交给意图识别填充） */
    private String tradeType; // in/out
    private String target;    // price/quantity
    private Integer month;

    /** 预测槽位（与 Flask 要求字段一致，支持中文别名入参） */
    @JsonAlias({"贸易伙伴名称"})
    private String tradePartnerName;

    @JsonAlias({"商品名称"})
    private String productName;

    @JsonAlias({"贸易方式"})
    private String tradeMode;

    @JsonAlias({"注册地名称"})
    private String registerName;
}
