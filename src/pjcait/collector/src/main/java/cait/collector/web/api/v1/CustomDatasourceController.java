package cait.collector.web.api.v1;
import java.util.Date;

import cait.collector.common.model.db.CustomApiDatasourceEntity;
import cait.collector.common.model.db.CustomCrawlerEntity;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.model.request.CustomApiDatasourceRequest;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.CustomDatasourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/configure/datasource/custom")
public class CustomDatasourceController {

    private final CustomDatasourceService customDatasourceService;
    private final ObjectMapper jacksonObjectMapper;

    public CustomDatasourceController(CustomDatasourceService customDatasourceService, ObjectMapper jacksonObjectMapper) {
        this.customDatasourceService = customDatasourceService;
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @PostMapping("/add")
    public Response<String> add(@RequestBody CustomApiDatasourceRequest request) throws JsonProcessingException {
        var entity = convert(request);

        customDatasourceService.addCustomApiDatasource(entity);

        return Response.success();
    }

    @PostMapping("/set-status")
    public Response<String> setCrawlerStatus(@RequestParam("id") Long id,
                                             @RequestParam("status") Integer status) {
        if (!status.equals(0) && !status.equals(1)) {
            return Response.error(ServiceCode.ParamWrong);
        }

        var entity = new CustomApiDatasourceEntity();
        entity.setStatus(status);

        customDatasourceService.updateCustomApiDatasource(id, entity);
        return Response.success();
    }

    @PostMapping("/update")
    public Response<String> updateDatasource(@RequestParam("id") Long id,
                                             @RequestBody CustomApiDatasourceRequest request)
            throws JsonProcessingException {
        var entity = convert(request);
        customDatasourceService.updateCustomApiDatasource(id, entity);
        return Response.success();
    }

    private CustomApiDatasourceEntity convert(@RequestBody CustomApiDatasourceRequest request)
            throws JsonProcessingException
    {
        var entity = new CustomApiDatasourceEntity();
        entity.setName(request.getName());
        entity.setReqUrl(request.getReqUrl());
        entity.setCrawlMethod(request.getCrawlMethod());
        entity.setParams(jacksonObjectMapper.writeValueAsString(request.getParams()));
        entity.setStatus(request.getStatus());

        return entity;
    }

    @PostMapping("/delete")
    public Response<String> deleteDatasource(@RequestParam("id") Long id) {
        customDatasourceService.deleteCustomApiDatasource(id);
        return Response.success();
    }
}
