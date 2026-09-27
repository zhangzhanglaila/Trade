package com.example.tdproject.ontology.dto;

import lombok.Data;

/**
 * 创建/更新属性请求DTO
 */
@Data
public class PropertyCreateRequest {
    
    /**
     * 属性名（本地名称）
     */
    private String name;
    
    /**
     * 属性类型：object/datatype/annotation
     */
    private String type;
    
    /**
     * 定义域（该属性适用的类URI）
     */
    private String domain;
    
    /**
     * 值域（对象属性对应类URI，数据属性对应数据类型）
     */
    private String range;
    
    /**
     * 属性描述
     */
    private String description;
}
