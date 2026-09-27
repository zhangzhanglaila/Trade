package com.example.tdproject.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.generator.domain.ComprehensiveData;
import com.example.tdproject.generator.domain.CountryMonthlyTrade;
import com.example.tdproject.generator.domain.CorpusEntries;
import com.example.tdproject.generator.domain.TradeMethodStats;
import com.example.tdproject.generator.service.ComprehensiveDataService;
import com.example.tdproject.generator.service.CountryMonthlyTradeService;
import com.example.tdproject.generator.service.CorpusEntriesService;
import com.example.tdproject.generator.service.TradeMethodStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据看板及CRUD控制器
 */
@RestController
@RequestMapping("/api")
@Tag(name = "数据管理", description = "包含CRUD操作及看板统计接口")
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final ComprehensiveDataService comprehensiveDataService;
    private final CountryMonthlyTradeService countryMonthlyTradeService;
    private final TradeMethodStatsService tradeMethodStatsService;
    private final CorpusEntriesService corpusEntriesService;

    // -------------------------- 综合数据CRUD --------------------------
    @PostMapping("/comprehensive")
    @Operation(summary = "新增综合数据")
    public ResponseEntity<Boolean> saveComprehensive(@RequestBody ComprehensiveData data) {
        return ResponseEntity.ok(comprehensiveDataService.saveComprehensiveData(data));
    }

    @PutMapping("/comprehensive")
    @Operation(summary = "更新综合数据")
    public ResponseEntity<Boolean> updateComprehensive(@RequestBody ComprehensiveData data) {
        return ResponseEntity.ok(comprehensiveDataService.updateComprehensiveData(data));
    }

    @DeleteMapping("/comprehensive/{id}")
    @Operation(summary = "删除综合数据")
    public ResponseEntity<Boolean> deleteComprehensive(@PathVariable Long id) {
        return ResponseEntity.ok(comprehensiveDataService.removeComprehensiveData(id));
    }

    @GetMapping("/comprehensive/page")
    @Operation(summary = "分页查询综合数据")
    public ResponseEntity<IPage<ComprehensiveData>> getComprehensivePage(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @Parameter(required = false) String country,
            @Parameter(required = false) Integer year) {
        return ResponseEntity.ok(comprehensiveDataService.queryPageByConditions(pageNum, pageSize, country, year));
    }

    // -------------------------- 国家月度贸易CRUD --------------------------
    @PostMapping("/country-trade")
    @Operation(summary = "新增国家月度贸易数据")
    public ResponseEntity<Boolean> saveCountryTrade(@RequestBody CountryMonthlyTrade trade) {
        return ResponseEntity.ok(countryMonthlyTradeService.saveCountryMonthlyTrade(trade));
    }

    @PutMapping("/country-trade")
    @Operation(summary = "更新国家月度贸易数据")
    public ResponseEntity<Boolean> updateCountryTrade(@RequestBody CountryMonthlyTrade trade) {
        return ResponseEntity.ok(countryMonthlyTradeService.updateCountryMonthlyTrade(trade));
    }

    @DeleteMapping("/country-trade/{id}")
    @Operation(summary = "删除国家月度贸易数据")
    public ResponseEntity<Boolean> deleteCountryTrade(@PathVariable Long id) {
        return ResponseEntity.ok(countryMonthlyTradeService.removeCountryMonthlyTrade(id));
    }

    @GetMapping("/country-trade/page")
    @Operation(summary = "分页查询国家月度贸易数据")
    public ResponseEntity<IPage<CountryMonthlyTrade>> getCountryTradePage(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @Parameter(required = false) String country,
            @Parameter(required = false) Integer yearMonth) {
        return ResponseEntity.ok(countryMonthlyTradeService.queryPageByConditions(pageNum, pageSize, country, yearMonth));
    }

    // -------------------------- 贸易方式统计CRUD --------------------------
    @PostMapping("/trade-method")
    @Operation(summary = "新增贸易方式统计数据")
    public ResponseEntity<Boolean> saveTradeMethod(@RequestBody TradeMethodStats stats) {
        return ResponseEntity.ok(tradeMethodStatsService.saveTradeMethodStats(stats));
    }

    @PutMapping("/trade-method")
    @Operation(summary = "更新贸易方式统计数据")
    public ResponseEntity<Boolean> updateTradeMethod(@RequestBody TradeMethodStats stats) {
        return ResponseEntity.ok(tradeMethodStatsService.updateTradeMethodStats(stats));
    }

    @DeleteMapping("/trade-method/{id}")
    @Operation(summary = "删除贸易方式统计数据")
    public ResponseEntity<Boolean> deleteTradeMethod(@PathVariable Long id) {
        return ResponseEntity.ok(tradeMethodStatsService.removeTradeMethodStats(id));
    }

    @GetMapping("/trade-method/page")
    @Operation(summary = "分页查询贸易方式统计数据")
    public ResponseEntity<IPage<TradeMethodStats>> getTradeMethodPage(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @Parameter(required = false) String methodType,
            @Parameter(required = false) Integer year) {
        return ResponseEntity.ok(tradeMethodStatsService.queryPageByConditions(pageNum, pageSize, methodType, year));
    }

    // -------------------------- 语料条目CRUD --------------------------
    @PostMapping("/corpus")
    @Operation(summary = "新增语料条目数据")
    public ResponseEntity<Boolean> saveCorpus(@RequestBody CorpusEntries entries) {
        return ResponseEntity.ok(corpusEntriesService.saveCorpusEntries(entries));
    }

    @PutMapping("/corpus")
    @Operation(summary = "更新语料条目数据")
    public ResponseEntity<Boolean> updateCorpus(@RequestBody CorpusEntries entries) {
        return ResponseEntity.ok(corpusEntriesService.updateCorpusEntries(entries));
    }

    @DeleteMapping("/corpus/{id}")
    @Operation(summary = "删除语料条目数据")
    public ResponseEntity<Boolean> deleteCorpus(@PathVariable Long id) {
        return ResponseEntity.ok(corpusEntriesService.removeCorpusEntries(id));
    }

    @GetMapping("/corpus/page")
    @Operation(summary = "分页查询语料条目数据")
    public ResponseEntity<IPage<CorpusEntries>> getCorpusPage(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @Parameter(required = false) String corpusType) {
        return ResponseEntity.ok(corpusEntriesService.queryPageByType(pageNum, pageSize, corpusType));
    }

    // -------------------------- 看板统计接口 --------------------------
    @GetMapping("/dashboard/comprehensive")
    @Operation(summary = "获取当前综合统计数据")
    public ResponseEntity<ComprehensiveData> getCurrentComprehensive() {
        LocalDate now = LocalDate.now();
        return ResponseEntity.ok(comprehensiveDataService.getLatestByYearMonth(now.getYear(), now.getMonthValue()));
    }

    @GetMapping("/dashboard/country-ratio")
    @Operation(summary = "获取国家贸易金额占比")
    public ResponseEntity<List<Map<String, Object>>> getCountryRatio(
            @Parameter(required = false) Integer year,
            @Parameter(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return ResponseEntity.ok(tradeMethodStatsService.getCountryTradeRatio(y, m));
    }

    @GetMapping("/dashboard/method-ratio")
    @Operation(summary = "获取贸易方式占比")
    public ResponseEntity<List<Map<String, Object>>> getMethodRatio(
            @Parameter(required = false) Integer year,
            @Parameter(required = false) Integer month,
            @Parameter(required = false, description = "国家名称，为空则查询所有国家")
            @RequestParam(required = false) String country) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return ResponseEntity.ok(tradeMethodStatsService.getTradeMethodRatio(y, m, country));
    }


    @GetMapping("/dashboard/last12months")
    @Operation(summary = "获取近12个月贸易数据（支持按国家筛选，默认返回所有国家汇总）")
    public ResponseEntity<List<CountryMonthlyTrade>> getLast12Months(
            @Parameter(required = false, description = "国家名称，为空则返回所有国家汇总数据")
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(countryMonthlyTradeService.getLast12MonthsData(country));
    }

    @GetMapping("/dashboard/corpus-yearly")
    @Operation(summary = "获取历年语料统计")
    public ResponseEntity<List<CorpusEntries>> getYearlyCorpus() {
        return ResponseEntity.ok(corpusEntriesService.getAllEntries());
    }
}