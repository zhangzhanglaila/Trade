-- PostgreSQL数据库初始化脚本 - 语料数据库（支持向量和关系查询）

-- 1. 创建扩展（如果还未安装）
-- 注意：需要先安装pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. 创建新闻内容表
CREATE TABLE IF NOT EXISTS news_content (
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
    -- 向量列，用于存储新闻内容的嵌入向量
    title_vector VECTOR(768),
    content_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 创建实体表
CREATE TABLE IF NOT EXISTS entities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    -- 向量列，用于存储实体名称的嵌入向量
    name_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 创建名称和类型的联合唯一索引
    UNIQUE(name, entity_type)
);

-- 4. 创建新闻-实体关联表
CREATE TABLE IF NOT EXISTS news_entities (
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, entity_id),
    -- 可选：添加实体在新闻中的出现位置等信息
    occurrence_positions INTEGER[],
    frequency INTEGER DEFAULT 1
);

-- 5. 创建关系表
CREATE TABLE IF NOT EXISTS relationships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    relationship_type VARCHAR(100) NOT NULL,
    -- 可选：关系强度评分
    confidence_score FLOAT DEFAULT 1.0,
    -- 关系的向量表示
    relation_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 创建关系属性表（存储关系的动态属性）
CREATE TABLE IF NOT EXISTS relationship_attributes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    attribute_key VARCHAR(100) NOT NULL,
    attribute_value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 确保每个关系的每个属性键唯一
    UNIQUE(relationship_id, attribute_key)
);

-- 7. 创建新闻-关系关联表
CREATE TABLE IF NOT EXISTS news_relationships (
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, relationship_id)
);

-- 8. 创建索引以优化查询性能

-- 为新闻表创建索引
CREATE INDEX IF NOT EXISTS idx_news_content_date ON news_content("date");
CREATE INDEX IF NOT EXISTS idx_news_content_title ON news_content(title);

-- 为实体表创建索引
CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(entity_type);
CREATE INDEX IF NOT EXISTS idx_entities_name ON entities(name);

