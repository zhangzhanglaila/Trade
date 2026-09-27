from DrissionPage import ChromiumPage
from DrissionPage.items import NoneElement

class Crawler:
  def __init__(self, driver_path_or_address=None, tab_id=None):
    self.chromium = ChromiumPage(addr_or_opts=driver_path_or_address, tab_id=tab_id)

  def run(self):
    pass

