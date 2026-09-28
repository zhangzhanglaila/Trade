#!/bin/bash
# run_matrix.sh —— 补齐冒烟矩阵
#
# 上轮只跑了「出口-单价」。本脚本补齐其余 3 个目标组合 × {time, commodity} 共 6 次，
# 与上轮保持完全一致的配置：出口/进口真实数据抽 12000 行、30 轮、4 线程。
# 顺序刻意把最关键的 time 协议排在最前，中途中断也能拿到核心证据。
#
# 用法: bash run_matrix.sh
# 输出: /tmp/smoke/matrix/<tag>.log  以及 matrix.out 总控日志

set -u

SCRIPT=/home/wust_1/Trade/src/backend-training/python-scripts/Trade_Transformer_LSTM_price.py
OUTDIR=/tmp/smoke/matrix
mkdir -p "$OUTDIR"
cd /home/wust_1/Trade || exit 1

echo "MATRIX_START $(date -u '+%F %T')"

while read -r TT TG SM; do
  [ -z "${TT:-}" ] && continue

  case "$TT" in
    out) CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/出口/merged_output.csv" ;;
    in)  CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv" ;;
    *)   echo "未知 trade_type: $TT"; continue ;;
  esac

  TAG="${TT}_${TG}_${SM}"
  LOG="$OUTDIR/${TAG}.log"
  MODEL="/tmp/smoke/m_${TAG}"

  rm -rf "$MODEL"
  echo "=== ${TAG} START $(date -u '+%F %T') ==="

  timeout 3000 python3 "$SCRIPT" \
    --csv_path "$CSV" \
    --model_output_path "$MODEL" \
    --trade_type "$TT" \
    --target "$TG" \
    --split_mode "$SM" \
    --max_rows 12000 \
    --epochs 30 \
    --threads 4 \
    > "$LOG" 2>&1

  rc=$?
  echo "=== ${TAG} END $(date -u '+%F %T') EXIT=$rc ==="
done <<'JOBS'
out quantity time
in price time
in quantity time
out quantity commodity
in price commodity
in quantity commodity
JOBS

echo "MATRIX_ALL_DONE $(date -u '+%F %T')"
