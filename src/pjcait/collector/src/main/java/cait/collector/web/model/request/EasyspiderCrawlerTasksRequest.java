package cait.collector.web.model.request;

import lombok.Data;

import java.util.Map;

@Data
public class EasyspiderCrawlerTasksRequest {

    private String name;

    private String taskId;

    private Map<String, String> params;

    private int status;
}
