package com.example.tdproject.ai.http;

import com.example.tdproject.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 统一的大模型客户端（OpenAI 兼容 HTTP 协议）。
 *
 * <p>对话与嵌入分别独立配置，因此可以对话用 DeepSeek、嵌入用 SiliconFlow。
 * 只要目标服务提供 <code>/chat/completions</code> 与 <code>/embeddings</code>
 * 这两个 OpenAI 风格的接口（DeepSeek、SiliconFlow、vLLM、Ollama、DashScope 兼容模式均满足），
 * 改 <code>ai.llm.*</code> 配置即可切换，无需改代码。</p>
 *
 * <p>本类取代了原先写死 DashScope 协议的 {@code DashScopeClient}：
 * 原实现只认千问私有返回结构，且依赖一个从未配置过的 DASHSCOPE_API_KEY，
 * 导致问答的 RAG 分支必然抛「配置不完整」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Java HttpClient 默认 keep-alive 为 1200s，明显长于本网络路径上中间设备
        // 实际保留空闲连接的时间。连接被对端静默丢弃后仍留在池里，再次复用时会
        // 把请求写进黑洞并一直挂到超时。
        //
        // 证据：同一负载（batch=64、并发 4、200 次连续请求）用 Python/urllib 打，
        // 100% 成功、吞吐 3.3 批次/s、单次最慢 3.1s，说明接口与网络都没问题；
        // 而 Java 侧会零星出现正好等于请求超时的 120s 挂起，且第 1/2/3 次重试
        // 全部挂死（重试复用到池里另一条已失效的连接）。
        // 缩短到 20s，让空闲连接在变陈旧之前就被客户端主动关闭。
        if (System.getProperty("jdk.httpclient.keepalive.timeout") == null) {
            System.setProperty("jdk.httpclient.keepalive.timeout", "20");
        }
    }

    /**
     * HTTP 客户端。之所以不是 final：命中超时后需要整体重建以丢弃连接池
     * （见 {@link #resetHttpClient()}）。
     */
    private volatile HttpClient httpClient = buildHttpClient();

    private static HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                // 强制 HTTP/1.1。实测在本机网络上，Java 默认的 HTTP/2 多路复用
                // 在「并发 + 数百 KB 请求体」时会卡死：socket 的 Send-Q 堆积数万字节、
                // 对端不再读取，请求一直挂到超时（同一负载用 HTTP/1.1 的客户端 1~3 秒即返回）。
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 重建 HTTP 客户端，丢弃整个连接池。
     *
     * <p>超时（{@code HttpTimeoutException}）在本项目里几乎总是「池中存在半开连接」
     * 而非真的慢：实测同负载用 Python 客户端 200 次连续请求 100% 成功。此时若沿用
     * 原客户端重试，重试很可能又挑中另一条已失效的连接，三次重试一起挂死 ——
     * 这正是此前建索引卡在数千条不动、日志里成片 {@code request timed out} 的原因。
     * 重建后每条重试都走全新连接，才能真正自愈。</p>
     */
    private void resetHttpClient() {
        httpClient = buildHttpClient();
        log.warn("已重建 LLM HTTP 客户端（丢弃原连接池），以排除半开连接；原客户端交由 GC 回收");
    }

    /** 对话接口是否已配置可用的凭据。 */
    public boolean isChatConfigured() {
        AiProperties.Llm.Endpoint c = props.getLlm().getChat();
        return notBlank(c.getBaseUrl()) && notBlank(c.getApiKey()) && notBlank(c.getModel());
    }

    /** 嵌入接口是否已配置可用的凭据。 */
    public boolean isEmbeddingConfigured() {
        AiProperties.Llm.Embedding e = props.getLlm().getEmbedding();
        return notBlank(e.getBaseUrl()) && notBlank(e.getApiKey()) && notBlank(e.getModel());
    }

    public String chatDescription() {
        AiProperties.Llm.Endpoint c = props.getLlm().getChat();
        return c.getModel() + " @ " + c.getBaseUrl();
    }

    // ==================================================================
    // 对话
    // ==================================================================

    /**
     * 生成式对话。
     *
     * @param messages    [{role: "system"|"user"|"assistant", content: "..."}]
     * @param extraParams 额外请求体字段，如 {temperature: 0.2}
     * @return 模型回复正文；无法解析时抛异常并附带原始响应
     */
    public String chat(List<Map<String, String>> messages, Map<String, Object> extraParams) {
        AiProperties.Llm.Endpoint c = props.getLlm().getChat();
        if (!isChatConfigured()) {
            throw new IllegalStateException(
                    "对话大模型未配置：请设置 ai.llm.chat.base-url / api-key / model"
                            + "（对应 config/.env 的 DEEPSEEK_BASE_URL、DEEPSEEK_API_KEY）");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", c.getModel());
        body.put("messages", messages);
        body.put("stream", false);
        if (extraParams != null) {
            body.putAll(extraParams);
        }

        JsonNode root = postJson(url(c.getBaseUrl(), c.getEndpointPath()), c.getApiKey(), body);
        String content = extractChatContent(root);
        if (content == null) {
            throw new IllegalStateException("无法从对话接口返回中解析正文：" + abbreviate(root.toString()));
        }
        return content;
    }

    private String extractChatContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode msg = choices.get(0).path("message");
            // 推理类模型可能把正文放在 content、思维链放在 reasoning_content；
            // 极少数情况下 content 为空而 reasoning_content 有值，做一次兜底。
            String c = textOrNull(msg.path("content"));
            if (c != null) return c;
            String r = textOrNull(msg.path("reasoning_content"));
            if (r != null) return r;
        }
        // DashScope 原生格式兜底
        String t = textOrNull(root.path("output").path("text"));
        if (t != null) return t;
        // 部分实现直接返回 {"content": "..."}
        return textOrNull(root.path("content"));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String s = node.asText();
        return (s == null || s.isBlank()) ? null : s;
    }

    // ==================================================================
    // 嵌入
    // ==================================================================

    /** 单条文本向量化。 */
    public List<Double> embed(String text) {
        List<List<Double>> r = embedBatch(List.of(text == null ? "" : text));
        return r.isEmpty() ? List.of() : r.get(0);
    }

    /**
     * 批量向量化。OpenAI 兼容接口支持 input 传数组，一次请求多条能显著减少建索引耗时。
     *
     * @return 与入参等长、顺序一致的向量列表
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        AiProperties.Llm.Embedding e = props.getLlm().getEmbedding();
        if (!isEmbeddingConfigured()) {
            throw new IllegalStateException(
                    "嵌入模型未配置：请设置 ai.llm.embedding.base-url / api-key / model"
                            + "（对应 config/.env 的 SILICONFLOW_BASE_URL、SILICONFLOW_API_KEY）");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", e.getModel());
        body.put("input", texts);
        body.put("encoding_format", "float");

        JsonNode root = postJson(url(e.getBaseUrl(), e.getEndpointPath()), e.getApiKey(), body);

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("嵌入接口返回缺少 data 数组：" + abbreviate(root.toString()));
        }

        // 按 index 归位，避免服务端乱序返回
        List<List<Double>> out = new ArrayList<>(Collections.nCopies(texts.size(), null));
        for (JsonNode item : data) {
            int idx = item.path("index").asInt(-1);
            if (idx < 0 || idx >= texts.size()) {
                // 未返回 index 时按出现顺序回填
                idx = nextFreeSlot(out);
                if (idx < 0) break;
            }
            JsonNode emb = item.path("embedding");
            if (!emb.isArray()) {
                throw new IllegalStateException("嵌入接口返回缺少 embedding 数组：" + abbreviate(item.toString()));
            }
            List<Double> vec = new ArrayList<>(emb.size());
            for (JsonNode v : emb) {
                vec.add(v.asDouble());
            }
            out.set(idx, vec);
        }

        // 个别位置为空说明服务端条数对不上，用空向量占位并告警
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) {
                log.warn("嵌入接口返回条数不足，第 {} 条缺失（请求 {} 条，返回 {} 条）", i, texts.size(), data.size());
                out.set(i, List.of());
            }
        }
        return out;
    }

    private int nextFreeSlot(List<List<Double>> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) return i;
        }
        return -1;
    }

    // ==================================================================
    // HTTP
    // ==================================================================

    private JsonNode postJson(String url, String apiKey, Object body) {
        AiProperties.Llm cfg = props.getLlm();
        int maxAttempts = cfg.getMaxRetries() == null || cfg.getMaxRetries() < 1 ? 1 : cfg.getMaxRetries();
        long timeoutSec = cfg.getTimeoutSeconds() == null || cfg.getTimeoutSeconds() < 1 ? 120 : cfg.getTimeoutSeconds();

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("请求体序列化失败: " + e.getMessage(), e);
        }

        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startNs = System.nanoTime();
            // 本次尝试失败后的退避时长：命中 429 时由 backoffMs() 给出秒级值，
            // 其余可重试错误沿用毫秒级小幅退避。
            long throttleMs = 400L * attempt;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = resp.statusCode();
                long costMs = (System.nanoTime() - startNs) / 1_000_000;

                if (code / 100 == 2) {
                    log.info("LLM 调用成功 {} HTTP {} 请求 {} 字节 / 响应 {} 字节 耗时 {} ms",
                            url, code, json.length(), resp.body().length(), costMs);
                    return objectMapper.readTree(resp.body());
                }

                String msg = "大模型接口 HTTP " + code + ": " + abbreviate(resp.body());
                // 4xx（除 429）属请求本身的问题，重试无意义
                if (code != 429 && code / 100 == 4) {
                    throw new IllegalStateException(msg);
                }
                last = new IllegalStateException(msg);
                if (code == 429) {
                    // 429 是配额/速率限制（实测 SiliconFlow 返回
                    // "Request was rejected due to rate limiting. Details: TPM limit reached."）。
                    // 此时用原来的 400ms 毫秒级退避重试毫无意义：瞬时重试只会持续撞满
                    // 配额窗口，三次重试全部失败，还会把窗口继续占住。改为秒级指数退避，
                    // 并优先遵从服务端给出的 Retry-After。
                    throttleMs = backoffMs(resp, attempt);
                }
                log.warn("调用 {} 失败（第 {}/{} 次，{} ms，退避 {} ms）：{}",
                        url, attempt, maxAttempts, costMs, throttleMs, msg);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("大模型请求被中断: " + e.getMessage(), e);
            } catch (HttpTimeoutException te) {
                // HttpTimeoutException 是 IOException 的子类，必须放在它前面捕获。
                // 这里的超时几乎都源于「连接池里存在半开连接」而非真的慢（实测同负载
                // 用 Python 客户端 200 次连续请求 100% 成功）。若不重建连接池，重试会
                // 再次挑中失效连接，三次重试一起挂死 —— 建索引停摆就是这么来的。
                long costMs = (System.nanoTime() - startNs) / 1_000_000;
                last = new RuntimeException("大模型请求超时: " + te.getMessage(), te);
                log.warn("调用 {} 超时（第 {}/{} 次，{} ms）：{}，重建连接池后重试",
                        url, attempt, maxAttempts, costMs, te.getMessage());
                resetHttpClient();
            } catch (IOException e) {
                long costMs = (System.nanoTime() - startNs) / 1_000_000;
                last = new RuntimeException("大模型请求失败: " + e.getMessage(), e);
                log.warn("调用 {} 异常（第 {}/{} 次，{} ms）：{}", url, attempt, maxAttempts, costMs, e.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(throttleMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        throw last != null ? last : new IllegalStateException("大模型请求失败");
    }

    /**
     * 429（配额/速率限制）的退避时长。
     *
     * <p>优先遵从服务端的 {@code Retry-After}（秒）；没有该响应头时用指数退避 + 抖动，
     * 上限 60s。加抖动是为了避免多个 rag-embed 线程在同一时刻同时重试，再次把
     * 配额窗口一次性打满。</p>
     */
    private long backoffMs(HttpResponse<?> resp, int attempt) {
        try {
            Optional<String> ra = resp.headers().firstValue("Retry-After");
            if (ra.isPresent()) {
                long sec = Long.parseLong(ra.get().trim());
                return Math.min(120_000L, Math.max(1_000L, sec * 1000L));
            }
        } catch (Exception ignore) {
            // Retry-After 可能是 HTTP-date 而非秒数，解析失败就走指数退避
        }
        long base = Math.min(60_000L, 5_000L * (1L << Math.min(attempt - 1, 4)));
        return base + (long) (Math.random() * 1_000L);
    }

    private String url(String baseUrl, String path) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (path == null || path.isBlank()) return b;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
