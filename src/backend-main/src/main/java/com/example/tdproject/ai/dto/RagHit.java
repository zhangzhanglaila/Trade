package com.example.tdproject.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class RagHit {
    private Long newsId;
    private String title;
    private String source;
    private Date publishTime;
    private Double score;
    /**
     * 该条是否真的进了大模型的上下文。
     *
     * <p>检索会取回比上下文容量更多的候选，用来说明「一共捞到多少条」；
     * 但只有得分最高的前若干条会作为片段交给大模型。前端据此把两类分开显示，
     * 避免用户误以为「列出来的都参与了回答」或反之。</p>
     */
    private Boolean usedInContext;
}
