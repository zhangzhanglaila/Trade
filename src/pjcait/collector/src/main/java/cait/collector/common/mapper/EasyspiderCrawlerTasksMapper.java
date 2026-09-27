package cait.collector.common.mapper;

import cait.collector.common.model.db.EasyspiderCrawlerTasksEntity;
import cait.collector.common.model.db.EasyspiderDatasourceTasksEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EasyspiderCrawlerTasksMapper extends BaseMapper<EasyspiderCrawlerTasksEntity> {
}
