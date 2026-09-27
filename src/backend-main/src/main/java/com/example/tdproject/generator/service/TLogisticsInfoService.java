package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.tdproject.generator.domain.TLogisticsInfo;
import java.util.List;

public interface TLogisticsInfoService extends IService<TLogisticsInfo> {
    boolean saveLogisticsInfo(TLogisticsInfo logisticsInfo);
    boolean updateLogisticsInfo(TLogisticsInfo logisticsInfo);
    boolean removeLogisticsInfo(Long id);
    TLogisticsInfo getLogisticsInfoById(Long id);
    List<TLogisticsInfo> getByCountryAndImportType(String country, Integer importExportType);
    IPage<TLogisticsInfo> getPage(int pageNum, int pageSize, String country, String commodityName);
    // 添加到TLogisticsInfoService接口
    List<TLogisticsInfo> queryByConditions(
            Integer dataYearMonth,
            Integer tradePartnerCode,
            String tradePartnerName,
            Integer commodityCode,
            Integer importExportType,
            String country);

    IPage<TLogisticsInfo> queryPageByConditions(
            int pageNum,
            int pageSize,
            Integer dataYearMonth,
            Integer tradePartnerCode,
            String tradePartnerName,
            Integer commodityCode,
            Integer importExportType,
            String country);
}