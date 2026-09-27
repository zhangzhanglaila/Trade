package cait.collector.common.model;

public class EnumValues {
    public static class CrawlMethod {
        public static final int PageRaw = 0;
        public static final int Selenium = 1;
        public static final int Adaptor = 2;
        public static final int Easyspider = 3;
        public static final int Custom = 4;
    }

    public static class CrawlTaskStatus {
        public static final int Ready = 0;
        public static final int Published = 1;
        public static final int Received = 2;
        public static final int Finished = 3;
        public static final int Failed = 4;
    }

    public static class DatasourceStatus {
        public static final int Enabled = 0;
        public static final int Disabled = 1;
        public static final int Deleted = 2;
    }

    public static class CustomCrawlerStatus {
        public static final int Enabled = 0;
        public static final int Disabled = 1;
        public static final int Deleted = 2;
    }

}
