-- 简化版语料库数据库初始化脚本（支持向量和关系特性）
-- 注意：此脚本需要PostgreSQL 13+版本和pgvector扩展

-- 创建扩展（如果尚未安装）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- 确保pgvector扩展已安装
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'pgvector扩展未安装，请先安装: CREATE EXTENSION vector;';
    END IF;
END $$;

-- 创建主表结构

-- 1. 新闻内容表（包含向量字段）
CREATE TABLE IF NOT EXISTS news_content (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    "date" TIMESTAMP,
    content_text TEXT NOT NULL,
    url VARCHAR(1000) UNIQUE,
    source VARCHAR(100),
    title_vector VECTOR(768),
    content_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 实体表（包含向量字段）
CREATE TABLE IF NOT EXISTS entities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    name_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, entity_type)
);

-- 3. 关系表（包含向量字段）
CREATE TABLE IF NOT EXISTS relationships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    relationship_type VARCHAR(100) NOT NULL,
    relation_vector VECTOR(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_not_self_relation CHECK (source_entity_id <> target_entity_id)
);

-- 4. 新闻实体关联表
CREATE TABLE IF NOT EXISTS news_entities (
    news_id UUID REFERENCES news_content(id) ON DELETE CASCADE,
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (news_id, entity_id)
);

