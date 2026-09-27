package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CrawlerListResponse {

    private List<CustomCrawlerListResponse> custom;

    private List<EasyspiderCrawlerListResponse> easyspider;

}
