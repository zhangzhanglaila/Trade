package cait.collector.common.mapper;

import cait.collector.common.model.db.CrawlTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlTaskMapper extends BaseMapper<CrawlTaskEntity> {
}
