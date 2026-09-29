package com.example.tdproject.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * 新闻语料的向量库抽象。
 *
 * <p>当前有两种实现，通过 <code>ai.vector.provider</code> 切换：</p>
 * <ul>
 *   <li><b>local</b>（默认）：{@link LocalNewsVectorStore}，向量存 MySQL、检索时载入内存做精确余弦扫描。
 *       语料规模在十万级以内时，暴力检索的耗时（毫秒级）远小于网络往返，且不引入额外中间件。</li>
 *   <li><b>milvus</b>：{@link MilvusNewsVectorStore}，需要自行部署 Milvus 服务。</li>
 * </ul>
 */
public interface NewsVectorStore {

    /** 确保底层集合/表已就绪。recreate=true 时清空已有数据。 */
    void ensureCollection(boolean recreate);

    /** 写入或覆盖单条向量。embedding 会按 L2 归一化后存储，使点积等价于余弦相似度。 */
    void upsert(long newsId, float[] embedding, String country, Integer year);

    /** 批量写入，建索引时用，避免逐条往返。 */
    void upsertBatch(List<VectorRecord> records);

    /** 按新闻 ID 删除向量。 */
    void deleteByNewsId(long newsId);

    /** 当前已入库的向量条数；无法统计时返回 -1。 */
    long count();

    /**
     * 已载入内存的向量条数，用于告诉用户「本次在多少条语料里检索」。
     *
     * <p>与 {@link #count()} 的区别在于成本：本方法只读内存计数，不产生数据库往返，
     * 因此可以在每次问答时调用而不增加可感知的耗时。返回 -1 表示未知，
     * 此时调用方可退回 {@link #count()}。</p>
     */
    default int size() {
        return -1;
    }

    /** 清空全部向量。 */
    void clear();

    /**
     * 相似度检索。
     *
     * @param query   查询向量（内部会归一化）
     * @param country 可选过滤条件，null 表示不过滤
     * @param year    可选过滤条件，null 表示不过滤
     * @param topK    返回条数上限
     * @return 每条为 {newsId: Long, score: Double}，按 score 降序
     */
    List<Map<String, Object>> search(float[] query, String country, Integer year, int topK);

    /** 一条待写入的向量。 */
    class VectorRecord {
        private final long newsId;
        private final float[] vec;
        private final String country;
        private final Integer year;

        public VectorRecord(long newsId, float[] vec, String country, Integer year) {
            this.newsId = newsId;
            this.vec = vec;
            this.country = country;
            this.year = year;
        }

        public long getNewsId() { return newsId; }
        public float[] getVec() { return vec; }
        public String getCountry() { return country; }
        public Integer getYear() { return year; }
    }
}
