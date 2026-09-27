package cait.collector.common.mapper;

import cait.collector.common.model.db.CustomApiDatasourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomApiDatasourceMapper extends BaseMapper<CustomApiDatasourceEntity> {
}
