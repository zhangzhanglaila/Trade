package cait.collector.datasource;

import cait.collector.easyspider.EasyspiderCallbackData;
import cait.collector.easyspider.EasyspiderTaskInvoker;
import cait.common.pb.CollectorPb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class GenericEasyspiderDatasource {
    private final EasyspiderTaskInvoker easyspiderTaskInvoker;

    public GenericEasyspiderDatasource(EasyspiderTaskInvoker easyspiderTaskInvoker) {
        this.easyspiderTaskInvoker = easyspiderTaskInvoker;
    }

    // todo 对于不同的esDatasource（人民网、新华社等等），差别在需要传给es的参数不一样，传递一些通用的参数（如当前时间等等），可以实现一定程度上的通用，
    //  这里还需要实现生成callback之后，在redis中设置任务信息(已在easyspiderTaskInvoker.invokeTask中实现)
    public CollectorPb.EasyspiderCallbackInfo invokeTask(
            String taskId, String url, Map<String, String> extraParams,
            Consumer<EasyspiderCallbackData> callback
    ) throws IOException {
        var now = System.currentTimeMillis();

        var params = new HashMap<String, String>() {{
            put("urlList_0", url);
            put("now", String.valueOf(now));
            if (extraParams != null) {
                putAll(extraParams);
            }
        }};

        return easyspiderTaskInvoker.invokeTask(taskId, params, callback);
    }
}
