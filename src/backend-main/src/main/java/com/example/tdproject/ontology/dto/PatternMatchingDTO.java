package com.example.tdproject.ontology.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 模式匹配请求DTO
 * 用于在图谱中查找匹配指定模式的子图
 */
@Data
public class PatternMatchingDTO {
    
    /**
     * 模式节点列表
     */
    private List<PatternNode> nodes;
    
    /**
     * 模式边列表
     */
    private List<PatternEdge> edges;
    
    /**
     * 最小匹配比例（默认0.7）
     * 用于部分匹配场景，如允许部分边缺失
     */
    private Double minMatchRatio = 0.7;
    
    /**
     * 最大返回结果数（默认50）
     */
    private Integer maxResults = 50;
    
    /**
     * 模式节点定义
     */
    @Data
    public static class PatternNode {
        /**
         * 节点ID（变量名，如"?x"或具体ID如"individual_1"）
         */
        private String nodeId;
        
        /**
         * 实体类型：INDIVIDUAL, CLASS, PROPERTY
         */
        private String entityType;
        
        /**
         * 是否为变量节点
         * true: 变量节点，需要查找候选实体
         * false: 具体节点，直接使用actualNodeId
         */
        private Boolean isVariable = true;
        
        /**
         * 具体节点ID（非变量时使用）
         */
        private String actualNodeId;
        
        /**
         * 约束条件
         * 支持：className, individualName, propertyName等
         */
        private Map<String, Object> constraints;
    }
    
    /**
     * 模式边定义
     */
    @Data
    public static class PatternEdge {
        /**
         * 源节点ID（对应PatternNode的nodeId）
         */
        private String sourceNodeId;
        
        /**
         * 目标节点ID（对应PatternNode的nodeId）
         */
        private String targetNodeId;
        
        /**
         * 关系类型（可选）
         * 如：rdf:type, objectProperty, subClassOf等
         */
        private String relationshipType;
        
        /**
         * 是否可选边
         * true: 边可以不存在，不影响匹配
         * false: 边必须存在
         */
        private Boolean isOptional = false;
    }
}
