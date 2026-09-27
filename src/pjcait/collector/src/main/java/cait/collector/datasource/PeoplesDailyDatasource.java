package cait.collector.datasource;

import cait.collector.configure.OkhttpConfigure;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PeoplesDailyDatasource {

    private static final String IndexPageUrlFormat = "http://finance.people.com.cn/GB/70846/index%d.html";

    private final OkHttpClient okHttpClient;

    public PeoplesDailyDatasource(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    private static final String NewsItemsXpath = "/html/body/div[5]/div[1]/div[2]/ul/li";

    @Data
    @Builder
    public static class NewsItem {
        private String title;
        private String date;
        private String url;
    }

    public List<NewsItem> getNewsRange(int startPage, int endPage, String minDate) throws IOException {
        List<NewsItem> result = new ArrayList<>();

        for (int page = startPage; page <= endPage; page++) {
            var news = getNews(page, minDate);
            if (news.isEmpty()) {
                break;
            }

            result.addAll(news);
        }

        return result;
    }

    public List<NewsItem> getNews(int page, String minDate) throws IOException {
        List<NewsItem> result = new ArrayList<>();

        log.info("Processing page {}", page);
        Document document = Jsoup.parse(requestIndexPage(page));
        var newsItems = document.selectXpath(NewsItemsXpath);
        for (var item : newsItems) {
            var newsItemBuilder = NewsItem.builder();
            var aTag = item.getElementsByTag("a").first();
            if (aTag == null) {
                continue;
            }

            var dateElement = item.getElementsByTag("em").first();

            newsItemBuilder.title(aTag.text());
            newsItemBuilder.url(aTag.attr("href"));

            if (minDate != null && dateElement != null && dateElement.text().compareTo(minDate) < 0) {
                break;
            }

            newsItemBuilder.date(dateElement == null ? "" : dateElement.text());

            result.add(newsItemBuilder.build());
        }

        log.info("Page {} process finished, {} pages", page, result.size());

        return result;
    }

    public List<String> getNewsToday() {
        return new ArrayList<>();
    }

    private String requestIndexPage(int page) throws IOException {
        Request request = new Request.Builder()
                .url(IndexPageUrlFormat.formatted(page))
                .headers(Headers.of(OkhttpConfigure.DefaultHeaders))
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Request PeoplesDailyDatasource index page failed: {}", response.code());
            }

            if (response.body() != null) {
                return response.body().string();
            }
        }

        return "";
    }
}
