package com.example.tdproject.ontology.service.impl;

import com.example.tdproject.generator.domain.Ontology;
import com.example.tdproject.generator.service.OntologyService;
import com.example.tdproject.ontology.dto.*;
import com.example.tdproject.ontology.repository.GraphRepository;
import com.example.tdproject.ontology.service.GraphAnalysisService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图分析服务实现类
 */
@Slf4j
@Service("ontologyGraphAnalysisService")
public class GraphAnalysisServiceImpl implements GraphAnalysisService {

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private OntologyService ontologyService;

    @Override
    public CommunityDetectionResult detectCommunities(Long ontologyId, String algorithm,
                                                       Double resolution, Integer maxIterations, Integer minCommunitySize) {
        try {
            log.info("开始社区发现: 本体ID={}, 算法={}, 分辨率={}, 最大迭代={}, 最小社区大小={}",
                    ontologyId, algorithm, resolution, maxIterations, minCommunitySize);

            // 设置默认参数
            resolution = resolution != null ? resolution : 1.0;
            maxIterations = maxIterations != null ? maxIterations : 100;
            minCommunitySize = minCommunitySize != null ? minCommunitySize : 2;

            // 获取图谱数据
            Map<String, List<String>> adjacencyList = buildAdjacencyList(ontologyId);
            if (adjacencyList.isEmpty()) {
                return createEmptyCommunityResult(algorithm, resolution, maxIterations, minCommunitySize);
            }

            // 执行社区发现算法
            CommunityDetectionResult result = executeCommunityDetection(
                    adjacencyList, algorithm, resolution, maxIterations, minCommunitySize);

            // 添加节点详情
            addCommunityNodeDetails(result, ontologyId);

            log.info("社区发现完成: 算法={}, 发现{}个社区, 模块度={}",
                    algorithm, result.getCommunities().size(), result.getModularity());

            return result;

        } catch (Exception e) {
            log.error("社区发现失败", e);
            throw new RuntimeException("社区发现失败: " + e.getMessage());
        }
    }

