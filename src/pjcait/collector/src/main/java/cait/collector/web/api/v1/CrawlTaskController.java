package cait.collector.web.api.v1;

import cait.collector.common.mapper.CrawlTaskMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CrawlTaskEntity;
import cait.collector.common.model.db.CustomCrawlerEntity;
import cait.collector.web.model.response.CrawlTaskResponse;
import cait.collector.web.model.response.PagingData;
import cait.collector.web.model.response.Response;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/status/crawl-task")
public class CrawlTaskController {

    private final CrawlTaskMapper crawlTaskMapper;
    private final ObjectMapper jacksonObjectMapper;
    private final JavaType stringMapJavaType;
    private final JavaType stringListJavaType;

    public CrawlTaskController(CrawlTaskMapper crawlTaskMapper, ObjectMapper jacksonObjectMapper) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.stringMapJavaType = jacksonObjectMapper.getTypeFactory()
                .constructParametricType(HashMap.class, String.class, String.class);

        this.stringListJavaType = jacksonObjectMapper.getTypeFactory()
                .constructParametricType(ArrayList.class, String.class);
    }

    /**
     * 根据 ID 查询单个任务
     */
    @GetMapping("/{id}")
    public Response<CrawlTaskResponse> getById(@PathVariable String id) {
        var entity = crawlTaskMapper.selectById(id);
        var response = this.convert(entity);

        return Response.success(response);
    }

    /**
     * 查询所有任务（支持分页和可选筛选）
     */
    @GetMapping("/list")
    public Response<PagingData<CrawlTaskResponse>> queryTasks(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int size
    ) {
        var query = new LambdaQueryWrapper<CrawlTaskEntity>()
                .orderByDesc(CrawlTaskEntity::getUpdateTime);

        var pageResult = crawlTaskMapper.selectPage(new Page<>(page, size), query);

        var pagingData = new PagingData<CrawlTaskResponse>();
        pagingData.setCurrentPage(pageResult.getCurrent());
        pagingData.setPageSize(pageResult.getSize());
        pagingData.setTotalPage(pageResult.getPages());
        pagingData.setTotalResult(pageResult.getTotal());
        pagingData.setData(pageResult.getRecords().stream().map(this::convert).toList());

        return Response.success(pagingData);
    }

    private CrawlTaskResponse convert(CrawlTaskEntity entity) {
        List<String> urls = Collections.emptyList();
        Map<String, String> additionalInfo = Map.of();

        try {
            urls = jacksonObjectMapper.readValue(entity.getUrls(), stringListJavaType);
            additionalInfo = jacksonObjectMapper.readValue(entity.getAdditionalInfo(), stringMapJavaType);
        } catch (Exception e) {
            log.error("error when parsing status record json", e);
        }

        return CrawlTaskResponse.builder()
        		.id(entity.getId())
        		.crawlType(entity.getCrawlType())
        		.source(entity.getSource())
        		.urls(urls)
        		.cleanHtml(entity.getCleanHtml())
        		.archive(entity.getArchive())
        		.additionalInfo(additionalInfo)
        		.message(entity.getMessage())
        		.createTime(entity.getCreateTime().getTime())
        		.updateTime(entity.getUpdateTime().getTime())
        		.status(entity.getStatus())
        		.build();
    }

    /**
     * 查询所有任务（不分页）
     */
    @GetMapping("/all")
    public Response<List<CrawlTaskResponse>> listAll() {
        var result =  crawlTaskMapper.selectList(new QueryWrapper<CrawlTaskEntity>().last("limit 1000"));
        var responseList = result.stream().map(this::convert).toList();

        return Response.success(responseList);
    }
}
