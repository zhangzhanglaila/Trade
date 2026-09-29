package com.example.tdproject.ai.rag;

import com.example.tdproject.ai.config.AiProperties;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;

import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Milvus 版向量库实现。
 * <p>默认不启用：需要显式设置 <code>ai.vector.provider=milvus</code> 并把
 * <code>ai.milvus.host/port</code> 指向已部署的 Milvus 服务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.vector", name = "provider", havingValue = "milvus")
public class MilvusNewsVectorStore implements NewsVectorStore {

    public static final String FIELD_NEWS_ID = "news_id";
    public static final String FIELD_EMBEDDING = "embedding";
    public static final String FIELD_COUNTRY = "country";
    public static final String FIELD_YEAR = "year";

    private final AiProperties props;
    private final MilvusClientFactory clientFactory;

    @Override
    public void ensureCollection(boolean recreate) {
        AiProperties.Milvus m = props.getMilvus();
        String collection = m.getCollection();
        Integer dim = m.getDimension();
        if (collection == null || collection.isBlank() || dim == null) {
            throw new IllegalStateException("Milvus 配置不完整：ai.milvus.collection/dimension");
        }

        MilvusClient client = clientFactory.client();

        R<Boolean> has = client.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collection).build()
        );
        if (Boolean.TRUE.equals(has.getData())) {
            if (!recreate) {
                // 确保已 load
                client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
                return;
            }
            log.info("Milvus collection 已存在，执行 drop 并重建: {}", collection);
            client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(collection).build());
        }

        List<FieldType> fields = new ArrayList<>();
        fields.add(FieldType.newBuilder()
                .withName(FIELD_NEWS_ID)
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build());

        fields.add(FieldType.newBuilder()
                .withName(FIELD_EMBEDDING)
                .withDataType(DataType.FloatVector)
                .withDimension(dim)
                .build());

        // 过滤字段（可选，但我们创建出来便于 expr 过滤）
        fields.add(FieldType.newBuilder()
                .withName(FIELD_COUNTRY)
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build());

        fields.add(FieldType.newBuilder()
                .withName(FIELD_YEAR)
                .withDataType(DataType.Int64)
                .build());

        CreateCollectionParam.Builder createBuilder = CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("news corpus vector store")
                .withShardsNum(2);
        for (FieldType f : fields) {
            createBuilder.addFieldType(f);
        }
        CreateCollectionParam create = createBuilder.build();


        R<?> r = client.createCollection(create);
        if (r.getStatus() != 0) {
            throw new IllegalStateException("创建 Milvus collection 失败: " + r.getMessage());
        }

        // 为向量字段建索引（HNSW + COSINE）
        Map<String, Object> indexParams = new HashMap<>();
        indexParams.put("M", 16);
        indexParams.put("efConstruction", 200);

        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName(FIELD_EMBEDDING)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam(toJson(indexParams))
                .build());

        // load
        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
    }

    /**
     * 简易 upsert：先 delete 再 insert。
     * 注意：如果数据量大，建议后续改用 Milvus Upsert（若 SDK/服务端支持）。
     */
    @Override
    public void upsert(long newsId, float[] embedding, String country, Integer year) {
        deleteByNewsId(newsId);
        insert(newsId, toFloatList(embedding), country, year);
    }

    @Override
    public void upsertBatch(List<VectorRecord> records) {
        if (records == null) return;
        for (VectorRecord r : records) {
            if (r == null) continue;
            upsert(r.getNewsId(), r.getVec(), r.getCountry(), r.getYear());
        }
    }

    @Override
    public long count() {
        // Milvus 侧需要额外查询才能统计，这里不阻塞主流程
        return -1L;
    }

    @Override
    public void clear() {
        ensureCollection(true);
    }

    private List<Float> toFloatList(float[] arr) {
        if (arr == null) return List.of();
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private void insert(long newsId, List<Float> embedding, String country, Integer year) {
        AiProperties.Milvus m = props.getMilvus();
        String collection = m.getCollection();

        if (embedding == null || embedding.size() != m.getDimension()) {
            throw new IllegalArgumentException("embedding 维度不匹配：expected=" + m.getDimension() + ", actual=" + (embedding == null ? 0 : embedding.size()));
        }

        MilvusClient client = clientFactory.client();

        List<Long> ids = List.of(newsId);
        List<List<Float>> vectors = List.of(embedding);
        List<String> countries = List.of(country == null ? "" : country);
        List<Long> years = List.of(year == null ? 0L : year.longValue());

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(FIELD_NEWS_ID, ids));
        fields.add(new InsertParam.Field(FIELD_EMBEDDING, vectors));
        fields.add(new InsertParam.Field(FIELD_COUNTRY, countries));
        fields.add(new InsertParam.Field(FIELD_YEAR, years));

        R<?> r = client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());

        if (r.getStatus() != 0) {
            throw new IllegalStateException("Milvus insert 失败: " + r.getMessage());
        }
    }

    @Override
    public void deleteByNewsId(long newsId) {
        AiProperties.Milvus m = props.getMilvus();
        String collection = m.getCollection();

        MilvusClient client = clientFactory.client();
        String expr = FIELD_NEWS_ID + " in [" + newsId + "]";
        R<?> r = client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(expr)
                .build());

        // delete 即使没数据也可能返回 status==0；这里不强制报错
        if (r.getStatus() != 0) {
            throw new IllegalStateException("Milvus delete 失败: " + r.getMessage());
        }
    }

    /**
     * @return 每条结果：{newsId: long, score: double}
     */
    @Override
    public List<Map<String, Object>> search(float[] queryVec, String country, Integer year, int topK) {
        AiProperties.Milvus m = props.getMilvus();
        String collection = m.getCollection();
        List<Float> queryEmbedding = toFloatList(queryVec);

        if (queryEmbedding == null || queryEmbedding.size() != m.getDimension()) {
            throw new IllegalArgumentException("queryEmbedding 维度不匹配：expected=" + m.getDimension() + ", actual=" + (queryEmbedding == null ? 0 : queryEmbedding.size()));
        }

        MilvusClient client = clientFactory.client();

        String expr = buildExpr(country, year);

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK <= 0 ? (m.getTopK() == null ? 5 : m.getTopK()) : topK)
                .withOutFields(List.of(FIELD_NEWS_ID, FIELD_COUNTRY, FIELD_YEAR))
                .withVectorFieldName(FIELD_EMBEDDING)
                .withVectors(List.of(queryEmbedding))
                // 对 HNSW 可用 ef；这里不强依赖
                .withParams("{\"ef\":64}");

        if (expr != null) {
            builder.withExpr(expr);
        }

        R<SearchResults> r = client.search(builder.build());
        if (r.getStatus() != 0) {
            throw new IllegalStateException("Milvus search 失败: " + r.getMessage());
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(r.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

        List<Map<String, Object>> results = new ArrayList<>();
        for (SearchResultsWrapper.IDScore s : scores) {
            Map<String, Object> row = new HashMap<>();
            row.put("newsId", s.getLongID());
            row.put("score", (double) s.getScore());
            results.add(row);
        }
        return results;
    }

    private String buildExpr(String country, Integer year) {
        List<String> parts = new ArrayList<>();
        if (country != null && !country.isBlank()) {
            parts.add(FIELD_COUNTRY + " == \"" + escape(country.trim()) + "\"");
        }
        if (year != null) {
            parts.add(FIELD_YEAR + " == " + year);
        }
        if (parts.isEmpty()) return null;
        return String.join(" and ", parts);
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append('"').append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