-- 5. 实体属性表
CREATE TABLE IF NOT EXISTS entity_properties (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    property_name VARCHAR(100) NOT NULL,
    property_value TEXT,
    property_type VARCHAR(50) DEFAULT 'string',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 关系属性表
CREATE TABLE IF NOT EXISTS relationship_properties (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    relationship_id UUID REFERENCES relationships(id) ON DELETE CASCADE,
    property_name VARCHAR(100) NOT NULL,
    property_value TEXT,
    property_type VARCHAR(50) DEFAULT 'string',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 添加唯一性约束
ALTER TABLE entity_properties ADD CONSTRAINT entity_properties_entity_id_property_name_key UNIQUE(entity_id, property_name);
ALTER TABLE relationship_properties ADD CONSTRAINT relationship_properties_relationship_id_property_name_key UNIQUE(relationship_id, property_name);

-- 创建索引（基本索引、关系索引和向量索引）
-- 基本索引
CREATE INDEX IF NOT EXISTS idx_news_content_title ON news_content(title);
CREATE INDEX IF NOT EXISTS idx_entities_name ON entities(name);
CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(entity_type);
CREATE INDEX IF NOT EXISTS idx_relationships_source ON relationships(source_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_target ON relationships(target_entity_id);
CREATE INDEX IF NOT EXISTS idx_relationships_type ON relationships(relationship_type);

-- 增强关系查询的索引
CREATE INDEX IF NOT EXISTS idx_news_entities_entity ON news_entities(entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_properties_entity ON entity_properties(entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_properties_name ON entity_properties(property_name);
CREATE INDEX IF NOT EXISTS idx_relationship_properties_rel ON relationship_properties(relationship_id);
CREATE INDEX IF NOT EXISTS idx_relationship_properties_name ON relationship_properties(property_name);

-- 向量索引
CREATE INDEX IF NOT EXISTS idx_news_title_vector ON news_content USING ivfflat(title_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_news_content_vector ON news_content USING ivfflat(content_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_entities_name_vector ON entities USING ivfflat(name_vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_relationships_vector ON relationships USING ivfflat(relation_vector vector_cosine_ops) WITH (lists = 100);

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为所有表创建更新时间触发器
CREATE TRIGGER update_news_content_modtime
BEFORE UPDATE ON news_content
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_entities_modtime
BEFORE UPDATE ON entities
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_relationships_modtime
BEFORE UPDATE ON relationships
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_entity_properties_modtime
BEFORE UPDATE ON entity_properties
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_relationship_properties_modtime
BEFORE UPDATE ON relationship_properties
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 核心功能函数

-- 1. 插入新闻函数（支持向量）
CREATE OR REPLACE FUNCTION insert_news(
    p_title VARCHAR(500),
    p_content_text TEXT,
    p_date TIMESTAMP DEFAULT NULL,
    p_url VARCHAR(1000) DEFAULT NULL,
    p_source VARCHAR(100) DEFAULT NULL,
    p_title_vector VECTOR(768) DEFAULT NULL,
    p_content_vector VECTOR(768) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    new_news_id UUID;
BEGIN
    INSERT INTO news_content (title, content_text, "date", url, source, title_vector, content_vector)
    VALUES (p_title, p_content_text, p_date, p_url, p_source, p_title_vector, p_content_vector)
    RETURNING id INTO new_news_id;
    
    RETURN new_news_id;
END;
$$ LANGUAGE plpgsql;

-- 2. 添加实体函数（支持向量）
CREATE OR REPLACE FUNCTION add_entity(
    p_name VARCHAR(255),
    p_entity_type VARCHAR(100),
    p_name_vector VECTOR(768) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    entity_id UUID;
BEGIN
    INSERT INTO entities (name, entity_type, name_vector)
    VALUES (p_name, p_entity_type, p_name_vector)
    ON CONFLICT (name, entity_type) DO UPDATE
    SET updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO entity_id;
    
    RETURN entity_id;
END;
$$ LANGUAGE plpgsql;

-- 3. 添加关系函数（支持向量）
CREATE OR REPLACE FUNCTION add_relationship(
    p_source_entity_id UUID,
    p_target_entity_id UUID,
    p_relationship_type VARCHAR(100),
    p_relation_vector VECTOR(768) DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    relationship_id UUID;
BEGIN
    -- 检查源实体和目标实体是否存在
    IF NOT EXISTS (SELECT 1 FROM entities WHERE id = p_source_entity_id) THEN
        RAISE EXCEPTION '源实体不存在';
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM entities WHERE id = p_target_entity_id) THEN
        RAISE EXCEPTION '目标实体不存在';
    END IF;
    
    -- 创建关系
    INSERT INTO relationships (source_entity_id, target_entity_id, relationship_type, relation_vector)
    VALUES (p_source_entity_id, p_target_entity_id, p_relationship_type, p_relation_vector)
    RETURNING id INTO relationship_id;
    
    RETURN relationship_id;
END;
$$ LANGUAGE plpgsql;

-- 4. 关联新闻和实体函数
CREATE OR REPLACE FUNCTION link_news_entity(
    p_news_id UUID,
    p_entity_id UUID
) RETURNS BOOLEAN AS $$
BEGIN
    -- 检查新闻和实体是否存在
    IF NOT EXISTS (SELECT 1 FROM news_content WHERE id = p_news_id) THEN
        RAISE EXCEPTION '新闻不存在';
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM entities WHERE id = p_entity_id) THEN
        RAISE EXCEPTION '实体不存在';
    END IF;
    
    -- 创建关联
    INSERT INTO news_entities (news_id, entity_id)
    VALUES (p_news_id, p_entity_id)
    ON CONFLICT DO NOTHING;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 5. 向量搜索函数
CREATE OR REPLACE FUNCTION search_similar_items(
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
            %I 
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

-- 6. 关系查询函数
CREATE OR REPLACE FUNCTION get_entity_relationships(
    p_entity_id UUID,
    p_relationship_type VARCHAR(100) DEFAULT NULL,
    p_direction VARCHAR(10) DEFAULT 'all' -- 'outgoing', 'incoming', 'all'
) RETURNS TABLE (
    related_entity_id UUID,
    related_entity_name VARCHAR(255),
    relationship_type VARCHAR(100),
    direction VARCHAR(10)
) AS $$
BEGIN
    IF p_direction = 'outgoing' OR p_direction = 'all' THEN
        RETURN QUERY
        SELECT 
            e.id, e.name, r.relationship_type, 'outgoing' as direction
        FROM 
            relationships r
        JOIN 
            entities e ON r.target_entity_id = e.id
        WHERE 
            r.source_entity_id = p_entity_id
            AND (p_relationship_type IS NULL OR r.relationship_type = p_relationship_type);
    END IF;
    
    IF p_direction = 'incoming' OR p_direction = 'all' THEN
        RETURN QUERY
        SELECT 
            e.id, e.name, r.relationship_type, 'incoming' as direction
        FROM 
            relationships r
        JOIN 
            entities e ON r.source_entity_id = e.id
        WHERE 
            r.target_entity_id = p_entity_id
            AND (p_relationship_type IS NULL OR r.relationship_type = p_relationship_type);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- 7. 实体属性管理函数
CREATE OR REPLACE FUNCTION set_entity_property(
    p_entity_id UUID,
    p_property_name VARCHAR(100),
    p_property_value TEXT,
    p_property_type VARCHAR(50) DEFAULT 'string'
) RETURNS BOOLEAN AS $$
BEGIN
    -- 检查实体是否存在
    IF NOT EXISTS (SELECT 1 FROM entities WHERE id = p_entity_id) THEN
        RAISE EXCEPTION '实体不存在';
    END IF;
    
    -- 设置或更新属性
    INSERT INTO entity_properties (entity_id, property_name, property_value, property_type)
    VALUES (p_entity_id, p_property_name, p_property_value, p_property_type)
    ON CONFLICT ON CONSTRAINT entity_properties_entity_id_property_name_key
    DO UPDATE SET 
        property_value = EXCLUDED.property_value,
        property_type = EXCLUDED.property_type,
        updated_at = CURRENT_TIMESTAMP;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 8. 删除新闻函数
CREATE OR REPLACE FUNCTION delete_news(p_news_id UUID) RETURNS BOOLEAN AS $$
BEGIN
    -- 检查新闻是否存在
    IF NOT EXISTS (SELECT 1 FROM news_content WHERE id = p_news_id) THEN
        RAISE EXCEPTION '新闻不存在';
    END IF;
    
    -- 删除新闻（关联数据会级联删除）
    DELETE FROM news_content WHERE id = p_news_id;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 9. 删除实体函数
CREATE OR REPLACE FUNCTION delete_entity(p_entity_id UUID) RETURNS BOOLEAN AS $$
BEGIN
    -- 检查实体是否存在
    IF NOT EXISTS (SELECT 1 FROM entities WHERE id = p_entity_id) THEN
        RAISE EXCEPTION '实体不存在';
    END IF;
    
    -- 删除实体（关联数据会级联删除）
    DELETE FROM entities WHERE id = p_entity_id;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 10. 删除关系函数
CREATE OR REPLACE FUNCTION delete_relationship(p_relationship_id UUID) RETURNS BOOLEAN AS $$
BEGIN
    -- 检查关系是否存在
    IF NOT EXISTS (SELECT 1 FROM relationships WHERE id = p_relationship_id) THEN
        RAISE EXCEPTION '关系不存在';
    END IF;
    
    -- 删除关系（关联属性会级联删除）
    DELETE FROM relationships WHERE id = p_relationship_id;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- 示例数据插入
-- 创建一些示例实体
-- 注意：这里使用模拟向量，实际应用中应使用真实的嵌入向量

-- 清理权限（确保用户有适当的权限）
-- 假设使用的是当前连接的用户
DO $$
BEGIN
    -- 为示例创建一些数据
    PERFORM add_entity('张三', '人物');
    PERFORM add_entity('李四', '人物');
    PERFORM add_entity('王五', '人物');
    PERFORM add_entity('北京', '地点');
    PERFORM add_entity('上海', '地点');
    
    -- 创建一些关系
    PERFORM add_relationship(
        (SELECT id FROM entities WHERE name = '张三'),
        (SELECT id FROM entities WHERE name = '李四'),
        '朋友'
    );
    
    PERFORM add_relationship(
        (SELECT id FROM entities WHERE name = '张三'),
        (SELECT id FROM entities WHERE name = '北京'),
        '居住在'
    );
    
    -- 添加实体属性
    PERFORM set_entity_property(
        (SELECT id FROM entities WHERE name = '张三'),
        '年龄',
        '30',
        'number'
    );
    
    PERFORM set_entity_property(
        (SELECT id FROM entities WHERE name = '北京'),
        '人口',
        '2100万',
        'string'
    );
    
    -- 创建示例新闻
    PERFORM insert_news(
        '北京城市发展规划新闻',
        '北京市最近发布了新的城市发展规划，包括交通、教育、医疗等多个方面的改进...',
        CURRENT_TIMESTAMP,
        'https://example.com/news/beijing-plan'
    );
    
    -- 关联新闻和实体
    PERFORM link_news_entity(
        (SELECT id FROM news_content WHERE title = '北京城市发展规划新闻'),
        (SELECT id FROM entities WHERE name = '北京')
    );
    
    RAISE NOTICE '示例数据创建完成！';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE '示例数据创建失败: %', SQLERRM;
END $$;

-- 完成初始化
SELECT '简化版语料库数据库初始化完成！' AS status;
