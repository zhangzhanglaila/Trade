-- PostgreSQL 语料数据库完整初始化脚本
-- 包含数据库创建、用户权限、表结构和初始化数据

-- ============================================
-- 第一部分：数据库和用户创建（需要管理员权限）
-- ============================================

-- 切换到postgres数据库以创建新数据库
\c postgres;

-- 创建数据库（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'corpus_db') THEN
        CREATE DATABASE corpus_db;
        RAISE NOTICE '数据库 corpus_db 已创建';
    ELSE
        RAISE NOTICE '数据库 corpus_db 已存在';
    END IF;
END
$$;

-- 创建用户（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'corpus_user') THEN
        CREATE USER corpus_user WITH PASSWORD 'corpus_password';
        RAISE NOTICE '用户 corpus_user 已创建';
    ELSE
        RAISE NOTICE '用户 corpus_user 已存在';
    END IF;
END
$$;

-- 授予用户权限
ALTER DATABASE corpus_db OWNER TO corpus_user;
GRANT ALL PRIVILEGES ON DATABASE corpus_db TO corpus_user;

-- ============================================
-- 第二部分：数据库结构初始化（切换到corpus_db）
-- ============================================

\c corpus_db;

-- 启用必要的扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 创建模式（可选）
CREATE SCHEMA IF NOT EXISTS corpus;
SET search_path TO corpus, public;

-- 授予用户对模式的权限
GRANT ALL ON SCHEMA corpus TO corpus_user;

-- ============================================
-- 第三部分：核心表结构创建
-- ============================================

-- 1. 新闻内容表
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
    title_vector VECTOR(768),
    content_vector VECTOR(768),
    source VARCHAR(100), -- 数据源标识
    language VARCHAR(10) DEFAULT 'zh',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 实体表
CREATE TABLE IF NOT EXISTS entities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    name_vector VECTOR(768),
    normalized_name VARCHAR(255), -- 标准化名称，用于实体对齐
    entity_id VARCHAR(255), -- 外部知识图谱ID（如果有）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, entity_type)
);

-- 3. 实体属性表
CREATE TABLE IF NOT EXISTS entity_attributes (
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    attribute_key VARCHAR(100) NOT NULL,
    attribute_value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id, attribute_key)
);

-- 4. 新闻-实体关联表
CREATE TABLE IF NOT EXISTS news_entities (
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, entity_id),
    occurrence_positions INTEGER[],
    frequency INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. 实体提及表
CREATE TABLE IF NOT EXISTS entity_mentions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    mention_text VARCHAR(500) NOT NULL,
    context_before TEXT,
    context_after TEXT,
    position_start INTEGER NOT NULL,
    position_end INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 关系表
CREATE TABLE IF NOT EXISTS relationships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    relationship_type VARCHAR(100) NOT NULL,
    confidence_score FLOAT DEFAULT 1.0,
    relation_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 添加约束避免自循环关系
    CONSTRAINT check_not_self_relation CHECK (source_entity_id <> target_entity_id)
);

-- 7. 关系属性表
CREATE TABLE IF NOT EXISTS relationship_attributes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    attribute_key VARCHAR(100) NOT NULL,
    attribute_value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(relationship_id, attribute_key)
);

-- 8. 新闻-关系关联表
CREATE TABLE IF NOT EXISTS news_relationships (
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, relationship_id),
    extract_method VARCHAR(100), -- 提取方法：rule_based, ml_based, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. 实体层级关系表
CREATE TABLE IF NOT EXISTS entity_hierarchy (
    parent_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    child_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    hierarchy_type VARCHAR(100) DEFAULT 'category',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (parent_entity_id, child_entity_id, hierarchy_type)
);

-- 10. 关系历史表
CREATE TABLE IF NOT EXISTS relationship_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    confidence_score FLOAT NOT NULL,
    effective_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 第四部分：索引创建
-- ============================================

