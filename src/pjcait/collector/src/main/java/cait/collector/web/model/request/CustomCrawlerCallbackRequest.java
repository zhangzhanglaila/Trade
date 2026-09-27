package cait.collector.web.model.request;

import lombok.Data;

import java.util.Map;

@Data
public class CustomCrawlerCallbackRequest {

    private int code;

    private String msg;

    private CallbackData data;

    @Data
    public static class CallbackData {
        private String taskId;
        private Map<String, ResultItem> result;
    }

    @Data
    public static class ResultItem {
        private String title;

        private String date;

        private String desc;

        private String tags;

        private String url;

        private String contentHtml;

        private String contentText;

        private String content;

        private String pics;

    }
}
