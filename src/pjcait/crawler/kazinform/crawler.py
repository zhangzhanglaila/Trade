import time
import urllib.parse

from bs4 import BeautifulSoup

from common.crawler_base import Crawler
from DrissionPage.items import NoneElement

url_format = 'https://cn.inform.kz/tag/TITR/?page={page}'

news_page_url_base = 'https://cn.inform.kz/'


class KazinformCrawler(Crawler):
  """哈通社中文版(cn.inform.kz)抓取。

  2026-09 更新：站点改版，列表卡片由 .tagpageCard 换成
  .news-category__item > a.news-card（标题 .card-title、时间 time.meta-date）；
  正文容器由 .article__body-text 换成 .article__content，时间改为 <time>。
  另注：该站有 Cloudflare 人机验证，必须在非 headless（xvfb 虚拟显示）下访问，
  headless 会停在 "Just a moment..." 挑战页。
  """

  def __init__(self, driver_path_or_address=None, tab_id=None):
    super().__init__(driver_path_or_address, tab_id)

  def run(self):
    pass

  def get_urls(self, page):
    url = url_format.format(page=page)
    tab = self.chromium.new_tab()
    tab.get(url, retry=4, timeout=60)
    tab.set.NoneElement_value('')
    results = []
    cards = tab.eles('css:.news-category__item', timeout=0)
    for card in cards:
      a_ele = card.ele('css:a.news-card', timeout=0)
      if isinstance(a_ele, NoneElement):
        continue
      link = a_ele.attr('href')
      if not link:
        continue
      if not link.startswith('http'):
        link = urllib.parse.urljoin(news_page_url_base, link)

      title_ele = card.ele('css:.card-title', timeout=0)
      title = title_ele.text if not isinstance(title_ele, NoneElement) else ''
      date_ele = card.ele('css:time.meta-date', timeout=0)
      date = date_ele.text if not isinstance(date_ele, NoneElement) else ''
      img_ele = card.ele('css:img', timeout=0)
      cover = ''
      if not isinstance(img_ele, NoneElement):
        cover = img_ele.attr('src') or ''

      results.append({
        'title': title or '',
        'date': date or '',
        'cover': cover or '',
        'link': link,
      })

    if self.chromium.tabs_count >= 1:
      tab.close()

    return results

  def _parse_article(self, tab, url):
    tab.get(url, retry=4, timeout=90)
    title_ele = tab.ele('css:.article__title', timeout=0)
    title = title_ele.text if not isinstance(title_ele, NoneElement) else ''
    date_ele = tab.ele('css:time', timeout=0)
    date = date_ele.text if not isinstance(date_ele, NoneElement) else ''
    tags_ele = tab.ele('css:.article__tags', timeout=0)
    tags = tags_ele.text if not isinstance(tags_ele, NoneElement) else ''
    content_ele = tab.ele('css:.article__content', timeout=0)
    if isinstance(content_ele, NoneElement):
      return {'title': title, 'date': date, 'desc': '', 'tags': tags, 'url': url,
              'content_html': '', 'content_text': '', 'pics': ''}
    return {
      'title': title,
      'date': date,
      'desc': '',
      'tags': tags,
      'url': url,
      'content_html': content_ele.html,
      'content_text': content_ele.text,
      'pics': '',
    }

  def get_contents(self, urls, delay=None):
    tab = self.chromium.new_tab()
    tab.set.NoneElement_value('')
    results = []
    for url in urls:
      results.append(self._parse_article(tab, url))
      if delay is not None:
        time.sleep(delay)
    tab.close()
    return results

  def get_content(self, url):
    tab = self.chromium.new_tab()
    tab.set.NoneElement_value('')
    try:
      return self._parse_article(tab, url)
    finally:
      tab.close()


if __name__ == '__main__':
  crawler = KazinformCrawler()
  urls = crawler.get_urls(page=1)
  print("列表条数:", len(urls))
  for u in urls[:3]:
    print(u)
  if urls:
    print(crawler.get_content(urls[0]['link']))
