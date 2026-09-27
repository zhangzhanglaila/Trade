package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.tdproject.generator.domain.TNewsCorpus;
import java.util.List;

public interface TNewsCorpusService extends IService<TNewsCorpus> {
    boolean saveNewsCorpus(TNewsCorpus newsCorpus);
    boolean updateNewsCorpus(TNewsCorpus newsCorpus);
    boolean removeNewsCorpus(Long id);
    TNewsCorpus getNewsCorpusById(Long id);
    List<TNewsCorpus> getByCountryAndYear(String country, Integer year);
    IPage<TNewsCorpus> searchByCountryYearAndTitle(int pageNum, int pageSize,
                                                   String country, Integer year, String keyword);

    // 新增混合查询方法
    List<TNewsCorpus> queryByMixConditions(String country, Integer year,
                                           String titleKeyword, String contentKeyword);

    IPage<TNewsCorpus> queryPageByMixConditions(int pageNum, int pageSize,
                                                String country, Integer year,
                                                String titleKeyword, String contentKeyword);
}