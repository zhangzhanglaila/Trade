package com.example.tdproject.generator.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.CorpusEntries;
import com.example.tdproject.generator.service.CorpusEntriesService;
import com.example.tdproject.generator.mapper.CorpusEntriesMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author 21634
* @description 针对表【corpus_entries(历年语料条目统计表)】的数据库操作Service实现
* @createDate 2025-10-09 15:47:09
*/
@Service
public class CorpusEntriesServiceImpl extends ServiceImpl<CorpusEntriesMapper, CorpusEntries>
    implements CorpusEntriesService{

    @Override
    public boolean saveCorpusEntries(CorpusEntries entries) {
        Assert.notNull(entries, "语料数据不能为空");
        return save(entries);
    }

    @Override
    public boolean updateCorpusEntries(CorpusEntries entries) {
        Assert.notNull(entries, "语料数据不能为空");
        Assert.notNull(entries.getId(), "ID不能为空");
        return updateById(entries);
    }

    @Override
    public boolean removeCorpusEntries(Long id) {
        Assert.notNull(id, "ID不能为空");
        return removeById(id);
    }

    @Override
    public List<CorpusEntries> getAllEntries() {
        return baseMapper.selectYearlyStats();
    }

    @Override
    public IPage<CorpusEntries> queryPageByType(int pageNum, int pageSize, String corpusType) {
        Page<CorpusEntries> page = new Page<>(pageNum, pageSize);
        QueryWrapper<CorpusEntries> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("year", "update_time");
        return page(page, queryWrapper);
    }
}
