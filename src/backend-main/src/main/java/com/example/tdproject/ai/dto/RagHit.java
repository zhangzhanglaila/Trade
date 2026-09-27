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
}
