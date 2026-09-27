package com.example.tdproject.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private TradeSpringBoot tradeSpringBoot = new TradeSpringBoot();
    private Flask flask = new Flask();
    private Qwen qwen = new Qwen();
    private Milvus milvus = new Milvus();

    @Data
    public static class TradeSpringBoot {
        /** 例如 http://127.0.0.1:8081/api */
        private String baseUrl;
    }

    @Data
    public static class Flask {
        /** 例如 http://127.0.0.1:5000 */
        private String baseUrl;
    }

    @Data
    public static class Qwen {
        /** API Key 建议走环境变量注入，不要提交到版本库 */
        private String apiKey;
        /** baseUrl 由你们提供（避免硬编码/猜测） */
        private String baseUrl;

        private Chat chat = new Chat();
        private Embedding embedding = new Embedding();

        /** endpoints: e.g. {chat: "/...", embedding: "/..."} */
        private Map<String, String> endpoints;

        @Data
        public static class Chat {
            private String model;
        }

        @Data
        public static class Embedding {
            private String model;
        }
    }

    @Data
    public static class Milvus {
        private String host;
        private Integer port;
        private String collection;
        private Integer dimension;
        private Integer topK = 5;
    }
}
