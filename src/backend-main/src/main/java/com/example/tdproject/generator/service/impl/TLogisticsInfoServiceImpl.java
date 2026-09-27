package com.example.tdproject.generator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.TLogisticsInfo;
import com.example.tdproject.generator.mapper.TLogisticsInfoMapper;
import com.example.tdproject.generator.service.TLogisticsInfoService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class TLogisticsInfoServiceImpl extends ServiceImpl<TLogisticsInfoMapper, TLogisticsInfo>
        implements TLogisticsInfoService {

    /**
     * 新增物流信息
     */
    public boolean saveLogisticsInfo(TLogisticsInfo logisticsInfo) {
        Assert.notNull(logisticsInfo, "物流信息不能为空");
        return save(logisticsInfo);
    }

    /**
     * 更新物流信息
     */
    public boolean updateLogisticsInfo(TLogisticsInfo logisticsInfo) {
        Assert.notNull(logisticsInfo, "物流信息不能为空");
        Assert.notNull(logisticsInfo.getId(), "物流ID不能为空");
        return updateById(logisticsInfo);
    }

    /**
     * 删除物流信息
     */
    public boolean removeLogisticsInfo(Long id) {
        Assert.notNull(id, "物流ID不能为空");
        return removeById(id);
    }

    /**
     * 根据ID查询物流信息
     */
    public TLogisticsInfo getLogisticsInfoById(Long id) {
        Assert.notNull(id, "物流ID不能为空");
        return getById(id);
    }

    /**
     * 按国家和进出口类型查询
     */
    public List<TLogisticsInfo> getByCountryAndImportType(String country, Integer importExportType) {
        QueryWrapper<TLogisticsInfo> queryWrapper = new QueryWrapper<>();
        return list(queryWrapper);
    }

    /**
     * 分页查询
     */
    public IPage<TLogisticsInfo> getPage(int pageNum, int pageSize, String country, String commodityName) {
        Page<TLogisticsInfo> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TLogisticsInfo> queryWrapper = new QueryWrapper<>();

        if (commodityName != null && !commodityName.isEmpty()) {
            queryWrapper.like("commodity_name", commodityName);
        }

        return page(page, queryWrapper);
    }

    @Override
    public List<TLogisticsInfo> queryByConditions(
            Integer dataYearMonth,
            Integer tradePartnerCode,
            String tradePartnerName,
            Integer commodityCode,
            Integer importExportType,
            String country) {
        QueryWrapper<TLogisticsInfo> queryWrapper = new QueryWrapper<>();
        if (dataYearMonth != null) {
            queryWrapper.eq("data_year_month", String.valueOf(dataYearMonth));
        }
        if (tradePartnerCode != null) {
            queryWrapper.eq("partner_code", String.valueOf(tradePartnerCode));
        }
        if (tradePartnerName != null && !tradePartnerName.isEmpty()) {
            queryWrapper.like("partner_name", tradePartnerName);
        }
        if (commodityCode != null) {
            queryWrapper.eq("commodity_code", String.valueOf(commodityCode));
        }
        return list(queryWrapper);
    }

    @Override
    public IPage<TLogisticsInfo> queryPageByConditions(
            int pageNum,
            int pageSize,
            Integer dataYearMonth,
            Integer tradePartnerCode,
            String tradePartnerName,
            Integer commodityCode,
            Integer importExportType,
            String country) {
        Page<TLogisticsInfo> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TLogisticsInfo> queryWrapper = new QueryWrapper<>();

        if (dataYearMonth != null) {
            queryWrapper.eq("data_year_month", String.valueOf(dataYearMonth));
        }
        if (tradePartnerCode != null) {
            queryWrapper.eq("partner_code", String.valueOf(tradePartnerCode));
        }
        if (tradePartnerName != null && !tradePartnerName.isEmpty()) {
            queryWrapper.like("partner_name", tradePartnerName);
        }
        if (commodityCode != null) {
            queryWrapper.eq("commodity_code", String.valueOf(commodityCode));
        }

        return page(page, queryWrapper);
    }
}
