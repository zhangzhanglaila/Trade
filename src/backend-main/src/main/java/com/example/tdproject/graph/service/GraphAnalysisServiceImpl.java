package com.example.tdproject.graph.service;

import com.example.tdproject.graph.dto.GraphPathDTO;
import com.example.tdproject.graph.dto.GraphPathDTO.PathEdgeDTO;
import com.example.tdproject.graph.dto.GraphPathDTO.PathNodeDTO;
import com.example.tdproject.graph.dto.PathAnalysisRequest;
import com.example.tdproject.graph.dto.PathAnalysisResponse;
import com.example.tdproject.ontology.repository.GraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 图分析服务实现
 * 基于Jena TDB实现图算法
 */
@Service
@Slf4j
public class GraphAnalysisServiceImpl implements GraphAnalysisService {

    @Autowired
    private GraphRepository graphRepository;

    @Override
    public PathAnalysisResponse findShortestPath(String namedGraphUri, PathAnalysisRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("查找最短路径: {} -> {}, graph: {}", 
                    request.getSourceNode(), request.getTargetNode(), namedGraphUri);
            
            // 构建完整URI
            String sourceUri = buildFullUri(namedGraphUri, request.getSourceNode());
            String targetUri = buildFullUri(namedGraphUri, request.getTargetNode());
            
            log.info("完整URI: {} -> {}", sourceUri, targetUri);
            
            // 使用BFS算法查找最短路径
            List<String> pathNodeUris = bfsShortestPath(namedGraphUri, sourceUri, targetUri, request.getMaxDepth());
            
            if (pathNodeUris == null || pathNodeUris.isEmpty()) {
                return PathAnalysisResponse.builder()
                        .status("success")
                        .message("未找到路径")
                        .sourceNode(request.getSourceNode())
                        .targetNode(request.getTargetNode())
                        .paths(new ArrayList<>())
                        .totalPaths(0)
                        .searchTime(System.currentTimeMillis() - startTime)
                        .build();
            }
            
            // 构建路径DTO
            GraphPathDTO path = buildPathFromUris(namedGraphUri, pathNodeUris);
            path.setType("shortest");
            
            List<GraphPathDTO> paths = new ArrayList<>();
            paths.add(path);
            
            return PathAnalysisResponse.builder()
                    .status("success")
                    .message("查找成功")
                    .sourceNode(request.getSourceNode())
                    .targetNode(request.getTargetNode())
                    .paths(paths)
                    .totalPaths(1)
                    .shortestLength(pathNodeUris.size())
                    .searchTime(System.currentTimeMillis() - startTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("查找最短路径失败", e);
            return PathAnalysisResponse.builder()
                    .status("error")
                    .message("查找失败: " + e.getMessage())
                    .sourceNode(request.getSourceNode())
                    .targetNode(request.getTargetNode())
                    .paths(new ArrayList<>())
                    .searchTime(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public PathAnalysisResponse findAllPaths(String namedGraphUri, PathAnalysisRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("查找所有路径: {} -> {}, maxDepth: {}, graph: {}", 
                    request.getSourceNode(), request.getTargetNode(), 
                    request.getMaxDepth(), namedGraphUri);
            
            // 构建完整URI
            String sourceUri = buildFullUri(namedGraphUri, request.getSourceNode());
            String targetUri = buildFullUri(namedGraphUri, request.getTargetNode());
            
            log.info("完整URI: {} -> {}", sourceUri, targetUri);
            
            // 使用DFS算法查找所有路径
            List<List<String>> allPaths = dfsAllPaths(namedGraphUri, sourceUri, targetUri, 
                    request.getMaxDepth(), request.getMaxPaths());
            
            if (allPaths.isEmpty()) {
                return PathAnalysisResponse.builder()
                        .status("success")
                        .message("未找到路径")
                        .sourceNode(request.getSourceNode())
                        .targetNode(request.getTargetNode())
                        .paths(new ArrayList<>())
                        .totalPaths(0)
                        .searchTime(System.currentTimeMillis() - startTime)
                        .build();
            }
            
            // 构建路径DTO列表
            List<GraphPathDTO> pathDTOs = new ArrayList<>();
            int minLength = Integer.MAX_VALUE;
            
            for (int i = 0; i < allPaths.size(); i++) {
                List<String> pathUris = allPaths.get(i);
                GraphPathDTO path = buildPathFromUris(namedGraphUri, pathUris);
                path.setPathId("path_" + i);
                path.setType(i == 0 ? "shortest" : "alternative");
                pathDTOs.add(path);
                
                if (pathUris.size() < minLength) {
                    minLength = pathUris.size();
                }
            }
            
            return PathAnalysisResponse.builder()
                    .status("success")
                    .message("查找成功，共找到 " + allPaths.size() + " 条路径")
                    .sourceNode(request.getSourceNode())
                    .targetNode(request.getTargetNode())
                    .paths(pathDTOs)
                    .totalPaths(pathDTOs.size())
                    .shortestLength(minLength)
                    .searchTime(System.currentTimeMillis() - startTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("查找所有路径失败", e);
            return PathAnalysisResponse.builder()
                    .status("error")
                    .message("查找失败: " + e.getMessage())
                    .sourceNode(request.getSourceNode())
                    .targetNode(request.getTargetNode())
                    .paths(new ArrayList<>())
                    .searchTime(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public PathAnalysisResponse findNeighbors(String namedGraphUri, String nodeUri, 
                                              Integer depth, String relationType) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 构建完整URI
            String fullUri = buildFullUri(namedGraphUri, nodeUri);
            log.info("查找邻居: {}, depth: {}, graph: {}", fullUri, depth, namedGraphUri);
            
            // 构建邻接表
            Map<String, List<String>> adjacencyList = buildAdjacencyList(namedGraphUri);
            
            // BFS查找邻居
            Set<String> visited = new HashSet<>();
            Set<String> allNeighbors = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            Map<String, Integer> distanceMap = new HashMap<>();
            
            queue.offer(fullUri);
            visited.add(fullUri);
            distanceMap.put(fullUri, 0);
            
            while (!queue.isEmpty()) {
                String current = queue.poll();
                int currentDepth = distanceMap.get(current);
                
                if (currentDepth >= depth) continue;
                
                List<String> neighbors = adjacencyList.getOrDefault(current, new ArrayList<>());
                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        allNeighbors.add(neighbor);
                        distanceMap.put(neighbor, currentDepth + 1);
                        queue.offer(neighbor);
                    }
                }
            }
            
            // 构建结果路径
            List<PathNodeDTO> neighborNodes = new ArrayList<>();
            int order = 0;
            for (String neighborUri : allNeighbors) {
                PathNodeDTO node = PathNodeDTO.builder()
                        .id("neighbor_" + order)
                        .uri(neighborUri)
                        .label(getLocalName(neighborUri))
                        .order(order++)
                        .build();
                neighborNodes.add(node);
            }
            
            GraphPathDTO path = GraphPathDTO.builder()
                    .pathId(UUID.randomUUID().toString())
                    .nodes(neighborNodes)
                    .edges(new ArrayList<>())
                    .length(neighborNodes.size())
                    .type("neighbors")
                    .build();
            
            return PathAnalysisResponse.builder()
                    .status("success")
                    .message("查找成功，找到 " + neighborNodes.size() + " 个邻居")
                    .sourceNode(nodeUri)
                    .targetNode(null)
                    .paths(Arrays.asList(path))
                    .totalPaths(1)
                    .searchDepth(depth)
                    .searchTime(System.currentTimeMillis() - startTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("查找邻居失败", e);
            return PathAnalysisResponse.builder()
                    .status("error")
                    .message("查找失败: " + e.getMessage())
                    .sourceNode(nodeUri)
                    .searchTime(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public PathAnalysisResponse analyzePath(String namedGraphUri, PathAnalysisRequest request) {
        if ("shortest".equals(request.getAlgorithm())) {
            return findShortestPath(namedGraphUri, request);
        } else if ("all".equals(request.getAlgorithm())) {
            return findAllPaths(namedGraphUri, request);
        }
        return findShortestPath(namedGraphUri, request);
    }

    /**
     * 构建完整URI
     */
    private String buildFullUri(String namedGraphUri, String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        
        // 如果已经是完整URI，直接返回
        if (nodeId.startsWith("http://") || nodeId.startsWith("https://")) {
            return nodeId;
        }
        
        // 构建完整URI
        String baseUri = namedGraphUri.endsWith("/") ? namedGraphUri : namedGraphUri + "/";
        return baseUri + nodeId;
    }

    /**
     * 从完整URI中提取本地名称
     */
    private String getLocalName(String uri) {
        if (uri == null || uri.isEmpty()) return "";
        int lastSlash = uri.lastIndexOf('/');
        int lastHash = uri.lastIndexOf('#');
        int pos = Math.max(lastSlash, lastHash);
        return pos > 0 ? uri.substring(pos + 1) : uri;
    }

    /**
     * 构建邻接表
     */
    private Map<String, List<String>> buildAdjacencyList(String namedGraphUri) {
        Map<String, List<String>> adjacencyList = new HashMap<>();
        
        String queryStr = String.format(
            "SELECT DISTINCT ?source ?target WHERE { " +
            "  GRAPH <%s> { " +
            "    ?source ?relation ?target . " +
            "    FILTER (isURI(?source) && isURI(?target)) " +
            "    FILTER (?source != ?target) " +
            "  } " +
            "}", namedGraphUri);
        
        try {
            Dataset dataset = graphRepository.getDataset();
            dataset.begin(org.apache.jena.query.ReadWrite.READ);
            
            try {
                Query query = QueryFactory.create(queryStr);
                try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
                    ResultSet results = qexec.execSelect();
                    while (results.hasNext()) {
                        QuerySolution soln = results.nextSolution();
                        Resource source = soln.getResource("source");
                        Resource target = soln.getResource("target");
                        
                        if (source != null && target != null) {
                            String sourceUri = source.getURI();
                            String targetUri = target.getURI();
                            
                            adjacencyList.computeIfAbsent(sourceUri, k -> new ArrayList<>()).add(targetUri);
                            // 无向图，双向添加
                            adjacencyList.computeIfAbsent(targetUri, k -> new ArrayList<>()).add(sourceUri);
                        }
                    }
                }
            } finally {
                dataset.commit();
            }
            
            log.info("构建邻接表完成，共 {} 个节点", adjacencyList.size());
            
            // 调试：打印部分邻接表内容
            if (log.isDebugEnabled()) {
                for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
                    log.debug("邻接表: {} -> {}", entry.getKey(), entry.getValue());
                }
            }
            // 打印前5个节点
            int count = 0;
            for (String node : adjacencyList.keySet()) {
                log.info("邻接表节点 {}: {}", count++, node);
                if (count >= 5) break;
            }
            
        } catch (Exception e) {
            log.error("构建邻接表失败", e);
        }
        
        return adjacencyList;
    }

    /**
     * BFS最短路径算法
     */
    private List<String> bfsShortestPath(String namedGraphUri, String startUri, String endUri, Integer maxDepth) {
        // 构建邻接表
        Map<String, List<String>> adjacencyList = buildAdjacencyList(namedGraphUri);
        
        if (!adjacencyList.containsKey(startUri)) {
            log.warn("起始节点不在图中: {}", startUri);
            return null;
        }
        
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        // 初始路径
        List<String> startPath = new ArrayList<>();
        startPath.add(startUri);
        queue.offer(startPath);
        visited.add(startUri);
        
        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String currentNode = path.get(path.size() - 1);
            
            // 找到目标
            if (currentNode.equals(endUri)) {
                return path;
            }
            
            // 超过最大深度
            if (path.size() >= maxDepth) {
                continue;
            }
            
            // 获取邻居节点
            List<String> neighbors = adjacencyList.getOrDefault(currentNode, new ArrayList<>());
            
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.offer(newPath);
                }
            }
        }
        
        return null; // 未找到路径
    }

    /**
     * DFS查找所有路径
     */
    private List<List<String>> dfsAllPaths(String namedGraphUri, String startUri, String endUri, 
                                           int maxDepth, int maxPaths) {
        // 构建邻接表
        Map<String, List<String>> adjacencyList = buildAdjacencyList(namedGraphUri);
        
        List<List<String>> allPaths = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // 从起点开始DFS
        dfs(adjacencyList, startUri, endUri, maxDepth, maxPaths, 
            currentPath, visited, allPaths);
        
        return allPaths;
    }

    private void dfs(Map<String, List<String>> adjacencyList, String currentNode, String endNode, 
                     int maxDepth, int maxPaths,
                     List<String> currentPath, Set<String> visited, 
                     List<List<String>> allPaths) {
        // 达到最大路径数
        if (allPaths.size() >= maxPaths) {
            return;
        }
        
        // 将当前节点加入路径
        currentPath.add(currentNode);
        visited.add(currentNode);
        
        // 超过最大深度
        if (currentPath.size() > maxDepth) {
            // 回溯
            currentPath.remove(currentPath.size() - 1);
            visited.remove(currentNode);
            return;
        }
        
        // 找到目标
        if (currentNode.equals(endNode)) {
            allPaths.add(new ArrayList<>(currentPath));
            // 回溯
            currentPath.remove(currentPath.size() - 1);
            visited.remove(currentNode);
            return;
        }
        
        // 获取邻居节点
        List<String> neighbors = adjacencyList.getOrDefault(currentNode, new ArrayList<>());
        
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                dfs(adjacencyList, neighbor, endNode, maxDepth, maxPaths,
                    currentPath, visited, allPaths);
            }
        }
        
