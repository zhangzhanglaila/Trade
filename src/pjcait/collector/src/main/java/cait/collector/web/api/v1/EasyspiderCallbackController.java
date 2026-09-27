package cait.collector.web.api.v1;

import cait.collector.easyspider.EasyspiderCallbackData;
import cait.collector.easyspider.EasyspiderTaskInvoker;
import cait.collector.web.model.request.EasyspiderCallbackRequest;
import cait.collector.web.model.response.Response;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/easyspider")
public class EasyspiderCallbackController {

    private final EasyspiderTaskInvoker easyspiderTaskInvoker;

    public EasyspiderCallbackController(EasyspiderTaskInvoker easyspiderTaskInvoker) {
        this.easyspiderTaskInvoker = easyspiderTaskInvoker;
    }

    @PostMapping("/callback")
    public Response<Object> callback(@RequestParam("id") String callbackId,
                                     @RequestBody EasyspiderCallbackRequest request
    ) {
        var cbData = EasyspiderCallbackData.builder()
                .url(request.getUrl())
                .result(request.getResult())
                .data(request.getData())
                .build();

        easyspiderTaskInvoker.execCallback(cbData, callbackId);

        return Response.success();
    }

}
