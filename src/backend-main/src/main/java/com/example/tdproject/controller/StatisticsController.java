package com.example.tdproject.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.tdproject.generator.domain.*;
import com.example.tdproject.generator.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计控制器 - 为大屏提供各模块统计数据
 */
@RestController
@RequestMapping("/api/statistics")
@Tag(name = "数据统计", description = "各模块统计数据汇总")
@Slf4j
@RequiredArgsConstructor
public class StatisticsController {

    private final ComprehensiveDataService comprehensiveDataService;
    private final CountryMonthlyTradeService countryMonthlyTradeService;
    private final TradeMethodStatsService tradeMethodStatsService;
    private final CorpusEntriesService corpusEntriesService;
    // 注意：以下服务需要根据实际情况注入
    // private final TNewsCorpusService newsCorpusService;
    // private final OntologyService ontologyService;
    // private final UserService userService;

    /**
     * 获取大屏所有统计数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取大屏所有统计数据")
    public ResponseEntity<Map<String, Object>> getDashboardStatistics() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 核心指标
            result.put("coreStats", getCoreStats());
            
            // 2. 贸易数据统计
            result.put("tradeStats", getTradeStats());
            
            // 3. 语料统计
            result.put("corpusStats", getCorpusStats());
            
            // 4. 本体统计（简化版）
            result.put("ontologyStats", getOntologyStats());
            
            // 5. 采集统计（简化版）
            result.put("collectionStats", getCollectionStats());
            
            // 6. 系统统计（简化版）
            result.put("systemStats", getSystemStats());
            
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 核心指标
     */
    private Map<String, Object> getCoreStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 查询最新的综合数据
        ComprehensiveData latest = comprehensiveDataService.getLatestByYearMonth(
            LocalDate.now().getYear(), 
            LocalDate.now().getMonthValue()
        );
        
        if (latest == null) {
            // 如果没有当月数据，查询最新的一条
            latest = comprehensiveDataService.getOne(
                new QueryWrapper<ComprehensiveData>()
                    .orderByDesc("update_time")
                    .last("LIMIT 1")
            );
        }
        
        if (latest != null) {
            stats.put("corpusEntryCount", latest.getCorpusEntryCount());
            stats.put("dataEntryCount", latest.getDataEntryCount());
            stats.put("queryVisitCount", latest.getQueryVisitCount());
            stats.put("tradeCountryCount", latest.getTradeCountryCount());
        } else {
            stats.put("corpusEntryCount", 0);
            stats.put("dataEntryCount", 0);
            stats.put("queryVisitCount", 0);
            stats.put("tradeCountryCount", 0);
        }
        
        return stats;
    }

    /**
     * 贸易数据统计
     */
    private Map<String, Object> getTradeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 总记录数
        long totalRecords = countryMonthlyTradeService.count();
        stats.put("totalRecords", totalRecords);
        
        // 进口记录数（模拟：假设60%是进口）
        stats.put("importRecords", (long)(totalRecords * 0.6));
        
        // 出口记录数（模拟：假设40%是出口）
        stats.put("exportRecords", (long)(totalRecords * 0.4));
        
        // 覆盖国家数
        int countryCount = countryMonthlyTradeService.list(
            new QueryWrapper<CountryMonthlyTrade>()
                .select("country")
                .groupBy("country")
        ).size();
        stats.put("countryCount", countryCount);
        
        // 商品种类数（从 trade_method_stats 统计）
        int commodityCount = tradeMethodStatsService.list(
            new QueryWrapper<TradeMethodStats>()
                .select("trade_method")
                .groupBy("trade_method")
        ).size();
        stats.put("commodityCount", commodityCount);
        
        // 本月新增
        LocalDate now = LocalDate.now();
        long thisMonthCount = countryMonthlyTradeService.count(
            new QueryWrapper<CountryMonthlyTrade>()
                .eq("year", now.getYear())
                .eq("month", now.getMonthValue())
        );
        stats.put("thisMonthNew", thisMonthCount);
        
        return stats;
    }

    /**
     * 语料统计
     */
    private Map<String, Object> getCorpusStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 语料总数
        List<CorpusEntries> allCorpus = corpusEntriesService.list();
        int totalCorpus = allCorpus.stream()
            .mapToInt(CorpusEntries::getEntryCount)
            .sum();
        stats.put("totalCorpus", totalCorpus);
        
        // 今年新增
        int currentYear = LocalDate.now().getYear();
        int thisYearCount = allCorpus.stream()
            .filter(c -> c.getYear() != null && c.getYear() == currentYear)
            .mapToInt(CorpusEntries::getEntryCount)
            .sum();
        stats.put("thisYearNew", thisYearCount);
        
        // 覆盖国家数（模拟）
        stats.put("countryCount", 5);
        
        // 平均字数（模拟）
        stats.put("avgWords", 1250);
        
        // 数据来源数（模拟）
        stats.put("sourceCount", 12);
        
        // 待处理数（模拟）
        stats.put("pendingCount", 23);
        
        return stats;
    }

    /**
     * 本体统计（简化版）
     */
    private Map<String, Object> getOntologyStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 从数据库查询本体项目数
        // 这里简化处理，实际应该从 ontologyService 查询
        stats.put("projectCount", 8);
        stats.put("classCount", 45);
        stats.put("propertyCount", 128);
        stats.put("individualCount", 2340);
        stats.put("relationCount", 89);
        stats.put("lastUpdateDays", 3);
        
        return stats;
    }

    /**
     * 采集统计（简化版）
     */
    private Map<String, Object> getCollectionStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("taskCount", 5);
        stats.put("totalCollected", 12450);
        stats.put("successRate", "94.5%");
        stats.put("todayCollected", 156);
        stats.put("activeSource", 8);
        stats.put("abnormalTask", 1);
        
        return stats;
    }

    /**
     * 系统统计（简化版）
     */
    private Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalUser", 25);
        stats.put("activeUser", 18);
        stats.put("totalQA", 3927);
        stats.put("lastUpdateHours", 2);
        stats.put("runningDays", 120);
        stats.put("todayApiCalls", 1250);
        
        return stats;
    }
}
