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
    /** 旧版千问配置，已被 llm 取代；仅为向后兼容保留。 */
    private Qwen qwen = new Qwen();
    private Milvus milvus = new Milvus();
    /** 问答大模型（统一走 OpenAI 兼容 HTTP 协议）。 */
    private Llm llm = new Llm();
    /** 向量库选型。 */
    private Vector vector = new Vector();

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

    /**
     * 问答所用的大模型配置。
     * <p>chat 与 embedding 允许指向两家不同的服务商，例如：
     * 对话用 DeepSeek（deepseek-chat），嵌入用 SiliconFlow 的 BAAI/bge-m3（1024 维）。
     * 若本地部署了 vLLM / Ollama 等 OpenAI 兼容服务，把 base-url 指过去即可无缝切换。</p>
     */
    @Data
    public static class Llm {
        private Endpoint chat = new Endpoint();
        private Embedding embedding = new Embedding();
        /** 单次 HTTP 请求超时（秒）。 */
        private Integer timeoutSeconds = 60;
        /** 失败重试次数（网络抖动 / 限流时有用）。 */
        private Integer maxRetries = 3;

        /** 通用 OpenAI 兼容端点。 */
        @Data
        public static class Endpoint {
            /** 不含路径的根地址，如 https://api.deepseek.com */
            private String baseUrl;
            private String apiKey;
            private String model;
            /** 相对路径，如 /chat/completions */
            private String endpointPath;
        }

        @Data
        public static class Embedding {
            private String baseUrl;
            private String apiKey;
            private String model;
            private String endpointPath;
            /** 向量维度，必须与向量库一致。 */
            private Integer dimension = 1024;
            /** 单次请求携带的文本条数（批量建索引时用）。 */
            private Integer batchSize = 64;
            /** 建索引时的并发请求数；实测 4 并发比单线程快 2 倍以上。 */
            private Integer concurrency = 4;
        }
    }

    /** 向量库选型：provider = local | milvus */
    @Data
    public static class Vector {
        private String provider = "local";
        private Integer topK = 5;
    }
}
