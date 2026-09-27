package com.example.tdproject.graph.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 路径分析响应DTO
 */
@Data
@Builder
public class PathAnalysisResponse {
    
    /**
     * 状态：success, error
     */
    private String status;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 起始节点
     */
    private String sourceNode;
    
    /**
     * 目标节点
     */
    private String targetNode;
    
    /**
     * 找到的路径列表
     */
    private List<GraphPathDTO> paths;
    
    /**
     * 路径总数
     */
    private Integer totalPaths;
    
    /**
     * 最短路径长度
     */
    private Integer shortestLength;
    
    /**
     * 搜索耗时（毫秒）
     */
    private Long searchTime;
    
    /**
     * 搜索深度
     */
    private Integer searchDepth;
    
    /**
     * 统计信息
     */
    private Map<String, Object> statistics;
}
