package cait.collector.web.api.v1;

import cait.collector.common.model.db.CustomApiDatasourceEntity;
import cait.collector.common.model.db.EasyspiderDatasourceTasksEntity;
import cait.collector.configure.datasource.GdeltDatasourceConfiguration;
import cait.collector.web.model.response.CustomDatasourceListResponse;
import cait.collector.web.model.response.DatasourceListResponse;
import cait.collector.web.model.response.EasyspiderDatasourceListResponse;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.CustomDatasourceService;
import cait.collector.web.service.EasyspiderDatasourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasource")
public class DatasourceController {
    private final CustomDatasourceService customDatasourceService;
    private final EasyspiderDatasourceService easyspiderDatasourceService;
    private final GdeltDatasourceConfiguration gdeltDatasourceConfiguration;

    public DatasourceController(CustomDatasourceService customDatasourceService,
                                EasyspiderDatasourceService easyspiderDatasourceService,
                                GdeltDatasourceConfiguration gdeltDatasourceConfiguration) {
        this.customDatasourceService = customDatasourceService;
        this.easyspiderDatasourceService = easyspiderDatasourceService;
        this.gdeltDatasourceConfiguration = gdeltDatasourceConfiguration;
    }

    @GetMapping("/list")
    public Response<DatasourceListResponse> getDatasource() {
        var customDatasource = customDatasourceService.getCustomApiDatasource();
        var customDatasourceList = customDatasource.stream().map(this::convert).toList();

        var esDatasource = easyspiderDatasourceService.getEasyspiderDatasourceTasks();
        var esDatasourceList = esDatasource.stream().map(this::convert).toList();

        var response = DatasourceListResponse.builder()
                .custom(customDatasourceList)
                .easyspider(esDatasourceList)
                .gdelt(DatasourceListResponse.GDELTDatasourceListResponse.builder()
                        .enabled(gdeltDatasourceConfiguration.getEnabled())
                        .build()
                )
                .build();

        return Response.success(response);
    }

    private CustomDatasourceListResponse convert(CustomApiDatasourceEntity entity) {
        return CustomDatasourceListResponse.builder()
        		.id(entity.getId().toString())
        		.name(entity.getName())
        		.reqUrl(entity.getReqUrl())
        		.crawlMethod(entity.getCrawlMethod())
        		.params(entity.getParams())
        		.createTime(entity.getCreateTime().getTime())
        		.updateTime(entity.getUpdateTime().getTime())
        		.status(entity.getStatus())
        		.build();
    }

    private EasyspiderDatasourceListResponse convert(EasyspiderDatasourceTasksEntity entity) {
        return EasyspiderDatasourceListResponse.builder()
        		.id(entity.getId().toString())
        		.name(entity.getName())
        		.taskId(entity.getTaskId())
        		.url(entity.getUrl())
        		.crawlMethod(entity.getCrawlMethod())
        		.params(entity.getExtraParams())
        		.createTime(entity.getCreateTime().getTime())
        		.updateTime(entity.getUpdateTime().getTime())
        		.status(entity.getStatus())
        		.build();
    }
}
