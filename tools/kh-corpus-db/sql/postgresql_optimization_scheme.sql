-- PostgreSQL 数据库关联关系和索引优化方案

-- 1. 增强索引策略

-- (1) 复合索引优化
-- 优化按实体类型查询和排序
CREATE INDEX IF NOT EXISTS idx_entities_type_name ON entities(entity_type, name);

-- 优化关系查询路径
CREATE INDEX IF NOT EXISTS idx_relationships_triple ON relationships(
    source_entity_id, relationship_type, target_entity_id
);

-- 优化新闻与实体的联合查询
CREATE INDEX IF NOT EXISTS idx_news_entities_entity_news ON news_entities(entity_id, news_id);

-- 优化按日期范围查询新闻
CREATE INDEX IF NOT EXISTS idx_news_content_date_range ON news_content(
    date DESC, id
);

-- (2) 覆盖索引
-- 优化常见的新闻标题查询
CREATE INDEX IF NOT EXISTS idx_news_content_title_cover ON news_content(title) INCLUDE (id, date);

-- 优化实体名称查询
CREATE INDEX IF NOT EXISTS idx_entities_name_cover ON entities(name) INCLUDE (id, entity_type);

-- (3) 部分索引
-- 为重要实体类型创建专门索引
CREATE INDEX IF NOT EXISTS idx_entities_important_types ON entities(entity_type, name)
WHERE entity_type IN ('地点', '人物', '组织机构');

-- 为高置信度关系创建索引
CREATE INDEX IF NOT EXISTS idx_relationships_high_confidence ON relationships(
    relationship_type, source_entity_id, target_entity_id
) WHERE confidence_score > 0.8;

