#!/bin/bash
# rerun_outprice.sh —— 补跑「出口-单价」× {time, commodity}
#
# 起因：这两个组合最早由上一轮会话在 09:5x-10:00 跑出（/tmp/smoke/run_time.log、
#       run_commodity.log），但当时脚本还没有本轮的「退化诊断」输出，指标口径与
#       其余 6 个组合不一致；且 run_time.log 那次以 EXIT_A=1 崩溃。
# 目的：用当前代码重跑，使 4 目标 × 2 协议 = 8 个组合口径完全一致、产物齐全。
# 输出：/tmp/smoke/rerun_outprice/<tag>.log

set -u

SCRIPT=/home/wust_1/Trade/src/backend-training/python-scripts/Trade_Transformer_LSTM_price.py
OUTDIR=/tmp/smoke/rerun_outprice
mkdir -p "$OUTDIR"
cd /home/wust_1/Trade || exit 1

echo "OUTPRICE_RERUN_START $(date -u '+%F %T')"

while read -r TT TG SM; do
  [ -z "${TT:-}" ] && continue

  case "$TT" in
    out) CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/出口/merged_output.csv" ;;
    in)  CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv" ;;
    *)   echo "未知 trade_type: $TT"; continue ;;
  esac

  TAG="${TT}_${TG}_${SM}"
  LOG="$OUTDIR/${TAG}.log"
  MODEL="/tmp/smoke/p_${TAG}"

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
out price time
out price commodity
JOBS

echo "OUTPRICE_RERUN_ALL_DONE $(date -u '+%F %T')"
