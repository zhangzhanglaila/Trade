#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
PostgreSQL语料数据库 - 数据导入和查询示例代码

本脚本提供以下功能：
1. 数据库连接配置
2. 新闻数据从CSV导入
3. 知识抽取结果从文本文件导入
4. 向量搜索示例
5. 关系查询示例
6. 批量操作示例
"""

import os
import re
import uuid
import psycopg2
import pandas as pd
from psycopg2.extras import Json, execute_batch
import numpy as np
from datetime import datetime

# 数据库连接配置
DB_CONFIG = {
    'host': 'localhost',
    'database': 'corpus_db',
    'user': 'corpus_user',
    'password': 'corpus_password',
    'port': 5432
}

# 模拟向量生成函数（实际应用中应使用真实的嵌入模型）
def generate_vector(text, dimension=768):
    """生成文本的模拟向量表示"""
    # 简单哈希生成向量（实际应使用如BERT、Sentence-BERT等模型）
    import hashlib
    hash_val = int(hashlib.md5(text.encode('utf-8')).hexdigest(), 16)
    np.random.seed(hash_val % (2**32 - 1))
    return np.random.randn(dimension).tolist()

class PostgreSQLCorpusManager:
    """PostgreSQL语料数据库管理类"""
    
    def __init__(self, config=None):
        self.config = config or DB_CONFIG
        self.conn = None
        self.cursor = None
    
    def connect(self):
        """连接到数据库"""
        try:
            self.conn = psycopg2.connect(**self.config)
            self.cursor = self.conn.cursor()
            print("成功连接到PostgreSQL数据库")
            # 设置搜索路径到corpus模式
            self.cursor.execute("SET search_path TO corpus, public")
            self.conn.commit()
        except Exception as e:
            print(f"数据库连接失败: {e}")
            raise
    
    def disconnect(self):
        """断开数据库连接"""
        if self.cursor:
            self.cursor.close()
        if self.conn:
            self.conn.close()
        print("数据库连接已关闭")
    
    def import_news_from_csv(self, csv_path, source, batch_size=100):
        """从CSV文件导入新闻数据"""
        if not os.path.exists(csv_path):
            print(f"CSV文件不存在: {csv_path}")
            return 0
        
        print(f"开始导入新闻数据: {csv_path}")
        try:
            # 读取CSV文件
            df = pd.read_csv(csv_path)
            total_rows = len(df)
            imported_count = 0
            
            # 分批处理
            for i in range(0, total_rows, batch_size):
                batch = df.iloc[i:i+batch_size]
                
                for _, row in batch.iterrows():
                    try:
                        # 准备数据
                        title = row.get('title', '')
                        date_str = row.get('date', '')
                        # 解析日期
                        date = None
                        if date_str:
                            try:
                                # 尝试不同的日期格式
                                date = pd.to_datetime(date_str)
                            except:
                                date = None
                        
                        # 生成向量
                        title_vector = generate_vector(title) if title else None
                        content_text = row.get('content_text', '')
                        content_vector = generate_vector(content_text) if content_text else None
                        
                        # 处理标签
                        tags_str = row.get('tags', '')
                        tags = []
                        if tags_str:
                            # 简单解析标签，实际需要根据CSV格式调整
                            tags = [tag.strip() for tag in re.findall(r'\w+', tags_str)]
                        
                        # 调用存储过程插入数据
                        self.cursor.callproc(
                            'corpus.insert_news',
                            [
                                title,
                                date,
                                row.get('desc', ''),
                                tags,
                                row.get('url', ''),
                                row.get('content_html', ''),
                                content_text,
                                None,  # pics
                                row.get('create_time', None),
                                source,
                                'zh',
                                title_vector,
                                content_vector
                            ]
                        )
                        
                        imported_count += 1
                        if imported_count % 10 == 0:
                            print(f"已导入 {imported_count}/{total_rows} 条新闻")
                            
                    except Exception as e:
                        print(f"导入新闻失败 (行 {i+_}): {e}")
                        self.conn.rollback()
                        continue
                
                # 提交批次
                self.conn.commit()
            
            print(f"新闻导入完成，共导入 {imported_count} 条记录")
            return imported_count
            
        except Exception as e:
            print(f"导入CSV过程中出错: {e}")
            self.conn.rollback()
            return 0
    
    def import_knowledge_extraction(self, result_dir, batch_size=50):
        """从知识抽取结果文件导入实体和关系"""
        if not os.path.exists(result_dir):
            print(f"结果目录不存在: {result_dir}")
            return 0
        
        print(f"开始导入知识抽取结果: {result_dir}")
        
        # 获取所有txt文件
        txt_files = [f for f in os.listdir(result_dir) if f.endswith('.txt')]
        total_files = len(txt_files)
        processed_files = 0
        imported_entities = 0
        imported_relationships = 0
        
        # 实体缓存，避免重复调用数据库
        entity_cache = {}
        
        for txt_file in txt_files:
            file_path = os.path.join(result_dir, txt_file)
            
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    
                    # 分割实体和关系部分
                    if '---' in content:
                        entities_part, relationships_part = content.split('---', 1)
                    else:
                        entities_part = content
                        relationships_part = ''
                    
                    # 处理实体部分
                    entity_lines = entities_part.strip().split('\n')
                    current_entities = {}
                    
                    for line in entity_lines:
                        line = line.strip()
                        if not line or line.startswith('```'):
                            continue
                        
                        try:
                            # 解析实体和类型
                            if ',' in line:
                                entity_name, entity_type = line.split(',', 1)
                                entity_name = entity_name.strip()
                                entity_type = entity_type.strip()
                                
                                # 检查缓存
                                cache_key = (entity_name, entity_type)
                                if cache_key not in entity_cache:
                                    # 生成向量
                                    name_vector = generate_vector(entity_name)
                                    
                                    # 添加实体
                                    self.cursor.callproc(
                                        'corpus.add_entity',
                                        [entity_name, entity_type, name_vector, None, None]
                                    )
                                    entity_id = self.cursor.fetchone()[0]
                                    entity_cache[cache_key] = entity_id
                                else:
                                    entity_id = entity_cache[cache_key]
                                
                                current_entities[entity_name] = entity_id
                                imported_entities += 1
                                
                        except Exception as e:
                            print(f"解析实体失败 (文件 {txt_file}): {line} - {e}")
                            continue
                    
                    # 处理关系部分
                    relationship_lines = relationships_part.strip().split('\n')
                    
                    for line in relationship_lines:
                        line = line.strip()
                        if not line or line.startswith('```'):
                            continue
                        
                        try:
                            # 解析关系格式: (源实体,关系,目标实体);(属性1:"属性值",属性2:"属性值")
                            if ';' in line:
                                relation_part, attributes_part = line.split(';', 1)
                            else:
                                relation_part = line
                                attributes_part = '()'
                            
                            # 解析关系三元组
                            match = re.search(r'\((.*?),\s*(.*?),\s*(.*?)\)', relation_part)
                            if match:
                                source_entity, relation_type, target_entity = match.groups()
                                source_entity = source_entity.strip()
                                relation_type = relation_type.strip()
                                target_entity = target_entity.strip()
                                
                                # 检查实体是否存在
                                if source_entity in current_entities and target_entity in current_entities:
                                    source_id = current_entities[source_entity]
                                    target_id = current_entities[target_entity]
                                    
                                    # 解析属性
                                    attributes = {}
                                    if attributes_part.strip() != '()':
                                        attr_matches = re.findall(r'(\w+):"([^"]+)"', attributes_part)
                                        for attr_key, attr_value in attr_matches:
                                            attributes[attr_key] = attr_value
                                    
                                    # 生成关系向量
                                    relation_text = f"{source_entity} {relation_type} {target_entity}"
                                    relation_vector = generate_vector(relation_text)
                                    
                                    # 添加关系
                                    self.cursor.callproc(
                                        'corpus.add_relationship',
                                        [
                                            source_id,
                                            target_id,
                                            relation_type,
                                            1.0,  # 置信度
                                            relation_vector,
                                            Json(attributes) if attributes else None
                                        ]
                                    )
                                    
                                    imported_relationships += 1
                        
                        except Exception as e:
                            print(f"解析关系失败 (文件 {txt_file}): {line} - {e}")
                            continue
                
                processed_files += 1
                if processed_files % 10 == 0:
                    print(f"已处理 {processed_files}/{total_files} 个文件")
                
                # 提交批次
                if processed_files % batch_size == 0:
                    self.conn.commit()
                
            except Exception as e:
                print(f"处理文件失败: {txt_file} - {e}")
                continue
        
        # 最后提交剩余的更改
        self.conn.commit()
        
        print(f"知识抽取结果导入完成")
        print(f"- 处理文件: {processed_files}/{total_files}")
        print(f"- 导入实体: {imported_entities}")
        print(f"- 导入关系: {imported_relationships}")
        
        return processed_files
    
    def search_similar_news(self, query_text, limit=10, threshold=0.7):
        """搜索相似新闻"""
        try:
            # 生成查询向量
            query_vector = generate_vector(query_text)
            
            # 调用搜索函数
            self.cursor.callproc(
                'corpus.search_similar_items',
                [query_vector, 'news_content', 'content_vector', 'id', limit, threshold]
            )
            
            results = self.cursor.fetchall()
            similar_news_ids = [r[0] for r in results]
            
            if not similar_news_ids:
                print("未找到相似新闻")
                return []
            
            # 获取新闻详情
            placeholders = ','.join(['%s'] * len(similar_news_ids))
            query = f"""
                SELECT id, title, date, "desc", content_text, similarity_score
                FROM (
                    SELECT 
                        id, title, date, "desc", content_text,
                        (content_vector <=> %s) as similarity_score
                    FROM corpus.news_content
                    WHERE id IN ({placeholders})
                ) as scored_news
                ORDER BY similarity_score DESC
            """
            
            self.cursor.execute(query, [query_vector] + similar_news_ids)
            news_results = self.cursor.fetchall()
            
            print(f"找到 {len(news_results)} 条相似新闻")
            return news_results
            
        except Exception as e:
            print(f"搜索相似新闻失败: {e}")
            return []
    
    def query_entity_relationships(self, entity_name, entity_type=None, depth=2):
        """查询实体的关系网络"""
        try:
            # 查找实体ID
            if entity_type:
                query = """
                    SELECT id FROM corpus.entities 
                    WHERE name = %s AND entity_type = %s
                """
                self.cursor.execute(query, [entity_name, entity_type])
            else:
                query = """
                    SELECT id FROM corpus.entities 
                    WHERE name = %s
                """
                self.cursor.execute(query, [entity_name])
            
            result = self.cursor.fetchone()
            if not result:
                print(f"未找到实体: {entity_name}")
                return []
            
            entity_id = result[0]
            
            # 调用关系网络查询函数
            self.cursor.callproc(
                'corpus.get_entity_relationship_network',
                [entity_id, depth, None]
            )
            
            relationships = self.cursor.fetchall()
            print(f"找到 {len(relationships)} 个相关实体和关系")
            return relationships
            
        except Exception as e:
            print(f"查询实体关系失败: {e}")
            return []
    
    def batch_insert_example(self, entities_list, batch_size=500):
        """批量插入实体示例"""
        try:
            # 使用execute_batch进行批量插入
            query = """
                INSERT INTO corpus.entities (id, name, entity_type, name_vector)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (name, entity_type) DO UPDATE
                SET name_vector = COALESCE(EXCLUDED.name_vector, corpus.entities.name_vector)
            """
            
            # 准备数据
            data = []
            for entity in entities_list:
                entity_id = uuid.uuid4()
                name_vector = generate_vector(entity['name'])
                data.append((
                    entity_id,
                    entity['name'],
                    entity['type'],
                    name_vector
                ))
            
            # 执行批量插入
            execute_batch(self.cursor, query, data, page_size=batch_size)
            self.conn.commit()
            
            print(f"批量插入完成，共 {len(data)} 条实体")
            return len(data)
            
        except Exception as e:
            print(f"批量插入失败: {e}")
            self.conn.rollback()
            return 0


def main():
    """主函数 - 演示各种功能"""
    manager = PostgreSQLCorpusManager()
    
    try:
        # 连接数据库
        manager.connect()
        
        # 示例1: 导入新闻数据
        print("\n=== 示例1: 导入新闻数据 ===")
        news_csv_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "..", "..", "data", "04_采集_新闻语料",
                             "news_content_03-kazinform.csv")
        manager.import_news_from_csv(news_csv_path, source="kazinform", batch_size=10)
        
        # 示例2: 导入知识抽取结果
        print("\n=== 示例2: 导入知识抽取结果 ===")
        results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "..", "..", "data", "07_派生_知识图谱构建产物",
                           "results")
        manager.import_knowledge_extraction(results_dir, batch_size=10)
        
        # 示例3: 向量搜索
        print("\n=== 示例3: 向量搜索 ===")
        query_text = "哈萨克斯坦出口贸易增长"
        similar_news = manager.search_similar_news(query_text, limit=5)
        for news in similar_news:
            print(f"标题: {news[1]}")
            print(f"相似度: {news[5]:.4f}")
            print(f"摘要: {news[3][:100]}..." if news[3] else "摘要: 无")
            print("---")
        
        # 示例4: 实体关系查询
        print("\n=== 示例4: 实体关系查询 ===")
        relationships = manager.query_entity_relationships("哈萨克斯坦", "地点", depth=1)
        for rel in relationships:
            node_id, node_name, node_type, rel_id, rel_type, direction, depth_level = rel
            if depth_level > 0:
                print(f"{node_name} ({node_type}) - {direction} -> {rel_type}")
        
    except Exception as e:
        print(f"主函数执行失败: {e}")
    finally:
        # 断开连接
        manager.disconnect()


if __name__ == "__main__":
    # 运行演示
    # main()
    
    # 打印使用说明
    print("PostgreSQL语料数据库示例代码")
    print("--------------------------")
    print("使用方法:")
    print("1. 确保PostgreSQL数据库已安装并配置pgvector扩展")
    print("2. 首先运行 postgresql_full_initialization.sql 创建数据库结构")
    print("3. 修改本脚本中的DB_CONFIG配置数据库连接信息")
    print("4. 取消main()函数调用的注释以运行完整演示")
    print("\n功能模块:")
    print("- PostgreSQLCorpusManager: 数据库管理主类")
    print("- import_news_from_csv: 从CSV导入新闻")
    print("- import_knowledge_extraction: 导入知识抽取结果")
    print("- search_similar_news: 向量相似性搜索")
    print("- query_entity_relationships: 实体关系网络查询")
    print("- batch_insert_example: 批量插入示例")
