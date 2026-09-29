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

    /** 参与本轮检索的语料总条数（取自向量库内存计数，-1 表示未知）。 */
    private Integer corpusSize;

    /** 实际进入大模型上下文的片段条数，等于 sources 中 usedInContext=true 的条数。 */
    private Integer contextDocs;
}
