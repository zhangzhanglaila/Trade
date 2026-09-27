package cait.collector.configure;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIClientConfig {
    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api_key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.temperature}")
    private Double temperature;

    @Value("${llm.system-prompt}")
    private String systemPrompt;

    @Bean
    public OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}
