package cait.collector.worker.crawler.cleaner;

import cait.collector.configure.OpenAIClientConfig;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NewsContentCleaner {

    private final OpenAIClient openAIClient;
    private final OpenAIClientConfig openAIClientConfig;

    public NewsContentCleaner(OpenAIClient openAIClient, OpenAIClientConfig openAIClientConfig) {
        this.openAIClient = openAIClient;
        this.openAIClientConfig = openAIClientConfig;
    }

    private static final String CleanRequestPromptTemplate = """
            你将从一段从新闻网页上抓取下来的文本进行清洗。由于抓取的文本是整个网页，因此包含了许多与新闻正文无关的内容，如网站导航文本、广告文本，以及与当前新闻内容无关的其他新闻文本等。通常你需要提取的新闻正文内容在文本的中间位置，所以通常来说把文本的前部和后部剔除，即为新闻的正文。请你理解新闻内容并识别不需要的部分，然后将新闻正文内容原样输出，不要做修改，并且除了新闻正文内容，其他内容都不要输出。
            
            接下来请你进行处理：
            ```
            {{ %s }}
            ```
            
            文本结束，请输出你的处理结果：
            
            """;

    public String clean(String content) {
        var text = CleanRequestPromptTemplate.formatted(content);
        var request = ChatCompletionCreateParams.builder()
                .model(openAIClientConfig.getModel())
                .maxCompletionTokens(5600)
                .temperature(openAIClientConfig.getTemperature())
                .addSystemMessage(openAIClientConfig.getSystemPrompt())
                .addMessage(ChatCompletionUserMessageParam.builder().content(text).build())
                .build();

        var sb = new StringBuilder();
        try(var response = openAIClient.chat().completions().createStreaming(request)){
            var stream = response.stream();
            stream.forEach(chunk -> chunk._choices().asKnown()
                    .flatMap(choices -> choices.stream()
                            .findFirst()
                            .flatMap(choice -> choice.delta().content())
                    )
                    .ifPresent(sb::append));
        } catch (Exception e) {
            log.error("error when cleaning text content", e);
            throw new RuntimeException(e);
        }

        return sb.toString();
    }
}
