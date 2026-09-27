"""pjcait 图谱构建脚本的统一凭据加载器。

所有明文口令 / API Key 都集中在项目根的 ``config/.env``（该文件不入库），
本模块负责向上查找并加载它，然后以常量形式提供给各个脚本。

用法::

    from _trade_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))

也可以直接用 ``env(...)`` 取任意变量::

    from _trade_config import env
    api_key = env('DEEPSEEK_API_KEY')

详见 ``config/README.md``。
"""

from __future__ import annotations

import os
from pathlib import Path

__all__ = [
    'env', 'ENV_FILE',
    'NEO4J_URI', 'NEO4J_USERNAME', 'NEO4J_PASSWORD', 'NEO4J_DATABASE',
    'NEO4J_URI_REMOTE', 'NEO4J_DATABASE_REMOTE',
    'DEEPSEEK_BASE_URL', 'DEEPSEEK_API_KEY',
    'SILICONFLOW_BASE_URL', 'SILICONFLOW_API_KEY',
]


def _find_env_file() -> Path | None:
    """从本文件所在目录逐级向上查找 config/.env。"""
    for parent in Path(__file__).resolve().parent.parents:
        candidate = parent / 'config' / '.env'
        if candidate.is_file():
            return candidate
    return None


def _load(path: Path) -> None:
    """把 key=value 逐行读入 os.environ（不覆盖已存在的环境变量）。"""
    for raw in path.read_text(encoding='utf-8').splitlines():
        line = raw.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, _, value = line.partition('=')
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        if key and key not in os.environ:
            os.environ[key] = value


ENV_FILE = _find_env_file()
if ENV_FILE is not None:
    _load(ENV_FILE)


def env(name: str, default: str = '') -> str:
    """读取配置项；未设置时返回 default。"""
    value = os.environ.get(name)
    return default if value is None or value == '' else value


# ---- Neo4j（图谱构建链路）----
NEO4J_URI = env('NEO4J_URI', 'bolt://localhost:7687')
NEO4J_USERNAME = env('NEO4J_USERNAME', 'neo4j')
NEO4J_PASSWORD = env('NEO4J_PASSWORD')
NEO4J_DATABASE = env('NEO4J_DATABASE', 'kh-trade-news-graph')
NEO4J_URI_REMOTE = env('NEO4J_URI_REMOTE')
NEO4J_DATABASE_REMOTE = env('NEO4J_DATABASE_REMOTE', 'cait4')

# ---- LLM API ----
DEEPSEEK_BASE_URL = env('DEEPSEEK_BASE_URL', 'https://api.deepseek.com')
DEEPSEEK_API_KEY = env('DEEPSEEK_API_KEY')
SILICONFLOW_BASE_URL = env('SILICONFLOW_BASE_URL', 'https://api.siliconflow.cn/v1')
SILICONFLOW_API_KEY = env('SILICONFLOW_API_KEY')


def neo4j_driver(database: str | None = None, uri: str | None = None):
    """按 config/.env 建立 Neo4j 连接（延迟导入 neo4j，避免无依赖时报错）。"""
    from neo4j import GraphDatabase

    if not NEO4J_PASSWORD:
        raise RuntimeError(
            '未配置 NEO4J_PASSWORD。请执行 cp config/.env.example config/.env 后填写，'
            '或设置同名环境变量。'
        )
    return GraphDatabase.driver(
        uri or NEO4J_URI,
        auth=(NEO4J_USERNAME, NEO4J_PASSWORD),
        database=database or NEO4J_DATABASE,
    )
