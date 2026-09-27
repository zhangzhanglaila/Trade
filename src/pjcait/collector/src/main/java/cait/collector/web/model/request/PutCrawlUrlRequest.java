package cait.collector.web.model.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PutCrawlUrlRequest {

    private List<String> urls;

    private int crawlType;

    private boolean commitPublish;

    private boolean cleanHtml;

    private Map<String, String> additionalInfo;

}
