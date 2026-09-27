from elasticsearch import Elasticsearch
import json
import os

ES_HOST = "http://localhost:9200"
INDEX_NAME = "news_content_03"
BATCH_SIZE = 10
PROGRESS_FILE = "progress.txt"

import builder


# 你的处理函数
def process(doc_id, content):
  builder.process("data/langchain", doc_id, content)


from tqdm import tqdm

es = Elasticsearch(ES_HOST)
def main():
  query = {
    "query": {
      "match_all": {}
    },
    "sort": [
      {"create_time": "asc"}
    ],
    "size": BATCH_SIZE
  }

  search_after = None

  while True:
    if search_after:
      query["search_after"] = search_after

    response = es.search(index=INDEX_NAME, body=query)
    hits = response['hits']['hits']
    if not hits:
      break

    for hit in tqdm(hits):
      content = hit['_source'].get('content_text')
      if content:
        process(hit['_id'], content)

    # 准备下一页的 search_after 值
    last_hit = hits[-1]
    sort_values = last_hit['sort']
    search_after = sort_values


if __name__ == "__main__":
  main()
