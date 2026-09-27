package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class CustomDatasourceListResponse {

    private String id;

    private String name;

    private String reqUrl;

    /**
     * 指示了获取到页面url之后应当用什么方法爬取页面内容
     */
    private int crawlMethod;

    private String params;

    private Long createTime;

    private Long updateTime;

    private int status;

}
