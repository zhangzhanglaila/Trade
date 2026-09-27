package com.example.tdproject.generator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.mapper.TNewsCorpusMapper;
import com.example.tdproject.generator.service.TNewsCorpusService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class TNewsCorpusServiceImpl extends ServiceImpl<TNewsCorpusMapper, TNewsCorpus>
        implements TNewsCorpusService {

    /**
     * 新增新闻语料
     */
    public boolean saveNewsCorpus(TNewsCorpus newsCorpus) {
        Assert.notNull(newsCorpus, "新闻语料不能为空");
        Assert.hasText(newsCorpus.getNewsTitle(), "新闻标题不能为空");
        return save(newsCorpus);
    }

    /**
     * 更新新闻语料
     */
    public boolean updateNewsCorpus(TNewsCorpus newsCorpus) {
        Assert.notNull(newsCorpus, "新闻语料不能为空");
        Assert.notNull(newsCorpus.getId(), "新闻ID不能为空");
        return updateById(newsCorpus);
    }

    /**
     * 删除新闻语料
     */
    public boolean removeNewsCorpus(Long id) {
        Assert.notNull(id, "新闻ID不能为空");
        return removeById(id);
    }

    /**
     * 根据ID查询新闻语料
     */
    public TNewsCorpus getNewsCorpusById(Long id) {
        Assert.notNull(id, "新闻ID不能为空");
        return getById(id);
    }

    /**
     * 按国家和年份查询
     */
    public List<TNewsCorpus> getByCountryAndYear(String country, Integer year) {
        QueryWrapper<TNewsCorpus> queryWrapper = new QueryWrapper<>();
        if (country != null && !country.isEmpty()) {
            queryWrapper.like("title", country);
        }
        if (year != null) {
            queryWrapper.apply("YEAR(publish_time) = {0}", year);
        }
        queryWrapper.orderByDesc("publish_time");
        return list(queryWrapper);
    }

    /**
     * 按国家、年份和标题关键词分页搜索
     */
    public IPage<TNewsCorpus> searchByCountryYearAndTitle(int pageNum, int pageSize,
                                                          String country, Integer year, String keyword) {
        Page<TNewsCorpus> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TNewsCorpus> queryWrapper = new QueryWrapper<>();

        if (country != null && !country.isEmpty()) {
            queryWrapper.like("title", country);
        }
        if (year != null) {
            queryWrapper.apply("YEAR(publish_time) = {0}", year);
        }
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.like("title", keyword);
        }

        queryWrapper.orderByDesc("publish_time");
        return page(page, queryWrapper);
    }

    @Override
    public List<TNewsCorpus> queryByMixConditions(String country, Integer year,
                                                  String titleKeyword, String contentKeyword) {
        QueryWrapper<TNewsCorpus> queryWrapper = new QueryWrapper<>();

        if (country != null && !country.isEmpty()) {
            queryWrapper.like("title", country);
        }
        if (year != null) {
            queryWrapper.apply("YEAR(publish_time) = {0}", year);
        }
        if (titleKeyword != null && !titleKeyword.isEmpty()) {
            queryWrapper.like("title", titleKeyword);
        }
        if (contentKeyword != null && !contentKeyword.isEmpty()) {
            queryWrapper.like("content", contentKeyword);
        }

        queryWrapper.orderByDesc("publish_time");
        return list(queryWrapper);
    }

    /**
     * 混合条件分页查询
     */
    @Override
    public IPage<TNewsCorpus> queryPageByMixConditions(int pageNum, int pageSize,
                                                       String country, Integer year,
                                                       String titleKeyword, String contentKeyword) {
        Page<TNewsCorpus> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TNewsCorpus> queryWrapper = new QueryWrapper<>();

        if (country != null && !country.isEmpty()) {
            queryWrapper.like("title", country);
        }
        if (year != null) {
            queryWrapper.apply("YEAR(publish_time) = {0}", year);
        }
        if (titleKeyword != null && !titleKeyword.isEmpty()) {
            queryWrapper.like("title", titleKeyword);
        }
        if (contentKeyword != null && !contentKeyword.isEmpty()) {
            queryWrapper.like("content", contentKeyword);
        }

        queryWrapper.orderByDesc("publish_time");
        return page(page, queryWrapper);
    }
}
