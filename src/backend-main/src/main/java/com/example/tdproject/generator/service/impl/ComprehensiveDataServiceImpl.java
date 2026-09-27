package com.example.tdproject.generator.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.ComprehensiveData;
import com.example.tdproject.generator.service.ComprehensiveDataService;
import com.example.tdproject.generator.mapper.ComprehensiveDataMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author 21634
* @description 针对表【comprehensive_data(综合统计数据表)】的数据库操作Service实现
* @createDate 2025-10-09 15:47:09
*/
@Service
public class ComprehensiveDataServiceImpl extends ServiceImpl<ComprehensiveDataMapper, ComprehensiveData>
    implements ComprehensiveDataService{

    @Override
    public boolean saveComprehensiveData(ComprehensiveData data) {
        Assert.notNull(data, "数据不能为空");
        return save(data);
    }

    @Override
    public boolean updateComprehensiveData(ComprehensiveData data) {
        Assert.notNull(data, "数据不能为空");
        Assert.notNull(data.getId(), "ID不能为空");
        return updateById(data);
    }

    @Override
    public boolean removeComprehensiveData(Long id) {
        Assert.notNull(id, "ID不能为空");
        return removeById(id);
    }

    @Override
    public List<ComprehensiveData> queryByConditions(String country, Integer year) {
        QueryWrapper<ComprehensiveData> queryWrapper = new QueryWrapper<>();
        if (year != null) {
            queryWrapper.eq("stat_year", year);
        }
        queryWrapper.orderByDesc("stat_year", "stat_month", "update_time");
        return list(queryWrapper);
    }

    @Override
    public IPage<ComprehensiveData> queryPageByConditions(int pageNum, int pageSize, String country, Integer year) {
        Page<ComprehensiveData> page = new Page<>(pageNum, pageSize);
        QueryWrapper<ComprehensiveData> queryWrapper = new QueryWrapper<>();
        if (year != null) {
            queryWrapper.eq("stat_year", year);
        }
        queryWrapper.orderByDesc("stat_year", "stat_month", "update_time");
        return page(page, queryWrapper);
    }

    @Override
    public ComprehensiveData getLatestByYearMonth(Integer year, Integer month) {
        // 先查询指定年月的数据
        ComprehensiveData data = baseMapper.selectOne(new QueryWrapper<ComprehensiveData>()
                .eq("stat_year", year)
                .eq("stat_month", month)
                .orderByDesc("update_time")
                .last("LIMIT 1"));

        // 如果没有找到，查询最新的一条数据（不限时间）
        if (data == null) {
            data = baseMapper.selectOne(new QueryWrapper<ComprehensiveData>()
                    .orderByDesc("stat_year", "stat_month", "update_time")
                    .last("LIMIT 1"));
        }
        return data;
    }
}
