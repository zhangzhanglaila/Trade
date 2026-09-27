package cait.collector.web.service;

import cait.collector.common.mapper.EasyspiderDatasourceTasksMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.EasyspiderDatasourceTasksEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EasyspiderDatasourceService {

    private final EasyspiderDatasourceTasksMapper easyspiderDatasourceTasksMapper;

    public EasyspiderDatasourceService(EasyspiderDatasourceTasksMapper easyspiderDatasourceTasksMapper) {
        this.easyspiderDatasourceTasksMapper = easyspiderDatasourceTasksMapper;
    }

    public List<EasyspiderDatasourceTasksEntity> getEasyspiderDatasourceTasks() {
        var query = new LambdaQueryWrapper<EasyspiderDatasourceTasksEntity>()
                .ne(EasyspiderDatasourceTasksEntity::getStatus, EnumValues.DatasourceStatus.Deleted)
                .orderByDesc(EasyspiderDatasourceTasksEntity::getCreateTime);

        return easyspiderDatasourceTasksMapper.selectList(query);
    }

    public void addEasyspiderDatasourceTask(EasyspiderDatasourceTasksEntity entity) {
        entity.setId(YitIdHelper.nextId());
        entity.setStatus(EnumValues.DatasourceStatus.Enabled); // 设置默认状态
        easyspiderDatasourceTasksMapper.insert(entity);
    }

    public void updateEasyspiderDatasourceTask(Long id, EasyspiderDatasourceTasksEntity updatedData) {
        updatedData.setId(id); // 确保更新的是指定ID的记录
        var query = new LambdaQueryWrapper<EasyspiderDatasourceTasksEntity>()
                .eq(EasyspiderDatasourceTasksEntity::getId, id)
                .ne(EasyspiderDatasourceTasksEntity::getStatus, EnumValues.DatasourceStatus.Deleted);

        easyspiderDatasourceTasksMapper.update(updatedData, query);
    }

    @Transactional
    public void deleteEasyspiderDatasourceTask(Long id) {
        LambdaUpdateWrapper<EasyspiderDatasourceTasksEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(EasyspiderDatasourceTasksEntity::getId, id)
                .set(EasyspiderDatasourceTasksEntity::getStatus, EnumValues.DatasourceStatus.Deleted);
        easyspiderDatasourceTasksMapper.update(null, updateWrapper);
    }
}
