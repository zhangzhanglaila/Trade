package cait.collector.web.api.v1;

import cait.collector.configure.datasource.GdeltDatasourceConfiguration;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.model.request.GDELTDatasourceConfigRequest;
import cait.collector.web.model.response.Response;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/configure/datasource/gdelt")
public class GDELTDatasourceController {
    private final GdeltDatasourceConfiguration gdeltDatasourceConfiguration;

    public GDELTDatasourceController(GdeltDatasourceConfiguration gdeltDatasourceConfiguration) {
        this.gdeltDatasourceConfiguration = gdeltDatasourceConfiguration;
    }

    @PostMapping("/set-status")
    public Response<?> setStatus(@RequestParam("status") Integer status) {
        if (!status.equals(1) && !status.equals(0)) {
            return Response.error(ServiceCode.ParamWrong);
        }

        gdeltDatasourceConfiguration.setEnabled(status != 0);

        return Response.success();
    }

    @PostMapping("/configure")
    public Response<?> configure(@RequestBody GDELTDatasourceConfigRequest request) {
        if (request.getEnable() != null) {
            gdeltDatasourceConfiguration.setEnabled(request.getEnable());
        }

        if (request.getCleanHtml() != null) {
            gdeltDatasourceConfiguration.setCleanHtml(request.getCleanHtml());
        }

        return Response.success();
    }
}
