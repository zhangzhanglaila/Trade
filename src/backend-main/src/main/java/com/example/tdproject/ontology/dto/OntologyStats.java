package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本体统计数据DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体统计数据")
public class OntologyStats {
    
    @Schema(description = "类数量", example = "12")
    private long classCount;
    
    @Schema(description = "实例数量", example = "45")
    private long individualCount;
    
    @Schema(description = "属性数量", example = "23")
    private long propertyCount;
    
    @Schema(description = "三元组数量", example = "156")
    private long tripleCount;
}