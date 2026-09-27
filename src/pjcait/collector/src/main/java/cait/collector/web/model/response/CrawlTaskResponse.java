package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CrawlTaskResponse {

    private String id;

    private Integer crawlType;

    private Integer source;

    private List<String> urls;

    private Boolean cleanHtml;

    private Boolean archive;

    private Map<String, String> additionalInfo;

    private String message;

    private Long createTime;

    private Long updateTime;

    private Integer status;

}
