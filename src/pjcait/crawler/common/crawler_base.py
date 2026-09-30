from DrissionPage import ChromiumPage, ChromiumOptions
from DrissionPage.items import NoneElement

class Crawler:
  def __init__(self, driver_path_or_address=None, tab_id=None):
    # 站点(cn.inform.kz)有 Cloudflare 人机验证，headless 会被识别并卡在
    # "Just a moment..." 挑战页，必须用非 headless；服务器无 GUI，靠
    # `xvfb-run -a` 提供虚拟显示。--no-sandbox 等为受限环境所需。
    if driver_path_or_address is None:
      co = ChromiumOptions()
      co.set_argument('--no-sandbox')
      co.set_argument('--disable-dev-shm-usage')
      co.set_argument('--disable-gpu')
      co.set_argument('--disable-blink-features=AutomationControlled')
      self.chromium = ChromiumPage(addr_or_opts=co, tab_id=tab_id)
    else:
      self.chromium = ChromiumPage(addr_or_opts=driver_path_or_address, tab_id=tab_id)

  def run(self):
    pass
