import time

from bs4 import BeautifulSoup

from common.crawler_base import Crawler
from DrissionPage.items import NoneElement

url_format = 'https://cn.inform.kz/tag/TITR/?page={page}'

item = {
  "title": "",
  "date": "",
  "link": "",
  "cover": "",
}

news_page_url_base = 'https://cn.inform.kz/'

from store.es import EsStorage


class KazinformCrawler(Crawler):
  def __init__(self, driver_path_or_address=None, tab_id=None):
    super().__init__(driver_path_or_address, tab_id)
    self.storage = EsStorage()

  def run(self):
    pass

  def get_urls(self, page):
    url = url_format.format(page=page)
    tab = self.chromium.new_tab()
    tab.get(url,retry=4,timeout=60)
    tab.set.NoneElement_value('')
    results = []
    cards = tab.eles('css:.tagpageCard', timeout=0)
    for card in cards:
      link = card.ele('css:a', timeout=0).attr('href')
      if link is None:
        continue

      title = card.ele('css:.tagpageCard_title', timeout=0).text
      date = card.ele('css:.tagpageCard_time', timeout=0).text
      cover_ele = card.ele('css:picture > source', timeout=0)
      cover = cover_ele.attr('srcset')

      news_item = {
        'title': title if title else '',
        'date': date if date else '',
        'cover': cover if cover else '',
        'link': link,
      }

      results.append(news_item)
    pass

    if self.chromium.tabs_count >= 1:
      tab.close()

    return results

  def get_contents(self, urls:list[str], delay=None):
    tab = self.chromium.new_tab()
    tab.set.NoneElement_value('')

    results = []

    for url in urls:
      tab.get(url, retry=4,timeout=60)
      title = tab.ele('css:.article__title').text
      date = tab.ele('css:.article__time').text
      desc = tab.ele('css:.article__description').text
      tags = tab.ele('css:.article__tags').text

      content_ele = tab.ele('css:.article__body-text')
      content_html = content_ele.html
      content_text = content_ele.text

      results.append({
        'title': title,
        'date': date,
        'desc': desc,
        'tags': tags,
        'url': url,
        'content_html': content_html,
        'content_text': content_text,
        'pics': '',
      })
      if delay is not None:
        time.sleep(delay)
    pass

    tab.close()

    return results

  def get_content(self, url):
    tab = self.chromium.new_tab()
    tab.set.NoneElement_value('')

    tab.get(url, retry=4,timeout=60)
    title = tab.ele('css:.article__title').text
    date = tab.ele('css:.article__time').text
    desc = tab.ele('css:.article__description').text
    tags = tab.ele('css:.article__tags').text

    content_ele = tab.ele('css:.article__body-text')
    content_html = content_ele.html
    content_text = content_ele.text

    tab.close()

    return {
      'title': title,
      'date': date,
      'desc': desc,
      'tags': tags,
      'url': url,
      'content_html': content_html,
      'content_text': content_text,
      'pics': '',
    }

if __name__ == '__main__':
  crawler = KazinformCrawler()
  result = crawler.get_content(['https://cn.inform.kz/news/zhongdizaidongguoguanxingzhongriqizhongyaodi-48e91b/'])
  print(result)
