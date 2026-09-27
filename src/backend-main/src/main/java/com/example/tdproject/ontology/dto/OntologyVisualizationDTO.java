package com.example.tdproject.ontology.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 本体可视化DTO
 */
@Data
@Builder
public class OntologyVisualizationDTO {
    
    /**
     * 节点列表
     */
    private List<NodeDTO> nodes;
    
    /**
     * 边列表
     */
    private List<EdgeDTO> edges;
    
    /**
     * 统计信息
     */
    private Map<String, Object> statistics;
    
    /**
     * 节点DTO
     */
    @Data
    @Builder
    public static class NodeDTO {
        private String id;           // 节点唯一标识
        private String label;        // 显示文本
        private String type;         // 类型: class/individual/property
        private String color;        // 颜色
        private Integer size;        // 大小
        private Integer level;       // 层级: 0-类, 1-实例
        private String parentId;     // 父节点ID（用于层次结构）
        private String uri;          // 资源URI
        private Map<String, Object> data; // 扩展数据
    }
    
    /**
     * 边DTO
     */
    @Data
    @Builder
    public static class EdgeDTO {
        private String id;           // 边唯一标识
        private String source;       // 起始节点ID
        private String target;       // 目标节点ID
        private String label;        // 关系标签
        private String type;         // 类型: subClassOf/instanceOf/objectProperty/datatypeProperty
        private String color;        // 颜色
    }
}