    @Override
    public CentralityResult analyzeCentrality(Long ontologyId, String centralityType,
                                               Integer limit, Double minThreshold) {
        try {
            log.info("中心度分析: 本体ID={}, 类型={}, 限制={}, 最小阈值={}",
                    ontologyId, centralityType, limit, minThreshold);

            // 设置默认参数
            limit = limit != null ? limit : 10;
            minThreshold = minThreshold != null ? minThreshold : 0.0;

            // 获取图谱数据
            Map<String, List<String>> adjacencyList = buildAdjacencyList(ontologyId);
            List<String> nodes = new ArrayList<>(adjacencyList.keySet());

            if (nodes.isEmpty()) {
                return createEmptyCentralityResult(centralityType, limit, minThreshold);
            }

            // 计算中心度
            Map<String, Double> centralityScores = calculateCentrality(adjacencyList, nodes, centralityType);

            // 处理结果
            List<Map<String, Object>> results = processCentralityResults(centralityScores, minThreshold, limit, ontologyId);

            // 构建返回结果
            CentralityResult result = CentralityResult.builder()
                    .centralityType(centralityType)
                    .algorithm(getCentralityAlgorithmInfo(centralityType))
                    .results(results)
                    .totalNodes(nodes.size())
                    .qualifiedNodes(centralityScores.size())
                    .returnedCount(results.size())
                    .limit(limit)
                    .minThreshold(minThreshold)
                    .statistics(calculateStatistics(centralityScores))
                    .build();

            log.info("中心度分析完成: 类型={}, 返回{}个节点", centralityType, results.size());
            return result;

        } catch (Exception e) {
            log.error("中心度分析失败", e);
            throw new RuntimeException("中心度分析失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryNeighbors(Long ontologyId, String nodeId, Integer depth) {
        // 使用前端已实现的本地版本，后端提供基础数据
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, List<String>> adjacencyList = buildAdjacencyList(ontologyId);
            
            Set<String> neighbors = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            Map<String, Integer> levelMap = new HashMap<>();

            queue.offer(nodeId);
            levelMap.put(nodeId, 0);

            while (!queue.isEmpty()) {
                String currentNode = queue.poll();
                int currentLevel = levelMap.get(currentNode);

                if (currentLevel >= depth) continue;

                List<String> nodeNeighbors = adjacencyList.getOrDefault(currentNode, new ArrayList<>());
                for (String neighbor : nodeNeighbors) {
                    if (!levelMap.containsKey(neighbor)) {
                        neighbors.add(neighbor);
                        levelMap.put(neighbor, currentLevel + 1);
                        queue.offer(neighbor);
                    }
                }
            }

            result.put("centerNode", nodeId);
            result.put("depth", depth);
            result.put("neighbors", new ArrayList<>(neighbors));
            result.put("totalNeighbors", neighbors.size());

        } catch (Exception e) {
            log.error("邻居查询失败", e);
            throw new RuntimeException("邻居查询失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> queryAssociations(Long ontologyId, List<String> nodeIds, String queryType) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("关联查询: 实体数量={}, 查询类型={}", nodeIds.size(), queryType);

            Map<String, List<String>> adjacencyList = buildAdjacencyList(ontologyId);
            List<Map<String, Object>> associations = new ArrayList<>();

            if ("DIRECT".equals(queryType) || "ALL".equals(queryType)) {
                for (int i = 0; i < nodeIds.size(); i++) {
                    for (int j = i + 1; j < nodeIds.size(); j++) {
                        String entity1 = nodeIds.get(i);
                        String entity2 = nodeIds.get(j);

                        if (adjacencyList.containsKey(entity1) &&
                                adjacencyList.get(entity1).contains(entity2)) {
                            Map<String, Object> association = new HashMap<>();
                            association.put("source", entity1);
                            association.put("target", entity2);
                            association.put("type", "DIRECT");
                            associations.add(association);
                        }
                    }
                }
            }

            result.put("associations", associations);
            result.put("queryType", queryType);

        } catch (Exception e) {
            log.error("关联查询失败", e);
            throw new RuntimeException("关联查询失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> expandEntity(Long ontologyId, String entityId, Integer expandLevel, String direction) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 参数校验和默认值
            if (expandLevel == null || expandLevel < 1) expandLevel = 1;
            if (expandLevel > 5) expandLevel = 5; // 最大5层
            if (direction == null || direction.isEmpty()) direction = "BOTH";
            
            log.info("实体扩线开始: 本体ID={}, 实体={}, 层级={}, 方向={}", ontologyId, entityId, expandLevel, direction);
            
            // 构建有向邻接表（出边和入边）
            DirectedAdjacencyList directedAdjList = buildDirectedAdjacencyList(ontologyId);
            if (directedAdjList.isEmpty() || !directedAdjList.containsNode(entityId)) {
                log.warn("实体不存在或图谱为空: {}", entityId);
                result.put("centerEntity", entityId);
                result.put("expandLevel", expandLevel);
                result.put("direction", direction);
                result.put("nodes", new ArrayList<>());
                result.put("edges", new ArrayList<>());
                result.put("levelStatistics", new HashMap<>());
                result.put("totalNodes", 0);
                result.put("totalEdges", 0);
                return result;
            }
            
            // 根据方向选择邻接关系
            Map<String, List<String>> adjacencyList;
            switch (direction.toUpperCase()) {
                case "OUT":
                    adjacencyList = directedAdjList.getOutgoingEdges();
                    break;
                case "IN":
                    adjacencyList = directedAdjList.getIncomingEdges();
                    break;
                case "BOTH":
                default:
                    // 合并出边和入边
                    adjacencyList = directedAdjList.getUndirectedEdges();
                    break;
            }
            
            // 获取本体信息用于查询节点详情
            Ontology ontology = ontologyService.getById(ontologyId);
            String namedGraphUri = null;
            OntologyVisualizationDTO vizData = null;
            if (ontology != null) {
                namedGraphUri = graphRepository.buildNamedGraphUri(
                        ontology.getProjectName(), ontology.getVersionNumber());
                vizData = graphRepository.getVisualizationData(namedGraphUri);
            }
            
            // 构建节点ID到节点详情的映射
            Map<String, OntologyVisualizationDTO.NodeDTO> nodeMap = new HashMap<>();
            if (vizData != null && vizData.getNodes() != null) {
                for (OntologyVisualizationDTO.NodeDTO node : vizData.getNodes()) {
                    nodeMap.put(node.getId(), node);
                }
            }
            
            // 扩线结果
            Map<String, Map<String, Object>> allNodes = new HashMap<>();
            List<Map<String, Object>> allEdges = new ArrayList<>();
            Map<Integer, Integer> levelStatistics = new HashMap<>();
            
            // 当前层节点和已访问节点
            Set<String> currentLevelNodes = new HashSet<>();
            Set<String> visitedNodes = new HashSet<>();
            
            // 初始化中心节点
            currentLevelNodes.add(entityId);
            visitedNodes.add(entityId);
            
            // 添加中心节点详情
            Map<String, Object> centerNodeInfo = new HashMap<>();
            OntologyVisualizationDTO.NodeDTO centerNodeDto = nodeMap.get(entityId);
            if (centerNodeDto != null) {
                centerNodeInfo.put("id", centerNodeDto.getId());
                centerNodeInfo.put("label", centerNodeDto.getLabel());
                centerNodeInfo.put("type", centerNodeDto.getType());
                centerNodeInfo.put("color", centerNodeDto.getColor());
            } else {
                centerNodeInfo.put("id", entityId);
                centerNodeInfo.put("label", entityId);
                centerNodeInfo.put("type", "UNKNOWN");
            }
            centerNodeInfo.put("level", 0);
            centerNodeInfo.put("isCenter", true);
            allNodes.put(entityId, centerNodeInfo);
            
            levelStatistics.put(0, 1);
            
            // 逐层扩展（BFS）
            for (int level = 1; level <= expandLevel; level++) {
                Set<String> nextLevelNodes = new HashSet<>();
                
                for (String currentNode : currentLevelNodes) {
                    List<String> neighbors = adjacencyList.getOrDefault(currentNode, new ArrayList<>());
                    
                    for (String neighborId : neighbors) {
                        // 添加边（即使节点已访问也要添加边）
                        String edgeKey = currentNode + "-" + neighborId;
                        boolean edgeExists = allEdges.stream().anyMatch(e -> 
                            (currentNode.equals(e.get("source")) && neighborId.equals(e.get("target"))) ||
                            (neighborId.equals(e.get("source")) && currentNode.equals(e.get("target")))
                        );
                        
                        if (!edgeExists) {
                            Map<String, Object> edge = new HashMap<>();
                            edge.put("source", currentNode);
                            edge.put("target", neighborId);
                            allEdges.add(edge);
                        }
                        
                        // 如果邻居未访问，添加到下一层
                        if (!visitedNodes.contains(neighborId)) {
                            nextLevelNodes.add(neighborId);
                            visitedNodes.add(neighborId);
                            
                            // 添加节点详情
                            Map<String, Object> neighborInfo = new HashMap<>();
                            OntologyVisualizationDTO.NodeDTO neighborDto = nodeMap.get(neighborId);
                            if (neighborDto != null) {
                                neighborInfo.put("id", neighborDto.getId());
                                neighborInfo.put("label", neighborDto.getLabel());
                                neighborInfo.put("type", neighborDto.getType());
                                neighborInfo.put("color", neighborDto.getColor());
                            } else {
                                neighborInfo.put("id", neighborId);
                                neighborInfo.put("label", neighborId);
                                neighborInfo.put("type", "UNKNOWN");
                            }
                            neighborInfo.put("level", level);
                            neighborInfo.put("isCenter", false);
                            allNodes.put(neighborId, neighborInfo);
                        }
                    }
                }
                
                levelStatistics.put(level, nextLevelNodes.size());
                currentLevelNodes = nextLevelNodes;
                
                // 如果没有新节点，提前结束
                if (nextLevelNodes.isEmpty()) {
                    break;
                }
            }
            
            // 构建返回结果
            result.put("centerEntity", entityId);
            result.put("expandLevel", expandLevel);
            result.put("direction", direction);
            result.put("nodes", new ArrayList<>(allNodes.values()));
            result.put("edges", allEdges);
            result.put("levelStatistics", levelStatistics);
            result.put("totalNodes", allNodes.size());
            result.put("totalEdges", allEdges.size());
            
            log.info("实体扩线完成: 节点数={}, 边数={}", allNodes.size(), allEdges.size());
            
        } catch (Exception e) {
            log.error("实体扩线失败", e);
            throw new RuntimeException("实体扩线失败: " + e.getMessage());
        }
        
        return result;
    }

    @Override
    public Map<String, Object> analyzeGraphStructure(Long ontologyId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("开始分析图谱结构，本体ID: {}", ontologyId);
            
            // 获取本体信息
            Ontology ontology = ontologyService.getById(ontologyId);
            if (ontology == null) {
                log.warn("本体不存在: {}", ontologyId);
                result.put("nodeCount", 0);
                result.put("edgeCount", 0);
                result.put("avgDegree", 0);
                result.put("density", 0);
                return result;
            }
            
            // 从Jena图数据库获取数据
            String namedGraphUri = graphRepository.buildNamedGraphUri(
                    ontology.getProjectName(), ontology.getVersionNumber());
            OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);
            
            if (vizData == null || vizData.getNodes() == null || vizData.getEdges() == null) {
                log.warn("可视化数据为空: {}", namedGraphUri);
                result.put("nodeCount", 0);
                result.put("edgeCount", 0);
                result.put("avgDegree", 0);
                result.put("density", 0);
                return result;
            }
            
            // 基础统计
            int nodeCount = vizData.getNodes().size();
            int edgeCount = vizData.getEdges().size();
            
            // 节点类型统计
            int classCount = 0;
            int individualCount = 0;
            Map<String, Integer> nodeTypeDistribution = new HashMap<>();
            for (OntologyVisualizationDTO.NodeDTO node : vizData.getNodes()) {
                String type = node.getType() != null ? node.getType() : "unknown";
                nodeTypeDistribution.merge(type, 1, Integer::sum);
                if ("class".equals(type)) {
                    classCount++;
                } else if ("individual".equals(type)) {
                    individualCount++;
                }
            }
            
            // 关系类型分布
            Map<String, Integer> edgeTypeDistribution = new HashMap<>();
            for (OntologyVisualizationDTO.EdgeDTO edge : vizData.getEdges()) {
                String type = edge.getType() != null ? edge.getType() : "unknown";
                edgeTypeDistribution.merge(type, 1, Integer::sum);
            }
            
            // 构建邻接表和计算度分布
            Map<String, List<String>> adjacencyList = buildAdjacencyList(ontologyId);
            Map<String, Integer> degreeMap = new HashMap<>();
            int maxDegree = 0;
            int minDegree = Integer.MAX_VALUE;
            int isolatedNodes = 0;
            
            for (String nodeId : adjacencyList.keySet()) {
                int degree = adjacencyList.get(nodeId).size();
                degreeMap.put(nodeId, degree);
                maxDegree = Math.max(maxDegree, degree);
                minDegree = Math.min(minDegree, degree);
                if (degree == 0) {
                    isolatedNodes++;
                }
            }
            
            // 计算平均度
            double avgDegree = nodeCount > 0 ? degreeMap.values().stream().mapToInt(Integer::intValue).average().orElse(0) : 0;
            
            // 计算图密度（无向图）
            double density = nodeCount > 1 ? 
                    (double) (edgeCount * 2) / (nodeCount * (nodeCount - 1)) : 0;
            
            // 连通性分析
            int connectedComponents = countConnectedComponents(adjacencyList);
            boolean isConnected = connectedComponents == 1 && nodeCount > 0;
            
            // 计算度分布直方图
            Map<String, Object> degreeDistribution = calculateDegreeDistribution(degreeMap, maxDegree);
            
            // 计算聚类系数
            double avgClusteringCoefficient = calculateAverageClusteringCoefficient(adjacencyList);
            
            // 计算图的直径和平均路径长度（只对最大连通分量）
            Map<String, Object> pathMetrics = calculatePathMetrics(adjacencyList);
            
            // 获取高度数节点排行（Top 10）
            List<Map<String, Object>> topDegreeNodes = getTopDegreeNodes(degreeMap, vizData.getNodes(), 10);
            
            // 构建返回结果
            result.put("nodeCount", nodeCount);
            result.put("edgeCount", edgeCount);
            result.put("classCount", classCount);
            result.put("individualCount", individualCount);
            result.put("avgDegree", Math.round(avgDegree * 100) / 100.0);
            result.put("maxDegree", maxDegree);
            result.put("minDegree", minDegree == Integer.MAX_VALUE ? 0 : minDegree);
            result.put("isolatedNodes", isolatedNodes);
            result.put("density", Math.round(density * 1000) / 1000.0);
            result.put("connectedComponents", connectedComponents);
            result.put("isConnected", isConnected);
            result.put("nodeTypeDistribution", nodeTypeDistribution);
            result.put("edgeTypeDistribution", edgeTypeDistribution);
            result.put("degreeDistribution", degreeDistribution);
            result.put("avgClusteringCoefficient", Math.round(avgClusteringCoefficient * 1000) / 1000.0);
            result.put("diameter", pathMetrics.get("diameter"));
            result.put("avgPathLength", pathMetrics.get("avgPathLength"));
            result.put("topDegreeNodes", topDegreeNodes);
            
            log.info("图谱结构分析完成: 节点={}, 边={}, 类={}, 实例={}, 连通分量={}, 平均聚类系数={}, 直径={}",
                    nodeCount, edgeCount, classCount, individualCount, connectedComponents, 
                    avgClusteringCoefficient, pathMetrics.get("diameter"));
            
        } catch (Exception e) {
            log.error("图谱结构分析失败", e);
            throw new RuntimeException("图谱结构分析失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 计算度分布
     */
    private Map<String, Object> calculateDegreeDistribution(Map<String, Integer> degreeMap, int maxDegree) {
        Map<String, Object> distribution = new HashMap<>();
        
        if (degreeMap.isEmpty()) {
            distribution.put("bins", new ArrayList<>());
            distribution.put("counts", new ArrayList<>());
            return distribution;
        }
        
        // 创建度分布直方图（分为10个区间或按实际度数）
        int binCount = Math.min(10, maxDegree + 1);
        List<String> bins = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        
        if (maxDegree <= 10) {
            // 度数较少时，每个度数一个区间
            for (int i = 0; i <= maxDegree; i++) {
                bins.add(String.valueOf(i));
                final int degree = i;
                int count = (int) degreeMap.values().stream().filter(d -> d == degree).count();
                counts.add(count);
            }
        } else {
            // 度数较多时，分为10个区间
            int binSize = (maxDegree + 9) / 10;
            for (int i = 0; i < 10; i++) {
                int start = i * binSize;
                int end = Math.min((i + 1) * binSize - 1, maxDegree);
                bins.add(start + "-" + end);
                final int s = start;
                final int e = end;
                int count = (int) degreeMap.values().stream().filter(d -> d >= s && d <= e).count();
                counts.add(count);
            }
        }
        
        distribution.put("bins", bins);
        distribution.put("counts", counts);
        return distribution;
    }
    
    /**
     * 计算平均聚类系数
     */
    private double calculateAverageClusteringCoefficient(Map<String, List<String>> adjacencyList) {
        if (adjacencyList.isEmpty()) {
            return 0;
        }
        
        double totalCoefficient = 0;
        int validNodes = 0;
        
        for (String node : adjacencyList.keySet()) {
            List<String> neighbors = adjacencyList.get(node);
            int degree = neighbors.size();
            
            if (degree < 2) {
                continue; // 度数小于2的节点无法形成三角形
            }
            
            // 计算邻居之间的边数
            int edgeCount = 0;
            for (int i = 0; i < neighbors.size(); i++) {
                for (int j = i + 1; j < neighbors.size(); j++) {
                    String n1 = neighbors.get(i);
                    String n2 = neighbors.get(j);
                    if (adjacencyList.getOrDefault(n1, new ArrayList<>()).contains(n2)) {
                        edgeCount++;
                    }
                }
            }
            
            // 聚类系数 = 实际边数 / 可能的最大边数
            int possibleEdges = degree * (degree - 1) / 2;
            double coefficient = (double) edgeCount / possibleEdges;
            totalCoefficient += coefficient;
            validNodes++;
        }
        
        return validNodes > 0 ? totalCoefficient / validNodes : 0;
    }
    
    /**
     * 计算路径指标（直径和平均路径长度）
     */
    private Map<String, Object> calculatePathMetrics(Map<String, List<String>> adjacencyList) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("diameter", 0);
        metrics.put("avgPathLength", 0);
        
        if (adjacencyList.isEmpty()) {
            return metrics;
        }
        
        // 找到最大连通分量
        List<String> largestComponent = findLargestComponent(adjacencyList);
        if (largestComponent.size() < 2) {
            return metrics;
        }
        
        int diameter = 0;
        long totalDistance = 0;
        int pathCount = 0;
        
        // 对最大连通分量中的每个节点进行BFS
        for (String startNode : largestComponent) {
            Map<String, Integer> distances = bfsDistances(startNode, adjacencyList);
            
            for (String targetNode : largestComponent) {
                if (!startNode.equals(targetNode) && distances.containsKey(targetNode)) {
                    int dist = distances.get(targetNode);
                    diameter = Math.max(diameter, dist);
                    totalDistance += dist;
                    pathCount++;
                }
            }
        }
        
        metrics.put("diameter", diameter);
        metrics.put("avgPathLength", pathCount > 0 ? Math.round((double) totalDistance / pathCount * 100) / 100.0 : 0);
        
        return metrics;
    }
    
    /**
     * BFS计算从起点到所有节点的最短距离
     */
    private Map<String, Integer> bfsDistances(String startNode, Map<String, List<String>> adjacencyList) {
        Map<String, Integer> distances = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(startNode);
        visited.add(startNode);
        distances.put(startNode, 0);
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distances.get(current);
            
            for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distances.put(neighbor, currentDist + 1);
                    queue.offer(neighbor);
                }
            }
        }
        
        return distances;
    }
    
