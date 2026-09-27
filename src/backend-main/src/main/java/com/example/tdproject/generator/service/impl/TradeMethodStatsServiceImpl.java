package com.example.tdproject.generator.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.TradeMethodStats;
import com.example.tdproject.generator.service.TradeMethodStatsService;
import com.example.tdproject.generator.mapper.TradeMethodStatsMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
* @author 21634
* @description 针对表【trade_method_stats(贸易方式统计数据表)】的数据库操作Service实现
* @createDate 2025-10-09 15:47:09
*/
@Service
public class TradeMethodStatsServiceImpl extends ServiceImpl<TradeMethodStatsMapper, TradeMethodStats>
    implements TradeMethodStatsService{

    @Override
    public boolean saveTradeMethodStats(TradeMethodStats stats) {
        Assert.notNull(stats, "统计数据不能为空");
        return save(stats);
    }

    @Override
    public boolean updateTradeMethodStats(TradeMethodStats stats) {
        Assert.notNull(stats, "统计数据不能为空");
        Assert.notNull(stats.getId(), "ID不能为空");
        return updateById(stats);
    }

    @Override
    public boolean removeTradeMethodStats(Long id) {
        Assert.notNull(id, "ID不能为空");
        return removeById(id);
    }

    @Override
    public List<TradeMethodStats> queryByConditions(String methodType, Integer year) {
        QueryWrapper<TradeMethodStats> queryWrapper = new QueryWrapper<>();
        if (methodType != null && !methodType.isEmpty()) {
            queryWrapper.eq("trade_method", methodType);
        }
        if (year != null) {
            queryWrapper.eq("stat_year", year);
        }
        return list(queryWrapper);
    }

    @Override
    public IPage<TradeMethodStats> queryPageByConditions(int pageNum, int pageSize, String methodType, Integer year) {
        Page<TradeMethodStats> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TradeMethodStats> queryWrapper = new QueryWrapper<>();
        if (methodType != null && !methodType.isEmpty()) {
            queryWrapper.eq("trade_method", methodType);
        }
        if (year != null) {
            queryWrapper.eq("stat_year", year);
        }
        return page(page, queryWrapper);
    }
    @Override
    public List<Map<String, Object>> getCountryTradeRatio(Integer year, Integer month) {
        // 先查询指定年月的数据
        List<Map<String, Object>> result = baseMapper.selectMaps(new QueryWrapper<TradeMethodStats>()
                .select("country_name", "SUM(trade_amount) as total_amount")
                .eq("stat_year", year)
                .eq("stat_month", month)
                .groupBy("country_name"));
        
        // 如果没有数据，查询最近一个有数据的年月
        if (result == null || result.isEmpty()) {
            Map<String, Object> latest = baseMapper.selectMaps(new QueryWrapper<TradeMethodStats>()
                    .select("stat_year", "stat_month")
                    .orderByDesc("stat_year", "stat_month")
                    .last("LIMIT 1")).stream().findFirst().orElse(null);
            
            if (latest != null) {
                Integer latestYear = Integer.valueOf(latest.get("stat_year").toString());
                Integer latestMonth = Integer.valueOf(latest.get("stat_month").toString());
                result = baseMapper.selectMaps(new QueryWrapper<TradeMethodStats>()
                        .select("country_name", "SUM(trade_amount) as total_amount")
                        .eq("stat_year", latestYear)
                        .eq("stat_month", latestMonth)
                        .groupBy("country_name"));
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getTradeMethodRatio(Integer year, Integer month) {
        // 先查询指定年月的数据
        List<Map<String, Object>> result = baseMapper.selectMaps(new QueryWrapper<TradeMethodStats>()
                .select("trade_method", "SUM(trade_amount) as total_amount")
                .eq("stat_year", year)
                .eq("stat_month", month)
                .groupBy("trade_method"));
        
        // 如果没有数据，查询最近一个有数据的年月
        if (result == null || result.isEmpty()) {
            Map<String, Object> latest = baseMapper.selectMaps(new QueryWrapper<TradeMethodStats>()
                    .select("stat_year", "stat_month")
                    .orderByDesc("stat_year", "stat_month")
                    .last("LIMIT 1")).stream().findFirst().orElse(null);
            
            if (latest != null) {
                Integer latestYear = Integer.valueOf(latest.get("stat_year").toString());
                Integer latestMonth = Integer.valueOf(latest.get("stat_month").toString());
                result = baseMapper.selectMaps(new QueryWrapper<TradeMethodStats>()
                        .select("trade_method", "SUM(trade_amount) as total_amount")
                        .eq("stat_year", latestYear)
                        .eq("stat_month", latestMonth)
                        .groupBy("trade_method"));
            }
        }
        return result;
    }
    @Override
    public List<Map<String, Object>> getTradeMethodRatio(Integer year, Integer month, String country) {
        // 1. 查询符合条件的贸易方式金额数据
        QueryWrapper<TradeMethodStats> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("trade_method", "SUM(trade_amount) as total_amount")
                .eq("stat_year", year)
                .eq("stat_month", month);

        // 按国家筛选（如果有）
        if (StringUtils.hasText(country)) {
            queryWrapper.eq("country_name", country);
        }
        queryWrapper.groupBy("trade_method");

        List<Map<String, Object>> methodAmounts = baseMapper.selectMaps(queryWrapper);

        // 2. 计算总金额
        BigDecimal total = methodAmounts.stream()
                .map(map -> new BigDecimal(map.get("total_amount").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 计算占比并替换为百分比数值
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            methodAmounts.forEach(map -> {
                BigDecimal amount = new BigDecimal(map.get("total_amount").toString());
                // 计算百分比（保留两位小数）
                BigDecimal ratio = amount.divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                map.put("ratio", ratio.setScale(2, RoundingMode.HALF_UP));
                // 保留原始金额，便于前端展示
                map.put("total_amount", amount);
            });
        }

        return methodAmounts;
    }
}




