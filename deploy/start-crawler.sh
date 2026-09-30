#!/usr/bin/env bash
# ================================================================
# 启动新闻爬虫服务（Flask，默认 :56887）
#
# 前置依赖（服务器本地，不在 git 内）：
#   1) Python venv：/data/crawler-venv，含
#      flask requests curl_cffi beautifulsoup4 DrissionPage tqdm python-consul
#      （curl_cffi 用于绕过 mofcom 的 TLS 指纹反爬；DrissionPage 驱动浏览器）
#   2) Google Chrome（/opt/google/chrome），DrissionPage 依赖
#   3) Xvfb：cn.inform.kz 有 Cloudflare 人机验证，headless 会被拦，
#      必须用非 headless + Xvfb 虚拟显示
#   4) Elasticsearch / Consul 均**不需要**（相关硬依赖已从爬虫代码移除）
#
# 用法：
#   bash deploy/start-crawler.sh
#   CRAWLER_VENV=/data/crawler-venv CRAWLER_PORT=56887 bash deploy/start-crawler.sh
# ================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRAWLER_DIR="$ROOT/src/pjcait/crawler"
VENV="${CRAWLER_VENV:-/data/crawler-venv}"
PORT="${CRAWLER_PORT:-56887}"
DISPLAY_NUM="${CRAWLER_DISPLAY:-:99}"
LOG="$ROOT/logs/crawler.log"
XVFB_LOG="$ROOT/logs/xvfb.log"

if [ ! -f "$CRAWLER_DIR/server.py" ]; then
  echo "错误：找不到 $CRAWLER_DIR/server.py"
  exit 1
fi
if [ ! -x "$VENV/bin/python" ]; then
  echo "错误：venv 不存在：$VENV（请先创建并安装依赖，见文件头说明）"
  exit 1
fi

mkdir -p "$ROOT/logs"

port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

if port_busy "$PORT"; then
  echo "[跳过] 端口 $PORT 已被占用（爬虫可能已在运行）"
  exit 0
fi

# Xvfb 虚拟显示（未运行则启动），供非 headless Chrome 使用
if ! pgrep -f "Xvfb $DISPLAY_NUM" >/dev/null 2>&1; then
  nohup Xvfb "$DISPLAY_NUM" -screen 0 1600x1000x24 > "$XVFB_LOG" 2>&1 &
  sleep 2
  echo "Xvfb 启动于 $DISPLAY_NUM"
fi

cd "$CRAWLER_DIR" || exit 1
DISPLAY="$DISPLAY_NUM" setsid nohup "$VENV/bin/python" server.py > "$LOG" 2>&1 &
echo "$!" > "$ROOT/logs/crawler.pid"
echo "爬虫启动中 PID=$!，日志 $LOG"

for i in $(seq 1 30); do
  if port_busy "$PORT"; then
    echo "爬虫已就绪：http://127.0.0.1:$PORT"
    exit 0
  fi
  sleep 2
done

echo "警告：爬虫 60 秒内未就绪，请查看 $LOG"
tail -n 20 "$LOG"
exit 1
