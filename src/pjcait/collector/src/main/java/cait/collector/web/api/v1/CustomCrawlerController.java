package cait.collector.web.api.v1;

import cait.collector.common.model.db.CustomCrawlerEntity;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.model.request.CustomCrawlerRequest;
import cait.collector.web.model.response.Response;
import cait.collector.web.service.CustomCrawlerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/configure/crawler/custom")
public class CustomCrawlerController {

    private final CustomCrawlerService customCrawlerService;
    private final ObjectMapper jacksonObjectMapper;

    public CustomCrawlerController(CustomCrawlerService customCrawlerService, ObjectMapper jacksonObjectMapper) {
        this.customCrawlerService = customCrawlerService;
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    @PostMapping("/add")
    public Response<String> add(@RequestBody CustomCrawlerRequest request) throws JsonProcessingException {
        var entity = convert(request);

        customCrawlerService.addCustomCrawler(entity);

        return Response.success();
    }

    @PostMapping("/update")
    public Response<String> updateCrawler(@RequestParam("id") Long id,
                                          @RequestBody CustomCrawlerRequest request) throws JsonProcessingException {
        var entity = convert(request);
        customCrawlerService.updateCustomCrawler(id, entity);
        return Response.success();
    }

    @PostMapping("/set-status")
    public Response<String> setCrawlerStatus(@RequestParam("id") Long id,
                                          @RequestParam("status") Integer status) {
        if (!status.equals(0) && !status.equals(1)) {
            return Response.error(ServiceCode.ParamWrong);
        }

        var entity = new CustomCrawlerEntity();
        entity.setStatus(status);

        customCrawlerService.updateCustomCrawler(id, entity);
        return Response.success();
    }

    private CustomCrawlerEntity convert(@RequestBody CustomCrawlerRequest request) throws JsonProcessingException {
        var entity = new CustomCrawlerEntity();
        entity.setName(request.getName());
        entity.setReqUrl(request.getReqUrl());
        entity.setParams(jacksonObjectMapper.writeValueAsString(request.getParams()));
        entity.setStatus(request.getStatus());

        return entity;
    }

    @PostMapping("/delete")
    public Response<String> deleteCrawler(@RequestParam("id") Long id) {
        customCrawlerService.deleteCustomCrawler(id);
        return Response.success();
    }
}
