package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.generator.domain.CorpusEntries;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 21634
* @description 针对表【corpus_entries(历年语料条目统计表)】的数据库操作Service
* @createDate 2025-10-09 15:47:09
*/
public interface CorpusEntriesService extends IService<CorpusEntries> {
    boolean saveCorpusEntries(CorpusEntries entries);
    boolean updateCorpusEntries(CorpusEntries entries);
    boolean removeCorpusEntries(Long id);
    List<CorpusEntries> getAllEntries();
    IPage<CorpusEntries> queryPageByType(int pageNum, int pageSize, String corpusType);
}
