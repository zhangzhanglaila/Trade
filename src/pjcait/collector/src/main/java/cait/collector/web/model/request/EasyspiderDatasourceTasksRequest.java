package cait.collector.web.model.request;

import lombok.Data;

import java.util.Map;

@Data
public class EasyspiderDatasourceTasksRequest {

    private String name;

    private String taskId;

    private String url;

    /**
     * 指示了获取到页面url之后应当用什么方法爬取页面内容
     */
    private int crawlMethod;

    private Map<String, String> params;

    private int status;

}
