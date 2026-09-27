package cait.collector.datasource;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

@Service
public class GoogleDatasource {

    // api reference: https://developers.google.com/custom-search/v1/reference/rest/v1/cse/list?hl=zh-cn
    private static final String GoogleSearchApiBase = "https://www.googleapis.com/customsearch/v1";

    @Data
    @Builder
    public static class SearchParameters {

        private String key;

        private String q;

        private String cx;

        private String start;

        private String num;

        private String lr;

        private String dateRestrict;

        private String sort;

    }
}
