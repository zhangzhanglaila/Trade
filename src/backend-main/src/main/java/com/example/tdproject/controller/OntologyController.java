package com.example.tdproject.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.example.tdproject.generator.domain.Ontology;
import com.example.tdproject.generator.service.OntologyService;
import com.example.tdproject.ontology.dto.*;
import com.example.tdproject.ontology.enums.RdfFileFormat;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/ontology")
@Tag(name = "本体管理", description = "本体信息的CRUD及版本管理接口")
@Slf4j
public class OntologyController {

    private final OntologyService ontologyService;

    public OntologyController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    // -------------------------- 基础CRUD操作（优先测试） --------------------------

    /**
     * 新增本体（基础操作）
     */
    @PostMapping("/add")
    @Operation(summary = "新增本体", description = "创建新的本体项目，初始版本为1.0（基础操作）")
    public ResponseEntity<Result<Ontology>> addOntology(
            @Parameter(description = "本体实体信息", required = true) @Valid @RequestBody Ontology ontology) {
        try {
            // 验证必要参数
            if (ontology == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体信息不能为空"));
            }
            if (StringUtils.isBlank(ontology.getProjectName())) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "项目名称不能为空"));
            }
            if (StringUtils.isBlank(ontology.getCreator())) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "创建人不能为空"));
            }

            // 设置初始版本号
            if (StringUtils.isBlank(ontology.getVersionNumber())) {
                ontology.setVersionNumber("1.0");
            }

            // 保存新本体
            boolean success = ontologyService.save(ontology);
            if (success) {
                log.info("新增本体成功，项目名称: {}", ontology.getProjectName());
                return ResponseEntity.ok(Result.build(ontology));
            }
            log.warn("新增本体失败，项目名称: {}", ontology.getProjectName());
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "新增失败"));
        } catch (Exception e) {
            log.error("新增本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 根据ID查询本体（基础操作）
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询本体详情", description = "根据ID查询本体详情（基础操作）")
    public ResponseEntity<Result<Ontology>> getById(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "ID不能为空"));
            }

            Ontology ontology = ontologyService.getById(id);
            if (ontology != null) {
                return ResponseEntity.ok(Result.build(ontology));
            }
            return ResponseEntity.status(ResultCodeEnum.NOT_FOUND.getCode())
                    .body(Result.build(ResultCodeEnum.NOT_FOUND, "本体不存在"));
        } catch (Exception e) {
            log.error("查询本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 分页查询本体列表（基础操作）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询本体", description = "分页查询所有当前版本的本体（基础操作）")
    public ResponseEntity<Result<IPage<Ontology>>> getPage(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            IPage<Ontology> page = ontologyService.selectPage(pageNum, pageSize);
            return ResponseEntity.ok(Result.build(page));
        } catch (Exception e) {
            log.error("分页查询本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 更新本体（基础操作）
     */
    @PutMapping("/update")
    @Operation(summary = "修改本体", description = "更新本体信息，自动创建新版本（基础操作）")
    public ResponseEntity<Result<String>> updateOntology(
            @Parameter(description = "本体实体信息（需包含ID）", required = true) @Valid @RequestBody Ontology ontology) {
        try {
            if (ontology == null || ontology.getId() == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            if (StringUtils.isBlank(ontology.getProjectName())) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "项目名称不能为空"));
            }

            ontology.setModifyTime(new Date());
            boolean success = ontologyService.updateById(ontology);
            if (success) {
                log.info("修改本体成功，ID: {}", ontology.getId());
                return ResponseEntity.ok(Result.build("修改成功"));
            }
            log.warn("修改本体失败，ID: {}", ontology.getId());
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "修改失败"));
        } catch (Exception e) {
            log.error("修改本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    // -------------------------- 版本管理相关操作（次优先测试） --------------------------

    /**
     * 查询项目的所有版本
     */
    @GetMapping("/versions/{projectName}")
    @Operation(summary = "查询版本列表", description = "根据项目名称查询所有版本（包括历史版本）")
    public ResponseEntity<Result<List<Ontology>>> getVersions(
            @Parameter(description = "项目名称", required = true) @PathVariable String projectName) {
        try {
            if (StringUtils.isBlank(projectName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "项目名称不能为空"));
            }

            List<Ontology> versions = ontologyService.getVersionsByProjectName(projectName);
            return ResponseEntity.ok(Result.build(versions));
        } catch (Exception e) {
            log.error("查询版本列表异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    // OntologyController.java 保持不变
    @PostMapping("/rollback")
    @Operation(summary = "版本回滚", description = "将指定历史版本恢复为当前版本")
    public ResponseEntity<Result<String>> rollback(
            @Parameter(description = "历史版本ID", required = true) @RequestParam Long versionId) {
        try {
            if (versionId == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "版本ID不能为空"));
            }

            boolean success = ontologyService.rollbackVersion(versionId);
            if (success) {
                log.info("版本回滚成功，版本ID: {}", versionId);
                return ResponseEntity.ok(Result.build("版本回滚成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "版本回滚失败"));
        } catch (Exception e) {
            log.error("版本回滚异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 单条删除
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除本体", description = "根据ID删除指定本体信息")
    public ResponseEntity<Result<String>> deleteOntology(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }

            boolean success = ontologyService.removeById(id);
            if (success) {
                log.info("删除本体成功，ID: {}", id);
                return ResponseEntity.ok(Result.build("删除成功"));
            }
            log.warn("删除本体失败，ID: {}", id);
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "删除失败"));
        } catch (Exception e) {
            log.error("删除本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除本体", description = "根据ID列表批量删除本体信息")
    public ResponseEntity<Result<String>> batchDeleteOntology(
            @Parameter(description = "本体ID列表", required = true) @RequestBody List<Long> ids) {
        try {
            if (CollectionUtils.isEmpty(ids)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "ID列表不能为空"));
            }

            boolean success = ontologyService.removeByIds(ids);
            if (success) {
                log.info("批量删除本体成功，ID列表: {}", ids);
                return ResponseEntity.ok(Result.build("批量删除成功"));
            }
            log.warn("批量删除本体失败，ID列表: {}", ids);
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "批量删除失败"));
        } catch (Exception e) {
            log.error("批量删除本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    // -------------------------- 导入导出操作（较复杂，后测试） --------------------------


    /**
     * 按创建人搜索本体
     */
    @GetMapping("/search-by-creator")
    @Operation(summary = "按创建人搜索", description = "根据创建人分页查询本体")
    public ResponseEntity<Result<IPage<Ontology>>> searchByCreator(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "创建人名称", required = true) @RequestParam String creator) {
        try {
            if (StringUtils.isEmpty(creator)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "创建人不能为空"));
            }

            IPage<Ontology> page = ontologyService.searchByCreator(pageNum, pageSize, creator);
            return ResponseEntity.ok(Result.build(page));
        } catch (Exception e) {
            log.error("按创建人搜索本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 按项目名搜索本体
     */
    @GetMapping("/search-by-project")
    @Operation(summary = "按项目名搜索", description = "根据项目名模糊匹配分页查询本体")
    public ResponseEntity<Result<IPage<Ontology>>> searchByProjectName(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "项目名称", required = true) @RequestParam String projectName) {
        try {
            if (StringUtils.isEmpty(projectName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "项目名称不能为空"));
            }

            IPage<Ontology> page = ontologyService.searchByProjectName(pageNum, pageSize, projectName);
            return ResponseEntity.ok(Result.build(page));
        } catch (Exception e) {
            log.error("按项目名搜索本体异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }


    // -------------------------- 特殊操作（最后测试） --------------------------

    /**
     * 批量标记历史版本
     */
    @PostMapping("/mark-all-history")
    @Operation(summary = "批量标记历史版本", description = "将指定项目的所有版本标记为历史版本（特殊场景使用）")
    public ResponseEntity<Result<String>> markAllAsHistory(
            @Parameter(description = "项目名称", required = true) @RequestParam String projectName) {
        try {
            if (StringUtils.isBlank(projectName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "项目名称不能为空"));
            }

            boolean success = ontologyService.markAllAsHistory(projectName);
            if (success) {
                log.info("项目[{}]所有版本已标记为历史版本", projectName);
                return ResponseEntity.ok(Result.build("操作成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "操作失败"));
        } catch (Exception e) {
            log.error("批量标记历史版本异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

        @PostMapping("/import")
        @Operation(summary = "导入本体", description = "从RDF/CSV文件导入本体。RDF直接导入，CSV自动转换为RDF。")
        public ResponseEntity<Result<String>> importOntology(
                @Parameter(description = "文件(支持.rdf/.owl/.ttl/.nt/.csv)", required = true) @RequestParam("file") MultipartFile file,
                @Parameter(description = "项目名称", required = true) @RequestParam String projectName,
                @Parameter(description = "创建人", required = true) @RequestParam String creator,
                @Parameter(description = "CSV转换模式(original/custom)，仅CSV文件有效") @RequestParam(required = false, defaultValue = "original") String csvMode) {
            try {
                // 参数校验
                if (file == null || file.isEmpty()) {
                    return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "文件不能为空"));
                }
                if (StringUtils.isEmpty(projectName)) {
                    return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "项目名称不能为空"));
                }
                if (StringUtils.isEmpty(creator)) {
                    return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "创建人不能为空"));
                }

                String fileName = file.getOriginalFilename();
                if (fileName == null) {
                    return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "文件名不能为空"));
                }
                
                String lowerFileName = fileName.toLowerCase();
                
                // RDF 文件直接导入
                if (lowerFileName.endsWith(".rdf") || lowerFileName.endsWith(".owl") 
                        || lowerFileName.endsWith(".ttl") || lowerFileName.endsWith(".nt")) {
                    boolean success = ontologyService.importOntologyFromRdf(file, projectName, creator, null);
                    if (success) {
                        log.info("导入RDF本体成功，项目名称: {}，文件: {}", projectName, fileName);
                        return ResponseEntity.ok(Result.build("导入成功"));
                    }
                    return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                            .body(Result.build(ResultCodeEnum.FAIL, "导入失败：项目已存在或文件格式错误"));
                } 
                // CSV 文件转换后导入
                else if (lowerFileName.endsWith(".csv")) {
                    boolean success = ontologyService.importOntologyFromCsv(file, projectName, creator, csvMode);
                    if (success) {
                        log.info("导入CSV本体成功，项目名称: {}，文件: {}，模式: {}", projectName, fileName, csvMode);
                        return ResponseEntity.ok(Result.build("导入成功（CSV已转换）"));
                    }
                    return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                            .body(Result.build(ResultCodeEnum.FAIL, "导入失败"));
                } else {
                    return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, 
                                    "不支持的文件格式。请上传 .rdf/.owl/.ttl/.nt/.csv 文件"));
                }
            } catch (RuntimeException e) {
                log.error("导入本体失败", e);
                return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                        .body(Result.build(ResultCodeEnum.FAIL, "导入失败：" + e.getMessage()));
            } catch (Exception e) {
                log.error("导入本体系统异常", e);
                return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "系统异常，导入失败"));
            }
        }

        /**
         * 验证 RDF 文件格式
         */
        @PostMapping("/validate-rdf")
        @Operation(summary = "验证RDF文件格式", description = "预检查RDF文件格式是否正确，返回验证结果和错误信息")
        public ResponseEntity<Result<RdfValidationResult>> validateRdf(
                @Parameter(description = "RDF文件", required = true) @RequestParam("file") MultipartFile file) {
            try {
                if (file == null || file.isEmpty()) {
                    return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                            .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "文件不能为空"));
                }
                
                RdfValidationResult result = ontologyService.validateRdfFormat(file);
                return ResponseEntity.ok(Result.build(result));
                
            } catch (Exception e) {
                log.error("验证RDF文件失败", e);
                return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.SERVICE_ERROR, "验证失败：" + e.getMessage()));
            }
        }

        /**
         * 按ID导出本体
         */
        @GetMapping("/export/{id}")
        @Operation(summary = "按ID导出本体", description = "根据ID导出单个本体为Excel文件")
        public void exportById(
                @Parameter(description = "本体ID", required = true)
                @PathVariable Long id,
                HttpServletResponse response) {
            try {
                ontologyService.exportById(id, response);
            } catch (Exception e) {
                log.error("导出本体失败", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        /**
         * 导出所有当前版本本体
         */
        @GetMapping("/export/all")
        @Operation(summary = "导出所有本体", description = "导出所有当前版本的本体为Excel文件")
        public void exportAll(HttpServletResponse response) {
            try {
                ontologyService.exportAll(response);
            } catch (Exception e) {
                log.error("导出所有本体失败", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

    // ==================== 图数据库相关新接口 ====================

    /**
     * 1. 创建本体并上传文件（支持OWL/RDF/TTL/NT格式）
     */
    @PostMapping(value = "/create-with-file", consumes = "multipart/form-data")
    @Operation(summary = "创建本体（带文件上传）", description = "创建新本体并解析上传的RDF/OWL/TTL/NT文件到图数据库")
    public ResponseEntity<Result<Ontology>> createOntologyWithFile(
            @Parameter(description = "本体名称", required = true) @RequestParam("ontologyName") String ontologyName,
            @Parameter(description = "创建人", required = true) @RequestParam("creatorName") String creatorName,
            @Parameter(description = "版本号，默认1.0") @RequestParam(value = "version", required = false, defaultValue = "1.0") String version,
            @Parameter(description = "命名空间URI") @RequestParam(value = "namespaceUri", required = false) String namespaceUri,
            @Parameter(description = "文件格式(OWL/RDF/TTL/NT)") @RequestParam(value = "fileFormat", required = false) String fileFormat,
            @Parameter(description = "本体文件") @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            // 参数校验
            if (StringUtils.isBlank(ontologyName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体名称不能为空"));
            }
            if (StringUtils.isBlank(creatorName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "创建人不能为空"));
            }

            Ontology ontology = ontologyService.createOntologyWithFile(
                    ontologyName, creatorName, version, namespaceUri, fileFormat, file);
            
            log.info("创建本体成功（带文件）: {}, version: {}", ontologyName, ontology.getVersionNumber());
            return ResponseEntity.ok(Result.build(ontology));
        } catch (Exception e) {
            log.error("创建本体（带文件）失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }

    /**
     * 2. 检查本体名称在图数据库中是否存在
     */
    @GetMapping("/check-name/{ontologyName}")
    @Operation(summary = "检查本体名称是否存在", description = "检查本体名称是否已在图数据库中存在")
    public ResponseEntity<Result<CheckNameResult>> checkOntologyName(
            @Parameter(description = "本体名称", required = true) @PathVariable String ontologyName) {
        try {
            if (StringUtils.isBlank(ontologyName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体名称不能为空"));
            }

            CheckNameResult result = ontologyService.checkOntologyNameInGraph(ontologyName);
            return ResponseEntity.ok(Result.build(result));
        } catch (Exception e) {
            log.error("检查本体名称失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 3. 获取本体版本历史（用于入库弹窗）
     */
    @GetMapping("/{ontologyName}/versions")
    @Operation(summary = "获取本体版本历史", description = "根据本体名称获取所有版本历史，按时间倒序排列")
    public ResponseEntity<Result<List<VersionInfo>>> getOntologyVersions(
            @Parameter(description = "本体名称", required = true) @PathVariable String ontologyName) {
        try {
            if (StringUtils.isBlank(ontologyName)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体名称不能为空"));
            }

            List<VersionInfo> versions = ontologyService.getOntologyVersionHistory(ontologyName);
            return ResponseEntity.ok(Result.build(versions));
        } catch (Exception e) {
            log.error("获取版本历史失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 4. 本体入库（导入到图数据库，支持版本更新）
     */
    @PostMapping("/import-to-house")
    @Operation(summary = "本体入库", description = "将本体导入图数据库，支持创建新版本")
    public ResponseEntity<Result<String>> importToHouse(
            @Parameter(description = "入库请求参数", required = true) @Valid @RequestBody ImportToHouseRequest request) {
        try {
            boolean success = ontologyService.importOntologyToHouse(request);
            if (success) {
                log.info("本体入库成功, id: {}, version: {}", request.getSourceOntologyId(), request.getNewVersion());
                return ResponseEntity.ok(Result.build("入库成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "入库失败"));
        } catch (Exception e) {
            log.error("本体入库失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }

    /**
     * 5. 版本回滚（按版本号）
     */
    @PostMapping("/rollback-version")
    @Operation(summary = "版本回滚", description = "将本体回滚到指定版本号")
    public ResponseEntity<Result<String>> rollbackVersion(
            @Parameter(description = "回滚请求参数", required = true) @Valid @RequestBody RollbackVersionRequest request) {
        try {
            boolean success = ontologyService.rollbackOntologyByVersion(request);
            if (success) {
                log.info("版本回滚成功, id: {}, to version: {}", request.getSourceOntologyId(), request.getNewVersion());
                return ResponseEntity.ok(Result.build("回滚成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "回滚失败"));
        } catch (Exception e) {
            log.error("版本回滚失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }

    /**
     * 6. 导出本体文件（支持OWL/RDF/TTL/NT格式）
     */
    @GetMapping("/{id}/export-file")
    @Operation(summary = "导出本体文件", description = "导出本体为OWL/RDF/TTL/NT格式的文件")
    public void exportOntologyFile(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "导出格式(OWL/RDF/TTL/NT)", required = true) @RequestParam("format") String format,
            HttpServletResponse response) {
        try {
            if (id == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            RdfFileFormat fileFormat = RdfFileFormat.fromCode(format);
            if (fileFormat == null) {
                fileFormat = RdfFileFormat.OWL;
            }
            log.info("导出格式: {} -> {}", format, fileFormat.getCode());
            ontologyService.exportOntologyFile(id, fileFormat, response);
        } catch (Exception e) {
            log.error("导出本体文件失败", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("发送错误响应失败", ioException);
            }
        }
    }

    // ==================== 本体详情查询接口 ====================

    /**
     * 获取本体统计数据
     */
    @GetMapping("/{id}/stats")
    @Operation(summary = "获取本体统计数据", description = "获取本体的类数量、实例数量、属性数量、三元组数量")
    public ResponseEntity<Result<OntologyStats>> getOntologyStats(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            OntologyStats stats = ontologyService.getOntologyStats(id);
            return ResponseEntity.ok(Result.build(stats));
        } catch (Exception e) {
            log.error("获取本体统计数据失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 获取本体中的所有类
     */
    @GetMapping("/{id}/classes")
    @Operation(summary = "获取本体类列表", description = "获取本体中的所有类信息，可按名称搜索")
    public ResponseEntity<Result<List<ClassInfo>>> getOntologyClasses(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "搜索关键词（类名）") @RequestParam(required = false) String keyword) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            List<ClassInfo> classes = ontologyService.getOntologyClasses(id, keyword);
            return ResponseEntity.ok(Result.build(classes));
        } catch (Exception e) {
            log.error("获取本体类列表失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 获取本体中的所有实例
     */
    @GetMapping("/{id}/individuals")
    @Operation(summary = "获取本体实例列表", description = "获取本体中的所有实例信息，可按类筛选和名称搜索")
    public ResponseEntity<Result<List<IndividualInfo>>> getOntologyIndividuals(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "所属类URI（可选）") @RequestParam(required = false) String classUri,
            @Parameter(description = "搜索关键词（可选）") @RequestParam(required = false) String keyword) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            List<IndividualInfo> individuals = ontologyService.getOntologyIndividuals(id, classUri, keyword);
            return ResponseEntity.ok(Result.build(individuals));
        } catch (Exception e) {
            log.error("获取本体实例列表失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 获取本体中的所有属性
     */
    @GetMapping("/{id}/properties")
    @Operation(summary = "获取本体属性列表", description = "获取本体中的所有属性信息，可按类型和名称搜索")
    public ResponseEntity<Result<List<PropertyInfo>>> getOntologyProperties(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "属性类型：object/datatype/annotation（可选）") @RequestParam(required = false) String type,
            @Parameter(description = "搜索关键词（可选）") @RequestParam(required = false) String keyword) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            List<PropertyInfo> properties = ontologyService.getOntologyProperties(id, type, keyword);
            return ResponseEntity.ok(Result.build(properties));
        } catch (Exception e) {
            log.error("获取本体属性列表失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }
    
    // ==================== 类管理CRUD接口 ====================
    
    /**
     * 创建类
     */
    @PostMapping("/{id}/classes")
    @Operation(summary = "创建类", description = "在本体中创建新类")
    public ResponseEntity<Result<String>> createOntologyClass(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "类信息", required = true) @RequestBody ClassInfo classInfo) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            String classUri = ontologyService.createOntologyClass(id, classInfo);
            return ResponseEntity.ok(Result.build(classUri));
        } catch (Exception e) {
            log.error("创建类失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 更新类
     */
    @PutMapping("/{id}/classes")
    @Operation(summary = "更新类", description = "更新本体中的类信息")
    public ResponseEntity<Result<String>> updateOntologyClass(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "类URI", required = true) @RequestParam String classUri,
            @Parameter(description = "类信息", required = true) @RequestBody ClassInfo classInfo) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            boolean success = ontologyService.updateOntologyClass(id, classUri, classInfo);
            if (success) {
                return ResponseEntity.ok(Result.build("更新成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "更新失败"));
        } catch (Exception e) {
            log.error("更新类失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 删除类
     */
    @DeleteMapping("/{id}/classes")
    @Operation(summary = "删除类", description = "删除本体中的类")
    public ResponseEntity<Result<String>> deleteOntologyClass(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "类URI", required = true) @RequestParam String classUri) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            boolean success = ontologyService.deleteOntologyClass(id, classUri);
            if (success) {
                return ResponseEntity.ok(Result.build("删除成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "删除失败"));
        } catch (Exception e) {
            log.error("删除类失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    // ==================== 实例管理CRUD接口 ====================
    
    /**
     * 创建实例
     */
    @PostMapping("/{id}/individuals")
    @Operation(summary = "创建实例", description = "在本体中创建新实例")
    public ResponseEntity<Result<String>> createOntologyIndividual(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "实例信息", required = true) @RequestBody IndividualInfo individualInfo) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            String individualUri = ontologyService.createOntologyIndividual(id, individualInfo);
            return ResponseEntity.ok(Result.build(individualUri));
        } catch (Exception e) {
            log.error("创建实例失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 更新实例
     */
    @PutMapping("/{id}/individuals")
    @Operation(summary = "更新实例", description = "更新本体中的实例信息")
    public ResponseEntity<Result<String>> updateOntologyIndividual(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "实例URI", required = true) @RequestParam String individualUri,
            @Parameter(description = "实例信息", required = true) @RequestBody IndividualInfo individualInfo) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            boolean success = ontologyService.updateOntologyIndividual(id, individualUri, individualInfo);
            if (success) {
                return ResponseEntity.ok(Result.build("更新成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "更新失败"));
        } catch (Exception e) {
            log.error("更新实例失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 删除实例
     */
    @DeleteMapping("/{id}/individuals")
    @Operation(summary = "删除实例", description = "删除本体中的实例")
    public ResponseEntity<Result<String>> deleteOntologyIndividual(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "实例URI", required = true) @RequestParam String individualUri) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            boolean success = ontologyService.deleteOntologyIndividual(id, individualUri);
            if (success) {
                return ResponseEntity.ok(Result.build("删除成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "删除失败"));
        } catch (Exception e) {
            log.error("删除实例失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 获取实例的关系（对象属性、数据属性、反向关系）
     */
    @GetMapping("/{id}/individuals/relations")
    @Operation(summary = "获取实例关系", description = "获取实例的对象属性关系、数据属性和反向关系")
    public ResponseEntity<Result<IndividualRelations>> getIndividualRelations(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "实例URI", required = true) @RequestParam String individualUri) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            if (com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank(individualUri)) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "实例URI不能为空"));
            }
            IndividualRelations relations = ontologyService.getIndividualRelations(id, individualUri);
            return ResponseEntity.ok(Result.build(relations));
        } catch (Exception e) {
            log.error("获取实例关系失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    // ==================== 属性管理CRUD接口 ====================
    
    /**
     * 创建属性
     */
    @PostMapping("/{id}/properties")
    @Operation(summary = "创建属性", description = "在本体中创建新属性")
    public ResponseEntity<Result<String>> createOntologyProperty(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "属性信息", required = true) @RequestBody com.example.tdproject.ontology.dto.PropertyCreateRequest request) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            String propertyUri = ontologyService.createOntologyProperty(id, request);
            return ResponseEntity.ok(Result.build(propertyUri));
        } catch (Exception e) {
            log.error("创建属性失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 更新属性
     */
    @PutMapping("/{id}/properties")
    @Operation(summary = "更新属性", description = "更新本体中的属性信息")
    public ResponseEntity<Result<String>> updateOntologyProperty(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "属性URI", required = true) @RequestParam String propertyUri,
            @Parameter(description = "属性信息", required = true) @RequestBody com.example.tdproject.ontology.dto.PropertyCreateRequest request) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            boolean success = ontologyService.updateOntologyProperty(id, propertyUri, request);
            if (success) {
                return ResponseEntity.ok(Result.build("更新成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "更新失败"));
        } catch (Exception e) {
            log.error("更新属性失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    /**
     * 删除属性
     */
    @DeleteMapping("/{id}/properties")
    @Operation(summary = "删除属性", description = "删除本体中的属性")
    public ResponseEntity<Result<String>> deleteOntologyProperty(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "属性URI", required = true) @RequestParam String propertyUri) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            boolean success = ontologyService.deleteOntologyProperty(id, propertyUri);
            if (success) {
                return ResponseEntity.ok(Result.build("删除成功"));
            }
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL, "删除失败"));
        } catch (Exception e) {
            log.error("删除属性失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }
    
    // ==================== 可视化接口 ====================
    
    /**
     * 获取本体可视化数据
     */
    @GetMapping("/{id}/visualization")
    @Operation(summary = "获取本体可视化数据", description = "获取本体的节点和边数据用于图谱可视化")
    public ResponseEntity<Result<OntologyVisualizationDTO>> getOntologyVisualization(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id) {
        try {
            if (id == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "本体ID不能为空"));
            }
            OntologyVisualizationDTO visualization = ontologyService.getOntologyVisualization(id);
            return ResponseEntity.ok(Result.build(visualization));
        } catch (Exception e) {
            log.error("获取可视化数据失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }

    /**
     * 图谱入库 - 保存编辑后的图谱数据
     */
    @PostMapping("/{id}/warehouse")
    @Operation(summary = "图谱入库", description = "将编辑后的图谱数据保存到数据库")
    public ResponseEntity<Result<GraphWarehouseResponseDTO>> warehouseGraph(
            @Parameter(description = "本体ID", required = true) @PathVariable Long id,
            @Parameter(description = "入库请求数据", required = true) @RequestBody GraphWarehouseDTO dto) {
        try {
            log.info("收到入库请求, id={}", id);
            
            if (dto == null) {
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, "请求数据不能为空"));
            }
            
            // 使用路径中的id，并确保dto中的ontologyId一致
            dto.setOntologyId(id);
            
            // 详细的参数验证
            StringBuilder errorMsg = new StringBuilder();
            if (dto.getOntologyName() == null || dto.getOntologyName().isEmpty()) {
                errorMsg.append("ontologyName不能为空; ");
            }
            if (dto.getVersion() == null || dto.getVersion().isEmpty()) {
                errorMsg.append("version不能为空; ");
            }
            if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
                errorMsg.append("nodes不能为空; ");
            }
            
            if (errorMsg.length() > 0) {
                log.warn("参数验证失败: {}", errorMsg.toString());
                return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                        .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, errorMsg.toString()));
            }
            
            log.info("节点数量: {}, 关系数量: {}", 
                    dto.getNodes().size(), 
                    dto.getRelationships() != null ? dto.getRelationships().size() : 0);
            
            GraphWarehouseResponseDTO response = ontologyService.warehouseGraph(dto);
            return ResponseEntity.ok(Result.build(response));
        } catch (Exception e) {
            log.error("图谱入库失败", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR, e.getMessage()));
        }
    }

}