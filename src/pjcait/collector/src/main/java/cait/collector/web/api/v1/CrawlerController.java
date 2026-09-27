package cait.collector.web.api.v1;

import cait.collector.common.model.db.CustomCrawlerEntity;
import cait.collector.common.model.db.EasyspiderCrawlerTasksEntity;
import cait.collector.web.model.response.CustomCrawlerListResponse;
import cait.collector.web.model.response.CrawlerListResponse;
import cait.collector.web.model.response.EasyspiderCrawlerListResponse;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.CustomCrawlerService;
import cait.collector.web.service.EasyspiderCrawlerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crawler")
public class CrawlerController {
    private final CustomCrawlerService customCrawlerService;
    private final EasyspiderCrawlerService easyspiderCrawlerService;

    public CrawlerController(CustomCrawlerService customCrawlerService,
                                EasyspiderCrawlerService easyspiderCrawlerService) {
        this.customCrawlerService = customCrawlerService;
        this.easyspiderCrawlerService = easyspiderCrawlerService;
    }

    @GetMapping("/list")
    public Response<CrawlerListResponse> getCrawler() {
        var customCrawler = customCrawlerService.getCustomCrawler();
        var customCrawlerList = customCrawler.stream().map(this::convert).toList();

        var esCrawler = easyspiderCrawlerService.getEasyspiderCrawlerTasks();
        var esCrawlerList = esCrawler.stream().map(this::convert).toList();

        var response = CrawlerListResponse.builder()
                .custom(customCrawlerList)
                .easyspider(esCrawlerList)
                .build();

        return Response.success(response);
    }

    private CustomCrawlerListResponse convert(CustomCrawlerEntity entity) {
        return CustomCrawlerListResponse.builder()
        		.id(entity.getId().toString())
        		.name(entity.getName())
        		.reqUrl(entity.getReqUrl())
        		.params(entity.getParams())
        		.createTime(entity.getCreateTime().getTime())
        		.updateTime(entity.getUpdateTime().getTime())
        		.status(entity.getStatus())
        		.build();
    }

    private EasyspiderCrawlerListResponse convert(EasyspiderCrawlerTasksEntity entity) {
        return EasyspiderCrawlerListResponse.builder()
        		.id(entity.getId().toString())
        		.name(entity.getName())
        		.taskId(entity.getTaskId())
        		.params(entity.getExtraParams())
        		.createTime(entity.getCreateTime().getTime())
        		.updateTime(entity.getUpdateTime().getTime())
        		.status(entity.getStatus())
        		.build();
    }
}
