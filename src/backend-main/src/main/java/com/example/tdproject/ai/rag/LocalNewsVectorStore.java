package com.example.tdproject.ai.rag;

import com.example.tdproject.ai.config.AiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.*;

/**
 * 本地向量库：向量持久化在 MySQL，检索时全量载入内存做精确余弦扫描。
 *
 * <p><b>为什么不用 Milvus</b>：本机并未部署 Milvus（19530 未监听），而语料规模只有
 * 2.8 万条 × 1024 维 ≈ 115 MB；在这种量级下，精确暴力检索单次耗时仅几十毫秒，
 * 比一次 HTTP/网络往返还快，却省掉了一套需要独立运维的中间件。
 * 若语料规模涨到百万级，把 {@code ai.vector.provider} 改成 {@code milvus} 即可切回。</p>
 *
 * <p>内存占用：条数 × 维度 × 4 字节。2.8 万条 1024 维约 115 MB，可接受。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.vector", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalNewsVectorStore implements NewsVectorStore {

    private static final String TABLE = "news_vectors";
    private static final int INSERT_BATCH = 500;

    private final DataSource dataSource;
    private final AiProperties props;

    private JdbcTemplate jdbc;
    private final Object loadLock = new Object();

    /** 扁平向量矩阵，行优先：第 i 条的向量在 [i*dim, (i+1)*dim)。已 L2 归一化。 */
    private volatile float[] flat = new float[0];
    private volatile long[] ids = new long[0];
    private volatile String[] countries = new String[0];
    private volatile int[] years = new int[0];
    private volatile int rowCount = 0;
    private volatile int dim = 0;
    private volatile boolean loaded = false;
    /** 写入后置为 true，下次检索时重新载入。 */
    private volatile boolean stale = true;

    @PostConstruct
    public void init() {
        this.jdbc = new JdbcTemplate(dataSource);
        try {
            ensureCollection(false);
            log.info("本地向量库已就绪：table={} dim={}", TABLE, dimension());
        } catch (Exception e) {
            log.error("本地向量库初始化失败（问答检索将不可用）: {}", e.getMessage(), e);
        }
    }

    private int dimension() {
        Integer d = props.getLlm().getEmbedding().getDimension();
        if (d == null || d <= 0) {
            d = props.getMilvus().getDimension();
        }
        return (d == null || d <= 0) ? 1024 : d;
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    @Override
    public void ensureCollection(boolean recreate) {
        JdbcTemplate jt = jdbc();
        jt.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "  news_id BIGINT NOT NULL COMMENT '新闻ID',"
                + "  dim INT NOT NULL COMMENT '向量维度',"
                + "  country VARCHAR(64) DEFAULT NULL COMMENT '国家（可空）',"
                + "  `year` INT DEFAULT NULL COMMENT '年份（可空）',"
                + "  vec LONGBLOB NOT NULL COMMENT 'float32 小端序列',"
                + "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "  PRIMARY KEY (news_id),"
                + "  KEY idx_dim (dim)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻语料向量（RAG 本地检索用）'");

        if (recreate) {
            clear();
        }
    }

    @Override
    public void clear() {
        jdbc().execute("TRUNCATE TABLE " + TABLE);
        synchronized (loadLock) {
            resetMemory();
            loaded = true;
            stale = false;
        }
        log.info("本地向量库已清空");
    }

    private void resetMemory() {
        flat = new float[0];
        ids = new long[0];
        countries = new String[0];
        years = new int[0];
        rowCount = 0;
    }

    // ==================================================================
    // 写入
    // ==================================================================

    @Override
    public void upsert(long newsId, float[] embedding, String country, Integer year) {
        upsertBatch(List.of(new VectorRecord(newsId, embedding, country, year)));
    }

    @Override
    public void upsertBatch(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) return;
        int d = dimension();
        String sql = "INSERT INTO " + TABLE + " (news_id, dim, country, `year`, vec) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE dim = VALUES(dim), country = VALUES(country), "
                + "`year` = VALUES(`year`), vec = VALUES(vec)";

        List<Object[]> args = new ArrayList<>(records.size());
        for (VectorRecord r : records) {
            if (r == null || r.getVec() == null || r.getVec().length == 0) continue;
            if (r.getVec().length != d) {
                // 维度不符的条目直接跳过，避免污染检索结果
                continue;
            }
            args.add(new Object[]{
                    r.getNewsId(), d, r.getCountry(),
                    r.getYear() == null ? null : r.getYear(),
                    toBytes(normalize(r.getVec()))
            });
            if (args.size() >= INSERT_BATCH) {
                jdbc().batchUpdate(sql, args);
                args.clear();
            }
        }
        if (!args.isEmpty()) {
            jdbc().batchUpdate(sql, args);
        }
        stale = true;
    }

    @Override
    public void deleteByNewsId(long newsId) {
        jdbc().update("DELETE FROM " + TABLE + " WHERE news_id = ?", newsId);
        stale = true;
    }

    @Override
    public long count() {
        try {
            Long c = jdbc().queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
            return c == null ? 0L : c;
        } catch (Exception e) {
            log.warn("统计向量条数失败: {}", e.getMessage());
            return -1L;
        }
    }

    @Override
    public int size() {
        // 纯内存读取。search() 内部会先 ensureLoaded()，所以调用方在检索之后取值一定是新鲜的，
        // 未载入时返回 -1 让调用方决定是否退回 count()。
        return (loaded && !stale) ? rowCount : -1;
    }

    // ==================================================================
    // 检索
    // ==================================================================

    @Override
    public List<Map<String, Object>> search(float[] query, String country, Integer year, int topK) {
        if (query == null || query.length == 0) return List.of();
        int d = dimension();
        if (query.length != d) {
            throw new IllegalArgumentException("查询向量维度不匹配：expected=" + d + ", actual=" + query.length);
        }
        ensureLoaded();

        int n = rowCount;
        if (n == 0) return List.of();

        int k = Math.max(1, Math.min(topK, n));
        float[] q = normalize(query);
        float[] f = flat;
        long[] idArr = ids;
        String[] cArr = countries;
        int[] yArr = years;

        boolean filterCountry = country != null && !country.isBlank();
        boolean filterYear = year != null;

        float[] bestScore = new float[k];
        int[] bestIdx = new int[k];
        Arrays.fill(bestScore, Float.NEGATIVE_INFINITY);
        Arrays.fill(bestIdx, -1);
        int filled = 0;
        float worst = Float.NEGATIVE_INFINITY;

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            if (filterCountry && !country.trim().equals(cArr[i])) continue;
            if (filterYear && yArr[i] != year) continue;

            int base = i * d;
            float dot = 0f;
            for (int j = 0; j < d; j++) {
                dot += q[j] * f[base + j];
            }

            if (filled < k) {
                int pos = filled;
                while (pos > 0 && bestScore[pos - 1] < dot) {
                    bestScore[pos] = bestScore[pos - 1];
                    bestIdx[pos] = bestIdx[pos - 1];
                    pos--;
                }
                bestScore[pos] = dot;
                bestIdx[pos] = i;
                filled++;
                if (filled == k) worst = bestScore[k - 1];
            } else if (dot > worst) {
                int pos = k - 1;
                while (pos > 0 && bestScore[pos - 1] < dot) {
                    bestScore[pos] = bestScore[pos - 1];
                    bestIdx[pos] = bestIdx[pos - 1];
                    pos--;
                }
                bestScore[pos] = dot;
                bestIdx[pos] = i;
                worst = bestScore[k - 1];
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        List<Map<String, Object>> out = new ArrayList<>(filled);
        for (int i = 0; i < filled; i++) {
            if (bestIdx[i] < 0) continue;
            Map<String, Object> row = new HashMap<>(4);
            row.put("newsId", idArr[bestIdx[i]]);
            row.put("score", (double) bestScore[i]);
            out.add(row);
        }
        // 提到 info：这一行是判断「候选条数调大会不会拖慢检索」的唯一直接证据，
        // 一次问答只打一行，不构成噪声。
        log.info("本地向量检索：语料 {} 条，取回候选 {} 条，耗时 {} ms", n, out.size(), ms);
        return out;
    }

    // ==================================================================
    // 载入
    // ==================================================================

    private void ensureLoaded() {
        if (loaded && !stale) return;
        synchronized (loadLock) {
            if (loaded && !stale) return;
            long t0 = System.currentTimeMillis();
            int d = dimension();
            JdbcTemplate jt = jdbc();
            ensureCollection(false);

            Long cnt = jt.queryForObject(
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE dim = ?", Long.class, d);
            int capacity = cnt == null ? 0 : cnt.intValue();

            float[] f = new float[Math.max(capacity, 1) * d];
            long[] idArr = new long[Math.max(capacity, 1)];
            String[] cArr = new String[Math.max(capacity, 1)];
            int[] yArr = new int[Math.max(capacity, 1)];
            final int[] cursor = {0};

            jt.query("SELECT news_id, country, `year`, vec FROM " + TABLE + " WHERE dim = ?",
                    rs -> {
                        int i = cursor[0];
                        if (i >= capacity) return;
                        idArr[i] = rs.getLong(1);
                        cArr[i] = rs.getString(2);
                        int y = rs.getInt(3);
                        yArr[i] = rs.wasNull() ? Integer.MIN_VALUE : y;
                        byte[] blob = rs.getBytes(4);
                        if (blob != null && blob.length >= d * 4) {
                            int base = i * d;
                            for (int j = 0; j < d; j++) {
                                int o = j * 4;
                                int bits = (blob[o] & 0xFF)
                                        | ((blob[o + 1] & 0xFF) << 8)
                                        | ((blob[o + 2] & 0xFF) << 16)
                                        | ((blob[o + 3] & 0xFF) << 24);
                                f[base + j] = Float.intBitsToFloat(bits);
                            }
                            cursor[0] = i + 1;
                        }
                    }, d);

            int n = cursor[0];
            flat = f;
            ids = idArr;
            countries = cArr;
            years = yArr;
            rowCount = n;
            this.dim = d;
            loaded = true;
            stale = false;
            log.info("本地向量库载入完成：{} 条 × {} 维，耗时 {} ms",
                    n, d, System.currentTimeMillis() - t0);
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** L2 归一化，使点积等于余弦相似度。入参不会被修改。 */
    private float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        float[] out = v.clone();
        double norm = Math.sqrt(sum);
        if (norm > 1e-12) {
            float inv = (float) (1.0 / norm);
            for (int i = 0; i < out.length; i++) {
                out[i] *= inv;
            }
        }
        return out;
    }

    /** float[] → 小端字节序列（与回读逻辑严格对应）。 */
    private byte[] toBytes(float[] v) {
        byte[] b = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) {
            int bits = Float.floatToIntBits(v[i]);
            b[i * 4] = (byte) (bits & 0xFF);
            b[i * 4 + 1] = (byte) ((bits >>> 8) & 0xFF);
            b[i * 4 + 2] = (byte) ((bits >>> 16) & 0xFF);
            b[i * 4 + 3] = (byte) ((bits >>> 24) & 0xFF);
        }
        return b;
    }

    private JdbcTemplate jdbc() {
        if (jdbc == null) {
            synchronized (this) {
                if (jdbc == null) {
                    jdbc = new JdbcTemplate(dataSource);
                }
            }
        }
        return jdbc;
    }
}
