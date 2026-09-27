package com.example.tdproject.generator.mapper;

import com.example.tdproject.generator.domain.CorpusEntries;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author 21634
* @description 针对表【corpus_entries(历年语料条目统计表)】的数据库操作Mapper
* @createDate 2025-10-09 15:47:09
* @Entity generator.domain.CorpusEntries
*/
public interface CorpusEntriesMapper extends BaseMapper<CorpusEntries> {

    List<CorpusEntries> selectYearlyStats();
}




