import time

from elasticsearch import Elasticsearch
import json
import os

INDEX_NAME = "news_content_03"
BATCH_SIZE = 15
PROGRESS_FILE = "progress.txt"

import builder2


from tqdm import tqdm

es = Elasticsearch(hosts="http://10.221.0.8:9200")
from neo4j import GraphDatabase

from _trade_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database=NEO4J_DATABASE)

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
      news_id = hit['_id']

      filedir = 'data/pjcait/extract/new2/data'
      # filedir = os.path.join(filedir, str(int(time.time())))
      if not os.path.isdir(filedir):
        os.makedirs(filedir)

      filename = os.path.join(filedir, f'{news_id}.txt')
      if os.path.exists(filename):
        continue

      source = hit['_source']
      content = source.get('content_text')
      title = source.get('title')
      date = source.get('date')
      tags = source.get('tags')
      content = f'标题：{title}\n新闻时间：{date}\n{tags}\n正文：{content}'

      if content and len(content) < 1500:
        llm_result = builder2.call_llm(content)
        builder2.save_output(llm_result, filename)
        entities, relations = builder2.parse_llm_output(llm_result)
        builder2.insert_to_neo4j(driver, entities, relations)
      else:
        print(f'content len too long, skip, len: {len(content)}, id: {news_id}, title: {source["title"]}')

    last_hit = hits[-1]
    sort_values = last_hit['sort']
    search_after = sort_values


if __name__ == "__main__":
  main()
