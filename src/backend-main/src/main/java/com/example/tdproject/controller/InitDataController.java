package com.example.tdproject.controller;

import com.example.tdproject.generator.domain.*;
import com.example.tdproject.generator.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * 初始化测试数据控制器
 */
@RestController
@RequestMapping("/api/init")
@Tag(name = "数据初始化", description = "初始化测试数据")
@RequiredArgsConstructor
public class InitDataController {

    private final ComprehensiveDataService comprehensiveDataService;
    private final CountryMonthlyTradeService countryMonthlyTradeService;
    private final TradeMethodStatsService tradeMethodStatsService;
    private final CorpusEntriesService corpusEntriesService;

    @PostMapping("/all")
    @Operation(summary = "初始化所有测试数据")
    public ResponseEntity<String> initAllData() {
        try {
            initComprehensiveData();
            initCountryMonthlyTrade();
            initTradeMethodStats();
            initCorpusEntries();
            return ResponseEntity.ok("测试数据初始化成功！");
        } catch (Exception e) {
            return ResponseEntity.ok("初始化失败: " + e.getMessage());
        }
    }

    private void initComprehensiveData() {
        // 添加 2026年3月 的综合数据
        ComprehensiveData data = new ComprehensiveData();
        data.setCorpusEntryCount(1204);
        data.setDataEntryCount(8631);
        data.setQueryVisitCount(3927);
        data.setTradeCountryCount(45);
        data.setStatYear(2026);
        data.setStatMonth(3);
        data.setUpdateTime(new Date());
        comprehensiveDataService.saveComprehensiveData(data);
    }

    private void initCountryMonthlyTrade() {
        List<CountryMonthlyTrade> list = new ArrayList<>();
        String[] countries = {"哈萨克斯坦", "乌兹别克斯坦", "吉尔吉斯斯坦", "土库曼斯坦", "塔吉克斯坦"};
        
        // 添加近12个月的数据（2025年4月 - 2026年3月）
        int year = 2025;
        int month = 4;
        
        for (int i = 0; i < 12; i++) {
            for (String country : countries) {
                CountryMonthlyTrade trade = new CountryMonthlyTrade();
                trade.setCountry(country);
                trade.setYear(year);
                trade.setMonth(month);
                trade.setExportQuantity(new BigDecimal(80 + (int)(Math.random() * 50)));
                trade.setExportAmount(new BigDecimal(1600 + (int)(Math.random() * 1000)));
                trade.setImportQuantity(new BigDecimal(120 + (int)(Math.random() * 50)));
                trade.setImportAmount(new BigDecimal(2400 + (int)(Math.random() * 1000)));
                trade.setTotalQuantity(trade.getExportQuantity().add(trade.getImportQuantity()));
                trade.setTotalAmount(trade.getExportAmount().add(trade.getImportAmount()));
                list.add(trade);
            }
            
            month++;
            if (month > 12) {
                month = 1;
                year = 2026;
            }
        }
        
        for (CountryMonthlyTrade trade : list) {
            countryMonthlyTradeService.saveCountryMonthlyTrade(trade);
        }
    }

    private void initTradeMethodStats() {
        String[] methods = {"一般贸易", "边境贸易", "加工贸易", "其他贸易"};
        String[] countries = {"哈萨克斯坦", "乌兹别克斯坦", "吉尔吉斯斯坦", "土库曼斯坦", "塔吉克斯坦"};
        
        // 添加 2026年3月 的数据
        for (String country : countries) {
            for (String method : methods) {
                TradeMethodStats stats = new TradeMethodStats();
                stats.setCountryName(country);
                stats.setTradeMethod(method);
                stats.setTradeAmount(new BigDecimal(50000 + (int)(Math.random() * 100000)));
                stats.setStatYear(2026);
                stats.setStatMonth(3);
                tradeMethodStatsService.saveTradeMethodStats(stats);
            }
        }
    }

    private void initCorpusEntries() {
        // 添加历年语料数据
        for (int year = 2020; year <= 2026; year++) {
            CorpusEntries entry = new CorpusEntries();
            entry.setYear(year);
            entry.setEntryCount(100000 + (year - 2020) * 50000 + (int)(Math.random() * 20000));
            entry.setUpdateTime(new Date());
            corpusEntriesService.saveCorpusEntries(entry);
        }
    }
}
