import hashlib
import logging
import random
import time

from tqdm import tqdm

import kazinform
import mofcom


class Crawler:
  kazinform_crawler = kazinform.KazinformCrawler()
  mofcom_crawler = mofcom.MofcomCrawler()

  def get_news_urls(self, datasource_name, page=1, page_size=10):
    result_data = {
      "result": [],
      "metadata": {}
    }

    if datasource_name == "kazinform":
      result_data["result"] = self.kazinform_crawler.get_urls(page=page)
    elif datasource_name == "mofcom":
      result_data["result"] = self.mofcom_crawler.get_urls(page=page, page_size=page_size)

    return result_data

  def get_news_content(self, crawler: str, urls:list[str], delay=1):
    result = {}

    try:
      for url in tqdm(urls, desc='Crawling Peoples Daily'):
        if crawler == "kazinform":
          crawler_result = self.kazinform_crawler.get_content(url)
        elif crawler == "mofcom":
          crawler_result = self.mofcom_crawler.get_content(url)
        else:
          break

        urlHash = hashlib.sha256(url.encode(encoding='UTF-8')).hexdigest()

        content = crawler_result['content_text'].replace('\n', '').strip()
        title = crawler_result['title']
        date = crawler_result['date']
        tags = crawler_result['tags']
        crawler_result['content'] = f'标题：{title}\n新闻时间：{date}\n{tags}\n正文：{content}'
        result[urlHash] = crawler_result

        time.sleep(delay + random.uniform(0, 1))
    except Exception as e:
      logging.error(f"error: {str(e)}")

    return result