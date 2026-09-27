import urllib.parse

import consul
import config

consulClient = consul.Consul(
  host=config.Config.Consul.Address, port=config.Config.Consul.Port
)


def get_service_address(service_id, cb_type):
  # 连接到 Consul 服务

  # 获取所有服务实例
  services = consulClient.agent.services()

  # 根据服务 ID 查找目标实例
  service = services.get(service_id)
  if not service:
    raise ValueError(f"Service ID '{service_id}' not found in Consul")

  # 提取地址和端口
  address = service['Address']
  port = service['Port']
  metadata = service['Meta']
  scheme = metadata.get('scheme', 'http')
  base_path = metadata.get('base-path')

  if base_path:
    base_url = urllib.parse.urljoin(f"{scheme}://{address}:{port}", base_path)
    if cb_type == 'crawler':
      return urllib.parse.urljoin(base_url + '/', 'crawler/custom/callback')
    else:
      return urllib.parse.urljoin(base_url + '/', 'datasource/custom/callback')
  else:
    if cb_type == 'crawler':
      return f"{scheme}://{address}:{port}/crawler/custom/callback"
    else:
      return f"{scheme}://{address}:{port}/datasource/custom/callback"

