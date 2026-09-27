import hashlib
import time
from elasticsearch import Elasticsearch, helpers


class EsStorage:
  def __init__(self, elasticsearch=None):
    if elasticsearch is None:
      self.es = Elasticsearch("http://localhost:9200")
    else:
      self.es = elasticsearch

  def generate_id(self, link: str) -> str:
    return hashlib.md5(link.encode('utf-8')).hexdigest()

  def ensure_index(self, index_name: str):
    """检查索引是否存在，不存在则创建"""
    if not self.es.indices.exists(index=index_name):
      mapping = {
        "mappings": {
          "properties": {
            "title": {"type": "text"},
            "link": {"type": "keyword"},
            "cover": {"type": "keyword"},
            "news_date": {"type": "text"},
            "create_time": {"type": "long"}
          }
        }
      }
      self.es.indices.create(index=index_name, body=mapping)
      print(f"Created index: {index_name}")
    else:
      print(f"Index already exists: {index_name}")

  def ensure_content_index(self, index_name: str):
    """确保新闻内容索引存在，否则自动创建"""
    if not self.es.indices.exists(index=index_name):
      mapping = {
        "mappings": {
          "properties": {
            "title": {
              "type": "text",
              "fields": {"raw": {"type": "keyword"}}
            },
            "date": {"type": "keyword"},
            "desc": {"type": "text"},
            "tags": {"type": "keyword"},
            "url": {"type": "keyword"},
            "content_html": {"type": "text"},
            "content_text": {"type": "text"},
            "pics": {"type": "keyword"},
            "create_time": {"type": "long"}
          }
        }
      }
      self.es.indices.create(index=index_name, body=mapping)
      print(f"✅ Created index: {index_name}")
    else:
      print(f"ℹ️ Index already exists: {index_name}")

  def bulk_index_news_content(self, content_list: list[dict]):
    """批量写入新闻内容文档"""
    index_name = 'news_content_03'
    self.ensure_content_index(index_name)

    actions = []
    for doc in content_list:
      doc_id = self.generate_id(doc["url"])
      action = {
        "_index": index_name,
        "_id": doc_id,
        "_source": {
          "title": doc.get("title", ''),
          "date": doc.get("date", ''),
          "desc": doc.get("desc", ''),
          "tags": doc.get("tags", ''),
          "url": doc.get("url", ''),
          "content_html": doc.get("content_html", ''),
          "content_text": doc.get("content_text", ''),
          "pics": doc.get("pics", ''),
          "create_time": int(time.time()),
        }
      }
      actions.append(action)

    helpers.bulk(self.es, actions)

    print(f"✅ Bulk indexed {len(actions)} content documents.")

  def add_news_index(self, news):
    title = news.get('title', '')
    link = news.get('link', '')
    cover = news.get('cover', '')
    date = news.get('date', str(time.time()))

    es_index_name = 'news_index'
    self.ensure_index(es_index_name)
    doc_id = self.generate_id(link)
    doc = {
      "title": title,
      "link": link,
      "cover": cover,
      "news_date": date,
      "create_time": int(time.time())
    }

    self.es.index(index=es_index_name, id=doc_id, document=doc)
    print(f"Indexed: {doc_id}")

  def news_index_batch(self, news_list):
    es_index_name = 'news_index'
    self.ensure_index(es_index_name)

    docs = []
    for news in news_list:
      title = news.get('title', '')
      link = news.get('link', '')
      cover = news.get('cover', '')
      date = news.get('date', str(time.time()))

      doc_id = self.generate_id(link)
      docs.append({
        "_index": es_index_name,
        "_id": doc_id,
        "_source": {
          "title": title,
          "link": link,
          "cover": cover,
          "news_date": date,
          "create_time": int(time.time())
        }
      })
    helpers.bulk(self.es, docs)