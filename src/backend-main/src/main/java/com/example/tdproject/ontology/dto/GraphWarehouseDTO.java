package com.example.tdproject.ontology.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 图谱入库请求DTO
 */
@Data
public class GraphWarehouseDTO {
    
    /**
     * 本体ID
     */
    private Long ontologyId;
    
    /**
     * 本体名称
     */
    private String ontologyName;
    
    /**
     * 新版本号
     */
    private String version;
    
    /**
     * 版本说明
     */
    private String remark;
    
    /**
     * 节点列表
     */
    private List<NodeDTO> nodes;
    
    /**
     * 关系列表
     */
    private List<EdgeDTO> relationships;
    
    /**
     * 节点DTO
     */
    @Data
    public static class NodeDTO {
        private String id;
        private String name;
        private String type;
        private String iri;
        private String description;
        private String color;
        private Integer size;
        private Map<String, Object> data;
    }
    
    /**
     * 边DTO
     */
    @Data
    public static class EdgeDTO {
        private String id;
        private String source;
        private String target;
        private String relationshipName;
        private String relationshipType;
        private String description;
        private String color;
        private Integer width;
    }
}
