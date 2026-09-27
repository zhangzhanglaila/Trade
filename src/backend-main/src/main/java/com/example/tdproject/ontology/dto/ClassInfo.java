package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 类信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "类信息")
public class ClassInfo {
    
    @Schema(description = "类ID/URI", example = "http://example.org/Person")
    private String id;
    
    @Schema(description = "类名", example = "Person")
    private String name;
    
    @Schema(description = "类URI", example = "http://example.org/Person")
    private String uri;
    
    @Schema(description = "父类ID", example = "http://example.org/Thing")
    private String parentId;
    
    @Schema(description = "父类名", example = "Thing")
    private String parentName;
    
    @Schema(description = "描述", example = "人物类")
    private String description;
    
    @Schema(description = "实例数量", example = "10")
    private long individualCount;
}