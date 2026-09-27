package com.example.tdproject.ontology.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 实例信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实例信息")
public class IndividualInfo {
    
    @Schema(description = "实例ID/URI", example = "http://example.org/JohnDoe")
    private String id;
    
    @Schema(description = "实例名", example = "JohnDoe")
    private String name;
    
    @Schema(description = "实例URI", example = "http://example.org/JohnDoe")
    private String uri;
    
    @Schema(description = "所属类ID", example = "http://example.org/Person")
    private String classId;
    
    @Schema(description = "所属类名", example = "Person")
    private String className;
    
    @Schema(description = "描述", example = "示例人物")
    private String description;
    
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private String createTime;
    
    @Schema(description = "属性值映射", example = "{\"hasName\": \"John Doe\", \"hasAge\": 30}")
    private Map<String, Object> properties;
}