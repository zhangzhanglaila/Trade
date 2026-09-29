package com.example.tdproject.ai.rag;

import com.example.tdproject.ai.config.AiProperties;
import com.example.tdproject.ai.dto.RagHit;
import com.example.tdproject.ai.http.LlmClient;
import com.example.tdproject.generator.domain.TNewsCorpus;
import com.example.tdproject.generator.service.TNewsCorpusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRagService {

    /** 单条新闻进入提示词的内容上限。 */
    private static final int SNIPPET_MAX = 800;
    /**
     * 单次进入提示词的片段数上限的兜底值（正常从 ai.vector.topK 读取）。
     *
     * <p>「检索候选条数」与「进入提示词的条数」是两件事，这里刻意把它们分开：</p>
     * <ul>
     *   <li><b>上下文条数</b>（ai.vector.topK）：真正拼进提示词的片段数，直接决定大模型耗时。</li>
     *   <li><b>候选条数</b>（ai.vector.candidate-topk）：一次取回并展示的条数，
     *       决定来源列表有多长，也就是用户能看见「一共捞到多少条」。</li>
     * </ul>
     * <p>本地向量库是对全部向量做一遍余弦扫描，取 5 条和取 20 条的计算量几乎相同，
     * 所以放大候选条数可以在不改耗时的前提下把「检索规模」如实呈现给用户。</p>
     */
    private static final int DEFAULT_CONTEXT_DOCS = 5;

    /** 候选条数的兜底值。 */
    private static final int DEFAULT_CANDIDATE_TOPK = 20;

    /**
     * 国家关键词表：news_articles 表里并没有 country 列，
     * 建索引时用标题+正文做一次轻量匹配，把国家落到向量库的过滤字段上。
     * 命中不到就留空（检索时也不会被过滤条件误伤）。
     */
    private static final String[] COUNTRY_KEYWORDS = {
            "哈萨克斯坦", "乌兹别克斯坦", "吉尔吉斯斯坦", "塔吉克斯坦", "土库曼斯坦",
            "俄罗斯", "白俄罗斯", "乌克兰", "阿塞拜疆", "格鲁吉亚", "亚美尼亚", "蒙古",
            "中国", "巴基斯坦", "印度", "伊朗", "土耳其", "阿富汗",
            "德国", "波兰", "荷兰", "意大利", "法国", "英国", "西班牙", "美国", "日本", "韩国"
    };

    private final LlmClient llmClient;
    private final NewsVectorStore vectorStore;
    private final TNewsCorpusService newsCorpusService;
    private final AiProperties props;
    private final DataSource dataSource;

    private volatile JdbcTemplate jdbcTemplate;

    // ==================================================================
    // 问答
    // ==================================================================

    public RagAnswer answer(String question, String country, Integer year) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        int ctxDocs = contextDocs();
        int candidateK = candidateTopK(ctxDocs);

        // 1) 问题向量化
        List<Double> vec = llmClient.embed(question);
        if (vec == null || vec.isEmpty()) {
            throw new IllegalStateException("问题向量化失败：嵌入服务返回空向量");
        }
        float[] q = toFloatArray(vec);

        // 2) 向量检索；带过滤条件时若无结果，自动放宽一次（语料的国家/年份是尽力派生的，可能命中不到）
        List<Map<String, Object>> hits = vectorStore.search(q, country, year, candidateK);
        if (hits.isEmpty() && (country != null || year != null)) {
            log.info("带过滤条件(country={}, year={})检索为空，放宽条件重试", country, year);
            hits = vectorStore.search(q, null, null, candidateK);
        }
        if (hits.isEmpty()) {
            // 兜底：索引可能还没建
            int n = corpusSize();
            String tip = n == 0
                    ? "当前向量索引为空。请先执行 POST /ai/rag/index/full 构建索引。"
                    : "换一个更具体的问法（例如带上国家、商品或时间）。";
            return RagAnswer.builder()
                    .answer("已对当前全部新闻语料逐条比对，未找到与问题相关的内容。" + tip)
                    .sources(List.of())
                    .corpusSize(n)
                    .contextDocs(0)
                    .build();
        }

        // 3) 回 MySQL 取正文
        List<Long> ids = new ArrayList<>(hits.size());
        for (Map<String, Object> h : hits) {
            Object id = h.get("newsId");
            if (id instanceof Number) {
                ids.add(((Number) id).longValue());
            }
        }
        List<TNewsCorpus> corpusList = ids.isEmpty() ? List.of() : newsCorpusService.listByIds(ids);
        Map<Long, TNewsCorpus> byId = new HashMap<>();
        for (TNewsCorpus c : corpusList) {
            byId.put(c.getId(), c);
        }

        List<RagHit> sources = new ArrayList<>(hits.size());
        StringBuilder context = new StringBuilder();
        int usedInContext = 0;
        for (Map<String, Object> h : hits) {
            Object rawId = h.get("newsId");
            if (!(rawId instanceof Number)) continue;
            long id = ((Number) rawId).longValue();
            Object rawScore = h.get("score");
            Double score = rawScore instanceof Number ? ((Number) rawScore).doubleValue() : null;

            TNewsCorpus c = byId.get(id);
            if (c == null) continue;

            // 只有得分最高的前 ctxDocs 条会进提示词；其余仅作「还捞到了这些」展示，
            // 这样既不影响大模型耗时，也不会让用户以为检索只返回了寥寥几条。
            boolean used = usedInContext < ctxDocs;
            if (used) usedInContext++;

            sources.add(RagHit.builder()
                    .newsId(id)
                    .title(c.getNewsTitle())
                    .source(c.getNewsSource())
                    .publishTime(c.getPublishTime())
                    .score(score)
                    .usedInContext(used)
                    .build());

            if (!used) continue;

            String content = c.getNewsContent() == null ? "" : c.getNewsContent();
            if (content.length() > SNIPPET_MAX) {
                content = content.substring(0, SNIPPET_MAX) + "…";
            }
            context.append("【ID:").append(id).append(" | ")
                    .append(c.getPublishTime() == null ? "时间不详" : c.getPublishTime()).append("】\n")
                    .append("标题：").append(c.getNewsTitle()).append("\n")
                    .append("内容：").append(content).append("\n\n");
        }

        if (sources.isEmpty()) {
            return RagAnswer.builder()
                    .answer("检索到了向量结果，但在新闻表中未找到对应记录（索引与语料可能不同步，建议重建索引）。")
                    .sources(List.of())
                    .corpusSize(corpusSize())
                    .contextDocs(0)
                    .build();
        }

        // 4) 生成回答
        String system = "你是中哈贸易物流领域的新闻问答助手。\n"
                + "规则：\n"
                + "1. 只能依据我提供的【新闻片段】作答，禁止编造片段中不存在的数据、时间或结论。\n"
                + "2. 回答末尾用 (来源ID=xxx) 的形式标注所依据的片段。\n"
                + "3. 若片段不足以回答，请直接说明“现有语料不足以判断”，并指出还缺什么信息。\n"
                + "4. 用简体中文作答，条理清晰，不要重复罗列片段原文。";

        String user = "问题：" + question + "\n\n【新闻片段】\n" + context;

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user));

        String answer;
        try {
            answer = llmClient.chat(messages, Map.of("temperature", 0.2));
        } catch (Exception e) {
            log.error("生成回答失败: {}", e.getMessage(), e);
            // 大模型不可用时，至少把检索结果还给用户，不让整个问答直接失败
            answer = "（大模型调用失败：" + e.getMessage() + "）\n"
                    + "以下是与问题最相关的新闻片段，请自行参考：\n\n" + context;
        }

        return RagAnswer.builder()
                .answer(answer)
                .sources(sources)
                .corpusSize(corpusSize())
                .contextDocs(usedInContext)
                .build();
    }

    // ==================================================================
    // 建索引
    // ==================================================================

    /**
     * 全量建索引（从数据库流式读取）。
     *
     * <p>刻意<b>不用</b> {@code newsCorpusService.list()}：项目的 MyBatis 配置了
     * {@code log-impl: StdOutImpl}，一次 list() 会把 2.8 万行逐行打到 stdout，
     * 产生十几万行日志，既拖慢读取又淹没日志文件。这里直接用 JDBC 只取需要的 4 列，
     * 按 id 游标分页，内存里始终只保留一页语料。</p>
     *
     * @param limit 只索引前 N 条（调试用），<=0 表示全量
     * @return 成功索引的条数
     */
    public int indexAll(int limit) {
        vectorStore.ensureCollection(false);
        int pageSize = Math.max(500, batchSize() * 8);
        long afterId = 0L;
        int done = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            List<TNewsCorpus> rows = fetchPage(afterId, pageSize, limit > 0 ? limit - done : 0);
            if (rows.isEmpty()) break;

            afterId = rows.get(rows.size() - 1).getId();
            done += indexFull(rows);
            log.info("已处理 {} 条，累计耗时 {} s", done, (System.currentTimeMillis() - t0) / 1000);

            if (limit > 0 && done >= limit) break;
        }

        log.info("全部完成：成功 {} 条，向量库共 {} 条，总耗时 {} s",
                done, vectorStore.count(), (System.currentTimeMillis() - t0) / 1000);
        return done;
    }

    /** 按 id 游标取一页语料（只查建索引需要的列）。 */
    private List<TNewsCorpus> fetchPage(long afterId, int size, int remaining) {
        int n = size;
        if (remaining > 0) {
            n = Math.min(size, remaining);
        }
        if (n <= 0) return List.of();

        return jdbcTemplate().query(
                "SELECT id, title, content, publish_time FROM news_articles "
                        + "WHERE id > ? ORDER BY id ASC LIMIT ?",
                (rs, rowNum) -> {
                    TNewsCorpus c = new TNewsCorpus();
                    c.setId(rs.getLong("id"));
                    c.setNewsTitle(rs.getString("title"));
                    c.setNewsContent(rs.getString("content"));
                    c.setPublishTime(rs.getTimestamp("publish_time"));
                    return c;
                },
                afterId, n);
    }

    private JdbcTemplate jdbcTemplate() {
        if (jdbcTemplate == null) {
            synchronized (this) {
                if (jdbcTemplate == null) {
                    jdbcTemplate = new JdbcTemplate(dataSource);
                }
            }
        }
        return jdbcTemplate;
    }

    /**
     * 全量建索引。
     *
     * <p>两步优化，把 2.8 万条语料的建索引时间从「小时级」压到「分钟级」：</p>
     * <ol>
     *   <li><b>批量</b>：一次请求提交 batchSize 条文本（OpenAI 兼容接口的 input 支持数组），
     *       网络往返从 N 次降到 N/batchSize 次。</li>
     *   <li><b>并发</b>：多个批次同时发出。实测 4 并发吞吐约为单线程的 2.6 倍。</li>
     * </ol>
     * 向量写入集中在主线程串行执行，避免数据库连接被并发写入打满。
     */
    public int indexFull(List<TNewsCorpus> allNews) {
        if (allNews == null || allNews.isEmpty()) return 0;
        vectorStore.ensureCollection(false);

        int batchSize = batchSize();
        int total = allNews.size();

        // 切分批次
        List<List<TNewsCorpus>> chunks = new ArrayList<>();
        for (int start = 0; start < total; start += batchSize) {
            chunks.add(allNews.subList(start, Math.min(start + batchSize, total)));
        }

        int concurrency = Math.max(1, Math.min(concurrency(), chunks.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "rag-embed");
            t.setDaemon(true);
            return t;
        });

        int done = 0;
        int failed = 0;
        long t0 = System.currentTimeMillis();

        try {
            CompletionService<ChunkResult> cs = new ExecutorCompletionService<>(pool);
            for (List<TNewsCorpus> chunk : chunks) {
                cs.submit(() -> {
                    List<NewsVectorStore.VectorRecord> records = new ArrayList<>(chunk.size());
                    int[] chunkFailed = {0};
                    embedInto(chunk, records, chunkFailed, 0);
                    return new ChunkResult(chunk.size(), records, chunkFailed[0]);
                });
            }

            for (int i = 0; i < chunks.size(); i++) {
                ChunkResult res;
                try {
                    res = cs.take().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    failed += batchSize;
                    log.error("建索引任务异常: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                    continue;
                }

                failed += res.failed;
                if (!res.records.isEmpty()) {
                    try {
                        vectorStore.upsertBatch(res.records);
                        done += res.records.size();
                    } catch (Exception e) {
                        log.error("写入向量库失败（{} 条）: {}", res.records.size(), e.getMessage(), e);
                        failed += res.records.size();
                    }
                }

                if (done / 2000 != (done - res.records.size()) / 2000 || i == chunks.size() - 1) {
                    log.info("建索引进度：{}/{}（失败 {}），已耗时 {} s",
                            done, total, failed, (System.currentTimeMillis() - t0) / 1000);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        log.info("建索引完成：成功 {} 条，失败 {} 条，向量库共 {} 条，总耗时 {} s",
                done, failed, vectorStore.count(), (System.currentTimeMillis() - t0) / 1000);
        return done;
    }

    /**
     * 一次嵌入一批语料，失败则二分重试。
     *
     * <p>直接把整批降级成逐条会非常慢（2.8 万条逐条请求要几十分钟），
     * 二分可以快速定位到真正有问题的那几条，其余数据仍走批量通道。</p>
     */
    private void embedInto(List<TNewsCorpus> chunk, List<NewsVectorStore.VectorRecord> out,
                           int[] failed, int depth) {
        if (chunk == null || chunk.isEmpty()) return;

        List<String> texts = new ArrayList<>(chunk.size());
        for (TNewsCorpus c : chunk) {
            texts.add(buildIndexText(c));
        }

        List<List<Double>> vectors;
        try {
            vectors = llmClient.embedBatch(texts);
        } catch (Exception e) {
            if (chunk.size() > 1 && depth < 6) {
                int mid = chunk.size() / 2;
                log.warn("批次（{} 条）嵌入失败，二分重试：{}", chunk.size(), e.getMessage());
                embedInto(chunk.subList(0, mid), out, failed, depth + 1);
                embedInto(chunk.subList(mid, chunk.size()), out, failed, depth + 1);
            } else {
                failed[0] += chunk.size();
                log.warn("单条嵌入失败，跳过 newsId={}：{}",
                        chunk.get(0) == null ? null : chunk.get(0).getId(), e.getMessage());
            }
            return;
        }

        for (int i = 0; i < chunk.size(); i++) {
            TNewsCorpus c = chunk.get(i);
            if (c == null || c.getId() == null) {
                failed[0]++;
                continue;
            }
            List<Double> v = i < vectors.size() ? vectors.get(i) : null;
            if (v == null || v.isEmpty()) {
                failed[0]++;
                continue;
            }
            out.add(new NewsVectorStore.VectorRecord(
                    c.getId(), toFloatArray(v), deriveCountry(c), deriveYear(c)));
        }
    }

    /** 批次嵌入的中间结果。 */
    private static final class ChunkResult {
        final int size;
        final List<NewsVectorStore.VectorRecord> records;
        final int failed;

        ChunkResult(int size, List<NewsVectorStore.VectorRecord> records, int failed) {
            this.size = size;
            this.records = records;
            this.failed = failed;
        }
    }

    public void indexOne(TNewsCorpus c) {
        if (c == null || c.getId() == null) return;
        List<Double> vec = llmClient.embed(buildIndexText(c));
        if (vec == null || vec.isEmpty()) {
            throw new IllegalStateException("嵌入返回空向量，newsId=" + c.getId());
        }
        vectorStore.upsert(c.getId(), toFloatArray(vec), deriveCountry(c), deriveYear(c));
    }

    public void deleteIndex(Long newsId) {
        if (newsId == null) return;
        vectorStore.deleteByNewsId(newsId);
    }

    /** 当前向量库条数，供管理接口与前端展示。 */
    public long indexedCount() {
        return vectorStore.count();
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 标题权重更高：重复一次标题，让检索更偏向标题命中的新闻。 */
    private String buildIndexText(TNewsCorpus c) {
        String title = c.getNewsTitle() == null ? "" : c.getNewsTitle().trim();
        String content = c.getNewsContent() == null ? "" : c.getNewsContent();
        // 过长的正文会超出嵌入模型单条上限，截断到 1200 字足以表达主题
        if (content.length() > 1200) {
            content = content.substring(0, 1200);
        }
        return (title + "\n" + title + "\n" + content).trim();
    }

    /** news_articles 无 country 列，用关键词做一次尽力匹配，命中不到返回 null。 */
    private String deriveCountry(TNewsCorpus c) {
        StringBuilder sb = new StringBuilder();
        if (c.getNewsTitle() != null) sb.append(c.getNewsTitle());
        if (c.getNewsContent() != null) sb.append(c.getNewsContent(), 0, Math.min(c.getNewsContent().length(), 1000));
        String text = sb.toString();
        if (text.isEmpty()) return null;
        for (String kw : COUNTRY_KEYWORDS) {
            if (text.contains(kw)) return kw;
        }
        return null;
    }

    /** 由发布时间派生年份，供检索过滤使用。 */
    private Integer deriveYear(TNewsCorpus c) {
        if (c.getPublishTime() == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(c.getPublishTime());
        int y = cal.get(Calendar.YEAR);
        return (y < 1900 || y > 2100) ? null : y;
    }

    private float[] toFloatArray(List<Double> v) {
        float[] out = new float[v.size()];
        for (int i = 0; i < v.size(); i++) {
            Double d = v.get(i);
            out[i] = d == null ? 0f : d.floatValue();
        }
        return out;
    }

    /** 进入提示词的片段数。 */
    private int contextDocs() {
        Integer k = props.getVector().getTopK();
        if (k == null || k <= 0) {
            k = props.getMilvus().getTopK();
        }
        return (k == null || k <= 0) ? DEFAULT_CONTEXT_DOCS : k;
    }

    /**
     * 一次检索取回的候选条数，至少覆盖 ctxDocs。
     *
     * <p>之所以敢放大：本地向量库是对全部向量做一遍余弦扫描后再维护 top-k，
     * 维护成本相对扫描本身可忽略，取 5 条与取 20 条的耗时差在毫秒以内；
     * 真正的成本在提示词长度上，而提示词只吃 ctxDocs 条，与本值无关。</p>
     */
    private int candidateTopK(int ctxDocs) {
        Integer k = props.getVector().getCandidateTopK();
        int v = (k == null || k <= 0) ? DEFAULT_CANDIDATE_TOPK : k;
        return Math.max(v, ctxDocs);
    }

    /** 语料总条数。优先读向量库的内存计数，避免每次问答都打一次 COUNT(*)。 */
    private int corpusSize() {
        int n = vectorStore.size();
        if (n >= 0) return n;
        long c = vectorStore.count();
        if (c <= 0) return (int) c;
        return c > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c;
    }

    private int batchSize() {
        Integer b = props.getLlm().getEmbedding().getBatchSize();
        return (b == null || b <= 0) ? 64 : b;
    }

    private int concurrency() {
        Integer c = props.getLlm().getEmbedding().getConcurrency();
        return (c == null || c <= 0) ? 1 : c;
    }
}
