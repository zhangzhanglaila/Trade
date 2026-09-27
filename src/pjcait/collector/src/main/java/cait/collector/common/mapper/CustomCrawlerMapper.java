package cait.collector.common.mapper;

import cait.collector.common.model.db.CustomApiDatasourceEntity;
import cait.collector.common.model.db.CustomCrawlerEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomCrawlerMapper extends BaseMapper<CustomCrawlerEntity> {
}
