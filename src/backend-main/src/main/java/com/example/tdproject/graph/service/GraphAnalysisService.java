package com.example.tdproject.graph.service;

import com.example.tdproject.graph.dto.PathAnalysisRequest;
import com.example.tdproject.graph.dto.PathAnalysisResponse;

/**
 * 图分析服务接口
 * 提供最短路径、全通路径等图算法
 */
public interface GraphAnalysisService {
    
    /**
     * 查找最短路径
     * @param namedGraphUri 命名图URI
     * @param request 路径分析请求
     * @return 路径分析结果
     */
    PathAnalysisResponse findShortestPath(String namedGraphUri, PathAnalysisRequest request);
    
    /**
     * 查找所有路径
     * @param namedGraphUri 命名图URI
     * @param request 路径分析请求
     * @return 路径分析结果
     */
    PathAnalysisResponse findAllPaths(String namedGraphUri, PathAnalysisRequest request);
    
    /**
     * 查找节点邻居
     * @param namedGraphUri 命名图URI
     * @param nodeUri 节点URI
     * @param depth 深度
     * @param relationType 关系类型（可选）
     * @return 邻居节点列表
     */
    PathAnalysisResponse findNeighbors(String namedGraphUri, String nodeUri, Integer depth, String relationType);
    
    /**
     * 执行自定义路径分析
     * @param namedGraphUri 命名图URI
     * @param request 路径分析请求
     * @return 路径分析结果
     */
    PathAnalysisResponse analyzePath(String namedGraphUri, PathAnalysisRequest request);
}
