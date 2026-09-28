#!/bin/bash
# rerun_failed.sh —— 补跑冒烟矩阵中因 pred_quantity NameError 而失败的 3 个 run
#
# 起因：price 脚本以 --target quantity 调用时，末尾打印样本处引用了不存在的
#       pred_quantity 变量，抛 NameError 并以 EXIT=1 退出（commit cbf7b90 已修复）。
#       受影响的 run：out_quantity_time、in_quantity_time、out_quantity_commodity。
# 说明：这些 run 的评估指标其实已经完整打印，缺的只是 3 张结果图；此处整轮重跑，
#       以保证 8 个组合（4 目标 × 2 协议）口径完全一致、产物齐全。
# 输出：/tmp/smoke/rerun/<tag>.log

set -u

SCRIPT=/home/wust_1/Trade/src/backend-training/python-scripts/Trade_Transformer_LSTM_price.py
OUTDIR=/tmp/smoke/rerun
mkdir -p "$OUTDIR"
cd /home/wust_1/Trade || exit 1

echo "RERUN_START $(date -u '+%F %T')"

while read -r TT TG SM; do
  [ -z "${TT:-}" ] && continue

  case "$TT" in
    out) CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/出口/merged_output.csv" ;;
    in)  CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv" ;;
    *)   echo "未知 trade_type: $TT"; continue ;;
  esac

  TAG="${TT}_${TG}_${SM}"
  LOG="$OUTDIR/${TAG}.log"
  MODEL="/tmp/smoke/r_${TAG}"

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
in quantity time
out quantity commodity
JOBS

echo "RERUN_ALL_DONE $(date -u '+%F %T')"
