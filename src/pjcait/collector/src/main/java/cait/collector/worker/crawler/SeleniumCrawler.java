package cait.collector.worker.crawler;

import cait.collector.worker.crawler.cleaner.NewsContentCleaner;
import cait.collector.worker.storage.DataStorage;
import cait.common.pb.CollectorPb;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.StringJoiner;

@Service
public class SeleniumCrawler extends WholePageHtmlCrawler {


    public SeleniumCrawler(
            @Qualifier("byteRedisTemplate") RedisTemplate<String, byte[]> byteRedisTemplate,
            DataStorage dataStorage, NewsContentCleaner newsContentCleaner
    ) {

        super(byteRedisTemplate, dataStorage, newsContentCleaner);
    }

    private final ThreadLocal<WebDriver> driverLocal = new ThreadLocal<>();

    private WebDriver acquireDriver() {
        synchronized (driverLocal) {
            if (driverLocal.get() == null) {
                driverLocal.set(new ChromeDriver());
            }

            return driverLocal.get();
        }
    }

    public Map<String, DataStorage.StorageResult> crawl(CollectorPb.PageCrawlTask task) {
        return super.crawl(task);
    }

    private static final int MinFilterLineLength = 12;

    @Override
    protected String getPage(String url) {
        var driver = acquireDriver();
        driver.get(url);

        var html = driver.getPageSource();

        if (html == null) {
            return "";
        }

        Document document = Jsoup.parse(html);
        var body = document.body();

        var wholeText = normalizeWhitespace(body.wholeText());

        return filterLines(wholeText, MinFilterLineLength);
    }
}
