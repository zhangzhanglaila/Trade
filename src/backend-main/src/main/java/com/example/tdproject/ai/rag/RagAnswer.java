package com.example.tdproject.ai.rag;

import com.example.tdproject.ai.dto.RagHit;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RagAnswer {
    private String answer;
    private List<RagHit> sources;
}
