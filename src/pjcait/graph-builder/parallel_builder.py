import hashlib
import os
import re

import pandas
import threading

lock = threading.Lock()

csv_reader = pandas.read_csv(
  r'/home/linghang/work/pick-kh-trade-keyword/2014after.csv',
  iterator=True
)


def read_next_chunk(size):
  chunk = None

  lock.acquire()
  try:
    chunk = csv_reader.get_chunk(size)
  except Exception as e:
    print(e)
  finally:
    lock.release()

  return chunk


import builder2
from neo4j import GraphDatabase

from _trade_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD

driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database='neo4j')

filedir = r'/home/linghang/work/data/kh-trade-graph'
if not os.path.isdir(filedir):
  os.makedirs(filedir)

lock2 = threading.Lock()

chunk_cnt = 0
total_cnt = 0


def mark_chunk():
  global chunk_cnt

  lock2.acquire()
  chunk_cnt += 1
  after = chunk_cnt
  lock2.release()
  return after


def mark_total():
  global total_cnt

  lock2.acquire()
  total_cnt += 1
  after = total_cnt
  lock2.release()

  return after

from tqdm import tqdm

def process(chunk_size=20, worker=0):
  dataframe = read_next_chunk(chunk_size)

  while dataframe is not None:
    dataframe = dataframe.fillna('')
    _chunk_cnt = mark_chunk()
    rows = dataframe.iterrows()
    for idx, row in tqdm(rows, desc=f'chunk {_chunk_cnt} worker {worker}'):
    # for idx, row in rows:
      _total_cnt = mark_total()
      news_id = row['id']
      if news_id == '':
        continue

      hashed_id = hashlib.md5(news_id.encode('utf-8')).hexdigest()
      prefix1, prefix2 = hashed_id[:2], hashed_id[2:4]
      dir_path = os.path.join(filedir, prefix1, prefix2)
      if not os.path.exists(dir_path):
        os.makedirs(dir_path)

      filename = os.path.join(dir_path, f'{news_id}.txt')
      if os.path.exists(filename):
        continue

      content = row['content']

      title = row['title']
      if title != '':
        content = f'《{title}》\n{content}'

      if len(content) > 1800:
        print(f'\ncontent len too long, skip, worker {worker}, len: {len(content)}, id: {news_id}, title: {row["title"]}')
        continue

      retry_times = 0
      llm_result = None
      while retry_times < 4:
        try:
          llm_result = builder2.call_llm(content)
          break
        except Exception as e:
          print('\nError:',e, f'id: {news_id}')
          retry_times+=1
      if retry_times >= 4 or llm_result is None:
        continue

      try:
        builder2.save_output(llm_result, filename)
        entities, relations = builder2.parse_llm_output(llm_result)
        builder2.insert_to_neo4j(driver, entities, relations)
      except Exception as e:
        print('\nError:',e, f'id: {news_id}')
        writeerr(e, news_id)
    pass
    dataframe = read_next_chunk(chunk_size)
  pass

def writeerr(e, fid):
  try:
    with open(f'/home/linghang/work/errors/{fid}.txt', 'w') as writers: # 打开文件
      writers.write(f'{e}') # 将内容写到文件中
    writers.close() # 关闭文件，此处可以理解为保存
  except Exception as e:
    print(e)

num_threads = 5
barrier = threading.Barrier(num_threads + 1)  # 主线程也参与等待

from concurrent.futures import ThreadPoolExecutor, as_completed
import time

if __name__ == '__main__':
  threads = []
  for i in range(0, 5):
    th = threading.Thread(target=process, kwargs={'worker': i})
    th.start()
    threads.append(th)

  pass
