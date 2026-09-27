package com.example.tdproject.ontology.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区发现结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityDetectionResult {

    /**
     * 社区列表，每个社区包含一组节点ID
     */
    private List<Set<String>> communities;

    /**
     * 模块度（社区划分质量指标，-0.5 到 1.0 之间）
     */
    private Double modularity;

    /**
     * 实际迭代次数
     */
    private Integer iterations;

    /**
     * 使用的算法名称
     */
    private String algorithm;

    /**
     * 社区详细信息（包含节点详情）
     */
    private List<Map<String, Object>> communityDetails;

    /**
     * 统计信息
     */
    private Map<String, Object> statistics;

    /**
     * 算法参数
     */
    private Map<String, Object> parameters;
}
