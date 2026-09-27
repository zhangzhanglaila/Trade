package com.example.tdproject.generator.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.CountryMonthlyTrade;
import com.example.tdproject.generator.service.CountryMonthlyTradeService;
import com.example.tdproject.generator.mapper.CountryMonthlyTradeMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
* @author 21634
* @description 针对表【country_monthly_trade(国家月度贸易数据表)】的数据库操作Service实现
* @createDate 2025-10-09 15:47:09
*/
@Service
public class CountryMonthlyTradeServiceImpl extends ServiceImpl<CountryMonthlyTradeMapper, CountryMonthlyTrade>
    implements CountryMonthlyTradeService{

    @Override
    public boolean saveCountryMonthlyTrade(CountryMonthlyTrade trade) {
        Assert.notNull(trade, "贸易数据不能为空");
        return save(trade);
    }

    @Override
    public boolean updateCountryMonthlyTrade(CountryMonthlyTrade trade) {
        Assert.notNull(trade, "贸易数据不能为空");
        Assert.notNull(trade.getId(), "ID不能为空");
        return updateById(trade);
    }

    @Override
    public boolean removeCountryMonthlyTrade(Long id) {
        Assert.notNull(id, "ID不能为空");
        return removeById(id);
    }

    @Override
    public List<CountryMonthlyTrade> queryByConditions(String country, Integer yearMonth) {
        QueryWrapper<CountryMonthlyTrade> queryWrapper = new QueryWrapper<>();
        if (country != null && !country.isEmpty()) {
            queryWrapper.eq("country", country);
        }
        if (yearMonth != null) {
            String yearMonthText = String.valueOf(yearMonth);
            if (yearMonthText.length() == 6) {
                queryWrapper.eq("year", Integer.parseInt(yearMonthText.substring(0, 4)))
                        .eq("month", Integer.parseInt(yearMonthText.substring(4, 6)));
            }
        }
        queryWrapper.orderByDesc("year", "month");
        return list(queryWrapper);
    }

    @Override
    public IPage<CountryMonthlyTrade> queryPageByConditions(int pageNum, int pageSize, String country, Integer yearMonth) {
        Page<CountryMonthlyTrade> page = new Page<>(pageNum, pageSize);
        QueryWrapper<CountryMonthlyTrade> queryWrapper = new QueryWrapper<>();
        if (country != null && !country.isEmpty()) {
            queryWrapper.eq("country", country);
        }
        if (yearMonth != null) {
            String yearMonthText = String.valueOf(yearMonth);
            if (yearMonthText.length() == 6) {
                queryWrapper.eq("year", Integer.parseInt(yearMonthText.substring(0, 4)))
                        .eq("month", Integer.parseInt(yearMonthText.substring(4, 6)));
            }
        }
        queryWrapper.orderByDesc("year", "month");
        return page(page, queryWrapper);
    }
    // CountryMonthlyTradeServiceImpl.java
    @Override
    public List<CountryMonthlyTrade> getLast12MonthsData() {
        // 无参调用，默认查询所有国家并汇总
        return getLast12MonthsData(null);
    }

    @Override
    public List<CountryMonthlyTrade> getLast12MonthsData(String country) {
        // 1. 计算时间范围（近12个月）
        LocalDate now = LocalDate.now();
        LocalDate twelveMonthsAgo = now.minusMonths(11); // 包含当前月，共12个月

        // 2. 构建查询条件
        QueryWrapper<CountryMonthlyTrade> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("year", twelveMonthsAgo.getYear())
                .and(qw -> qw.ge("month", twelveMonthsAgo.getMonthValue())
                        .or().gt("year", twelveMonthsAgo.getYear()))
                .le("year", now.getYear())
                .and(qw -> qw.le("month", now.getMonthValue())
                        .or().lt("year", now.getYear()));

        // 3. 按国家筛选（如果有）
        if (StringUtils.hasText(country)) {
            queryWrapper.eq("country", country);
        }

        // 4. 按年月排序
        queryWrapper.orderByAsc("year", "month");

        // 5. 查询原始数据
        List<CountryMonthlyTrade> originalData = list(queryWrapper);

        // 6. 按年月汇总数据（同一月份不同国家的数据求和）
        Map<String, CountryMonthlyTrade> aggregatedData = new TreeMap<>();

        for (CountryMonthlyTrade trade : originalData) {
            String key = trade.getYear() + "-" + String.format("%02d", trade.getMonth());

            if (aggregatedData.containsKey(key)) {
                // 已存在该月份数据，进行累加
                CountryMonthlyTrade existing = aggregatedData.get(key);
                existing.setExportQuantity(existing.getExportQuantity().add(trade.getExportQuantity()));
                existing.setExportAmount(existing.getExportAmount().add(trade.getExportAmount()));
                existing.setImportQuantity(existing.getImportQuantity().add(trade.getImportQuantity()));
                existing.setImportAmount(existing.getImportAmount().add(trade.getImportAmount()));
                existing.setTotalQuantity(existing.getTotalQuantity().add(trade.getTotalQuantity()));
                existing.setTotalAmount(existing.getTotalAmount().add(trade.getTotalAmount()));
            } else {
                // 新月份，直接放入（复制对象避免修改原始数据）
                CountryMonthlyTrade newTrade = new CountryMonthlyTrade();
                BeanUtils.copyProperties(trade, newTrade);
                // 若筛选了国家则保留国家名称，否则置空表示汇总数据
                if (!StringUtils.hasText(country)) {
                    newTrade.setCountry(null);
                }
                aggregatedData.put(key, newTrade);
            }
        }

        // 7. 转换为列表返回
        return new ArrayList<>(aggregatedData.values());
    }
}




