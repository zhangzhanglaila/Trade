package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EasyspiderCrawlerListResponse {

    private String id;

    private String name;

    private String taskId;

    private String params;

    private Long createTime;

    private Long updateTime;

    private Integer status;

}
