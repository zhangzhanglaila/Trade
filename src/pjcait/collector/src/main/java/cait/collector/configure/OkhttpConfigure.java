package cait.collector.configure;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class OkhttpConfigure {

    public static final Map<String, String> DefaultHeaders = new HashMap<>() {{
        put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0");
        put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
        put("cache-control", "no-cache");
        put("pragma", "no-cache");
        put("priority", "u=0, i");
        put("upgrade-insecure-requests", "1");
    }};

    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(Duration.of(10, ChronoUnit.SECONDS));
        return builder.build();
    }
}
