package cait.collector.worker.crawler;

import cait.collector.worker.crawler.cleaner.NewsContentCleaner;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class RawHtmlCrawler extends WholePageHtmlCrawler {

    private final OkHttpClient okHttpClient;

    public RawHtmlCrawler(
            @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate,
            DataStorage dataStorage, OkHttpClient okHttpClient,
            NewsContentCleaner newsContentCleaner) {

        super(byteRedisTemplate, dataStorage, newsContentCleaner);
        this.okHttpClient = okHttpClient;
    }

    public Map<String, DataStorage.StorageResult> crawl(CollectorPb.PageCrawlTask task) {
        return super.crawl(task);
    }

    private static final int MinFilterLineLength = 12;

    @Override
    protected String getPage(String url) throws IOException {
        Document document = Jsoup.parse(requestPage(url));

        var body = document.body();
        var wholeText = normalizeWhitespace(body.wholeText());

        return filterLines(wholeText, MinFilterLineLength);
    }

    private static final int PageMaxSize = 10*1024*1024;

    private String requestPage(String url) throws IOException {
        var request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (var response = okHttpClient.newCall(request).execute()) {
            var body = response.body();
            if (body != null) {
                var contentLength = body.contentLength();
                if (contentLength < PageMaxSize) {
                    return readString(body.byteStream());
                } else {
                    throw new IOException("Context length is too large: " + contentLength);
                }
            }
        }

        throw new IOException(String.format("Page content is null: %s", url));
    }

    private String readString(InputStream inputStream) throws IOException {
        BoundedInputStream boundedInputStream = new BoundedInputStream.Builder()
                .setMaxCount(PageMaxSize)
                .setInputStream(inputStream)
                .setOnMaxCount((max, count) -> log.warn("Read too much bytes: {}, max {}", count, max))
                .get();

        return IOUtils.toString(boundedInputStream, StandardCharsets.UTF_8);
    }
}
