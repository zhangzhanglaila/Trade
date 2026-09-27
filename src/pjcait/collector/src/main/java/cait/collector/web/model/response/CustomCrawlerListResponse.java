package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomCrawlerListResponse {

    private String id;

    private String name;

    private String reqUrl;

    private String params;

    private Long createTime;

    private Long updateTime;

    private int status;

}
