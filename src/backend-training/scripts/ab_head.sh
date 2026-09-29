#!/bin/bash
# ab_head.sh —— A/B：输出头 head=direct vs head=residual
#
# 两条臂除 --head / --model_output_path 外参数完全一致，保证可比。
# 数据：进口全量 CSV，commodity 抽样取 8,000 行（整组抽商品 → 测试集非空，
# 与上一轮 ar_feature 的 A/B 同口径，direct 臂应复现 log 空间 R²≈0.90）。
set -u
PY=/tmp/fix3/price.py
CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv"
OUT=/tmp/fix3/abhead
mkdir -p $OUT
: > $OUT/progress.txt
echo "ABHEAD_START $(date -u '+%F %T')" >> $OUT/progress.txt

COMMON="--csv_path $CSV --trade_type in --target price --split_mode time \
--group_key full --ar_feature target_log --max_rows 8000 --sample_mode commodity \
--epochs 20 --patience 4 --threads 1 --batch_size 32 --seed 42"

for h in direct residual; do
  timeout 3000 python3 $PY $COMMON --head $h \
      --model_output_path $OUT/$h > $OUT/$h.log 2>&1 &
  echo "  $h pid=$!" >> $OUT/progress.txt
done

wait
echo "ABHEAD_DONE $(date -u '+%F %T')" >> $OUT/progress.txt
for h in direct residual; do
  echo "=== $h ===" >> $OUT/progress.txt
  tr '\r' '\n' < $OUT/$h.log | grep -aE "主判据|最强朴素基线|结论|模型  *R2|LOG_SPACE|输出头" \
      >> $OUT/progress.txt
done
