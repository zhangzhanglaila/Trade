package com.example.tdproject.ontology.dto;

import lombok.Builder;
import lombok.Data;

/**
 * RDF 文件验证结果
 */
@Data
@Builder
public class RdfValidationResult {
    
    /**
     * 是否验证通过
     */
    private boolean valid;
    
    /**
     * 文件格式（RDF/XML, Turtle, N-Triples 等）
     */
    private String format;
    
    /**
     * 三元组数量（如果解析成功）
     */
    private Long tripleCount;
    
    /**
     * 错误信息（如果验证失败）
     */
    private String errorMessage;
    
    /**
     * 详细错误（堆栈或行号等）
     */
    private String detailError;
    
    /**
     * 建议操作
     */
    private String suggestion;
}
