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
        // 时间基准改为「数据中实际最新的年月」，而不是 LocalDate.now()。
        //
        // 为什么：库里的贸易数据是一份历史快照（实测最新到 2025-03），而服务器当前
        // 系统时间为 2026-09。若按系统时钟推导窗口（2025-10 ~ 2026-09），窗口内
        // 一条数据都没有，接口恒返回空数组 —— 前端「近 12 个月」曲线因此全空。
        // 改为以数据最新月份回溯 11 个月，含义变为「数据最新的 12 个月」。
        // 这不伪造任何数据，只是把基准从「机器时钟」换成「数据自身的时间」。
        LocalDate anchor = resolveDataAnchorMonth();
        LocalDate from = anchor.minusMonths(11);
        int fromKey = from.getYear() * 100 + from.getMonthValue();
        int toKey = anchor.getYear() * 100 + anchor.getMonthValue();

        // 2. 构建查询条件（先用年份收窄，月份边界在 Java 侧精确过滤）
        QueryWrapper<CountryMonthlyTrade> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("year", from.getYear())
                .le("year", anchor.getYear());

        // 3. 按国家筛选（如果有）
        if (StringUtils.hasText(country)) {
            queryWrapper.eq("country", country);
        }

        // 4. 按年月排序
        queryWrapper.orderByAsc("year", "month");

        // 5. 查询原始数据
        List<CountryMonthlyTrade> originalData = list(queryWrapper);

        // 5b. 月份边界精确过滤（本表仅数百行，Java 侧过滤开销可忽略）
        originalData.removeIf(t -> {
            if (t.getYear() == null || t.getMonth() == null) {
                return true;
            }
            int key = t.getYear() * 100 + t.getMonth();
            return key < fromKey || key > toKey;
        });

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

    /**
     * 取「数据中实际最新的年月」作为时间基准（当月 1 号）。
     *
     * <p>库里这份贸易数据是历史快照（实测最新到 2025-03），而机器时钟是 2026-09。
     * 用机器时钟当基准会让「近 12 个月」永远落在没有数据的区间。表为空或字段
     * 异常时退回系统当前月，保证接口不会因此抛异常。
     */
    private LocalDate resolveDataAnchorMonth() {
        CountryMonthlyTrade latest = getOne(new QueryWrapper<CountryMonthlyTrade>()
                .select("year", "month")
                .orderByDesc("year", "month")
                .last("LIMIT 1"));
        if (latest != null && latest.getYear() != null && latest.getMonth() != null
                && latest.getMonth() >= 1 && latest.getMonth() <= 12) {
            return LocalDate.of(latest.getYear(), latest.getMonth(), 1);
        }
        return LocalDate.now().withDayOfMonth(1);
    }
}




