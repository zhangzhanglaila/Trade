package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.generator.domain.TradeMethodStats;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
* @author 21634
* @description 针对表【trade_method_stats(贸易方式统计数据表)】的数据库操作Service
* @createDate 2025-10-09 15:47:09
*/
public interface TradeMethodStatsService extends IService<TradeMethodStats> {
    boolean saveTradeMethodStats(TradeMethodStats stats);
    boolean updateTradeMethodStats(TradeMethodStats stats);
    boolean removeTradeMethodStats(Long id);
    List<Map<String, Object>> getTradeMethodRatio(Integer year, Integer month, String country);
    List<TradeMethodStats> queryByConditions(String methodType, Integer year);
    IPage<TradeMethodStats> queryPageByConditions(int pageNum, int pageSize, String methodType, Integer year);
    List<Map<String, Object>> getCountryTradeRatio(Integer year, Integer month);
    List<Map<String, Object>> getTradeMethodRatio(Integer year, Integer month);
}
