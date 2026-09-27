package com.example.tdproject.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.utils.Result;
import com.example.tdproject.utils.ResultCodeEnum;
import com.example.tdproject.generator.domain.TLogisticsInfo;
import com.example.tdproject.generator.service.TLogisticsInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物流信息控制器
 * 提供物流信息的CRUD及条件查询接口
 */
@RestController
@RequestMapping("/logistics")
@Tag(name = "物流信息管理", description = "物流信息相关接口")
@Slf4j
@RequiredArgsConstructor
@Validated
public class LogisticsInfoController {

    private final TLogisticsInfoService logisticsInfoService;

    /**
     * 新增物流信息，测试完成
     */
    @PostMapping("/addMessage")
    @Operation(summary = "新增物流信息", description = "添加一条包含国家、进出口类型等属性的物流信息")
    public ResponseEntity<Result<TLogisticsInfo>> addLogisticsInfo(
            @Parameter(description = "物流信息实体")
            @Valid @RequestBody TLogisticsInfo logisticsInfo) {
        try {
            boolean success = logisticsInfoService.saveLogisticsInfo(logisticsInfo);
            if (success) {
                log.info("新增物流信息成功，ID: {}", logisticsInfo.getId());
                return ResponseEntity.ok(Result.build(logisticsInfo));
            }
            log.warn("新增物流信息失败，数据: {}", logisticsInfo);
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL));
        } catch (IllegalArgumentException e) {
            log.warn("新增物流参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("新增物流信息异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 更新物流信息，测试完成
     */
    @PutMapping("/updateMessage")
    @Operation(summary = "更新物流信息", description = "根据ID更新物流信息记录")
    public ResponseEntity<Result<String>> updateLogisticsInfo(
            @Parameter(description = "物流信息实体，必须包含ID")
            @Valid @RequestBody TLogisticsInfo logisticsInfo) {
        try {
            boolean success = logisticsInfoService.updateLogisticsInfo(logisticsInfo);
            if (success) {
                log.info("更新物流信息成功，ID: {}", logisticsInfo.getId());
                return ResponseEntity.ok(Result.build("更新成功"));
            }
            log.warn("更新物流信息失败，ID: {}", logisticsInfo.getId());
            return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                    .body(Result.build(ResultCodeEnum.FAIL));
        } catch (IllegalArgumentException e) {
            log.warn("更新物流参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("更新物流信息异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 根据ID删除物流信息，测试完成

     */
    @DeleteMapping("/deleteMessage/{id}")
    @Operation(summary = "删除物流信息", description = "根据ID删除指定物流信息")
    public ResponseEntity<Result<String>> deleteLogisticsInfo(
            @Parameter(description = "物流信息ID", required = true)
            @PathVariable Long id) {

        boolean success = logisticsInfoService.removeLogisticsInfo(id);
        if (success) {
            log.info("删除物流信息成功，ID: {}", id);
            return ResponseEntity.ok(Result.build("删除成功"));
        }

        log.warn("删除物流信息失败，ID: {}", id);
        return ResponseEntity.status(ResultCodeEnum.FAIL.getCode())
                .body(Result.build(ResultCodeEnum.FAIL));
    }
    /**
     * 多条件组合查询
     * 支持按数据年月、贸易伙伴编码、贸易伙伴名称、商品编码、进出口类型、国家进行组合查询，没测，可以不用
     */
    @GetMapping("/query")
    @Operation(summary = "多条件查询", description = "支持按数据年月、贸易伙伴编码、贸易伙伴名称、商品编码、进出口类型、国家进行组合查询")
    public ResponseEntity<Result<List<TLogisticsInfo>>> queryByConditions(
            @Parameter(description = "数据年月（YYYYMM，如202501）") @RequestParam(required = false) Integer dataYearMonth,
            @Parameter(description = "贸易伙伴编码") @RequestParam(required = false) Integer tradePartnerCode,
            @Parameter(description = "贸易伙伴名称") @RequestParam(required = false) String tradePartnerName,
            @Parameter(description = "商品编码") @RequestParam(required = false) Integer commodityCode,
            @Parameter(description = "进出口类型（0-进口，1-出口）") @RequestParam(required = false) Integer importExportType,
            @Parameter(description = "国家名称") @RequestParam(required = false) String country) {
        try {
            List<TLogisticsInfo> list = logisticsInfoService.queryByConditions(
                    dataYearMonth, tradePartnerCode, tradePartnerName,
                    commodityCode, importExportType, country);
            log.info("多条件查询成功");
            return ResponseEntity.ok(Result.build(list));
        } catch (IllegalArgumentException e) {
            log.warn("查询参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("多条件查询异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }

    /**
     * 多条件分页查询
     * 支持按数据年月、贸易伙伴编码、贸易伙伴名称、商品编码、进出口类型、国家进行分页组合查询，测试完成
     */
    @GetMapping("/queryPage")
    @Operation(summary = "多条件分页查询", description = "支持按数据年月、贸易伙伴编码、贸易伙伴名称、商品编码、进出口类型、国家进行分页组合查询")
    public ResponseEntity<Result<IPage<TLogisticsInfo>>> queryPageByConditions(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "数据年月（YYYYMM，如202501）") @RequestParam(required = false) Integer dataYearMonth,
            @Parameter(description = "贸易伙伴编码") @RequestParam(required = false) Integer tradePartnerCode,
            @Parameter(description = "贸易伙伴名称") @RequestParam(required = false) String tradePartnerName,
            @Parameter(description = "商品编码") @RequestParam(required = false) Integer commodityCode,
            @Parameter(description = "进出口类型（0-进口，1-出口）") @RequestParam(required = false) Integer importExportType,
            @Parameter(description = "国家名称") @RequestParam(required = false) String country) {
        try {
            IPage<TLogisticsInfo> page = logisticsInfoService.queryPageByConditions(
                    pageNum, pageSize, dataYearMonth, tradePartnerCode,
                    tradePartnerName, commodityCode, importExportType, country);
            log.info("多条件分页查询成功，页码: {}, 条数: {}", pageNum, pageSize);
            return ResponseEntity.ok(Result.build(page));
        } catch (IllegalArgumentException e) {
            log.warn("分页查询参数错误: {}", e.getMessage(), e);
            return ResponseEntity.status(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.ARGUMENT_VALID_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("分页查询异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }


    /**
     * 分页查询（保留原接口），测试完成
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "按国家和商品名称分页筛选物流信息")
    public ResponseEntity<Result<IPage<TLogisticsInfo>>> getPage(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数，默认10") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "国家名称，可选") @RequestParam(required = false) String country,
            @Parameter(description = "商品名称，可选") @RequestParam(required = false) String commodityName) {
        try {
            IPage<TLogisticsInfo> page = logisticsInfoService.getPage(pageNum, pageSize, country, commodityName);
            log.info("分页查询物流信息成功，页码: {}, 条数: {}", pageNum, pageSize);
            return ResponseEntity.ok(Result.build(page));
        } catch (Exception e) {
            log.error("分页查询物流信息异常", e);
            return ResponseEntity.status(ResultCodeEnum.SERVICE_ERROR.getCode())
                    .body(Result.build(ResultCodeEnum.SERVICE_ERROR));
        }
    }
}