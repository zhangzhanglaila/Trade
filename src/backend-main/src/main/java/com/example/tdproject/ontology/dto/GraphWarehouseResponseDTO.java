package com.example.tdproject.ontology.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图谱入库响应DTO
 */
@Data
@Builder
public class GraphWarehouseResponseDTO {
    
    /**
     * 入库状态：success / failed
     */
    private String status;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 保存的节点数量
     */
    private Integer nodeCount;
    
    /**
     * 保存的关系数量
     */
    private Integer relationshipCount;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 入库时间
     */
    private LocalDateTime timestamp;
}
