# 简化版语料库系统测试计划

## 测试目标

验证简化版语料库系统是否满足以下核心需求：
- **增加**：能够添加新闻、实体、关系及其向量数据
- **删除**：能够删除新闻、实体和关系
- **查找**：能够执行基本查询、向量相似度搜索和关系查询
- **向量特性**：确保向量数据的正确存储和高效检索
- **关系性**：确保知识图谱结构和关系查询功能正常

## 测试环境准备

1. **PostgreSQL数据库**
   - 版本：PostgreSQL 13+（推荐14或15）
   - 扩展：确保已安装 `pgvector` 和 `uuid-ossp`
   - 配置：建议 `shared_buffers` 设置为系统内存的25%

2. **初始化**
   - 运行 `simple_corpus_initialization.sql` 脚本初始化数据库
   - 验证所有表、索引和函数是否成功创建

3. **测试数据准备**
   - 示例脚本会创建一些基础测试数据
   - 准备用于嵌入的真实向量（生产环境）或模拟向量（测试环境）

## 功能测试

### 1. 向量功能测试

#### 1.1 向量数据插入测试

```sql
-- 测试插入带向量的新闻
SELECT insert_news(
    p_title => '向量测试新闻',
    p_content_text => '这是一篇用于测试向量功能的新闻',
    p_title_vector => '[0.1, 0.2, 0.3, ..., 0.768]'::vector,
    p_content_vector => '[0.5, 0.6, 0.7, ..., 0.768]'::vector
) AS news_id;

-- 测试插入带向量的实体
SELECT add_entity(
    p_name => '向量测试实体',
    p_entity_type => '测试类型',
    p_name_vector => '[0.8, 0.9, 0.1, ..., 0.768]'::vector
) AS entity_id;
```

**验证方法**：查询插入的数据，确认向量列不为空。

#### 1.2 向量相似度搜索测试

```sql
-- 使用SQL直接搜索
SELECT 
    id, title, 
    (content_vector <=> '[0.1, 0.2, 0.3, ..., 0.768]'::vector) AS similarity
FROM 
    news_content
WHERE 
    content_vector IS NOT NULL
ORDER BY 
    similarity DESC
LIMIT 5;

-- 使用向量搜索函数
SELECT * FROM search_similar_items(
    p_query_vector => '[0.1, 0.2, 0.3, ..., 0.768]'::vector,
    p_table_name => 'news_content',
    p_vector_column => 'content_vector',
    p_limit => 5
);
```

**验证方法**：确认搜索结果按照相似度降序排列，且相似的内容排在前面。

#### 1.3 向量索引性能测试

```sql
-- 查看执行计划，确认使用了向量索引
EXPLAIN ANALYZE
SELECT 
    id, title, 
    (content_vector <=> '[0.1, 0.2, 0.3, ..., 0.768]'::vector) AS similarity
FROM 
    news_content
ORDER BY 
    similarity DESC
LIMIT 10;
```

**验证方法**：确认执行计划中包含 `Bitmap Heap Scan` 和 `ivfflat` 索引使用。

### 2. 关系功能测试

#### 2.1 关系创建测试

```sql
-- 先创建两个实体
SELECT add_entity('公司A', '组织') AS entity1_id;
SELECT add_entity('公司B', '组织') AS entity2_id;

-- 创建它们之间的关系（带向量）
SELECT add_relationship(
    p_source_entity_id => (SELECT id FROM entities WHERE name = '公司A'),
    p_target_entity_id => (SELECT id FROM entities WHERE name = '公司B'),
    p_relationship_type => '合作',
    p_relation_vector => '[0.4, 0.5, 0.6, ..., 0.768]'::vector
) AS relationship_id;
```

**验证方法**：查询 relationships 表，确认关系记录正确创建。

#### 2.2 关系查询测试

```sql
-- 使用关系查询函数
SELECT * FROM get_entity_relationships(
    p_entity_id => (SELECT id FROM entities WHERE name = '公司A'),
    p_direction => 'outgoing'
);

-- 深度关系查询
SELECT 
    e1.name as source,
    r1.relationship_type as first_relation,
    e2.name as middle,
    r2.relationship_type as second_relation,
    e3.name as target
FROM 
    relationships r1
JOIN 
    entities e1 ON r1.source_entity_id = e1.id
JOIN 
    entities e2 ON r1.target_entity_id = e2.id
JOIN 
    relationships r2 ON r2.source_entity_id = e2.id
JOIN 
    entities e3 ON r2.target_entity_id = e3.id;
```

**验证方法**：确认返回正确的关系路径和实体信息。

#### 2.3 属性管理测试

