package cait.collector.web.api.v1;

import cait.collector.common.model.db.EasyspiderCrawlerTasksEntity;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.model.request.EasyspiderCrawlerTasksRequest;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.EasyspiderCrawlerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/configure/crawler/easyspider")
public class EasyspiderCrawlerController {

    private final EasyspiderCrawlerService easyspiderCrawlerService;
    private final ObjectMapper jacksonObjectMapper;

    public EasyspiderCrawlerController(EasyspiderCrawlerService easyspiderCrawlerService, ObjectMapper jacksonObjectMapper) {
        this.easyspiderCrawlerService = easyspiderCrawlerService;
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @PostMapping("/add")
    public Response<String> add(@RequestBody EasyspiderCrawlerTasksRequest request)
            throws JsonProcessingException {
        var entity = convert(request);

        easyspiderCrawlerService.addEasyspiderCrawlerTask(entity);
        
        return Response.success();
    }

    @PostMapping("/set-status")
    public Response<String> setCrawlerStatus(@RequestParam("id") Long id,
                                             @RequestParam("status") Integer status) {
        if (!status.equals(0) && !status.equals(1)) {
            return Response.error(ServiceCode.ParamWrong);
        }

        var entity = new EasyspiderCrawlerTasksEntity();
        entity.setStatus(status);

        easyspiderCrawlerService.updateEasyspiderCrawlerTask(id, entity);
        return Response.success();
    }

    @PostMapping("/update")
    public Response<String> updateDatasource(@RequestParam("id") Long id,
                                             @RequestBody EasyspiderCrawlerTasksRequest request)
            throws JsonProcessingException {
        var entity = convert(request);
        easyspiderCrawlerService.updateEasyspiderCrawlerTask(id, entity);
        return Response.success();
    }

    private EasyspiderCrawlerTasksEntity convert(@RequestBody EasyspiderCrawlerTasksRequest request)
            throws JsonProcessingException {
        var entity = new EasyspiderCrawlerTasksEntity();
        entity.setName(request.getName());
        entity.setTaskId(request.getTaskId());
        entity.setExtraParams(jacksonObjectMapper.writeValueAsString(request.getParams()));
        entity.setStatus(request.getStatus());

        return entity;
    }

    @PostMapping("/delete")
    public Response<String> deleteDatasource(@RequestParam("id") Long id) {
        easyspiderCrawlerService.deleteEasyspiderCrawlerTask(id);
        return Response.success();
    }

}
