package com.example.tdproject.graph.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 图路径DTO
 */
@Data
@Builder
public class GraphPathDTO {
    
    /**
     * 路径ID
     */
    private String pathId;
    
    /**
     * 路径上的节点列表（按顺序）
     */
    private List<PathNodeDTO> nodes;
    
    /**
     * 路径上的边列表
     */
    private List<PathEdgeDTO> edges;
    
    /**
     * 路径长度（节点数）
     */
    private Integer length;
    
    /**
     * 路径权重（带权图时使用）
     */
    private Double weight;
    
    /**
     * 路径类型：shortest, alternative
     */
    private String type;
    
    /**
     * 路径节点DTO
     */
    @Data
    @Builder
    public static class PathNodeDTO {
        private String id;
        private String uri;
        private String label;
        private String type;
        private Integer order;
    }
    
    /**
     * 路径边DTO
     */
    @Data
    @Builder
    public static class PathEdgeDTO {
        private String id;
        private String source;
        private String target;
        private String label;
        private String type;
        private Double weight;
    }
}
