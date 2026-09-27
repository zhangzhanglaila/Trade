import threading
from datetime import datetime
from flask import Flask, request
import logging
from concurrent.futures import ThreadPoolExecutor
import requests


import config
import server_service
from server_service import Crawler

app = Flask(__name__)

executor = ThreadPoolExecutor()

crawler = Crawler()

def get_datasource(datasource_name, callback_instance, callback_id, last_time=None, metadata=None):
  try:
    result_map = crawler.get_news_urls(datasource_name=datasource_name, page=1, page_size=15)

    if metadata is not None:
      result_map["metadata"].update(metadata)

    response = { "code": 0, "msg": "ok", "data": result_map}
  except Exception as e:
    logging.error(e, stack_info=True)
    response = {"code": 1, "msg": f"error: {str(e)}"}

  callback_result('datasource', callback_instance, callback_id, response)


def callback_result(cb_type, callback_instance, callback_id, response):
  try:
    if isinstance(callback_instance, str) and callback_instance.startswith(('http://', 'https://')):
      callback_addr = callback_instance
    else:
      callback_addr = server_service.get_service_address(callback_instance, cb_type)
    cb_resp = requests.post(
      url=callback_addr,
      json=response,
      params={'callback_id': callback_id}
    )
    if cb_resp.status_code >= 400:
      raise Exception(cb_resp.status_code)
    json = cb_resp.json()
    if json.get('code') != 0:
      print('callback err resp:', cb_resp.json())
  except Exception as e:
    logging.error(e, stack_info=True)


@app.route('/api/datasource/async/<datasource_name>', methods=['POST'])
def get_news_list_async(datasource_name):
  try:
    if datasource_name not in ['kazinform', 'mofcom']:
      return {"code": 0, "msg": f'not supported datasource name: {datasource_name}'}

    req_json = request.get_json()
    last_time = req_json.get("last_time")
    if last_time is not None:
      last_time = datetime.fromtimestamp(last_time)

    callback_instance = req_json.get("callback_instance")
    if callback_instance is None:
      return {"code": 0, "msg": "callback_instance not provided"}

    callback_id = req_json.get("callback_id")
    if callback_id is None:
      return {"code": 0, "msg": "callback_id not provided"}

    threading.Thread(target=get_datasource, kwargs={
      'datasource_name': datasource_name,
      'callback_instance': callback_instance,
      'callback_id': callback_id,
      'last_time': last_time,
      'metadata': req_json,
    }).start()

    return {"code": 0, "msg": "ok", "data": callback_id}
  except Exception as e:
    logging.error(e, stack_info=True)
    return {"code": -1, "msg": "datasource err: " + str(e)}


def crawl(crawler_name, callback_instance, callback_id, urls, task_id):
  try:
    result_map = crawler.get_news_content(urls=urls, crawler=crawler_name)

    result = {
      "code": 0,
      "msg": "ok",
      "data": {
        'result': result_map,
        'metadata': {
          'task_id': task_id
        }
      }
    }

  except Exception as e:
    logging.error(e)
    result = { "code": 1, "msg": f"crawler error: {str(e)}", "data": {"task_id": task_id}}

  callback_result('crawler', callback_instance, callback_id, result)


@app.route('/api/crawl/async/<crawler_name>', methods=['POST'])
def crawl_page_async(crawler_name):
  try:
    if crawler_name not in ['kazinform', 'mofcom']:
      return {"code": 0, "msg": f'not supported crawler name: {crawler_name}'}

    req_json = request.get_json()
    urls = req_json.get("urls", [])

    params = req_json.get("params", {})

    task_id = params.get("task_id")
    if task_id is None:
      return {"code": 1, "msg": "task_id not provided"}

    callback_instance = params.get("callback_instance")
    if callback_instance is None:
      return {"code": 0, "msg": "callback_instance not provided"}

    callback_id = params.get("callback_id")
    if callback_id is None:
      return {"code": 1, "msg": "callback_id not provided"}

    threading.Thread(target=crawl, kwargs={
      'crawler_name': crawler_name,
      'callback_instance': callback_instance,
      'callback_id': callback_id,
      'urls': urls,
      'task_id': task_id,
    }).start()

    return {"code": 0, "msg": "ok", "data": callback_id}
  except Exception as e:
    return {"code": 1, "msg": f"error: {str(e)}"}


if __name__ == '__main__':
  app.run(debug=False, port=config.Config.ServerPort)
