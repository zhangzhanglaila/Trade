package com.example.tdproject.graph.controller;

import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import com.example.tdproject.generator.domain.Ontology;
import com.example.tdproject.generator.service.OntologyService;
import com.example.tdproject.graph.dto.PathAnalysisRequest;
import com.example.tdproject.graph.dto.PathAnalysisResponse;
import com.example.tdproject.graph.service.GraphAnalysisService;
import com.example.tdproject.ontology.dto.CentralityResult;
import com.example.tdproject.ontology.dto.CommunityDetectionResult;
import com.example.tdproject.ontology.dto.PatternMatchingDTO;
import com.example.tdproject.ontology.repository.GraphRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 图分析控制器
 * 提供路径分析、邻居查询等图算法API
 */
@RestController
@RequestMapping("/ontology/{id}/graph")
@Tag(name = "图分析", description = "提供最短路径、全通路径、邻居查询等图分析功能")
@Slf4j
public class GraphAnalysisController {

    @Autowired
    private GraphAnalysisService graphAnalysisService;

    @Autowired
    private com.example.tdproject.ontology.service.GraphAnalysisService ontologyGraphAnalysisService;

    @Autowired
    private OntologyService ontologyService;

    @Autowired
    private GraphRepository graphRepository;

    /**
     * 查找最短路径
     */
    @PostMapping("/shortest-path")
    @Operation(summary = "查找最短路径", description = "使用BFS算法查找两个节点之间的最短路径")
    public ResponseEntity<Result<PathAnalysisResponse>> findShortestPath(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @RequestBody PathAnalysisRequest request) {
        
        try {
            if (request.getSourceNode() == null || request.getTargetNode() == null) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "起始节点和目标节点不能为空"));
            }

            String namedGraphUri = getNamedGraphUri(id);
            if (namedGraphUri == null) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.NOT_FOUND, "本体不存在或无法访问"));
            }

            request.setAlgorithm("shortest");
            if (request.getMaxDepth() == null) {
                request.setMaxDepth(10);
            }

            PathAnalysisResponse response = graphAnalysisService.findShortestPath(namedGraphUri, request);
            
            if ("error".equals(response.getStatus())) {
                return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.SERVICE_ERROR, response.getMessage()));
            }
            
            return ResponseEntity.ok(Result.build(response));
            
        } catch (Exception e) {
            log.error("查找最短路径失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "查找失败: " + e.getMessage()));
        }
    }

    /**
     * 查找所有路径
     */
    @PostMapping("/all-paths")
    @Operation(summary = "查找所有路径", description = "使用DFS算法查找两个节点之间的所有路径")
    public ResponseEntity<Result<PathAnalysisResponse>> findAllPaths(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @RequestBody PathAnalysisRequest request) {
        
        try {
            if (request.getSourceNode() == null || request.getTargetNode() == null) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "起始节点和目标节点不能为空"));
            }

            String namedGraphUri = getNamedGraphUri(id);
            if (namedGraphUri == null) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.NOT_FOUND, "本体不存在或无法访问"));
            }

            request.setAlgorithm("all");
            if (request.getMaxDepth() == null) {
                request.setMaxDepth(5);
            }
            if (request.getMaxPaths() == null) {
                request.setMaxPaths(10);
            }

            PathAnalysisResponse response = graphAnalysisService.findAllPaths(namedGraphUri, request);
            
            if ("error".equals(response.getStatus())) {
                return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.SERVICE_ERROR, response.getMessage()));
            }
            
            return ResponseEntity.ok(Result.build(response));
            
        } catch (Exception e) {
            log.error("查找所有路径失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "查找失败: " + e.getMessage()));
        }
    }

    /**
     * 查找邻居节点（新版 - 返回简单节点ID列表）
     */
    @GetMapping("/neighbors")
    @Operation(summary = "查找邻居节点", description = "查找指定节点的邻居节点（支持多跳）")
    public ResponseEntity<Result<Object>> findNeighbors(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "节点ID", required = true) @RequestParam String nodeUri,
            @Parameter(description = "深度", example = "1") @RequestParam(defaultValue = "1") Integer depth) {
        
        try {
            if (nodeUri == null || nodeUri.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "节点ID不能为空"));
            }

            Map<String, Object> result = ontologyGraphAnalysisService.queryNeighbors(id, nodeUri, depth);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("查找邻居节点失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "查找失败: " + e.getMessage()));
        }
    }

    /**
     * 实体扩线（支持方向过滤）
     */
    @GetMapping("/expand")
    @Operation(summary = "实体扩线", description = "从中心实体向外扩展多层，支持方向过滤")
    public ResponseEntity<Result<Object>> expandEntity(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "中心实体ID", required = true) @RequestParam String entityId,
            @Parameter(description = "扩展层级(1-5)", example = "2") @RequestParam(defaultValue = "2") Integer expandLevel,
            @Parameter(description = "方向: OUT(出边)/IN(入边)/BOTH(双向)", example = "BOTH") @RequestParam(defaultValue = "BOTH") String direction) {
        
        try {
            if (entityId == null || entityId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "实体ID不能为空"));
            }

            Map<String, Object> result = ontologyGraphAnalysisService.expandEntity(id, entityId, expandLevel, direction);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("实体扩线失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "扩线失败: " + e.getMessage()));
        }
    }

    /**
     * 图谱结构分析
     */
    @GetMapping("/structure")
    @Operation(summary = "图谱结构分析", description = "分析图谱的节点数、边数、密度等结构指标")
    public ResponseEntity<Result<Object>> analyzeGraphStructure(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id) {
        
        try {
            Map<String, Object> result = ontologyGraphAnalysisService.analyzeGraphStructure(id);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("图谱结构分析失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "分析失败: " + e.getMessage()));
        }
    }

    /**
     * 统一路径分析接口
     */
    @PostMapping("/analyze")
    @Operation(summary = "路径分析", description = "统一的路径分析接口，支持多种算法")
    public ResponseEntity<Result<PathAnalysisResponse>> analyzePath(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @RequestBody PathAnalysisRequest request) {
        
        try {
            if (request.getSourceNode() == null || request.getTargetNode() == null) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "起始节点和目标节点不能为空"));
            }

            String namedGraphUri = getNamedGraphUri(id);
            if (namedGraphUri == null) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.NOT_FOUND, "本体不存在或无法访问"));
            }

            if (request.getMaxDepth() == null) {
                request.setMaxDepth(10);
            }
            if (request.getMaxPaths() == null) {
                request.setMaxPaths(10);
            }

            PathAnalysisResponse response = graphAnalysisService.analyzePath(namedGraphUri, request);
            
            if ("error".equals(response.getStatus())) {
                return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.SERVICE_ERROR, response.getMessage()));
            }
            
            return ResponseEntity.ok(Result.build(response));
            
        } catch (Exception e) {
            log.error("路径分析失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "分析失败: " + e.getMessage()));
        }
    }

    /**
     * 社区发现
     */
    @GetMapping("/communities")
    @Operation(summary = "社区发现", description = "使用Louvain、标签传播等算法发现图谱社区结构")
    public ResponseEntity<Result<CommunityDetectionResult>> detectCommunities(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "算法类型：LOUVAIN, LABEL_PROPAGATION, MODULARITY_OPTIMIZATION") 
            @RequestParam(defaultValue = "LOUVAIN") String algorithm,
            @Parameter(description = "分辨率参数") @RequestParam(defaultValue = "1.0") Double resolution,
            @Parameter(description = "最大迭代次数") @RequestParam(defaultValue = "100") Integer maxIterations,
            @Parameter(description = "最小社区大小") @RequestParam(defaultValue = "2") Integer minCommunitySize) {
        
        try {
            CommunityDetectionResult result = ontologyGraphAnalysisService.detectCommunities(
                    id, algorithm, resolution, maxIterations, minCommunitySize);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("社区发现失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "社区发现失败: " + e.getMessage()));
        }
    }

    /**
     * 中心度分析
     */
    @GetMapping("/centrality")
    @Operation(summary = "中心度分析", description = "计算节点的度中心性、介数中心性、接近中心性等")
    public ResponseEntity<Result<CentralityResult>> analyzeCentrality(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "中心度类型：DEGREE, BETWEENNESS, CLOSENESS, EIGENVECTOR") 
            @RequestParam(defaultValue = "DEGREE") String centralityType,
            @Parameter(description = "返回结果数量限制") @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "最小阈值") @RequestParam(defaultValue = "0.0") Double minThreshold) {
        
        try {
            CentralityResult result = ontologyGraphAnalysisService.analyzeCentrality(
                    id, centralityType, limit, minThreshold);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("中心度分析失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "中心度分析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取NamedGraph URI - 查询所有版本找到包含数据的
     */
    private String getNamedGraphUri(Long ontologyId) {
        // 逻辑已上提到 OntologyService.resolveNamedGraphUri()，与可视化接口
        // /ontology/{id}/visualization 共用。原先两处各写一份：这里遍历版本找
        // 有数据的图，可视化那边却直接用当前版本的 URI，于是版本号写法一变
        // （命名图 URI 是 prefix+name+"/v"+version，版本号自带 "v" → /vv1.0）
        // 可视化就查到空图，而图分析仍正常。这里只做转发，调用点不变。
        return ontologyService.resolveNamedGraphUri(ontologyId);
    }

    /**
     * 关联查询
     */
    @PostMapping("/associations")
    @Operation(summary = "关联查询", description = "查询多个节点之间的关联关系")
    public ResponseEntity<Result<Object>> queryAssociations(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @RequestBody List<String> entityIds,
            @Parameter(description = "查询类型：DIRECT, INDIRECT, ALL") 
            @RequestParam(defaultValue = "DIRECT") String queryType) {
        
        try {
            if (entityIds == null || entityIds.size() < 2) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "请至少选择两个节点"));
            }

            Map<String, Object> result = ontologyGraphAnalysisService.queryAssociations(id, entityIds, queryType);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("关联查询失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "关联查询失败: " + e.getMessage()));
        }
    }

    /**
     * 模式匹配
     */
    @PostMapping("/pattern/matching")
    @Operation(summary = "模式匹配", description = "在图谱中查找匹配指定模式的子图")
    public ResponseEntity<Result<Object>> patternMatching(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @RequestBody PatternMatchingDTO patternRequest) {
        
        try {
            // 参数校验
            if (patternRequest == null || patternRequest.getNodes() == null || patternRequest.getNodes().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "模式节点不能为空"));
            }
            
            // 校验模式节点
            for (PatternMatchingDTO.PatternNode node : patternRequest.getNodes()) {
                if (node.getNodeId() == null || node.getNodeId().trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "模式节点ID不能为空"));
                }
                if (node.getEntityType() == null || node.getEntityType().trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "模式节点类型不能为空"));
                }
            }
            
            // 校验模式边
            if (patternRequest.getEdges() != null) {
                for (PatternMatchingDTO.PatternEdge edge : patternRequest.getEdges()) {
                    if (edge.getSourceNodeId() == null || edge.getTargetNodeId() == null) {
                        return ResponseEntity.badRequest()
                                .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "模式边的源节点和目标节点ID不能为空"));
                    }
                }
            }

            Map<String, Object> result = ontologyGraphAnalysisService.patternMatching(id, patternRequest);
            
            return ResponseEntity.ok(Result.build(result));
            
        } catch (Exception e) {
            log.error("模式匹配失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "模式匹配失败: " + e.getMessage()));
        }
    }
}
