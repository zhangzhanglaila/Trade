package cait.collector.job.service;

import cait.collector.common.Constants;
import cait.collector.common.mapper.CustomApiDatasourceMapper;
import cait.collector.common.mapper.EasyspiderDatasourceTasksMapper;
import cait.collector.common.model.EnumValues;
import cait.collector.common.model.db.CustomApiDatasourceEntity;
import cait.collector.common.model.db.EasyspiderDatasourceTasksEntity;
import cait.collector.configure.datasource.GdeltDatasourceConfiguration;
import cait.collector.datasource.CustomApiDatasource;
import cait.collector.datasource.GDELTDatasource;
import cait.collector.datasource.GenericEasyspiderDatasource;
import cait.collector.messaging.CrawlTaskPublisher;
import cait.common.pb.CollectorPb;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yitter.idgen.YitIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DataUpdateJob extends QuartzJobBean {
    private final CrawlTaskPublisher crawlTaskPublisher;
    private final CustomApiDatasourceMapper customApiDatasourceMapper;
    private final CustomApiDatasource customApiDatasource;
    private final ObjectMapper jacksonObjectMapper;
    private final JavaType stringMapJavaType;
    private final EasyspiderDatasourceTasksMapper easyspiderDatasourceTasksMapper;
    private final GenericEasyspiderDatasource genericEasyspiderDatasource;
    private final GDELTDatasource gdeltDatasource;
    private final GdeltDatasourceConfiguration gdeltDatasourceConfiguration;
    private final StringRedisTemplate stringRedisTemplate;

    public DataUpdateJob(CrawlTaskPublisher crawlTaskPublisher,
                         CustomApiDatasourceMapper customApiDatasourceMapper,
                         CustomApiDatasource customApiDatasource,
                         ObjectMapper jacksonObjectMapper,
                         EasyspiderDatasourceTasksMapper easyspiderDatasourceTasksMapper,
                         GenericEasyspiderDatasource genericEasyspiderDatasource,
                         GDELTDatasource gdeltDatasource,
                         GdeltDatasourceConfiguration gdeltDatasourceConfiguration, StringRedisTemplate stringRedisTemplate) {

        this.crawlTaskPublisher = crawlTaskPublisher;
        this.customApiDatasourceMapper = customApiDatasourceMapper;
        this.customApiDatasource = customApiDatasource;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.stringMapJavaType = jacksonObjectMapper.getTypeFactory()
                .constructParametricType(HashMap.class, String.class, String.class);
        this.easyspiderDatasourceTasksMapper = easyspiderDatasourceTasksMapper;
        this.genericEasyspiderDatasource = genericEasyspiderDatasource;
        this.gdeltDatasource = gdeltDatasource;
        this.gdeltDatasourceConfiguration = gdeltDatasourceConfiguration;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info("Job execute start: {}", context.getJobDetail());

        try {
            executeCustomDatasource();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        try {
            executeEasyspiderDatasource();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        if (gdeltDatasourceConfiguration.getEnabled()) {
            try {
                executeGDELTDatasource();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private LocalDate LastGDELTDate = null;
    private static final int MaxGDELTTry = 30;

    public void executeGDELTDatasource() {
        log.info("executeGDELTDatasource");

        try {
            var date = LocalDate.now();
            String file = "";

            for (int i = 0; i < MaxGDELTTry; i++) {
                log.info("[GDELT datasource] downloading gkg file of date {}", date);
                file = gdeltDatasource.downloadGkgFile(date);
                if (file == null) {
                    date = date.minusDays(1);
                } else {
                    break;
                }
            }

            if (LastGDELTDate == null) {
                LastGDELTDate = date;
            } else {
                if (LastGDELTDate.equals(date) || LastGDELTDate.isAfter(date)) {
                    log.info("LastGDELTDate.equals(date) || LastGDELTDate.isAfter(date), LastGDELTDate={}, date={}",
                            LastGDELTDate, date
                    );
                    return;
                }
            }

            var newsList = gdeltDatasource.filterEvent(file, true);
            log.info("[GDELT Datasource] filtered {} news data", newsList.size());
            if (newsList.isEmpty()) {
                return;
            }

            var newsUrlList = newsList.stream().map(GDELTDatasource.GdeltEventItem::getUrl).toList();
            var taskPb = this.makeTaskPb(newsUrlList, CollectorPb.Source.SourceGDELT, EnumValues.CrawlMethod.Selenium, Map.of());
            crawlTaskPublisher.publishCrawlJob(taskPb);
        } catch (Exception e) {
            log.error("executeGDELTDatasource error", e);
        }
    }

    public void executeEasyspiderDatasource() {
        log.info("executeEasyspiderDatasource");

        var query = new LambdaQueryWrapper<EasyspiderDatasourceTasksEntity>()
                .eq(EasyspiderDatasourceTasksEntity::getStatus, EnumValues.DatasourceStatus.Enabled);
        var esTasks = easyspiderDatasourceTasksMapper.selectList(query);
        for (var esTask : esTasks) {
            try {
                Map<String, String> extraParams = jacksonObjectMapper.readValue(esTask.getExtraParams(), stringMapJavaType);
                genericEasyspiderDatasource.invokeTask(esTask.getTaskId(), esTask.getUrl(), extraParams, easyspiderCallbackData -> {
                    var urlList = easyspiderCallbackData.getResult();

                    var task = makeTaskPb(urlList, CollectorPb.Source.SourceEasyspider, esTask.getCrawlMethod(), Map.of());
                    crawlTaskPublisher.publishCrawlJob(task);
                });
            } catch (JsonProcessingException e) {
                log.error("error when parsing easyspider datasource param", e);
            } catch (IOException e) {
                log.error("error when getting url list using easyspider datasource", e);
            }
        }
    }

    public void executeCustomDatasource() {
        log.info("executeCustomDatasource");

        var query = new LambdaQueryWrapper<CustomApiDatasourceEntity>()
                .eq(CustomApiDatasourceEntity::getStatus, EnumValues.DatasourceStatus.Enabled);
        var datasourceList = customApiDatasourceMapper.selectList(query);
        for (var datasource : datasourceList) {
            try {
                Map<String, String> reqParams = jacksonObjectMapper.readValue(datasource.getParams(), stringMapJavaType);
                var callbackId = customApiDatasource.request(datasource.getReqUrl(), reqParams);
                stringRedisTemplate.opsForHash().putAll(
                        Constants.Redis.CustomDatasourceCallbackInfo.formatted(callbackId),
                        new HashMap<>() {{
                            put("id", datasource.getId().toString());
                            put("name", datasource.getName());
                            put("reqUrl", datasource.getReqUrl());
                            put("crawlMethod", datasource.getCrawlMethod().toString());
                        }}
                );
            } catch (JsonProcessingException e) {
                log.error("error when parsing custom api datasource param", e);
            } catch (IOException e) {
                log.error("error when getting url list using custom api datasource", e);
            }
        }
    }

    private CollectorPb.PageCrawlTask makeTaskPb(
            List<String> urlList, CollectorPb.Source source, int crawlMethod, Map<String, String> metadata
    ) {
        CollectorPb.CrawlType crawlType = switch (crawlMethod) {
            case EnumValues.CrawlMethod.PageRaw -> CollectorPb.CrawlType.CrawlTypePageRaw;
            case EnumValues.CrawlMethod.Selenium -> CollectorPb.CrawlType.CrawlTypeSelenium;
            case EnumValues.CrawlMethod.Adaptor -> CollectorPb.CrawlType.CrawlTypeAdaptor;
            case EnumValues.CrawlMethod.Easyspider -> CollectorPb.CrawlType.CrawlTypeEasySpider;
            case EnumValues.CrawlMethod.Custom -> CollectorPb.CrawlType.CrawlTypeCustom;
            default -> CollectorPb.CrawlType.CrawlTypePageRaw;
        };

        return CollectorPb.PageCrawlTask.newBuilder()
                .setId(String.valueOf(YitIdHelper.nextId()))
                .setCrawlType(crawlType)
                .addAllUrl(urlList)
                .setCleanHtml(true)
                .setArchive(true)
                .setSource(source)
//                .setCommitPublish(true)
                .putAllAdditionalInfo(metadata)
                .build();
    }
}
