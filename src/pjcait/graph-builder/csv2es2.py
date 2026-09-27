import pandas as pd
from elasticsearch import Elasticsearch, helpers
from datetime import datetime

BATCH_SIZE = 20
CSV_FILE = "data/kh-related-keyword/keyword-picked-merged.csv"
INDEX_NAME = "archived_news_ful"

es = Elasticsearch("http://10.221.0.8:9200")

# 创建映射
if es.indices.exists(index=INDEX_NAME):
  es.indices.delete(index=INDEX_NAME)

mapping = {
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "title": {"type": "text"},
      "content": {"type": "text"},
      "date": {"type": "keyword"}
    }
  }
}
es.indices.create(index=INDEX_NAME, body=mapping)

total_inserted = 0

for chunk in pd.read_csv(CSV_FILE, chunksize=BATCH_SIZE):
  actions = []

  for _, row in chunk.iterrows():
    try:
      # 清洗并格式化日期
      clean_date = pd.to_datetime(row["date"], errors="coerce")
      # 检查必需字段
      if pd.isna(row["id"]) or pd.isna(row["content"]):
        raise ValueError("缺失 id 或 content")

      row.fillna("", inplace=True)
      action = {
        "_index": INDEX_NAME,
        "_id": str(row["id"]),
        "_source": {
          "id": str(row["id"]),
          "title": row.get("title", ""),
          "content": row["content"],
          "date": row.get("date", "").replace("nan", ""),
        }
      }

      actions.append(action)
    except Exception as e:
      print(f"跳过错误行: {e}")

  # 执行 bulk 写入
  if actions:
    try:
      helpers.bulk(es, actions)
      total_inserted += len(actions)
      print(f"已导入 {total_inserted} 条数据...")
    except helpers.BulkIndexError as e:
      print(f"Bulk 写入失败，跳过 {len(e.errors)} 条记录：")
      for error in e.errors:
        print(error["index"]["error"])
      break

print(f"\n✅ 全部导入完成，成功导入 {total_inserted} 条记录。")
