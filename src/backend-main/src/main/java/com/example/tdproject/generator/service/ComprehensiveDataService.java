package com.example.tdproject.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.tdproject.generator.domain.ComprehensiveData;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 21634
* @description 针对表【comprehensive_data(综合统计数据表)】的数据库操作Service
* @createDate 2025-10-09 15:47:09
*/
public interface ComprehensiveDataService extends IService<ComprehensiveData> {
    boolean saveComprehensiveData(ComprehensiveData data);
    boolean updateComprehensiveData(ComprehensiveData data);
    boolean removeComprehensiveData(Long id);
    List<ComprehensiveData> queryByConditions(String country, Integer year);
    IPage<ComprehensiveData> queryPageByConditions(int pageNum, int pageSize, String country, Integer year);
    ComprehensiveData getLatestByYearMonth(Integer year, Integer month);
}
