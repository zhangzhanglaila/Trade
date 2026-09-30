import json
import time
import urllib.parse

# 用 curl_cffi 替代 requests：mofcom 的 WAF 按 TLS 指纹(JA3)拦截 Python
# urllib3 的握手（表现为 Connection reset by peer），而 curl_cffi 能模拟
# 真实 Chrome 的 TLS 指纹，实测可正常拿到 200 与内容。
from curl_cffi import requests
from bs4 import BeautifulSoup

from common.crawler_base import Crawler
from DrissionPage.items import NoneElement


INDEX_BASE_URL = "https://kz.mofcom.gov.cn/api-gateway/jpaas-publish-server/front/page/build/unit"
COMMON_PARAMS = {
  "webId": "bd20d5f33330492baaa05d6f7ef3546b",
  "pageId": "1DlUrqRc6Jej9ESbnpHqW",
  "parseType": "bulidstatic",
  "pageType": "column",
  "tagId": "信息列表",
  "tplSetId": "VLanA0rZaClIOpoC9xBrJ",
}

HEADERS = {
  "accept": "*/*",
  "accept-language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
  "cache-control": "no-cache",
  "pragma": "no-cache",
  "sec-ch-ua": "\"Chromium\";v=\"136\", \"Microsoft Edge\";v=\"136\", \"Not.A/Brand\";v=\"99\"",
  "sec-ch-ua-mobile": "?0",
  "sec-ch-ua-platform": "\"Windows\"",
  "sec-fetch-dest": "empty",
  "sec-fetch-mode": "cors",
  "sec-fetch-site": "same-origin",
  "x-requested-with": "XMLHttpRequest",
  "Referer": "https://kz.mofcom.gov.cn/jmxw/index.html",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0",
}

NEWS_PAGE_BASE_URL='https://kz.mofcom.gov.cn'

class MofcomCrawler(Crawler):
  def __init__(self, driver_path_or_address=None, tab_id=None):
    super().__init__(driver_path_or_address, tab_id)

  def run(self):
    pass

  def get_urls(self, page=1, page_size=15):
    """获取单页数据"""
    param_json = json.dumps({
      "pageNo": page,
      "pageSize": str(page_size)
    })

    params = COMMON_PARAMS.copy()
    params["paramJson"] = param_json

    response = requests.get(INDEX_BASE_URL, params=params, headers=HEADERS, impersonate="chrome")

    if response.status_code != 200:
      raise Exception(f"请求失败，第 {page} 页，状态码：{response.status_code}")

    data = response.json().get("data")
    if not data:
      return []

    html = data.get('html')
    if type(html) is not str:
      return []

    results = []
    soup = BeautifulSoup(html, "html.parser")
    news_list = soup.select('.txtList_01 > li')
    for news in news_list:
      a_ele = news.select('a')[0]
      if not a_ele:
        continue

      title = a_ele.text.strip()
      link = a_ele.get('href')
      if not link:
        continue
      link = urllib.parse.urljoin(NEWS_PAGE_BASE_URL, link)

      date_ele = news.select('span')
      date = date_ele[0].text.strip() if len(date_ele) else ''

      news_item = {
        'title': title,
        'date': date,
        'link': link,
        'cover': '',
      }
      results.append(news_item)

    return results

  def get_contents(self, urls: list[str], delay=None):
    tab = self.chromium.new_tab()
    tab.set.NoneElement_value('')

    results = []
    for url in urls:
      tab.get(url, retry=4, timeout=90)
      title = tab.ele('css:#artitle', timeout=0).text
      date = tab.ele('css:#barrierfree_container > section > section > div.wms-con > div.mCol > section.article-tool.f-cb > div.at-left > p:nth-child(2)', timeout=0).text
      tags = tab.ele('css:#barrierfree_container > section > section > div.wms-con > div.mCol > section.article-tool.f-cb > div.at-left > p:nth-child(1)', timeout=0).raw_text.strip()

      content_ele = tab.ele('css:#zoom', timeout=0)
      if isinstance(content_ele, NoneElement):
        continue

      content_html = content_ele.html
      content_text = content_ele.text

      results.append({
        'title': title,
        'date': date,
        'desc': '',
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

    tab.get(url, retry=4, timeout=90)
    title = tab.ele('css:#artitle', timeout=0).text
    date = tab.ele('css:#barrierfree_container > section > section > div.wms-con > div.mCol > section.article-tool.f-cb > div.at-left > p:nth-child(2)', timeout=0).text
    tags = tab.ele('css:#barrierfree_container > section > section > div.wms-con > div.mCol > section.article-tool.f-cb > div.at-left > p:nth-child(1)', timeout=0).raw_text.strip()

    content_ele = tab.ele('css:#zoom', timeout=0)
    if isinstance(content_ele, NoneElement):
      return {}

    content_html = content_ele.html
    content_text = content_ele.text

    tab.close()

    return {
      'title': title,
      'date': date,
      'desc': '',
      'tags': tags,
      'url': url,
      'content_html': content_html,
      'content_text': content_text,
      'pics': '',
    }


if __name__ == '__main__':
  crawler = MofcomCrawler()
  # urls = crawler.get_urls()
  # result = crawler.get_content([url['link'] for url in urls])
  result = crawler.get_content(['https://kz.mofcom.gov.cn/jmxw/art/2021/art_3d8eb663ae0d4f1eb6576200fa74577e.html'])
  print(result)