```sql
-- 设置实体属性
SELECT set_entity_property(
    p_entity_id => (SELECT id FROM entities WHERE name = '公司A'),
    p_property_name => '成立时间',
    p_property_value => '2000-01-01',
    p_property_type => 'date'
);

-- 查询实体属性
SELECT 
    e.name,
    ep.property_name,
    ep.property_value
FROM 
    entities e
JOIN 
    entity_properties ep ON e.id = ep.entity_id
WHERE 
    e.name = '公司A';
```

**验证方法**：确认属性正确存储和查询。

### 3. 核心功能测试（增加、删除、查找）

#### 3.1 增加功能测试

```sql
-- 测试所有增加功能
-- 1. 添加新闻
SELECT insert_news('测试新闻', '测试内容');

-- 2. 添加实体
SELECT add_entity('测试实体', '测试类型');

-- 3. 添加关系
SELECT add_relationship(
    (SELECT id FROM entities WHERE name = '测试实体'),
    (SELECT id FROM entities WHERE name = '公司A'),
    '相关'
);

-- 4. 关联新闻和实体
SELECT link_news_entity(
    (SELECT id FROM news_content WHERE title = '测试新闻'),
    (SELECT id FROM entities WHERE name = '测试实体')
);
```

**验证方法**：确认所有数据正确插入，且关联关系成立。

#### 3.2 删除功能测试

```sql
-- 测试删除功能
-- 1. 删除关联
DELETE FROM news_entities 
WHERE entity_id = (SELECT id FROM entities WHERE name = '测试实体');

-- 2. 删除新闻
SELECT delete_news(
    (SELECT id FROM news_content WHERE title = '测试新闻')
);

-- 3. 删除关系
SELECT delete_relationship(
    (SELECT id FROM relationships 
     WHERE source_entity_id = (SELECT id FROM entities WHERE name = '公司A')
     AND target_entity_id = (SELECT id FROM entities WHERE name = '公司B'))
);

-- 4. 删除实体
SELECT delete_entity(
    (SELECT id FROM entities WHERE name = '测试实体')
);
```

**验证方法**：确认数据被正确删除，且级联删除正常工作。

#### 3.3 查找功能测试

```sql
-- 基本查找
-- 1. 根据ID查找
SELECT * FROM news_content WHERE id = 'uuid-here';

-- 2. 根据名称查找实体
SELECT * FROM entities WHERE name LIKE '%公司%';

-- 3. 查找特定类型的实体
SELECT * FROM entities WHERE entity_type = '组织';

-- 4. 查找实体相关的所有新闻
SELECT n.title, e.name
FROM news_content n
JOIN news_entities ne ON n.id = ne.news_id
JOIN entities e ON ne.entity_id = e.id
WHERE e.name = '公司A';
```

**验证方法**：确认查询返回正确的结果集。

## 集成测试

使用 Python 示例代码进行端到端测试：

1. 修改 `corpus_examples.py` 中的数据库连接参数
2. 运行示例代码：`python corpus_examples.py`
3. 检查输出，确认所有操作成功完成

### 测试场景：

1. **基本数据管理**：添加、更新、删除数据
2. **向量搜索**：搜索相似内容，验证结果相关性
3. **关系查询**：查询实体关系网络，验证知识图谱功能
4. **性能测试**：在较大数据集上测试查询性能

## 性能考量

1. **向量索引优化**：
   - 调整 `lists` 参数（建议为数据量的平方根）
   - 考虑使用 `hnsw` 索引（PostgreSQL 15+）

2. **查询优化**：
   - 确保关系查询使用了合适的索引
   - 对频繁的复合查询创建适当的索引

3. **内存配置**：
   - 增加 `shared_buffers` 提高缓存命中率
   - 调整 `work_mem` 改善复杂查询性能

## 测试结果记录

| 测试项目 | 测试状态 | 通过/失败 | 问题描述 | 解决方法 |
|---------|---------|----------|---------|----------|
| 向量插入 | 未测试  |          |         |          |
| 向量搜索 | 未测试  |          |         |          |
| 关系创建 | 未测试  |          |         |          |
| 关系查询 | 未测试  |          |         |          |
| 数据增加 | 未测试  |          |         |          |
| 数据删除 | 未测试  |          |         |          |
| 基本查找 | 未测试  |          |         |          |
| 集成测试 | 未测试  |          |         |          |
| 性能测试 | 未测试  |          |         |          |

## 总结

本测试计划覆盖了简化版语料库系统的所有核心功能，特别关注向量特性和关系性功能。通过执行这些测试，可以验证系统是否满足需求，并发现潜在的问题。

实际部署前，建议在真实数据和生产环境下进行全面测试，确保系统在实际应用中的稳定性和性能。