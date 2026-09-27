package cait.collector.worker.crawler;

import cait.collector.common.Constants;
import cait.collector.worker.crawler.cleaner.NewsContentCleaner;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
public abstract class WholePageHtmlCrawler {

    private final DataStorage dataStorage;
    private final RedisTemplate<String, byte[]> byteRedisTemplate;

    private final NewsContentCleaner newsContentCleaner;

    public WholePageHtmlCrawler(
            @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate,
            DataStorage dataStorage, NewsContentCleaner newsContentCleaner
    ) {
        this.byteRedisTemplate = byteRedisTemplate;
        this.dataStorage = dataStorage;
        this.newsContentCleaner = newsContentCleaner;
    }

    public Map<String, DataStorage.StorageResult> crawl(CollectorPb.PageCrawlTask task) {
        var urls = task.getUrlList();
        var taskStatusRedisKey = Constants.Redis.CrawlTaskStatus.formatted(task.getId());
        var crawlResult = new HashMap<String, DataStorage.StorageResult>();

        for (var url : urls) {
            var urlHash = DigestUtils.sha256Hex(url);
            var status = CollectorPb.CrawlStatus.newBuilder()
                    .setStatus(CollectorPb.CrawlStatusEnum.CrawlStatReady);

            try {
                var statusData = status.build().toByteArray();
                byteRedisTemplate.opsForHash().put(taskStatusRedisKey, urlHash, statusData);
            } catch (Exception e) {
                log.warn("err when crawl status to redis", e);
            }

            try {
                var content = getPage(url);
                if (task.getCleanHtml()) {
                    content = newsContentCleaner.clean(content);
                }

                var storageResult = dataStorage.storage(task.getId(), urlHash, content.getBytes(StandardCharsets.UTF_8));
                crawlResult.put(urlHash, storageResult);

                status.setStatus(CollectorPb.CrawlStatusEnum.CrawlStatFinished);
            } catch (Exception e) {
                log.error(e.getMessage());
                status.setStatus(CollectorPb.CrawlStatusEnum.CrawlStatFailed);
                status.putMessage("task.err", e.toString());
            }

            try {
                var statusData = status.build().toByteArray();
                byteRedisTemplate.opsForHash().put(taskStatusRedisKey, urlHash, statusData);
            } catch (Exception e) {
                log.warn("err when crawl status to redis", e);
            }
        }

        return crawlResult;
    }

    protected static String normalizeWhitespace(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder output = new StringBuilder();
        boolean inWhitespace = false;
        boolean newlineAdded = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == ' ' || ch == '\t' || ch == '\r') {
                if (!inWhitespace) {
                    output.append(' ');
                    inWhitespace = true;
                }
            } else if (ch == '\n') {
                if (!newlineAdded) {
                    output.append('\n');
                    newlineAdded = true;
                    inWhitespace = true; // 也视为空白段
                }
            } else {
                output.append(ch);
                inWhitespace = false;
                newlineAdded = false;
            }
        }

        return output.toString().trim();
    }

    protected static String filterLines(String input, int minLength) {
        var splitText = input.split("\n");

        StringJoiner stringJoiner = new StringJoiner("\n");
        for (var text : splitText) {
            if (text.length() >= minLength) {
                stringJoiner.add(text);
            }
        }

        return stringJoiner.toString();
    }

    protected abstract String getPage(String url) throws IOException;
}
