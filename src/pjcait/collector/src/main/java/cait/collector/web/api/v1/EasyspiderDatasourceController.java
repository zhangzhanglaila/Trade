package cait.collector.web.api.v1;

import cait.collector.common.model.db.EasyspiderDatasourceTasksEntity;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.model.request.EasyspiderDatasourceTasksRequest;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.EasyspiderDatasourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/configure/datasource/easyspider")
public class EasyspiderDatasourceController {

    private final EasyspiderDatasourceService easyspiderDatasourceService;
    private final ObjectMapper jacksonObjectMapper;

    public EasyspiderDatasourceController(EasyspiderDatasourceService easyspiderDatasourceService, ObjectMapper jacksonObjectMapper) {
        this.easyspiderDatasourceService = easyspiderDatasourceService;
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @PostMapping("/add")
    public Response<String> add(@RequestBody EasyspiderDatasourceTasksRequest request)
            throws JsonProcessingException {
        var entity = convert(request);

        easyspiderDatasourceService.addEasyspiderDatasourceTask(entity);
        
        return Response.success();
    }

    @PostMapping("/set-status")
    public Response<String> setCrawlerStatus(@RequestParam("id") Long id,
                                             @RequestParam("status") Integer status) {
        if (!status.equals(0) && !status.equals(1)) {
            return Response.error(ServiceCode.ParamWrong);
        }

        var entity = new EasyspiderDatasourceTasksEntity();
        entity.setStatus(status);

        easyspiderDatasourceService.updateEasyspiderDatasourceTask(id, entity);
        return Response.success();
    }

    @PostMapping("/update")
    public Response<String> updateDatasource(@RequestParam("id") Long id,
                                             @RequestBody EasyspiderDatasourceTasksRequest request)
            throws JsonProcessingException {
        var entity = convert(request);
        easyspiderDatasourceService.updateEasyspiderDatasourceTask(id, entity);
        return Response.success();
    }

    private EasyspiderDatasourceTasksEntity convert(@RequestBody EasyspiderDatasourceTasksRequest request)
            throws JsonProcessingException {
        var entity = new EasyspiderDatasourceTasksEntity();
        entity.setName(request.getName());
        entity.setTaskId(request.getTaskId());
        entity.setUrl(request.getUrl());
        entity.setCrawlMethod(request.getCrawlMethod());
        entity.setExtraParams(jacksonObjectMapper.writeValueAsString(request.getParams()));
        entity.setStatus(request.getStatus());

        return entity;
    }

    @PostMapping("/delete")
    public Response<String> deleteDatasource(@RequestParam("id") Long id) {
        easyspiderDatasourceService.deleteEasyspiderDatasourceTask(id);
        return Response.success();
    }

}
