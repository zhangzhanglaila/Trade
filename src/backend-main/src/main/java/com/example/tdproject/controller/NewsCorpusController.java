package com.example.tdproject.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.service.TNewsCorpusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 新闻语料控制器
 * 处理新闻语料的HTTP请求，提供RESTful API接口
 */
@RestController
@RequestMapping("/news")
@Tag(name = "新闻语料管理", description = "新闻语料的CRUD及查询接口")
@Slf4j
@RequiredArgsConstructor // 构造器注入
@Validated // 参数校验开关
public class NewsCorpusController {

    private final TNewsCorpusService newsCorpusService;

    /** ① 查单条 ––– 前端 /news/selectNewsById/1 所需 */
    @GetMapping("/selectNewsById/{id}")
    @Operation(summary = "根据ID查询新闻语料")
    public ResponseEntity<Result<TNewsCorpus>> selectNewsById(
            @PathVariable Long id) {
        TNewsCorpus c = newsCorpusService.getById(id);
        return c == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.build(ResultCodeEnum.NOT_FOUND))
                : ResponseEntity.ok(Result.build(c));
    }
    /**
     * 新增新闻语料，测完了
     */
    @PostMapping("/addNews")
    @Operation(summary = "新增新闻语料", description = "添加一条包含国家、年份等属性的新闻语料")
    public ResponseEntity<Result<TNewsCorpus>> addNewsCorpus(
            @Parameter(description = "新闻语料实体，包含国家、年份、标题等信息")
            @Valid @RequestBody TNewsCorpus newsCorpus) { // 请求体校验
        try {
            boolean success = newsCorpusService.saveNewsCorpus(newsCorpus);
            if (success) {
                log.info("新增新闻语料成功，ID: {}", newsCorpus.getId());
                return ResponseEntity.ok(Result.build(newsCorpus));
            }
            log.warn("新增新闻语料失败，数据: {}", newsCorpus);
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL));
        } catch (IllegalArgumentException e) {
            log.warn("新增新闻参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("新增新闻语料异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 更新新闻语料，测完了
     */
    @PutMapping("/updateNews")
    @Operation(summary = "更新新闻语料")
    public ResponseEntity<Result<String>> updateNewsCorpus(
            @Valid @RequestBody TNewsCorpus newsCorpus) {
        boolean ok = newsCorpusService.updateNewsCorpus(newsCorpus);
        return ResponseEntity.ok(
                ok ? Result.build("更新成功")
                        : Result.build(ResultCodeEnum.FAIL));
    }

    /**
     * 根据ID删除新闻语料，测完了
     */
    @DeleteMapping("/deleteNews/{id}")
    @Operation(summary = "删除新闻语料", description = "根据ID删除指定新闻语料")
    public ResponseEntity<Result<String>> deleteNewsCorpus(
            @Parameter(description = "新闻语料ID", required = true)
            @PathVariable Long id) {
        try {
            boolean success = newsCorpusService.removeNewsCorpus(id);
            if (success) {
                log.info("删除新闻语料成功，ID: {}", id);
                return ResponseEntity.ok(Result.build("删除成功"));
            }
            log.warn("删除新闻语料失败，ID: {}", id);
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL));
        } catch (IllegalArgumentException e) {
            log.warn("删除新闻参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("删除新闻语料异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }




    /**
     * 按国家、年份和标题关键词分页搜索，直接用混合的
     */
    @GetMapping("/search")
    @Operation(summary = "关键词搜索", description = "按国家、年份和标题关键词分页查询新闻语料")
    public ResponseEntity<Result<IPage<TNewsCorpus>>> searchByCountryYearAndTitle(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "国家名称", required = true) @RequestParam String country,
            @Parameter(description = "年份", required = true) @RequestParam Integer year,
            @Parameter(description = "标题关键词", required = true) @RequestParam String keyword) {
        try {
            IPage<TNewsCorpus> page = newsCorpusService.searchByCountryYearAndTitle(
                    pageNum, pageSize, country, year, keyword);
            log.info("新闻语料搜索成功，国家: {}, 年份: {}, 关键词: {}", country, year, keyword);
            return ResponseEntity.ok(Result.build(page));
        } catch (IllegalArgumentException e) {
            log.warn("搜索新闻参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("搜索新闻语料异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }
    /**
     * 混合条件查询
     * 支持国家、年份、标题模糊、内容模糊的任意组合查询，，测完了，可以用
     */
    @GetMapping("/mix-query")
    @Operation(summary = "混合条件查询", description = "支持国家、年份、标题模糊、内容模糊的任意组合查询")
    public ResponseEntity<Result<List<TNewsCorpus>>> queryByMixConditions(
            @Parameter(description = "国家名称，如中国") @RequestParam(required = false) String country,
            @Parameter(description = "年份，如2023") @RequestParam(required = false) Integer year,
            @Parameter(description = "标题关键词（模糊匹配）") @RequestParam(required = false) String titleKeyword,
            @Parameter(description = "内容关键词（模糊匹配）") @RequestParam(required = false) String contentKeyword) {
        try {
            List<TNewsCorpus> list = newsCorpusService.queryByMixConditions(
                    country, year, titleKeyword, contentKeyword);
            log.info("新闻语料混合查询成功");
            return ResponseEntity.ok(Result.build(list));
        } catch (IllegalArgumentException e) {
            log.warn("混合查询参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("混合查询异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 混合条件分页查询
     */
    @GetMapping("/mix-query-page")
    @Operation(summary = "混合条件分页查询", description = "支持国家、年份、标题模糊、内容模糊的分页组合查询")
    public ResponseEntity<Result<IPage<TNewsCorpus>>> queryPageByMixConditions(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "国家名称，如中国") @RequestParam(required = false) String country,
            @Parameter(description = "年份，如2023") @RequestParam(required = false) Integer year,
            @Parameter(description = "标题关键词（模糊匹配）") @RequestParam(required = false) String titleKeyword,
            @Parameter(description = "内容关键词（模糊匹配）") @RequestParam(required = false) String contentKeyword) {
        try {
            IPage<TNewsCorpus> page = newsCorpusService.queryPageByMixConditions(
                    pageNum, pageSize, country, year, titleKeyword, contentKeyword);
            log.info("新闻语料混合分页查询成功，页码: {}, 条数: {}", pageNum, pageSize);
            return ResponseEntity.ok(Result.build(page));
        } catch (IllegalArgumentException e) {
            log.warn("混合分页查询参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("混合分页查询异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }
}