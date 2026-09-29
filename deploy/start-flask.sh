#!/usr/bin/env bash
# ================================================================
# 单独启动 Flask 预测服务（默认端口 5000）
#
# 为什么需要这个脚本：
#   训练后端只在「执行一次训练任务」的流程里才会拉起 Flask
#   （见 TrainingTaskService#startFlaskService），因此服务器每次重启后
#   /predict_* 会静默不可用，而 /health 又查不到这个情况。
#   这里提供独立、幂等的启动入口。
#
# 用法：
#   bash deploy/start-flask.sh
#   FLASK_PORT=5001 bash deploy/start-flask.sh
# ================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

PORT="${FLASK_PORT:-5000}"
HOST="${FLASK_HOST:-0.0.0.0}"
SCRIPTS="$ROOT/src/backend-training/python-scripts"
PY="${PYTHON_EXE:-/usr/bin/python3}"
LOG="$ROOT/logs/flask.log"

if [ ! -f "$SCRIPTS/app.py" ]; then
  echo "错误：找不到 $SCRIPTS/app.py"
  exit 1
fi

port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

if port_busy "$PORT"; then
  echo "[跳过] 端口 $PORT 已被占用（Flask 可能已在运行）"
  exit 0
fi

mkdir -p "$ROOT/logs"

# app.py 依赖相对路径加载模型（model/trade_Transformer/...），必须切到脚本目录再启动；
# 数据库等凭据由 app.py 自己向上查找 config/.env 载入。
cd "$SCRIPTS" || exit 1
nohup "$PY" app.py --host "$HOST" --port "$PORT" >> "$LOG" 2>&1 &
PID=$!
echo "$PID" > "$ROOT/logs/flask.pid"
echo "Flask 启动中 PID=$PID，日志 $LOG"

for i in $(seq 1 45); do
  if curl -sf "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
    echo "Flask 已就绪：http://127.0.0.1:$PORT"
    exit 0
  fi
  sleep 2
done

echo "警告：Flask 在 90 秒内未就绪，请查看 $LOG"
tail -n 20 "$LOG"
exit 1