-- (4) 全文搜索索引
-- 为新闻内容创建全文搜索索引
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_news_content_text_trgm ON news_content
USING gin(content_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_news_content_title_trgm ON news_content
USING gin(title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_entities_name_trgm ON entities
USING gin(name gin_trgm_ops);

-- 2. 增强关联关系设计

-- (1) 创建实体层级关系表（支持实体分类体系）
CREATE TABLE IF NOT EXISTS entity_hierarchy (
    parent_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    child_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    hierarchy_type VARCHAR(100) DEFAULT 'category', -- 类别、部分等关系类型
    PRIMARY KEY (parent_entity_id, child_entity_id, hierarchy_type),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entity_hierarchy_parent ON entity_hierarchy(parent_entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_hierarchy_child ON entity_hierarchy(child_entity_id);

-- (2) 创建实体属性表（存储实体的元数据）
CREATE TABLE IF NOT EXISTS entity_attributes (
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    attribute_key VARCHAR(100) NOT NULL,
    attribute_value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id, attribute_key)
);

CREATE INDEX IF NOT EXISTS idx_entity_attributes_key_value ON entity_attributes(attribute_key, attribute_value);

-- (3) 创建关系强度和时间维度表
CREATE TABLE IF NOT EXISTS relationship_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    confidence_score FLOAT NOT NULL,
    effective_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_relationship_history_date ON relationship_history(effective_date, expiry_date);
CREATE INDEX IF NOT EXISTS idx_relationship_history_relation ON relationship_history(relationship_id);

-- (4) 创建实体提及上下文表
CREATE TABLE IF NOT EXISTS entity_mentions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    mention_text VARCHAR(500) NOT NULL, -- 实际出现的文本形式
    context_before TEXT, -- 提及前的上下文
    context_after TEXT, -- 提及后的上下文
    position_start INTEGER NOT NULL,
    position_end INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entity_mentions_news_entity ON entity_mentions(news_id, entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_mentions_position ON entity_mentions(position_start, position_end);

-- 3. 高级视图和查询优化

-- (1) 实体共现视图
CREATE OR REPLACE VIEW entity_cooccurrences AS
SELECT
    e1.id as entity1_id,
    e1.name as entity1_name,
    e1.entity_type as entity1_type,
    e2.id as entity2_id,
    e2.name as entity2_name,
    e2.entity_type as entity2_type,
    COUNT(DISTINCT ne1.news_id) as cooccurrence_count
FROM
    entities e1
JOIN
    news_entities ne1 ON e1.id = ne1.entity_id
JOIN
    news_entities ne2 ON ne1.news_id = ne2.news_id
JOIN
    entities e2 ON ne2.entity_id = e2.id
WHERE
    e1.id < e2.id -- 避免重复计数
GROUP BY
    e1.id, e1.name, e1.entity_type, e2.id, e2.name, e2.entity_type
ORDER BY
    cooccurrence_count DESC;

-- (2) 关系网络视图
CREATE OR REPLACE VIEW relationship_network AS
WITH relationship_stats AS (
    SELECT
        source_entity_id,
        target_entity_id,
        relationship_type,
        COUNT(*) as occurrence_count,
        AVG(confidence_score) as avg_confidence
    FROM
        relationships
    GROUP BY
        source_entity_id, target_entity_id, relationship_type
)
SELECT
    rs.source_entity_id,
    rs.target_entity_id,
    rs.relationship_type,
    rs.occurrence_count,
    rs.avg_confidence,
    s.name as source_name,
    s.entity_type as source_type,
    t.name as target_name,
    t.entity_type as target_type
FROM
    relationship_stats rs
JOIN
    entities s ON rs.source_entity_id = s.id
JOIN
    entities t ON rs.target_entity_id = t.id
ORDER BY
    rs.occurrence_count DESC;

-- (3) 优化的向量搜索函数
CREATE OR REPLACE FUNCTION vector_search_optimized(
    p_query_vector VECTOR(768),
    p_table_name VARCHAR(100),
    p_vector_column VARCHAR(100),
    p_limit INT DEFAULT 10,
    p_threshold FLOAT DEFAULT 0.7
) RETURNS TABLE (
    id UUID,
    similarity_score FLOAT
) AS $$
BEGIN
    -- 使用动态SQL构建优化的向量查询
    RETURN QUERY EXECUTE format(
        'SELECT 
            id, 
            (%I <=> $1) as similarity_score 
         FROM 
            %I 
         WHERE 
            %I IS NOT NULL 
            AND (%I <=> $1) > $2 
         ORDER BY 
            similarity_score DESC 
         LIMIT $3',
        p_vector_column, p_table_name, p_vector_column, p_vector_column
    ) USING p_query_vector, p_threshold, p_limit;
END;
$$ LANGUAGE plpgsql;

-- (4) 高级实体关系查询函数
CREATE OR REPLACE FUNCTION get_entity_relationship_network(
    p_entity_id UUID,
    p_depth INT DEFAULT 2,
    p_relationship_types TEXT[] DEFAULT NULL
) RETURNS TABLE (
    node_id UUID,
    node_name VARCHAR(255),
    node_type VARCHAR(100),
    relationship_id UUID,
    relationship_type VARCHAR(100),
    direction VARCHAR(10), -- 'in' 或 'out'
    depth INT
) AS $$
BEGIN
    -- 使用递归CTE查询实体关系网络
    RETURN QUERY WITH RECURSIVE entity_graph(node_id, node_name, node_type, relationship_id, relationship_type, direction, depth)
    AS (
        -- 起始节点
        SELECT 
            e.id, e.name, e.entity_type, 
            NULL::UUID, NULL::VARCHAR(100), NULL::VARCHAR(10), 
            0
        FROM 
            entities e
        WHERE 
            e.id = p_entity_id
        
        UNION ALL
        
        -- 外关系（当前实体是源）
        SELECT
            t.id, t.name, t.entity_type,
            r.id, r.relationship_type, 'out',
            eg.depth + 1
        FROM
            entity_graph eg
        JOIN
            relationships r ON eg.node_id = r.source_entity_id
        JOIN
            entities t ON r.target_entity_id = t.id
        WHERE
            eg.depth < p_depth
            AND (p_relationship_types IS NULL OR r.relationship_type = ANY(p_relationship_types))
            AND t.id NOT IN (SELECT node_id FROM entity_graph)
        
        UNION ALL
        
        -- 内关系（当前实体是目标）
        SELECT
            s.id, s.name, s.entity_type,
            r.id, r.relationship_type, 'in',
            eg.depth + 1
        FROM
            entity_graph eg
        JOIN
            relationships r ON eg.node_id = r.target_entity_id
        JOIN
            entities s ON r.source_entity_id = s.id
        WHERE
            eg.depth < p_depth
            AND (p_relationship_types IS NULL OR r.relationship_type = ANY(p_relationship_types))
            AND s.id NOT IN (SELECT node_id FROM entity_graph)
    )
    SELECT * FROM entity_graph;
END;
$$ LANGUAGE plpgsql;

-- 4. 分区表策略（适用于大数据量）

-- 注意：以下是分区表策略示例，实际实施需要根据数据量和查询模式调整
-- 分区表示例（新闻内容按时间分区）

/*
-- 删除原表（如果需要转换为分区表）
DROP TABLE IF EXISTS news_content;

-- 创建分区表
CREATE TABLE news_content (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    "date" TIMESTAMP,
    "desc" TEXT,
    tags TEXT[],
    url VARCHAR(1000) UNIQUE,
    content_html TEXT,
    content_text TEXT NOT NULL,
    pics TEXT[],
    create_time BIGINT,
    title_vector VECTOR(768),
    content_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE ("date");

-- 创建分区
CREATE TABLE news_content_2023 PARTITION OF news_content
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');

CREATE TABLE news_content_2024 PARTITION OF news_content
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE news_content_2025 PARTITION OF news_content
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- 为每个分区创建索引
CREATE INDEX idx_news_content_2023_title ON news_content_2023(title);
CREATE INDEX idx_news_content_2024_title ON news_content_2024(title);
CREATE INDEX idx_news_content_2025_title ON news_content_2025(title);

CREATE INDEX idx_news_content_2023_vector ON news_content_2023 USING ivfflat(content_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_news_content_2024_vector ON news_content_2024 USING ivfflat(content_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_news_content_2025_vector ON news_content_2025 USING ivfflat(content_vector vector_cosine_ops) WITH (lists = 100);
*/

-- 5. 性能优化建议

-- (1) PostgreSQL 配置优化建议
/*
建议调整以下PostgreSQL配置参数以优化向量搜索和关系查询性能：

shared_buffers = 2GB           # 增加共享缓冲区（通常设置为系统内存的25%）
work_mem = 64MB                # 增加工作内存（用于排序和哈希操作）
maintenance_work_mem = 512MB   # 增加维护操作的工作内存
random_page_cost = 1.1         # 对于SSD存储，降低随机页成本
effective_cache_size = 6GB     # 设置为系统内存的75%
max_connections = 100          # 根据实际连接需求调整
checkpoint_completion_target = 0.9  # 平滑检查点写入

# 对于向量搜索优化
max_parallel_workers = 4       # 根据CPU核心数调整
max_parallel_workers_per_gather = 2
*/

-- (2) 批量操作优化函数
CREATE OR REPLACE FUNCTION batch_insert_entities(
    p_entities JSONB
) RETURNS INTEGER AS $$
DECLARE
    inserted_count INTEGER := 0;
BEGIN
    -- 使用INSERT ... SELECT优化批量插入
    INSERT INTO entities (name, entity_type, name_vector)
    SELECT 
        (entity->>'name')::VARCHAR(255),
        (entity->>'entity_type')::VARCHAR(100),
        NULL -- 向量值需要单独处理或通过应用程序传入
    FROM
        jsonb_array_elements(p_entities) AS entity
    ON CONFLICT (name, entity_type) DO NOTHING
    RETURNING 1 INTO inserted_count;
    
    RETURN inserted_count;
END;
$$ LANGUAGE plpgsql;

-- (3) 物化视图用于频繁查询
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_top_entities_by_news_count AS
SELECT 
    e.id, e.name, e.entity_type, 
    COUNT(ne.news_id) as news_count
FROM 
    entities e
JOIN 
    news_entities ne ON e.id = ne.entity_id
GROUP BY 
    e.id, e.name, e.entity_type
ORDER BY 
    news_count DESC
LIMIT 1000;

CREATE INDEX IF NOT EXISTS idx_mv_top_entities_id ON mv_top_entities_by_news_count(id);

-- 输出优化完成消息
SELECT '数据库关联关系和索引优化方案已创建！' as message;
