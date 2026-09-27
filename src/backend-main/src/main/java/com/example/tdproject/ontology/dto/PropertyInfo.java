package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 属性信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "属性信息")
public class PropertyInfo {
    
    @Schema(description = "属性ID/URI", example = "http://example.org/hasName")
    private String id;
    
    @Schema(description = "属性名", example = "hasName")
    private String name;
    
    @Schema(description = "属性URI", example = "http://example.org/hasName")
    private String uri;
    
    @Schema(description = "属性类型：object-对象属性, datatype-数据属性, annotation-注释属性", 
            example = "datatype", allowableValues = {"object", "datatype", "annotation"})
    private String type;
    
    @Schema(description = "定义域（适用类）", example = "Person")
    private String domain;
    
    @Schema(description = "值域（属性值类型）", example = "string")
    private String range;
    
    @Schema(description = "描述", example = "名称属性")
    private String description;
}