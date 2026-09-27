import pandas as pd
from elasticsearch import Elasticsearch, helpers

BATCH_SIZE = 10
CSV_FILE = "data/kh-related-keyword/keyword-picked-merged.csv"
INDEX_NAME = "archived_news"

es = Elasticsearch("http://localhost:9200")

if es.indices.exists(index=INDEX_NAME):
    es.indices.delete(index=INDEX_NAME)

mapping = {
    "mappings": {
        "properties": {
            "title": {"type": "text"},
            "content": {"type": "text"},
            "date": {"type": "date", "format": "yyyy-MM-dd"}
        }
    }
}
es.indices.create(index=INDEX_NAME, body=mapping)

from tqdm import tqdm

import warnings
warnings.simplefilter(action='ignore', category=FutureWarning)

total_inserted = 0
for chunk in tqdm(pd.read_csv(CSV_FILE, chunksize=BATCH_SIZE)):
    chunk.fillna("", inplace=True)
    actions = []
    for _, row in chunk.iterrows():
        actions.append({
            "_index": INDEX_NAME,
            "_id": row["id"],
            "_source": {
                "title": row["title"],
                "content": row["content"],
                "date": row["date"],
            }
        })
    helpers.bulk(es, actions)
    total_inserted += len(actions)

print(f"{total_inserted} records inserted.")
