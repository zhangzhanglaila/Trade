package cait.collector.web.service;

import cait.collector.common.mapper.CustomCrawlerMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CustomCrawlerEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.yitter.idgen.YitIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomCrawlerService {

    private final CustomCrawlerMapper customCrawlerMapper;

    public CustomCrawlerService(CustomCrawlerMapper customCrawlerMapper) {
        this.customCrawlerMapper = customCrawlerMapper;
    }

    public List<CustomCrawlerEntity> getCustomCrawler() {
        var query = new LambdaQueryWrapper<CustomCrawlerEntity>()
                .ne(CustomCrawlerEntity::getStatus, EnumValues.DatasourceStatus.Deleted)
                .orderByDesc(CustomCrawlerEntity::getCreateTime);

        return customCrawlerMapper.selectList(query);
    }

    public void addCustomCrawler(CustomCrawlerEntity entity) {
        // 设置默认状态
        entity.setId(YitIdHelper.nextId());
        entity.setStatus(EnumValues.DatasourceStatus.Enabled);
        customCrawlerMapper.insert(entity);
    }

    public void updateCustomCrawler(Long id, CustomCrawlerEntity updatedData) {
        var query = new LambdaQueryWrapper<CustomCrawlerEntity>()
                .eq(CustomCrawlerEntity::getId, id)
                .ne(CustomCrawlerEntity::getStatus, EnumValues.DatasourceStatus.Deleted);

        customCrawlerMapper.update(updatedData, query);
    }

    @Transactional
    public void deleteCustomCrawler(Long id) {
        LambdaUpdateWrapper<CustomCrawlerEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(CustomCrawlerEntity::getId, id)
                .set(CustomCrawlerEntity::getStatus, EnumValues.DatasourceStatus.Deleted);
        customCrawlerMapper.update(null, updateWrapper);
    }
}
