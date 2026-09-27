package cait.collector.web.service;

import cait.collector.common.mapper.CustomApiDatasourceMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CustomApiDatasourceEntity;
import cait.collector.common.model.db.EasyspiderDatasourceTasksEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomDatasourceService {

    private final CustomApiDatasourceMapper customApiDatasourceMapper;

    public CustomDatasourceService(CustomApiDatasourceMapper customApiDatasourceMapper) {
        this.customApiDatasourceMapper = customApiDatasourceMapper;
    }

    public List<CustomApiDatasourceEntity> getCustomApiDatasource() {
        var query = new LambdaQueryWrapper<CustomApiDatasourceEntity>()
                .ne(CustomApiDatasourceEntity::getStatus, EnumValues.DatasourceStatus.Deleted)
                .orderByDesc(CustomApiDatasourceEntity::getCreateTime);

        return customApiDatasourceMapper.selectList(query);
    }

    public void addCustomApiDatasource(CustomApiDatasourceEntity entity) {
        // 设置默认状态
        entity.setId(YitIdHelper.nextId());
        entity.setStatus(EnumValues.DatasourceStatus.Enabled);
        customApiDatasourceMapper.insert(entity);
    }

    public void updateCustomApiDatasource(Long id, CustomApiDatasourceEntity updatedData) {
        var query = new LambdaQueryWrapper<CustomApiDatasourceEntity>()
                .eq(CustomApiDatasourceEntity::getId, id)
                .ne(CustomApiDatasourceEntity::getStatus, EnumValues.DatasourceStatus.Deleted);

        customApiDatasourceMapper.update(updatedData, query);
    }

    @Transactional
    public void deleteCustomApiDatasource(Long id) {
        LambdaUpdateWrapper<CustomApiDatasourceEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CustomApiDatasourceEntity::getId, id)
                .set(CustomApiDatasourceEntity::getStatus, EnumValues.DatasourceStatus.Deleted);
        customApiDatasourceMapper.update(null, updateWrapper);
    }
}