-- 新闻表索引
CREATE INDEX IF NOT EXISTS idx_news_content_date ON news_content("date");
CREATE INDEX IF NOT EXISTS idx_news_content_title ON news_content(title);
CREATE INDEX IF NOT EXISTS idx_news_content_source ON news_content(source);
CREATE INDEX IF NOT EXISTS idx_news_content_title_trgm ON news_content USING gin(title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_news_content_text_trgm ON news_content USING gin(content_text gin_trgm_ops);

-- 向量索引
CREATE INDEX IF NOT EXISTS idx_news_title_vector ON news_content USING ivfflat(title_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_news_content_vector ON news_content USING ivfflat(content_vector vector_cosine_ops) WITH (lists = 100);

-- 实体表索引
CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(entity_type);
CREATE INDEX IF NOT EXISTS idx_entities_name ON entities(name);
CREATE INDEX IF NOT EXISTS idx_entities_type_name ON entities(entity_type, name);
CREATE INDEX IF NOT EXISTS idx_entities_name_trgm ON entities USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_entities_name_vector ON entities USING ivfflat(name_vector vector_cosine_ops) WITH (lists = 100);

-- 关系表索引
CREATE INDEX IF NOT EXISTS idx_relationships_source ON relationships(source_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_target ON relationships(target_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_type ON relationships(relationship_type);
CREATE INDEX IF NOT EXISTS idx_relationships_triple ON relationships(source_entity_id, relationship_type, target_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_vector ON relationships USING ivfflat(relation_vector vector_cosine_ops) WITH (lists = 100);

-- 关联表索引
CREATE INDEX IF NOT EXISTS idx_news_entities_news ON news_entities(news_id);
CREATE INDEX IF NOT EXISTS idx_news_entities_entity ON news_entities(entity_id);
CREATE INDEX IF NOT EXISTS idx_news_relationships_news ON news_relationships(news_id);
CREATE INDEX IF NOT EXISTS idx_news_relationships_relation ON news_relationships(relationship_id);

-- 实体提及索引
CREATE INDEX IF NOT EXISTS idx_entity_mentions_news ON entity_mentions(news_id);
CREATE INDEX IF NOT EXISTS idx_entity_mentions_entity ON entity_mentions(entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_mentions_position ON entity_mentions(position_start, position_end);

-- 层级关系索引
CREATE INDEX IF NOT EXISTS idx_entity_hierarchy_parent ON entity_hierarchy(parent_entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_hierarchy_child ON entity_hierarchy(child_entity_id);

-- ============================================
-- 第五部分：视图创建
-- ============================================

-- 实体概览视图
CREATE OR REPLACE VIEW entity_overview AS
SELECT 
    e.id, 
    e.name, 
    e.entity_type, 
    e.normalized_name,
    COUNT(DISTINCT ne.news_id) as news_count,
    COUNT(DISTINCT r.id) as relation_count
FROM 
    entities e
LEFT JOIN 
    news_entities ne ON e.id = ne.entity_id
LEFT JOIN 
    relationships r ON e.id = r.source_entity_id OR e.id = r.target_entity_id
GROUP BY 
    e.id, e.name, e.entity_type, e.normalized_name;

-- 关系网络视图
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
    COUNT(DISTINCT ra.id) as attribute_count,
    COUNT(DISTINCT nr.news_id) as news_reference_count
FROM 
    relationships r
JOIN 
    entities s ON r.source_entity_id = s.id
JOIN 
    entities t ON r.target_entity_id = t.id
LEFT JOIN 
    relationship_attributes ra ON r.id = ra.relationship_id
LEFT JOIN
    news_relationships nr ON r.id = nr.relationship_id
GROUP BY 
    r.id, r.source_entity_id, r.target_entity_id, r.relationship_type, 
    r.confidence_score, s.name, s.entity_type, t.name, t.entity_type;

-- 实体共现视图
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
    e1.id < e2.id
GROUP BY
    e1.id, e1.name, e1.entity_type, e2.id, e2.name, e2.entity_type
ORDER BY
    cooccurrence_count DESC;

-- ============================================
-- 第六部分：核心函数创建
-- ============================================

-- 1. 新闻插入函数
CREATE OR REPLACE FUNCTION corpus.insert_news(
    p_title VARCHAR(500),
    p_date TIMESTAMP,
    p_desc TEXT,
    p_tags TEXT[],
    p_url VARCHAR(1000),
    p_content_html TEXT,
    p_content_text TEXT,
    p_pics TEXT[],
    p_create_time BIGINT,
    p_source VARCHAR(100),
    p_language VARCHAR(10) DEFAULT 'zh',
    p_title_vector VECTOR(768) DEFAULT NULL,
    p_content_vector VECTOR(768) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    new_news_id UUID;
BEGIN
    INSERT INTO corpus.news_content (
        title, "date", "desc", tags, url, 
        content_html, content_text, pics, create_time,
        source, language, title_vector, content_vector
    ) VALUES (
        p_title, p_date, p_desc, p_tags, p_url,
        p_content_html, p_content_text, p_pics, p_create_time,
        p_source, p_language, p_title_vector, p_content_vector
    ) RETURNING id INTO new_news_id;
    
    RETURN new_news_id;
END;
$$ LANGUAGE plpgsql;

-- 2. 实体添加函数
CREATE OR REPLACE FUNCTION corpus.add_entity(
    p_name VARCHAR(255),
    p_entity_type VARCHAR(100),
    p_name_vector VECTOR(768) DEFAULT NULL,
    p_normalized_name VARCHAR(255) DEFAULT NULL,
    p_entity_id VARCHAR(255) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    entity_id UUID;
BEGIN
    INSERT INTO corpus.entities (name, entity_type, name_vector, normalized_name, entity_id)
    VALUES (p_name, p_entity_type, p_name_vector, 
            COALESCE(p_normalized_name, p_name), p_entity_id)
    ON CONFLICT (name, entity_type) DO UPDATE
    SET 
        name_vector = COALESCE(EXCLUDED.name_vector, entities.name_vector),
        normalized_name = COALESCE(EXCLUDED.normalized_name, entities.normalized_name),
        entity_id = COALESCE(EXCLUDED.entity_id, entities.entity_id)
    RETURNING id INTO entity_id;
    
    RETURN entity_id;
END;
$$ LANGUAGE plpgsql;

-- 3. 关系添加函数
CREATE OR REPLACE FUNCTION corpus.add_relationship(
    p_source_entity_id UUID,
    p_target_entity_id UUID,
    p_relationship_type VARCHAR(100),
    p_confidence_score FLOAT DEFAULT 1.0,
    p_relation_vector VECTOR(768) DEFAULT NULL,
    p_attributes JSONB DEFAULT NULL
) RETURNINGS UUID AS $$
DECLARE
    relationship_id UUID;
BEGIN
    -- 检查源实体和目标实体是否存在
    IF NOT EXISTS (SELECT 1 FROM corpus.entities WHERE id = p_source_entity_id) THEN
        RAISE EXCEPTION '源实体不存在: %', p_source_entity_id;
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM corpus.entities WHERE id = p_target_entity_id) THEN
        RAISE EXCEPTION '目标实体不存在: %', p_target_entity_id;
    END IF;
    
    -- 创建关系
    INSERT INTO corpus.relationships (
        source_entity_id, target_entity_id, relationship_type, 
        confidence_score, relation_vector
    ) VALUES (
        p_source_entity_id, p_target_entity_id, p_relationship_type,
        p_confidence_score, p_relation_vector
    ) RETURNING id INTO relationship_id;
    
    -- 添加关系属性
    IF p_attributes IS NOT NULL THEN
        FOR attr_key, attr_value IN SELECT * FROM jsonb_each_text(p_attributes) LOOP
            INSERT INTO corpus.relationship_attributes (relationship_id, attribute_key, attribute_value)
            VALUES (relationship_id, attr_key, attr_value);
        END LOOP;
    END IF;
    
    RETURN relationship_id;
END;
$$ LANGUAGE plpgsql;

-- 4. 向量搜索函数
CREATE OR REPLACE FUNCTION corpus.search_similar_items(
    p_query_vector VECTOR(768),
    p_table_name VARCHAR(100),
    p_vector_column VARCHAR(100),
    p_id_column VARCHAR(100) DEFAULT 'id',
    p_limit INT DEFAULT 10,
    p_threshold FLOAT DEFAULT 0.7
) RETURNS TABLE (
    item_id UUID,
    similarity_score FLOAT
) AS $$
BEGIN
    RETURN QUERY EXECUTE format(
        'SELECT 
            %I::UUID as item_id, 
            (%I <=> $1) as similarity_score 
         FROM 
            corpus.%I 
         WHERE 
            %I IS NOT NULL 
            AND (%I <=> $1) > $2 
         ORDER BY 
            similarity_score DESC 
         LIMIT $3',
        p_id_column, p_vector_column, p_table_name, p_vector_column, p_vector_column
    ) USING p_query_vector, p_threshold, p_limit;
END;
$$ LANGUAGE plpgsql;

-- 5. 批量导入辅助函数
CREATE OR REPLACE FUNCTION corpus.batch_import_news_entities(
    p_news_id UUID,
    p_entities JSONB[]
) RETURNS INTEGER AS $$
DECLARE
    entity_data JSONB;
    entity_id UUID;
    entity_name VARCHAR(255);
    entity_type VARCHAR(100);
    count INTEGER := 0;
BEGIN
    FOREACH entity_data IN ARRAY p_entities LOOP
        entity_name := entity_data->>'name';
        entity_type := entity_data->>'type';
        
        -- 添加实体
        entity_id := corpus.add_entity(entity_name, entity_type);
        
        -- 创建新闻-实体关联
        INSERT INTO corpus.news_entities (news_id, entity_id)
        VALUES (p_news_id, entity_id)
        ON CONFLICT DO NOTHING;
        
        count := count + 1;
    END LOOP;
    
    RETURN count;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 第七部分：初始化示例数据
-- ============================================

-- 插入示例实体类型
INSERT INTO corpus.entities (name, entity_type, normalized_name)
VALUES 
    ('地点', '实体类型', '地点'),
    ('人物', '实体类型', '人物'),
    ('组织机构', '实体类型', '组织机构'),
    ('货物', '实体类型', '货物'),
    ('政策', '实体类型', '政策')
ON CONFLICT (name, entity_type) DO NOTHING;

-- 插入示例关系类型
INSERT INTO corpus.entities (name, entity_type, normalized_name)
VALUES 
    ('出口', '关系类型', '出口'),
    ('收获', '关系类型', '收获'),
    ('种植', '关系类型', '种植'),
    ('签署', '关系类型', '签署'),
    ('见证', '关系类型', '见证')
ON CONFLICT (name, entity_type) DO NOTHING;

-- ============================================
-- 第八部分：权限设置
-- ============================================

-- 授予corpus_user用户对所有表的权限
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA corpus TO corpus_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA corpus TO corpus_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA corpus TO corpus_user;

-- 设置默认权限
ALTER DEFAULT PRIVILEGES IN SCHEMA corpus
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO corpus_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA corpus
    GRANT USAGE, SELECT ON SEQUENCES TO corpus_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA corpus
    GRANT EXECUTE ON FUNCTIONS TO corpus_user;

-- ============================================
-- 完成消息
-- ============================================

SELECT 'PostgreSQL 语料数据库初始化完成！' as message;
SELECT '数据库名: corpus_db' as info;
SELECT '用户名: corpus_user' as info;
SELECT '模式: corpus' as info;