    /**
     * 找到最大连通分量
     */
    private List<String> findLargestComponent(Map<String, List<String>> adjacencyList) {
        Set<String> visited = new HashSet<>();
        List<String> largestComponent = new ArrayList<>();
        
        for (String node : adjacencyList.keySet()) {
            if (!visited.contains(node)) {
                List<String> component = new ArrayList<>();
                Queue<String> queue = new LinkedList<>();
                queue.offer(node);
                visited.add(node);
                
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    component.add(current);
                    
                    for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.offer(neighbor);
                        }
                    }
                }
                
                if (component.size() > largestComponent.size()) {
                    largestComponent = component;
                }
            }
        }
        
        return largestComponent;
    }
    
    /**
     * 获取高度数节点排行
     */
    private List<Map<String, Object>> getTopDegreeNodes(Map<String, Integer> degreeMap, 
                                                        List<OntologyVisualizationDTO.NodeDTO> nodes, 
                                                        int limit) {
        // 创建节点ID到名称的映射
        Map<String, String> nodeNameMap = nodes.stream()
                .collect(Collectors.toMap(
                        OntologyVisualizationDTO.NodeDTO::getId,
                        n -> n.getLabel() != null ? n.getLabel() : n.getId(),
                        (v1, v2) -> v1));
        
        return degreeMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", entry.getKey());
                    node.put("name", nodeNameMap.getOrDefault(entry.getKey(), entry.getKey()));
                    node.put("degree", entry.getValue());
                    return node;
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算连通分量数量
     */
    private int countConnectedComponents(Map<String, List<String>> adjacencyList) {
        if (adjacencyList.isEmpty()) return 0;
        
        Set<String> visited = new HashSet<>();
        int components = 0;
        
        for (String node : adjacencyList.keySet()) {
            if (!visited.contains(node)) {
                components++;
                // BFS遍历该连通分量
                Queue<String> queue = new LinkedList<>();
                queue.offer(node);
                visited.add(node);
                
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.offer(neighbor);
                        }
                    }
                }
            }
        }
        
        return components;
    }

    /**
     * 构建有向邻接表
     */
    private DirectedAdjacencyList buildDirectedAdjacencyList(Long ontologyId) {
        DirectedAdjacencyList result = new DirectedAdjacencyList();
        
        try {
            // 获取本体信息
            Ontology ontology = ontologyService.getById(ontologyId);
            if (ontology == null) {
                log.warn("本体不存在: {}", ontologyId);
                return result;
            }
            
            // 构建命名图URI
            String namedGraphUri = graphRepository.buildNamedGraphUri(
                    ontology.getProjectName(), ontology.getVersionNumber());
            
            // 获取可视化数据
            OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);
            
            if (vizData == null || vizData.getNodes() == null || vizData.getEdges() == null) {
                log.warn("可视化数据为空: {}", namedGraphUri);
                return result;
            }
            
            // 初始化节点
            for (OntologyVisualizationDTO.NodeDTO node : vizData.getNodes()) {
                result.addNode(node.getId());
            }
            
            // 添加边（有向）
            for (OntologyVisualizationDTO.EdgeDTO edge : vizData.getEdges()) {
                String source = edge.getSource();
                String target = edge.getTarget();
                
                // 添加出边：source -> target
                result.addOutgoingEdge(source, target);
                // 添加入边：target <- source
                result.addIncomingEdge(target, source);
            }
            
            log.info("构建有向邻接表完成，节点数: {}, 出边数: {}, 入边数: {}",
                    result.getNodeCount(), result.getOutgoingEdgeCount(), result.getIncomingEdgeCount());
            
        } catch (Exception e) {
            log.error("构建有向邻接表失败，本体ID: {}", ontologyId, e);
        }
        
        return result;
    }

    /**
     * 有向邻接表内部类
     */
    private static class DirectedAdjacencyList {
        private Map<String, List<String>> outgoingEdges; // 出边：节点 -> 它指向的节点
        private Map<String, List<String>> incomingEdges; // 入边：节点 -> 指向它的节点
        
        public DirectedAdjacencyList() {
            this.outgoingEdges = new HashMap<>();
            this.incomingEdges = new HashMap<>();
        }
        
        public void addNode(String nodeId) {
            outgoingEdges.putIfAbsent(nodeId, new ArrayList<>());
            incomingEdges.putIfAbsent(nodeId, new ArrayList<>());
        }
        
        public void addOutgoingEdge(String source, String target) {
            outgoingEdges.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
        }
        
        public void addIncomingEdge(String target, String source) {
            incomingEdges.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
        }
        
        public Map<String, List<String>> getOutgoingEdges() {
            return outgoingEdges;
        }
        
        public Map<String, List<String>> getIncomingEdges() {
            return incomingEdges;
        }
        
        /**
         * 获取无向邻接表（合并出边和入边）
         */
        public Map<String, List<String>> getUndirectedEdges() {
            Map<String, List<String>> undirected = new HashMap<>();
            
            // 合并所有节点
            Set<String> allNodes = new HashSet<>();
            allNodes.addAll(outgoingEdges.keySet());
            allNodes.addAll(incomingEdges.keySet());
            
            for (String node : allNodes) {
                Set<String> neighbors = new HashSet<>();
                neighbors.addAll(outgoingEdges.getOrDefault(node, new ArrayList<>()));
                neighbors.addAll(incomingEdges.getOrDefault(node, new ArrayList<>()));
                undirected.put(node, new ArrayList<>(neighbors));
            }
            
            return undirected;
        }
        
        public boolean containsNode(String nodeId) {
            return outgoingEdges.containsKey(nodeId) || incomingEdges.containsKey(nodeId);
        }
        
        public boolean isEmpty() {
            return outgoingEdges.isEmpty() && incomingEdges.isEmpty();
        }
        
        public int getNodeCount() {
            Set<String> allNodes = new HashSet<>();
            allNodes.addAll(outgoingEdges.keySet());
            allNodes.addAll(incomingEdges.keySet());
            return allNodes.size();
        }
        
        public int getOutgoingEdgeCount() {
            return outgoingEdges.values().stream().mapToInt(List::size).sum();
        }
        
        public int getIncomingEdgeCount() {
            return incomingEdges.values().stream().mapToInt(List::size).sum();
        }
    }

    // ==================== 社区发现算法实现 ====================

    private CommunityDetectionResult executeCommunityDetection(Map<String, List<String>> adjacencyList,
                                                                 String algorithm, Double resolution,
                                                                 Integer maxIterations, Integer minCommunitySize) {
        switch (algorithm.toUpperCase()) {
            case "LOUVAIN":
                return executeLouvainAlgorithm(adjacencyList, resolution, maxIterations, minCommunitySize);
            case "LABEL_PROPAGATION":
                return executeLabelPropagationAlgorithm(adjacencyList, maxIterations, minCommunitySize);
            case "MODULARITY_OPTIMIZATION":
                return executeModularityOptimizationAlgorithm(adjacencyList, resolution, maxIterations, minCommunitySize);
            default:
                throw new IllegalArgumentException("不支持的社区发现算法: " + algorithm);
        }
    }

    private CommunityDetectionResult executeLouvainAlgorithm(Map<String, List<String>> adjacencyList,
                                                             Double resolution, Integer maxIterations, Integer minCommunitySize) {
        List<String> nodes = new ArrayList<>(adjacencyList.keySet());
        Map<String, Integer> nodeToCommunity = new HashMap<>();

        // 初始化：每个节点为一个社区
        for (int i = 0; i < nodes.size(); i++) {
            nodeToCommunity.put(nodes.get(i), i);
        }

        boolean improved = true;
        int iteration = 0;
        double bestModularity = calculateModularity(adjacencyList, nodeToCommunity, resolution);

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (String node : nodes) {
                int currentCommunity = nodeToCommunity.get(node);
                int bestCommunity = currentCommunity;
                double bestGain = 0.0;

                // 尝试将节点移动到邻居节点的社区
                Set<Integer> neighborCommunities = new HashSet<>();
                for (String neighbor : adjacencyList.getOrDefault(node, new ArrayList<>())) {
                    neighborCommunities.add(nodeToCommunity.get(neighbor));
                }

                for (Integer targetCommunity : neighborCommunities) {
                    if (!targetCommunity.equals(currentCommunity)) {
                        double gain = calculateModularityGain(adjacencyList, nodeToCommunity,
                                node, currentCommunity, targetCommunity, resolution);
                        if (gain > bestGain) {
                            bestGain = gain;
                            bestCommunity = targetCommunity;
                        }
                    }
                }

                // 移动节点到最佳社区
                if (bestCommunity != currentCommunity) {
                    nodeToCommunity.put(node, bestCommunity);
                    improved = true;
                }
            }

            double currentModularity = calculateModularity(adjacencyList, nodeToCommunity, resolution);
            if (currentModularity > bestModularity) {
                bestModularity = currentModularity;
            }
        }

        // 构建社区结果
        List<Set<String>> communities = buildCommunitiesFromAssignment(nodeToCommunity, minCommunitySize);

        return CommunityDetectionResult.builder()
                .communities(communities)
                .modularity(bestModularity)
                .iterations(iteration)
                .algorithm("LOUVAIN")
                .build();
    }

    private CommunityDetectionResult executeLabelPropagationAlgorithm(Map<String, List<String>> adjacencyList,
                                                                      Integer maxIterations, Integer minCommunitySize) {
        List<String> nodes = new ArrayList<>(adjacencyList.keySet());
        Map<String, String> nodeLabels = new HashMap<>();

        // 初始化：每个节点的标签为自己
        for (String node : nodes) {
            nodeLabels.put(node, node);
        }

        Random random = new Random();
        boolean changed = true;
        int iteration = 0;

        while (changed && iteration < maxIterations) {
            changed = false;
            iteration++;

            // 随机打乱节点顺序
            Collections.shuffle(nodes, random);

            for (String node : nodes) {
                // 统计邻居标签频率
                Map<String, Integer> labelCount = new HashMap<>();
                for (String neighbor : adjacencyList.getOrDefault(node, new ArrayList<>())) {
                    String label = nodeLabels.get(neighbor);
                    labelCount.put(label, labelCount.getOrDefault(label, 0) + 1);
                }

                if (!labelCount.isEmpty()) {
                    // 选择频率最高的标签
                    String mostFrequentLabel = labelCount.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(nodeLabels.get(node));

                    if (!mostFrequentLabel.equals(nodeLabels.get(node))) {
                        nodeLabels.put(node, mostFrequentLabel);
                        changed = true;
                    }
                }
            }
        }

        // 构建社区
        Map<String, Set<String>> labelToCommunity = new HashMap<>();
        for (Map.Entry<String, String> entry : nodeLabels.entrySet()) {
            labelToCommunity.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
        }

        List<Set<String>> communities = labelToCommunity.values().stream()
                .filter(community -> community.size() >= minCommunitySize)
                .collect(Collectors.toList());

        // 将标签映射转换为社区映射以计算模块度
        Map<String, Integer> nodeToCommunity = new HashMap<>();
        for (int i = 0; i < communities.size(); i++) {
            for (String node : communities.get(i)) {
                nodeToCommunity.put(node, i);
            }
        }

        double modularity = calculateModularity(adjacencyList, nodeToCommunity, 1.0);

        return CommunityDetectionResult.builder()
                .communities(communities)
                .modularity(modularity)
                .iterations(iteration)
                .algorithm("LABEL_PROPAGATION")
                .build();
    }

    private CommunityDetectionResult executeModularityOptimizationAlgorithm(Map<String, List<String>> adjacencyList,
                                                                            Double resolution, Integer maxIterations, Integer minCommunitySize) {
        // 模块度优化算法与Louvain类似，但使用不同的优化策略
        // 这里简化为使用Louvain算法的变体
        return executeLouvainAlgorithm(adjacencyList, resolution, maxIterations, minCommunitySize);
    }

    // ==================== 中心度计算实现 ====================

    private Map<String, Double> calculateCentrality(Map<String, List<String>> adjacencyList,
                                                    List<String> nodes, String centralityType) {
        switch (centralityType) {
            case "DEGREE":
                return calculateDegreeCentrality(adjacencyList);
            case "BETWEENNESS":
                return calculateBetweennessCentrality(adjacencyList, nodes);
            case "CLOSENESS":
                return calculateClosenessCentrality(adjacencyList, nodes);
            case "EIGENVECTOR":
                return calculateEigenvectorCentrality(adjacencyList, nodes);
            case "PAGERANK":
                return calculatePageRankCentrality(adjacencyList, nodes);
            default:
                throw new IllegalArgumentException("不支持的中心度类型: " + centralityType);
        }
    }

    private Map<String, Double> calculateDegreeCentrality(Map<String, List<String>> adjacencyList) {
        Map<String, Double> centrality = new HashMap<>();
        int totalNodes = adjacencyList.size();

        for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
            String node = entry.getKey();
            int degree = entry.getValue().size();
            // 标准化：度数 / (总节点数 - 1)
            double normalizedCentrality = totalNodes > 1 ? (double) degree / (totalNodes - 1) : 0.0;
            centrality.put(node, normalizedCentrality);
        }

        return centrality;
    }

    private Map<String, Double> calculateBetweennessCentrality(Map<String, List<String>> adjacencyList, List<String> nodes) {
        Map<String, Double> centrality = new HashMap<>();

        // 初始化所有节点的介数中心性为0
        for (String node : nodes) {
            centrality.put(node, 0.0);
        }

        // 对每对节点计算最短路径
        for (String source : nodes) {
            for (String target : nodes) {
                if (!source.equals(target)) {
                    List<List<String>> allShortestPaths = findAllShortestPaths(adjacencyList, source, target);

                    if (!allShortestPaths.isEmpty()) {
                        // 计算每个中间节点在最短路径中的贡献
                        for (List<String> path : allShortestPaths) {
                            for (int i = 1; i < path.size() - 1; i++) {
                                String intermediateNode = path.get(i);
                                double contribution = 1.0 / allShortestPaths.size();
                                centrality.put(intermediateNode,
                                        centrality.get(intermediateNode) + contribution);
                            }
                        }
                    }
                }
            }
        }

        // 标准化
        int n = nodes.size();
        double normalizationFactor = n > 2 ? (n - 1) * (n - 2) / 2.0 : 1.0;

        for (String node : nodes) {
            centrality.put(node, centrality.get(node) / normalizationFactor);
        }

        return centrality;
    }

    private Map<String, Double> calculateClosenessCentrality(Map<String, List<String>> adjacencyList, List<String> nodes) {
        Map<String, Double> centrality = new HashMap<>();

        for (String node : nodes) {
            double totalDistance = 0.0;
            int reachableNodes = 0;

            // 计算到所有其他节点的最短距离
            for (String target : nodes) {
                if (!node.equals(target)) {
                    int distance = findShortestPathLength(adjacencyList, node, target);
                    if (distance > 0) {
                        totalDistance += distance;
                        reachableNodes++;
                    }
                }
            }

            // 计算接近中心性
            if (reachableNodes > 0) {
                double averageDistance = totalDistance / reachableNodes;
                double closeness = 1.0 / averageDistance;
                // 标准化
                double normalizedCloseness = closeness * reachableNodes / (nodes.size() - 1);
                centrality.put(node, normalizedCloseness);
            } else {
                centrality.put(node, 0.0);
            }
        }

        return centrality;
    }

    private Map<String, Double> calculateEigenvectorCentrality(Map<String, List<String>> adjacencyList, List<String> nodes) {
        Map<String, Double> centrality = new HashMap<>();
        Map<String, Double> newCentrality = new HashMap<>();

        // 初始化所有节点的中心性为1
        for (String node : nodes) {
            centrality.put(node, 1.0);
        }

        // 迭代计算特征向量中心性
        int maxIter = 100;
        double tolerance = 1e-6;

        for (int iter = 0; iter < maxIter; iter++) {
            double maxChange = 0.0;

            // 计算新的中心性值
            for (String node : nodes) {
                double sum = 0.0;
                List<String> neighbors = adjacencyList.getOrDefault(node, new ArrayList<>());

                for (String neighbor : neighbors) {
                    sum += centrality.getOrDefault(neighbor, 0.0);
                }

                newCentrality.put(node, sum);
            }

            // 标准化
            double norm = Math.sqrt(newCentrality.values().stream()
                    .mapToDouble(v -> v * v).sum());

            if (norm > 0) {
                for (String node : nodes) {
                    double oldValue = centrality.get(node);
                    double newValue = newCentrality.get(node) / norm;
                    newCentrality.put(node, newValue);
                    maxChange = Math.max(maxChange, Math.abs(newValue - oldValue));
                }
            }

            // 更新中心性值
            centrality.putAll(newCentrality);

            // 检查收敛
            if (maxChange < tolerance) {
                break;
            }
        }

        return centrality;
    }

    /**
     * PageRank中心性：基于随机游走的节点重要性度量
     * 简化版实现：PR(node) = (1-d)/N + d * sum(PR(neighbor) / outDegree(neighbor))
     */
    private Map<String, Double> calculatePageRankCentrality(Map<String, List<String>> adjacencyList, List<String> nodes) {
        Map<String, Double> pageRank = new HashMap<>();
        int n = nodes.size();
        
        if (n == 0) return pageRank;
        
        // 阻尼系数（通常取0.85）
        double dampingFactor = 0.85;
        // 基础值
        double baseValue = (1.0 - dampingFactor) / n;
        
        // 初始化：所有节点的PR值为 1/N
        for (String node : nodes) {
            pageRank.put(node, 1.0 / n);
        }
        
        // 计算每个节点的出度（用于分配权重）
        Map<String, Integer> outDegree = new HashMap<>();
        for (String node : nodes) {
            outDegree.put(node, adjacencyList.getOrDefault(node, new ArrayList<>()).size());
        }
        
        // 迭代计算
        int maxIterations = 100;
        double tolerance = 1e-6;
        
        for (int iter = 0; iter < maxIterations; iter++) {
            Map<String, Double> newPageRank = new HashMap<>();
            double maxChange = 0.0;
            
            for (String node : nodes) {
                double sum = 0.0;
                
                // 收集所有指向当前节点的邻居
                for (String neighbor : nodes) {
                    List<String> neighborAdj = adjacencyList.getOrDefault(neighbor, new ArrayList<>());
                    if (neighborAdj.contains(node)) {
                        // 邻居的PR值按出度均分
                        int neighborOutDegree = outDegree.getOrDefault(neighbor, 1);
                        if (neighborOutDegree > 0) {
                            sum += pageRank.get(neighbor) / neighborOutDegree;
                        }
                    }
                }
                
                // PageRank公式
                double newValue = baseValue + dampingFactor * sum;
                newPageRank.put(node, newValue);
                
                // 计算变化量
                double oldValue = pageRank.get(node);
                maxChange = Math.max(maxChange, Math.abs(newValue - oldValue));
            }
            
            // 更新PageRank值
            pageRank.putAll(newPageRank);
            
            // 检查收敛
            if (maxChange < tolerance) {
                break;
            }
        }
        
        // 标准化到 0-1 范围
        double maxPR = pageRank.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (maxPR > 0) {
            for (String node : nodes) {
                pageRank.put(node, pageRank.get(node) / maxPR);
            }
        }
        
        return pageRank;
    }

    // ==================== 辅助方法 ====================

    private Map<String, List<String>> buildAdjacencyList(Long ontologyId) {
        Map<String, List<String>> adjacencyList = new HashMap<>();

        try {
            // 获取本体信息
            Ontology ontology = ontologyService.getById(ontologyId);
            if (ontology == null) {
                log.warn("本体不存在: {}", ontologyId);
                return adjacencyList;
            }

            // 构建命名图URI
            String namedGraphUri = graphRepository.buildNamedGraphUri(
                    ontology.getProjectName(), ontology.getVersionNumber());

            // 获取可视化数据
            OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);

            if (vizData == null || vizData.getNodes() == null || vizData.getEdges() == null) {
                log.warn("可视化数据为空: {}", namedGraphUri);
                return adjacencyList;
            }

            // 初始化邻接表
            for (OntologyVisualizationDTO.NodeDTO node : vizData.getNodes()) {
                adjacencyList.put(node.getId(), new ArrayList<>());
            }

            // 添加边关系（无向图）
            for (OntologyVisualizationDTO.EdgeDTO edge : vizData.getEdges()) {
                String source = edge.getSource();
                String target = edge.getTarget();

                if (adjacencyList.containsKey(source) && adjacencyList.containsKey(target)) {
                    adjacencyList.get(source).add(target);
                    adjacencyList.get(target).add(source);
                }
            }

            log.info("构建邻接表完成，本体ID: {}, 节点数: {}, 边数: {}",
                    ontologyId, vizData.getNodes().size(), vizData.getEdges().size());

        } catch (Exception e) {
            log.error("构建邻接表失败，本体ID: {}", ontologyId, e);
        }

        return adjacencyList;
    }

    private double calculateModularity(Map<String, List<String>> adjacencyList,
                                       Map<String, Integer> nodeToCommunity, Double resolution) {
        double modularity = 0.0;
        double totalEdges = 0;

        // 计算总边数
        for (List<String> neighbors : adjacencyList.values()) {
            totalEdges += neighbors.size();
        }
        totalEdges /= 2; // 无向图，每条边计算了两次

        if (totalEdges == 0) return 0.0;

        // 计算模块度
        for (String node : adjacencyList.keySet()) {
            int community = nodeToCommunity.get(node);
            List<String> neighbors = adjacencyList.get(node);

            for (String neighbor : neighbors) {
                int neighborCommunity = nodeToCommunity.get(neighbor);
                if (community == neighborCommunity) {
                    modularity += 1.0;
                }
            }
        }

        modularity /= (2 * totalEdges);

        // 计算期望值
        double expected = 0.0;
        Map<Integer, Double> communityDegrees = new HashMap<>();

        for (String node : adjacencyList.keySet()) {
            int community = nodeToCommunity.get(node);
            double degree = adjacencyList.get(node).size();
            communityDegrees.put(community, communityDegrees.getOrDefault(community, 0.0) + degree);
        }

        for (double degreeSum : communityDegrees.values()) {
            expected += Math.pow(degreeSum / (2 * totalEdges), 2);
        }

        modularity -= expected;

        return modularity / resolution;
    }

    private double calculateModularityGain(Map<String, List<String>> adjacencyList,
                                           Map<String, Integer> nodeToCommunity,
                                           String node, int currentCommunity, int targetCommunity,
                                           Double resolution) {
        // 简化计算：计算移动到目标社区的模块度增益
        nodeToCommunity.put(node, targetCommunity);
        double newModularity = calculateModularity(adjacencyList, nodeToCommunity, resolution);
        nodeToCommunity.put(node, currentCommunity);
        double oldModularity = calculateModularity(adjacencyList, nodeToCommunity, resolution);

        return newModularity - oldModularity;
    }

    private List<Set<String>> buildCommunitiesFromAssignment(Map<String, Integer> nodeToCommunity, int minCommunitySize) {
        Map<Integer, Set<String>> communityMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : nodeToCommunity.entrySet()) {
            communityMap.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
        }

        return communityMap.values().stream()
                .filter(community -> community.size() >= minCommunitySize)
                .collect(Collectors.toList());
    }

    private List<List<String>> findAllShortestPaths(Map<String, List<String>> adjacencyList,
                                                    String source, String target) {
        List<List<String>> allPaths = new ArrayList<>();
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        int shortestLength = Integer.MAX_VALUE;

        queue.offer(Arrays.asList(source));

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);

            if (path.size() > shortestLength) continue;

            if (current.equals(target)) {
                if (path.size() < shortestLength) {
                    shortestLength = path.size();
                    allPaths.clear();
                }
                allPaths.add(new ArrayList<>(path));
                continue;
            }

            for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                if (!path.contains(neighbor)) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.offer(newPath);
                }
            }
        }

        return allPaths;
    }

    private int findShortestPathLength(Map<String, List<String>> adjacencyList, String source, String target) {
        if (source.equals(target)) return 0;

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        Map<String, Integer> distance = new HashMap<>();

        queue.offer(source);
        visited.add(source);
        distance.put(source, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDistance = distance.get(current);

            for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                if (neighbor.equals(target)) {
                    return currentDistance + 1;
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distance.put(neighbor, currentDistance + 1);
                    queue.offer(neighbor);
                }
            }
        }

        return -1; // 不可达
    }

    private void addCommunityNodeDetails(CommunityDetectionResult result, Long ontologyId) {
        try {
            Ontology ontology = ontologyService.getById(ontologyId);
            if (ontology == null) return;

            String namedGraphUri = graphRepository.buildNamedGraphUri(
                    ontology.getProjectName(), ontology.getVersionNumber());
            OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);

            if (vizData == null || vizData.getNodes() == null) return;

            // 构建节点ID到节点的映射
            Map<String, OntologyVisualizationDTO.NodeDTO> nodeMap = new HashMap<>();
            for (OntologyVisualizationDTO.NodeDTO node : vizData.getNodes()) {
                nodeMap.put(node.getId(), node);
            }

            // 添加社区详细信息
            List<Map<String, Object>> communityDetails = new ArrayList<>();
            int communityIndex = 1;

            for (Set<String> community : result.getCommunities()) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("communityId", communityIndex++);
                detail.put("nodeCount", community.size());

                List<Map<String, Object>> nodes = new ArrayList<>();
                for (String nodeId : community) {
                    OntologyVisualizationDTO.NodeDTO node = nodeMap.get(nodeId);
                    if (node != null) {
                        Map<String, Object> nodeInfo = new HashMap<>();
                        nodeInfo.put("id", node.getId());
                        nodeInfo.put("label", node.getLabel());
                        nodeInfo.put("type", node.getType());
                        nodeInfo.put("color", node.getColor());
                        nodes.add(nodeInfo);
                    }
                }

                detail.put("nodes", nodes);
                detail.put("sampleNodes", nodes.stream().limit(5).collect(Collectors.toList()));
                communityDetails.add(detail);
            }

            result.setCommunityDetails(communityDetails);

            // 添加统计信息
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalCommunities", result.getCommunities().size());
            statistics.put("totalNodes", nodeMap.size());
            statistics.put("averageCommunitySize", result.getCommunities().isEmpty() ? 0 :
                    result.getCommunities().stream().mapToInt(Set::size).average().orElse(0));
            statistics.put("largestCommunitySize", result.getCommunities().isEmpty() ? 0 :
                    result.getCommunities().stream().mapToInt(Set::size).max().orElse(0));
            statistics.put("smallestCommunitySize", result.getCommunities().isEmpty() ? 0 :
                    result.getCommunities().stream().mapToInt(Set::size).min().orElse(0));

            result.setStatistics(statistics);

        } catch (Exception e) {
            log.error("添加社区节点详情失败", e);
        }
    }

    private List<Map<String, Object>> processCentralityResults(Map<String, Double> centralityScores,
                                                               Double minThreshold, Integer limit,
                                                               Long ontologyId) {
        // 获取节点详情
        Map<String, OntologyVisualizationDTO.NodeDTO> nodeMap = new HashMap<>();
        try {
            Ontology ontology = ontologyService.getById(ontologyId);
            if (ontology != null) {
                String namedGraphUri = graphRepository.buildNamedGraphUri(
                        ontology.getProjectName(), ontology.getVersionNumber());
                OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);
                if (vizData != null && vizData.getNodes() != null) {
                    for (OntologyVisualizationDTO.NodeDTO node : vizData.getNodes()) {
                        nodeMap.put(node.getId(), node);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取节点详情失败", e);
        }

        // 处理结果
        Map<String, OntologyVisualizationDTO.NodeDTO> finalNodeMap = nodeMap;
        return centralityScores.entrySet().stream()
                .filter(entry -> entry.getValue() >= minThreshold)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> nodeResult = new HashMap<>();
                    String nodeId = entry.getKey();
                    Double centralityValue = entry.getValue();

                    nodeResult.put("nodeId", nodeId);
                    nodeResult.put("centrality", Math.round(centralityValue * 10000.0) / 10000.0);

                    // 添加节点详细信息
                    OntologyVisualizationDTO.NodeDTO node = finalNodeMap.get(nodeId);
                    if (node != null) {
                        nodeResult.put("label", node.getLabel());
                        nodeResult.put("type", node.getType());
                        nodeResult.put("color", node.getColor());
                    } else {
                        nodeResult.put("label", nodeId);
                        nodeResult.put("type", "UNKNOWN");
                    }

                    return nodeResult;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> calculateStatistics(Map<String, Double> centralityScores) {
        Map<String, Object> stats = new HashMap<>();

        if (centralityScores.isEmpty()) {
            return stats;
        }

        List<Double> values = new ArrayList<>(centralityScores.values());
        values.sort(Double::compareTo);

        double max = values.get(values.size() - 1);
        double min = values.get(0);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = values.size() % 2 == 0 ?
                (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2.0 :
                values.get(values.size() / 2);

        stats.put("max", Math.round(max * 10000.0) / 10000.0);
        stats.put("min", Math.round(min * 10000.0) / 10000.0);
        stats.put("average", Math.round(avg * 10000.0) / 10000.0);
        stats.put("median", Math.round(median * 10000.0) / 10000.0);

        return stats;
    }

    private Map<String, Object> getCentralityAlgorithmInfo(String centralityType) {
        Map<String, Object> info = new HashMap<>();

        switch (centralityType) {
            case "DEGREE":
                info.put("name", "度中心性");
                info.put("description", "基于节点连接数量的中心性度量");
                info.put("interpretation", "值越高表示节点连接越多，在网络中越活跃");
                info.put("range", "0.0 - 1.0（标准化后）");
                break;
            case "BETWEENNESS":
                info.put("name", "介数中心性");
                info.put("description", "基于节点作为其他节点间最短路径桥梁频率的中心性度量");
                info.put("interpretation", "值越高表示节点越重要，是连接不同群体的关键桥梁");
                info.put("range", "0.0 - 1.0（标准化后）");
                break;
            case "CLOSENESS":
                info.put("name", "接近中心性");
                info.put("description", "基于节点到其他所有节点平均距离倒数的中心性度量");
                info.put("interpretation", "值越高表示节点越接近网络中心，信息传播越快");
                info.put("range", "0.0 - 1.0（标准化后）");
                break;
            case "EIGENVECTOR":
                info.put("name", "特征向量中心性");
                info.put("description", "基于节点邻居重要性的递归中心性度量");
                info.put("interpretation", "值越高表示节点连接到的重要节点越多");
                info.put("range", "0.0 - 1.0（标准化后）");
                break;
        }

        return info;
    }

    private CommunityDetectionResult createEmptyCommunityResult(String algorithm, Double resolution,
                                                                 Integer maxIterations, Integer minCommunitySize) {
        return CommunityDetectionResult.builder()
                .communities(new ArrayList<>())
                .modularity(0.0)
                .iterations(0)
                .algorithm(algorithm)
                .communityDetails(new ArrayList<>())
                .statistics(new HashMap<>())
                .parameters(Map.of(
                        "algorithm", algorithm,
                        "resolution", resolution,
                        "maxIterations", maxIterations,
                        "minCommunitySize", minCommunitySize
                ))
                .build();
    }

    private CentralityResult createEmptyCentralityResult(String centralityType, Integer limit, Double minThreshold) {
        return CentralityResult.builder()
                .centralityType(centralityType)
                .algorithm(getCentralityAlgorithmInfo(centralityType))
                .results(new ArrayList<>())
                .totalNodes(0)
                .qualifiedNodes(0)
                .returnedCount(0)
                .limit(limit)
                .minThreshold(minThreshold)
                .statistics(new HashMap<>())
                .build();
    }

    // ==================== 模式匹配实现 ====================

    @Override
    public Map<String, Object> patternMatching(Long ontologyId, PatternMatchingDTO patternRequest) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("开始模式匹配，本体ID: {}, 节点数: {}, 边数: {}",
                    ontologyId,
                    patternRequest.getNodes() != null ? patternRequest.getNodes().size() : 0,
                    patternRequest.getEdges() != null ? patternRequest.getEdges().size() : 0);
            
            // 参数校验
            if (patternRequest.getNodes() == null || patternRequest.getNodes().isEmpty()) {
                result.put("matchCount", 0);
                result.put("matches", new ArrayList<>());
                result.put("nodes", new ArrayList<>());
                result.put("edges", new ArrayList<>());
                return result;
            }
            
            // 获取本体信息
            Ontology ontology = ontologyService.getById(ontologyId);
            if (ontology == null) {
                log.warn("本体不存在: {}", ontologyId);
                result.put("matchCount", 0);
                result.put("matches", new ArrayList<>());
                return result;
            }
            
            // 获取图谱数据
            String namedGraphUri = graphRepository.buildNamedGraphUri(
                    ontology.getProjectName(), ontology.getVersionNumber());
            OntologyVisualizationDTO vizData = graphRepository.getVisualizationData(namedGraphUri);
            
            if (vizData == null || vizData.getNodes() == null || vizData.getNodes().isEmpty()) {
                log.warn("可视化数据为空: {}", namedGraphUri);
                result.put("matchCount", 0);
                result.put("matches", new ArrayList<>());
                return result;
            }
            
            // 1. 为每个变量节点找到候选实体
            Map<String, List<EntityCandidate>> variableCandidates = findVariableCandidates(
                    patternRequest, vizData);
            
            // 2. 生成所有可能的节点绑定组合
            List<Map<String, EntityCandidate>> allBindings = generateNodeBindings(variableCandidates);
            log.info("生成了 {} 个节点绑定组合", allBindings.size());
            
            // 3. 验证每个绑定组合的边约束
            List<ValidatedMatch> validMatches = validateBindings(
                    allBindings, patternRequest, vizData);
            log.info("找到 {} 个有效匹配", validMatches.size());
            
            // 4. 构建返回结果
            result = buildPatternResult(validMatches, patternRequest, vizData);
            
            log.info("模式匹配完成，返回 {} 个匹配，{} 个节点，{} 条边",
                    validMatches.size(),
                    ((List<?>) result.get("nodes")).size(),
                    ((List<?>) result.get("edges")).size());
            
        } catch (Exception e) {
            log.error("模式匹配失败", e);
            throw new RuntimeException("模式匹配失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 为每个变量节点查找候选实体
     */
    private Map<String, List<EntityCandidate>> findVariableCandidates(
            PatternMatchingDTO request, OntologyVisualizationDTO vizData) {
        Map<String, List<EntityCandidate>> candidates = new HashMap<>();
        
        // 构建节点ID到节点的映射
        Map<String, OntologyVisualizationDTO.NodeDTO> nodeMap = vizData.getNodes().stream()
                .collect(Collectors.toMap(OntologyVisualizationDTO.NodeDTO::getId, n -> n));
        
        for (PatternMatchingDTO.PatternNode node : request.getNodes()) {
            List<EntityCandidate> nodeCandidates = new ArrayList<>();
            
            if (Boolean.TRUE.equals(node.getIsVariable())) {
                // 变量节点：根据类型和约束查找候选
                nodeCandidates = findCandidatesByConstraints(node, vizData, nodeMap);
            } else if (node.getActualNodeId() != null) {
                // 具体节点：直接定位
                OntologyVisualizationDTO.NodeDTO actualNode = nodeMap.get(node.getActualNodeId());
                if (actualNode != null) {
                    EntityCandidate candidate = createCandidateFromNode(actualNode);
                    nodeCandidates.add(candidate);
                }
            }
            
            candidates.put(node.getNodeId(), nodeCandidates);
            log.debug("变量 {} 找到 {} 个候选实体", node.getNodeId(), nodeCandidates.size());
        }
        
        return candidates;
    }
    
    /**
     * 根据约束条件查找候选实体
     */
    private List<EntityCandidate> findCandidatesByConstraints(
            PatternMatchingDTO.PatternNode node,
            OntologyVisualizationDTO vizData,
            Map<String, OntologyVisualizationDTO.NodeDTO> nodeMap) {
        
        List<EntityCandidate> candidates = new ArrayList<>();
        String entityType = node.getEntityType();
        Map<String, Object> constraints = node.getConstraints();
        
        for (OntologyVisualizationDTO.NodeDTO graphNode : vizData.getNodes()) {
            // 类型匹配
            if (entityType != null && !entityType.equalsIgnoreCase(graphNode.getType())) {
                continue;
            }
            
            // 约束条件匹配
            if (constraints != null && !constraints.isEmpty()) {
                boolean matches = checkNodeConstraints(graphNode, constraints);
                if (!matches) {
                    continue;
                }
            }
            
            candidates.add(createCandidateFromNode(graphNode));
        }
        
        return candidates;
    }
    
    /**
     * 检查节点是否满足约束条件
     */
    private boolean checkNodeConstraints(OntologyVisualizationDTO.NodeDTO node, 
                                          Map<String, Object> constraints) {
        for (Map.Entry<String, Object> constraint : constraints.entrySet()) {
            String key = constraint.getKey();
            Object value = constraint.getValue();
            
            switch (key) {
                case "name":
                case "label":
                    if (value instanceof String) {
                        String nodeName = node.getLabel();
                        if (nodeName == null || !nodeName.toLowerCase().contains(((String) value).toLowerCase())) {
                            return false;
                        }
                    }
                    break;
                case "type":
                    if (!value.equals(node.getType())) {
                        return false;
                    }
                    break;
                // 可以扩展更多约束条件
                default:
                    // 其他约束条件在data中查找
                    if (node.getData() != null) {
                        Object dataValue = node.getData().get(key);
                        if (dataValue == null || !dataValue.equals(value)) {
                            return false;
                        }
                    }
                    break;
            }
        }
        return true;
    }
    
    /**
     * 从节点创建候选实体
     */
    private EntityCandidate createCandidateFromNode(OntologyVisualizationDTO.NodeDTO node) {
        EntityCandidate candidate = new EntityCandidate();
        candidate.setEntityId(node.getId());
        candidate.setEntityType(node.getType());
        candidate.setEntityName(node.getLabel());
        candidate.setIri(node.getUri());  // NodeDTO 使用 uri 而不是 iri
        candidate.setProperties(node.getData());
        return candidate;
    }
    
    /**
     * 生成所有可能的节点绑定组合（笛卡尔积）
     */
    private List<Map<String, EntityCandidate>> generateNodeBindings(
            Map<String, List<EntityCandidate>> variableCandidates) {
        List<Map<String, EntityCandidate>> allBindings = new ArrayList<>();
        
        if (variableCandidates.isEmpty()) {
            return allBindings;
        }
        
        // 获取所有变量名
        List<String> variables = new ArrayList<>(variableCandidates.keySet());
        
        // 限制候选数量，防止组合爆炸
        int maxCandidatesPerVariable = 50;
        for (List<EntityCandidate> candidates : variableCandidates.values()) {
            if (candidates.size() > maxCandidatesPerVariable) {
                candidates.subList(maxCandidatesPerVariable, candidates.size()).clear();
            }
        }
        
        // 生成笛卡尔积
        generateBindingsRecursive(variables, 0, new HashMap<>(), 
                variableCandidates, allBindings);
        
        // 限制总绑定数量
        int maxBindings = 10000;
        if (allBindings.size() > maxBindings) {
            return allBindings.subList(0, maxBindings);
        }
        
        return allBindings;
    }
    
    private void generateBindingsRecursive(List<String> variables, int index,
                                           Map<String, EntityCandidate> currentBinding,
                                           Map<String, List<EntityCandidate>> variableCandidates,
                                           List<Map<String, EntityCandidate>> allBindings) {
        if (index >= variables.size()) {
            allBindings.add(new HashMap<>(currentBinding));
            return;
        }
        
        String variable = variables.get(index);
        List<EntityCandidate> candidates = variableCandidates.get(variable);
        
        if (candidates == null || candidates.isEmpty()) {
            return; // 没有候选，此分支无效
        }
        
        for (EntityCandidate candidate : candidates) {
            // 检查是否已使用（避免不同变量绑定到同一实体）
            if (currentBinding.values().stream().anyMatch(c -> c.getEntityId().equals(candidate.getEntityId()))) {
                continue;
            }
            
            currentBinding.put(variable, candidate);
            generateBindingsRecursive(variables, index + 1, currentBinding, 
                    variableCandidates, allBindings);
            currentBinding.remove(variable);
        }
    }
    
    /**
     * 验证绑定组合的边约束
     */
    private List<ValidatedMatch> validateBindings(
            List<Map<String, EntityCandidate>> allBindings,
            PatternMatchingDTO request,
            OntologyVisualizationDTO vizData) {
        
        List<ValidatedMatch> validMatches = new ArrayList<>();
        Double minMatchRatio = request.getMinMatchRatio() != null ? request.getMinMatchRatio() : 0.7;
        Integer maxResults = request.getMaxResults() != null ? request.getMaxResults() : 50;
        
        // 构建边的邻接关系
        Map<String, Set<String>> edgeMap = buildEdgeMap(vizData);
        Map<String, String> edgeTypeMap = buildEdgeTypeMap(vizData);
        
        for (Map<String, EntityCandidate> binding : allBindings) {
            ValidatedMatch match = validateSingleBinding(binding, request, edgeMap, edgeTypeMap);
            
            if (match != null && match.getConfidence() >= minMatchRatio) {
                validMatches.add(match);
                
                // 限制结果数量
                if (validMatches.size() >= maxResults) {
                    break;
                }
            }
        }
        
        return validMatches;
    }
    
    /**
     * 构建边映射（用于快速查找）
     */
    private Map<String, Set<String>> buildEdgeMap(OntologyVisualizationDTO vizData) {
        Map<String, Set<String>> edgeMap = new HashMap<>();
        
        for (OntologyVisualizationDTO.EdgeDTO edge : vizData.getEdges()) {
            String source = edge.getSource();
            String target = edge.getTarget();
            
            edgeMap.computeIfAbsent(source, k -> new HashSet<>()).add(target);
            // 无向图：双向添加
            edgeMap.computeIfAbsent(target, k -> new HashSet<>()).add(source);
        }
        
        return edgeMap;
    }
    
    private Map<String, String> buildEdgeTypeMap(OntologyVisualizationDTO vizData) {
        Map<String, String> edgeTypeMap = new HashMap<>();
        
        for (OntologyVisualizationDTO.EdgeDTO edge : vizData.getEdges()) {
            String key = edge.getSource() + "->" + edge.getTarget();
            edgeTypeMap.put(key, edge.getType());
            // 无向图
            String reverseKey = edge.getTarget() + "->" + edge.getSource();
            edgeTypeMap.put(reverseKey, edge.getType());
        }
        
        return edgeTypeMap;
    }
    
    /**
     * 验证单个绑定
     */
    private ValidatedMatch validateSingleBinding(
            Map<String, EntityCandidate> binding,
            PatternMatchingDTO request,
            Map<String, Set<String>> edgeMap,
            Map<String, String> edgeTypeMap) {
        
        List<EdgeMatch> edgeMatches = new ArrayList<>();
        int totalEdges = request.getEdges() != null ? request.getEdges().size() : 0;
        int matchedEdges = 0;
        
        if (request.getEdges() != null) {
            for (PatternMatchingDTO.PatternEdge edge : request.getEdges()) {
                EntityCandidate source = binding.get(edge.getSourceNodeId());
                EntityCandidate target = binding.get(edge.getTargetNodeId());
                
                if (source == null || target == null) {
                    continue;
                }
                
                EdgeMatch edgeMatch = findRelationBetweenEntities(
                        source, target, edge, edgeMap, edgeTypeMap);
                
                if (edgeMatch != null) {
                    edgeMatches.add(edgeMatch);
                    matchedEdges++;
                } else if (!Boolean.TRUE.equals(edge.getIsOptional())) {
                    // 必需边不存在，整个匹配失败
                    return null;
                }
            }
        }
        
        double confidence = totalEdges > 0 ? (double) matchedEdges / totalEdges : 1.0;
        
        ValidatedMatch match = new ValidatedMatch();
        match.setMatchId(generateMatchId(binding));
        match.setNodeBinding(binding);
        match.setEdgeMatches(edgeMatches);
        match.setConfidence(confidence);
        
        return match;
    }
    
    /**
     * 查找两个实体间的关系
     */
    private EdgeMatch findRelationBetweenEntities(
            EntityCandidate source, EntityCandidate target,
            PatternMatchingDTO.PatternEdge edge,
            Map<String, Set<String>> edgeMap,
            Map<String, String> edgeTypeMap) {
        
        // 检查是否存在边
        Set<String> neighbors = edgeMap.get(source.getEntityId());
        if (neighbors == null || !neighbors.contains(target.getEntityId())) {
            return null;
        }
        
        // 检查关系类型（如果指定了）
        if (edge.getRelationshipType() != null && !edge.getRelationshipType().isEmpty()) {
            String actualType = edgeTypeMap.get(source.getEntityId() + "->" + target.getEntityId());
            if (!edge.getRelationshipType().equalsIgnoreCase(actualType)) {
                return null;
            }
        }
        
        EdgeMatch match = new EdgeMatch();
        match.setSourceVariable(edge.getSourceNodeId());
        match.setTargetVariable(edge.getTargetNodeId());
        match.setSourceEntityId(source.getEntityId());
        match.setTargetEntityId(target.getEntityId());
        match.setRelationType(edgeTypeMap.getOrDefault(
                source.getEntityId() + "->" + target.getEntityId(), "related"));
        
        return match;
    }
    
    /**
     * 生成匹配ID
     */
    private String generateMatchId(Map<String, EntityCandidate> binding) {
        StringBuilder sb = new StringBuilder("match_");
        binding.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(entry.getKey()).append("_")
                        .append(entry.getValue().getEntityId()).append("_"));
        return sb.toString();
    }
    
    /**
     * 构建模式匹配结果
     */
    private Map<String, Object> buildPatternResult(
            List<ValidatedMatch> validMatches,
            PatternMatchingDTO request,
            OntologyVisualizationDTO vizData) {
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> addedNodes = new HashSet<>();
        Set<String> addedEdges = new HashSet<>();
        List<Map<String, Object>> matchList = new ArrayList<>();
        
        // 构建节点ID到节点的映射
        Map<String, OntologyVisualizationDTO.NodeDTO> nodeMap = vizData.getNodes().stream()
                .collect(Collectors.toMap(OntologyVisualizationDTO.NodeDTO::getId, n -> n));
        
        for (ValidatedMatch match : validMatches) {
            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("matchId", match.getMatchId());
            matchInfo.put("confidence", match.getConfidence());
            
            List<String> nodeIds = new ArrayList<>();
            List<String> edgeIds = new ArrayList<>();
            
            // 添加节点
            for (Map.Entry<String, EntityCandidate> entry : match.getNodeBinding().entrySet()) {
                EntityCandidate candidate = entry.getValue();
                String nodeId = candidate.getEntityId();
                nodeIds.add(nodeId);
                
                if (!addedNodes.contains(nodeId)) {
                    OntologyVisualizationDTO.NodeDTO originalNode = nodeMap.get(nodeId);
                    if (originalNode != null) {
                        Map<String, Object> node = new HashMap<>();
                        node.put("id", originalNode.getId());
                        node.put("label", originalNode.getLabel());
                        node.put("name", originalNode.getLabel()); // NodeDTO没有getName()，使用getLabel()
                        node.put("type", originalNode.getType());
                        node.put("color", originalNode.getColor());
                        node.put("variable", entry.getKey()); // 绑定的变量名
                        nodes.add(node);
                        addedNodes.add(nodeId);
                    }
                }
            }
            
            // 添加边
            for (EdgeMatch edgeMatch : match.getEdgeMatches()) {
                String edgeId = edgeMatch.getSourceEntityId() + "->" + edgeMatch.getTargetEntityId();
                edgeIds.add(edgeId);
                
                if (!addedEdges.contains(edgeId)) {
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("source", edgeMatch.getSourceEntityId());
                    edge.put("target", edgeMatch.getTargetEntityId());
                    edge.put("type", edgeMatch.getRelationType());
                    edge.put("sourceVariable", edgeMatch.getSourceVariable());
                    edge.put("targetVariable", edgeMatch.getTargetVariable());
                    edges.add(edge);
                    addedEdges.add(edgeId);
                }
            }
            
            matchInfo.put("nodeIds", nodeIds);
            matchInfo.put("edgeIds", edgeIds);
            matchList.add(matchInfo);
        }
        
        result.put("matchCount", validMatches.size());
        result.put("matches", matchList);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("patternNodeCount", request.getNodes().size());
        result.put("patternEdgeCount", request.getEdges() != null ? request.getEdges().size() : 0);
        
        return result;
    }
    
    // ==================== 内部类定义 ====================
    
    @Data
    private static class EntityCandidate {
        private String entityId;
        private String entityType;
        private String entityName;
        private String iri;
        private Map<String, Object> properties;
    }
    
    @Data
    private static class ValidatedMatch {
        private String matchId;
        private Map<String, EntityCandidate> nodeBinding;
        private List<EdgeMatch> edgeMatches;
        private Double confidence;
    }
    
    @Data
    private static class EdgeMatch {
        private String sourceVariable;
        private String targetVariable;
        private String sourceEntityId;
        private String targetEntityId;
        private String relationType;
    }
}
