package com.example.tdproject.graph.dto;

import lombok.Data;

/**
 * 路径分析请求DTO
 */
@Data
public class PathAnalysisRequest {
    
    /**
     * 起始节点URI
     */
    private String sourceNode;
    
    /**
     * 目标节点URI
     */
    private String targetNode;
    
    /**
     * 起始节点ID（前端使用）
     */
    private String sourceId;
    
    /**
     * 目标节点ID（前端使用）
     */
    private String targetId;
    
    /**
     * 分析类型：shortest(最短路径), all(全通路径)
     */
    private String algorithm;
    
    /**
     * 最大搜索深度（防止环路导致无限搜索）
     */
    private Integer maxDepth;
    
    /**
     * 关系类型过滤（可选）
     */
    private String relationType;
    
    /**
     * 最大返回路径数（用于全通路径）
     */
    private Integer maxPaths;
    
    public PathAnalysisRequest() {
        this.maxDepth = 5;
        this.maxPaths = 10;
    }
}