-- 为关系表创建索引
CREATE INDEX IF NOT EXISTS idx_relationships_source ON relationships(source_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_target ON relationships(target_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_type ON relationships(relationship_type);

-- 为向量相似度搜索创建索引（使用IVFFlat索引）
-- 注意：nlist参数需要根据数据量调整
CREATE INDEX IF NOT EXISTS idx_news_title_vector ON news_content USING ivfflat(title_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_news_content_vector ON news_content USING ivfflat(content_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_entities_name_vector ON entities USING ivfflat(name_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_relationships_vector ON relationships USING ivfflat(relation_vector vector_cosine_ops) WITH (lists = 100);

-- 9. 创建用于频繁查询的视图

-- 视图：实体及其相关新闻数量
CREATE OR REPLACE VIEW entity_news_count AS
SELECT 
    e.id, e.name, e.entity_type, 
    COUNT(ne.news_id) as news_count
FROM 
    entities e
LEFT JOIN 
    news_entities ne ON e.id = ne.entity_id
GROUP BY 
    e.id, e.name, e.entity_type;

-- 视图：关系及其属性概览
CREATE OR REPLACE VIEW relationship_overview AS
SELECT 
    r.id, 
    r.source_entity_id,
    r.target_entity_id,
    r.relationship_type,
    r.confidence_score,
    s.name as source_entity_name,
    s.entity_type as source_entity_type,
    t.name as target_entity_name,
    t.entity_type as target_entity_type,
    COUNT(ra.id) as attribute_count
FROM 
    relationships r
JOIN 
    entities s ON r.source_entity_id = s.id
JOIN 
    entities t ON r.target_entity_id = t.id
LEFT JOIN 
    relationship_attributes ra ON r.id = ra.relationship_id
GROUP BY 
    r.id, r.source_entity_id, r.target_entity_id, r.relationship_type, 
    r.confidence_score, s.name, s.entity_type, t.name, t.entity_type;

-- 10. 创建示例数据插入函数（简化版）
CREATE OR REPLACE FUNCTION insert_news_with_entities(
    p_title VARCHAR(500),
    p_date TIMESTAMP,
    p_desc TEXT,
    p_tags TEXT[],
    p_url VARCHAR(1000),
    p_content_html TEXT,
    p_content_text TEXT,
    p_pics TEXT[],
    p_create_time BIGINT,
    p_title_vector VECTOR(768) DEFAULT NULL,
    p_content_vector VECTOR(768) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    new_news_id UUID;
BEGIN
    -- 插入新闻
    INSERT INTO news_content (
        title, "date", "desc", tags, url, 
        content_html, content_text, pics, create_time,
        title_vector, content_vector
    ) VALUES (
        p_title, p_date, p_desc, p_tags, p_url,
        p_content_html, p_content_text, p_pics, p_create_time,
        p_title_vector, p_content_vector
    ) RETURNING id INTO new_news_id;
    
    RETURN new_news_id;
END;
$$ LANGUAGE plpgsql;

-- 11. 创建添加实体的函数
CREATE OR REPLACE FUNCTION add_entity(
    p_name VARCHAR(255),
    p_entity_type VARCHAR(100),
    p_name_vector VECTOR(768) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    entity_id UUID;
BEGIN
    -- 使用upsert避免重复实体
    INSERT INTO entities (name, entity_type, name_vector)
    VALUES (p_name, p_entity_type, p_name_vector)
    ON CONFLICT (name, entity_type) DO UPDATE
    SET name_vector = COALESCE(EXCLUDED.name_vector, entities.name_vector)
    RETURNING id INTO entity_id;
    
    RETURN entity_id;
END;
$$ LANGUAGE plpgsql;

-- 12. 创建添加关系的函数
CREATE OR REPLACE FUNCTION add_relationship(
    p_source_entity_name VARCHAR(255),
    p_source_entity_type VARCHAR(100),
    p_target_entity_name VARCHAR(255),
    p_target_entity_type VARCHAR(100),
    p_relationship_type VARCHAR(100),
    p_attributes JSONB DEFAULT NULL,
    p_confidence_score FLOAT DEFAULT 1.0
) RETURNINGS UUID AS $$
DECLARE
    source_entity_id UUID;
    target_entity_id UUID;
    relationship_id UUID;
BEGIN
    -- 获取或创建源实体
    source_entity_id := add_entity(p_source_entity_name, p_source_entity_type);
    
    -- 获取或创建目标实体
    target_entity_id := add_entity(p_target_entity_name, p_target_entity_type);
    
    -- 创建关系
    INSERT INTO relationships (
        source_entity_id, target_entity_id, relationship_type, confidence_score
    ) VALUES (
        source_entity_id, target_entity_id, p_relationship_type, p_confidence_score
    ) RETURNING id INTO relationship_id;
    
    -- 添加关系属性（如果有）
    IF p_attributes IS NOT NULL THEN
        FOR attr_key, attr_value IN SELECT * FROM jsonb_each_text(p_attributes) LOOP
            INSERT INTO relationship_attributes (relationship_id, attribute_key, attribute_value)
            VALUES (relationship_id, attr_key, attr_value);
        END LOOP;
    END IF;
    
    RETURN relationship_id;
END;
$$ LANGUAGE plpgsql;

-- 13. 创建向量相似度搜索函数
CREATE OR REPLACE FUNCTION search_similar_news(
    p_query_vector VECTOR(768),
    p_limit INT DEFAULT 10,
    p_threshold FLOAT DEFAULT 0.7
) RETURNS TABLE (
    news_id UUID,
    title VARCHAR(500),
    content_text TEXT,
    similarity_score FLOAT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        nc.id as news_id,
        nc.title,
        nc.content_text,
        (nc.title_vector <=> p_query_vector) as similarity_score
    FROM
        news_content nc
    WHERE
        nc.title_vector IS NOT NULL
        AND (nc.title_vector <=> p_query_vector) > p_threshold
    ORDER BY
        similarity_score DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

-- 输出创建结果
SELECT '数据库初始化完成！' as message;
