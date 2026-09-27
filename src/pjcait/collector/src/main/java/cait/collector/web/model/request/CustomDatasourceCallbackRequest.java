package cait.collector.web.model.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CustomDatasourceCallbackRequest {

    private int code;

    private String msg;

    private Response data;

    @Data
    public static class Response {

        private List<ResultItem> result;

        private Map<String, String> metadata;

    }

    @Data
    public static class ResultItem {

        private String title;

        private String date;

        private String cover;

        private String link;

    }

}
