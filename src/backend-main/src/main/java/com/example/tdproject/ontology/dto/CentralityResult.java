package com.example.tdproject.ontology.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 中心度分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CentralityResult {

    /**
     * 中心度类型
     */
    private String centralityType;

    /**
     * 算法信息
     */
    private Map<String, Object> algorithm;

    /**
     * 中心度结果列表
     */
    private List<Map<String, Object>> results;

    /**
     * 总节点数
     */
    private Integer totalNodes;

    /**
     * 符合条件的节点数
     */
    private Integer qualifiedNodes;

    /**
     * 返回结果数
     */
    private Integer returnedCount;

    /**
     * 限制数量
     */
    private Integer limit;

    /**
     * 最小阈值
     */
    private Double minThreshold;

    /**
     * 统计信息（最大值、最小值、平均值、中位数）
     */
    private Map<String, Object> statistics;
}
