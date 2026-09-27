#!/usr/bin/env bash
# ================================================================
# 中哈贸易系统 · 停止全部服务
# ================================================================
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

kill_pidfile() {
  local f="$1"
  [ -f "$f" ] || return 0
  local pid; pid="$(cat "$f")"
  if kill -0 "$pid" 2>/dev/null; then
    echo "停止 $(basename "$f" .pid) (PID $pid)"
    kill "$pid" 2>/dev/null
    sleep 2
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
  fi
  rm -f "$f"
}

for f in logs/*.pid; do kill_pidfile "$f"; done

# Flask 由训练后端拉起，进程名不固定，按端口兜底清理
if command -v fuser >/dev/null 2>&1; then
  fuser -k 5000/tcp 2>/dev/null && echo "已清理 Flask (5000)"
fi

echo "完成。"
