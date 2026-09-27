package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EasyspiderDatasourceListResponse {

    private String id;

    private String name;

    private String taskId;

    private String url;

    /**
     * 指示了获取到页面url之后应当用什么方法爬取页面内容
     */
    private int crawlMethod;

    private String params;

    private Long createTime;

    private Long updateTime;

    private Integer status;

}
