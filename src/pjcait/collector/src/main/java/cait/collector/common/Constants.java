package cait.collector.common;

import java.time.format.DateTimeFormatter;

public class Constants {

    public static final DateTimeFormatter DefaultDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DefaultDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DefaultTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static class Kafka {
        public static class Topics {
            public static final String CrawlJob = "cait.topic.collector.job";
            public static final String ProcessJob = "cait.topic.processor.job";
        }

        public static class ConsumerGroups {
            public static final String CrawlWorker = "cait.group.collector.worker";
            public static final String Processor = "cait.group.processor.processor";
        }
    }

    public static class Redis {
        public static final String CrawlTaskInfo = "cait:collector:task:%s";
        public static final String CrawlTaskStatus = "cait:collector:task:status:%s";
        public static final String EasySpiderTaskCallbackInfo = "cait:collector:es-cb:info:%s";
        public static final String CustomDatasourceCallbackInfo = "cait:collector:ds:custom:info:%s";
        public static final String CustomDatasourceLastDataTime = "cait:collector:ds:custom:info:last:%s";
    }
}
