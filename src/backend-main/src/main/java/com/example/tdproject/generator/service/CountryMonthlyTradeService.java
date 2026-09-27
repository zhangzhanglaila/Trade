package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.generator.domain.CountryMonthlyTrade;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 21634
* @description 针对表【country_monthly_trade(国家月度贸易数据表)】的数据库操作Service
* @createDate 2025-10-09 15:47:09
*/
public interface CountryMonthlyTradeService extends IService<CountryMonthlyTrade> {
    boolean saveCountryMonthlyTrade(CountryMonthlyTrade trade);
    boolean updateCountryMonthlyTrade(CountryMonthlyTrade trade);
    boolean removeCountryMonthlyTrade(Long id);
    List<CountryMonthlyTrade> getLast12MonthsData();
    // 新增支持国家筛选的方法
    List<CountryMonthlyTrade> getLast12MonthsData(String country);
    List<CountryMonthlyTrade> queryByConditions(String country, Integer yearMonth);
    IPage<CountryMonthlyTrade> queryPageByConditions(int pageNum, int pageSize, String country, Integer yearMonth);

}
