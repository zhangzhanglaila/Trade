package cait.collector.web.service;

import cait.collector.common.mapper.EasyspiderCrawlerTasksMapper;
import cait.collector.common.mapper.EasyspiderDatasourceTasksMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.EasyspiderCrawlerTasksEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EasyspiderCrawlerService {

    private final EasyspiderCrawlerTasksMapper easyspiderCrawlerTasksMapper;

    public EasyspiderCrawlerService(EasyspiderCrawlerTasksMapper easyspiderCrawlerTasksMapper) {
        this.easyspiderCrawlerTasksMapper = easyspiderCrawlerTasksMapper;
    }

    public List<EasyspiderCrawlerTasksEntity> getEasyspiderCrawlerTasks() {
        var query = new LambdaQueryWrapper<EasyspiderCrawlerTasksEntity>()
                .ne(EasyspiderCrawlerTasksEntity::getStatus, EnumValues.DatasourceStatus.Deleted)
                .orderByDesc(EasyspiderCrawlerTasksEntity::getCreateTime);

        return easyspiderCrawlerTasksMapper.selectList(query);
    }

    public void addEasyspiderCrawlerTask(EasyspiderCrawlerTasksEntity entity) {
        entity.setId(YitIdHelper.nextId());
        entity.setStatus(EnumValues.DatasourceStatus.Enabled); // 设置默认状态
        easyspiderCrawlerTasksMapper.insert(entity);
    }

    public void updateEasyspiderCrawlerTask(Long id, EasyspiderCrawlerTasksEntity updatedData) {
        updatedData.setId(id); // 确保更新的是指定ID的记录
        var query = new LambdaQueryWrapper<EasyspiderCrawlerTasksEntity>()
                .eq(EasyspiderCrawlerTasksEntity::getId, id)
                .ne(EasyspiderCrawlerTasksEntity::getStatus, EnumValues.DatasourceStatus.Deleted);

        easyspiderCrawlerTasksMapper.update(updatedData, query);
    }

    @Transactional
    public void deleteEasyspiderCrawlerTask(Long id) {
        LambdaUpdateWrapper<EasyspiderCrawlerTasksEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(EasyspiderCrawlerTasksEntity::getId, id)
                .set(EasyspiderCrawlerTasksEntity::getStatus, EnumValues.DatasourceStatus.Deleted);
        easyspiderCrawlerTasksMapper.update(null, updateWrapper);
    }
}