        // 回溯
        currentPath.remove(currentPath.size() - 1);
        visited.remove(currentNode);
    }

    /**
     * 根据URI列表构建路径DTO
     */
    private GraphPathDTO buildPathFromUris(String namedGraphUri, List<String> nodeUris) {
        List<PathNodeDTO> nodes = new ArrayList<>();
        List<PathEdgeDTO> edges = new ArrayList<>();
        
        // 构建节点列表
        for (int i = 0; i < nodeUris.size(); i++) {
            String uri = nodeUris.get(i);
            PathNodeDTO node = PathNodeDTO.builder()
                    .id("node_" + i)
                    .uri(uri)
                    .label(getLocalName(uri))
                    .order(i)
                    .build();
            nodes.add(node);
        }
        
        // 构建边列表
        for (int i = 0; i < nodeUris.size() - 1; i++) {
            String sourceUri = nodeUris.get(i);
            String targetUri = nodeUris.get(i + 1);
            
            PathEdgeDTO edge = PathEdgeDTO.builder()
                    .id("edge_" + i)
                    .source(sourceUri)
                    .target(targetUri)
                    .label("relatedTo")
                    .type("objectProperty")
                    .build();
            
            edges.add(edge);
        }
        
        return GraphPathDTO.builder()
                .pathId(UUID.randomUUID().toString())
                .nodes(nodes)
                .edges(edges)
                .length(nodeUris.size())
                .build();
    }
}
