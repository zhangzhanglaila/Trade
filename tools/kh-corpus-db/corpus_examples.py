#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
简化版语料库系统使用示例

本文件展示如何使用Python与PostgreSQL数据库进行交互，
演示向量搜索和关系查询功能。
"""

import psycopg2
from psycopg2.extras import DictCursor
import uuid
import random
from datetime import datetime
import json


class CorpusExample:
    """语料库示例类"""
    
    def __init__(self, dbname="corpus", user="postgres", password="your_password", 
                 host="localhost", port="5432"):
        """初始化数据库连接"""
        self.conn_params = {
            "dbname": dbname,
            "user": user,
            "password": password,
            "host": host,
            "port": port
        }
        self.conn = None
        self.cursor = None
    
    def connect(self):
        """连接到数据库"""
        try:
            self.conn = psycopg2.connect(**self.conn_params)
            self.cursor = self.conn.cursor(cursor_factory=DictCursor)
            print("数据库连接成功!")
            return True
        except Exception as e:
            print(f"数据库连接失败: {e}")
            return False
    
    def disconnect(self):
        """断开数据库连接"""
        if self.cursor:
            self.cursor.close()
        if self.conn:
            self.conn.close()
        print("数据库连接已关闭")
    
    def generate_random_vector(self, dim=768):
        """
        生成随机向量用于演示
        注意：实际应用中应使用真实的嵌入模型（如BERT、GPT等）生成向量
        """
        return f"[{','.join([str(random.uniform(-1, 1)) for _ in range(dim)])}]"
    
    def add_news_with_vectors(self, title, content, date=None, url=None, source=None):
        """
        添加带向量的新闻
        """
        try:
            # 生成随机向量（实际应用中应使用真实嵌入）
            title_vector = self.generate_random_vector()
            content_vector = self.generate_random_vector()
            
            query = """
            SELECT insert_news(
                p_title => %s,
                p_content_text => %s,
                p_date => %s,
                p_url => %s,
                p_source => %s,
                p_title_vector => %s,
                p_content_vector => %s
            ) AS news_id
            """
            
            self.cursor.execute(query, (title, content, date, url, source, 
                                      title_vector, content_vector))
            news_id = self.cursor.fetchone()[0]
            self.conn.commit()
            print(f"新闻添加成功，ID: {news_id}")
            return news_id
        except Exception as e:
            self.conn.rollback()
            print(f"添加新闻失败: {e}")
            return None
    
    def add_entity_with_vector(self, name, entity_type):
        """
        添加带向量的实体
        """
        try:
            # 生成随机向量（实际应用中应使用真实嵌入）
            name_vector = self.generate_random_vector()
            
            query = """
            SELECT add_entity(
                p_name => %s,
                p_entity_type => %s,
                p_name_vector => %s
            ) AS entity_id
            """
            
            self.cursor.execute(query, (name, entity_type, name_vector))
            entity_id = self.cursor.fetchone()[0]
            self.conn.commit()
            print(f"实体添加成功，ID: {entity_id}")
            return entity_id
        except Exception as e:
            self.conn.rollback()
            print(f"添加实体失败: {e}")
            return None
    
    def search_similar_news(self, query_text, top_k=5):
        """
        搜索相似新闻
        注意：实际应用中应使用真实的向量模型将query_text转换为向量
        """
        try:
            # 生成查询向量（实际应用中应使用真实嵌入）
            query_vector = self.generate_random_vector()
            
            # 使用向量搜索函数
            query = """
            WITH similar_items AS (
                SELECT item_id, similarity_score
                FROM search_similar_items(
                    p_query_vector => %s,
                    p_table_name => 'news_content',
                    p_vector_column => 'content_vector',
                    p_limit => %s
                )
            )
            SELECT nc.id, nc.title, nc.content_text, si.similarity_score
            FROM similar_items si
            JOIN news_content nc ON si.item_id = nc.id
            ORDER BY si.similarity_score DESC
            """
            
            self.cursor.execute(query, (query_vector, top_k))
            results = self.cursor.fetchall()
            
            print(f"找到 {len(results)} 条相似新闻:")
            for result in results:
                print(f"相似度: {result['similarity_score']:.4f}")
                print(f"标题: {result['title']}")
                print(f"内容片段: {result['content_text'][:100]}...")
                print("---")
            
            return results
        except Exception as e:
            print(f"搜索相似新闻失败: {e}")
            return []
    
    def query_entity_relationships(self, entity_name, relationship_type=None, direction='all'):
        """
        查询实体关系
        """
        try:
            # 先获取实体ID
            self.cursor.execute("SELECT id FROM entities WHERE name = %s", (entity_name,))
            entity = self.cursor.fetchone()
            
            if not entity:
                print(f"未找到实体: {entity_name}")
                return []
            
            entity_id = entity['id']
            
            # 使用关系查询函数
            query = """
            SELECT * FROM get_entity_relationships(
                p_entity_id => %s,
                p_relationship_type => %s,
                p_direction => %s
            )
            """
            
            self.cursor.execute(query, (entity_id, relationship_type, direction))
            results = self.cursor.fetchall()
            
            print(f"实体 '{entity_name}' 的关系:")
            for result in results:
                print(f"关系类型: {result['relationship_type']}")
                print(f"方向: {result['direction']}")
                print(f"相关实体: {result['related_entity_name']}")
                print("---")
            
            return results
        except Exception as e:
            print(f"查询实体关系失败: {e}")
            return []
    
    def deep_relationship_query(self, start_entity_name, first_relation_type, second_relation_type):
        """
        深度关系查询：查找通过两层关系连接的实体
        """
        try:
            query = """
            WITH start_entity AS (
                SELECT id FROM entities WHERE name = %s
            )
            SELECT 
                e1.name as source,
                r1.relationship_type as first_relation,
                e2.name as middle,
                r2.relationship_type as second_relation,
                e3.name as target
            FROM 
                start_entity se
            JOIN
                relationships r1 ON r1.source_entity_id = se.id
            JOIN 
                entities e1 ON r1.source_entity_id = e1.id
            JOIN 
                entities e2 ON r1.target_entity_id = e2.id
            JOIN 
                relationships r2 ON r2.source_entity_id = e2.id
            JOIN 
                entities e3 ON r2.target_entity_id = e3.id
            WHERE 
                r1.relationship_type = %s AND
                r2.relationship_type = %s
            """
            
            self.cursor.execute(query, (start_entity_name, first_relation_type, second_relation_type))
            results = self.cursor.fetchall()
            
            print(f"深度关系查询结果（从'{start_entity_name}'开始）:")
            for result in results:
                print(f"{result['source']} -[{result['first_relation']}]-> {result['middle']} -[{result['second_relation']}]-> {result['target']}")
            
            return results
        except Exception as e:
            print(f"深度关系查询失败: {e}")
            return []
    
    def delete_example_news(self, title_pattern):
        """
        删除示例新闻
        """
        try:
            # 查找匹配的新闻
            self.cursor.execute(
                "SELECT id, title FROM news_content WHERE title ILIKE %s",
                (f"%{title_pattern}%",)
            )
            news_items = self.cursor.fetchall()
            
            if not news_items:
                print(f"未找到包含'{title_pattern}'的新闻")
                return False
            
            print(f"找到 {len(news_items)} 条匹配的新闻:")
            for item in news_items:
                print(f"准备删除: {item['title']} (ID: {item['id']})")
                # 调用删除函数
                self.cursor.execute("SELECT delete_news(%s)", (item['id'],))
            
            self.conn.commit()
            print("删除完成")
            return True
        except Exception as e:
            self.conn.rollback()
            print(f"删除新闻失败: {e}")
            return False
    
    def update_entity_property(self, entity_name, property_name, property_value, property_type='string'):
        """
        更新实体属性
        """
        try:
            # 先获取实体ID
            self.cursor.execute("SELECT id FROM entities WHERE name = %s", (entity_name,))
            entity = self.cursor.fetchone()
            
            if not entity:
                print(f"未找到实体: {entity_name}")
                return False
            
            entity_id = entity['id']
            
            # 调用属性更新函数
            query = """
            SELECT set_entity_property(
                p_entity_id => %s,
                p_property_name => %s,
                p_property_value => %s,
                p_property_type => %s
            )
            """
            
            self.cursor.execute(query, (entity_id, property_name, property_value, property_type))
            self.conn.commit()
            print(f"实体 '{entity_name}' 的属性 '{property_name}' 更新成功")
            return True
        except Exception as e:
            self.conn.rollback()
            print(f"更新实体属性失败: {e}")
            return False
    
    def run_demo(self):
        """
        运行完整的演示
        """
        if not self.connect():
            return
        
        try:
            print("==== 开始语料库演示 ====\n")
            
            # 1. 添加示例数据
            print("1. 添加示例数据")
            
            # 添加带向量的新闻
            news_id1 = self.add_news_with_vectors(
                title="人工智能技术最新进展",
                content="近期，人工智能技术在自然语言处理和计算机视觉领域取得了重大突破。特别是在大型语言模型方面，最新的研究成果显示出前所未有的能力...",
                date=datetime.now(),
                source="科技日报"
            )
            
            news_id2 = self.add_news_with_vectors(
                title="城市可持续发展规划",
                content="随着城市化进程的加速，如何实现城市的可持续发展成为全球关注的焦点。本报告分析了国内外先进城市的发展经验...",
                date=datetime.now(),
                source="城市规划杂志"
            )
            
            # 添加带向量的实体
            person_id = self.add_entity_with_vector("张明", "人物")
            org_id = self.add_entity_with_vector("科技创新研究院", "组织")
            city_id = self.add_entity_with_vector("深圳", "地点")
            
            print("\n2. 查询示例")
            
            # 2. 向量相似度搜索示例
            print("\n2.1 向量相似度搜索示例")
            self.search_similar_news("人工智能技术发展", top_k=2)
            
            # 3. 关系查询示例
            print("\n2.2 实体关系查询示例")
            # 注意：需要先创建关系才能查询到结果
            # 这里假设已经有一些关系存在
            
            # 4. 属性更新示例
            print("\n2.3 实体属性更新示例")
            self.update_entity_property("深圳", "GDP", "3万亿", "string")
            
            print("\n==== 演示完成 ====")
            
        except Exception as e:
            print(f"演示过程中出错: {e}")
        finally:
            self.disconnect()


# 使用说明
def main():
    """主函数"""
    # 创建示例类实例
    # 请根据实际情况修改数据库连接参数
    example = CorpusExample(
        dbname="corpus",  # 数据库名
        user="postgres",  # 用户名
        password="your_password",  # 密码
        host="localhost",  # 主机
        port="5432"  # 端口
    )
    
    # 运行演示
    example.run_demo()
    
    print("\n使用提示：")
    print("1. 请确保PostgreSQL数据库已安装pgvector扩展")
    print("2. 请先运行simple_corpus_initialization.sql初始化数据库")
    print("3. 实际应用中，请将generate_random_vector函数替换为真实的向量嵌入模型")
    print("4. 本示例提供了基本的CRUD操作和向量/关系查询功能")


if __name__ == "__main__":
    main()
