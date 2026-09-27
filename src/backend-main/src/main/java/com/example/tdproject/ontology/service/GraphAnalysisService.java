package com.example.tdproject.ontology.service;

import com.example.tdproject.ontology.dto.CommunityDetectionResult;
import com.example.tdproject.ontology.dto.CentralityResult;
import com.example.tdproject.ontology.dto.PatternMatchingDTO;
import java.util.List;
import java.util.Map;

/**
 * 图分析服务接口
 */
public interface GraphAnalysisService {

    /**
     * 社区发现
     *
     * @param ontologyId       本体ID
     * @param algorithm        算法类型：LOUVAIN, LABEL_PROPAGATION, MODULARITY_OPTIMIZATION
     * @param resolution       分辨率参数（默认1.0）
     * @param maxIterations    最大迭代次数（默认100）
     * @param minCommunitySize 最小社区大小（默认2）
     * @return 社区发现结果
     */
    CommunityDetectionResult detectCommunities(Long ontologyId, String algorithm,
                                                Double resolution, Integer maxIterations, Integer minCommunitySize);

    /**
     * 中心度分析
     *
     * @param ontologyId    本体ID
     * @param centralityType 中心度类型：DEGREE, BETWEENNESS, CLOSENESS, EIGENVECTOR
     * @param limit         返回结果数量限制
     * @param minThreshold  最小阈值
     * @return 中心度分析结果
     */
    CentralityResult analyzeCentrality(Long ontologyId, String centralityType, 
                                       Integer limit, Double minThreshold);

    /**
     * 邻居查询
     *
     * @param ontologyId    本体ID
     * @param nodeId        节点ID
     * @param depth         查询深度
     * @return 邻居查询结果
     */
    Map<String, Object> queryNeighbors(Long ontologyId, String nodeId, Integer depth);

    /**
     * 关联查询
     *
     * @param ontologyId    本体ID
     * @param nodeIds       节点ID列表
     * @param queryType     查询类型：DIRECT, INDIRECT, ALL
     * @return 关联查询结果
     */
    Map<String, Object> queryAssociations(Long ontologyId, List<String> nodeIds, String queryType);

    /**
     * 实体扩线（支持方向过滤）
     *
     * @param ontologyId       本体ID
     * @param entityId         中心实体ID
     * @param expandLevel      扩展层级（1-5）
     * @param direction        方向：IN/OUT/BOTH
     * @return 扩线结果
     */
    Map<String, Object> expandEntity(Long ontologyId, String entityId, Integer expandLevel, String direction);

    /**
     * 图谱结构分析
     *
     * @param ontologyId    本体ID
     * @return 图谱结构统计信息
     */
    Map<String, Object> analyzeGraphStructure(Long ontologyId);

    /**
     * 模式匹配
     * 在图谱中查找匹配指定模式的子图
     *
     * @param ontologyId    本体ID
     * @param patternRequest 模式匹配请求
     * @return 匹配结果，包含匹配的节点和边
     */
    Map<String, Object> patternMatching(Long ontologyId, PatternMatchingDTO patternRequest);
}
