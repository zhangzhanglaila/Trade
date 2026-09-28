#!/usr/bin/env bash
# A/B 对照：验证「特征集与目标不匹配」是否是「只有进口-数量能学会」的原因
#
# 两臂除 --ar_feature 外完全一致（同数据、同切分、同种子、同轮数、同线程）。
#   arm none       现状：continuous_cols=['year','month','数量','人民币']
#   arm target_log 加入目标自身的历史值（窗口为 t-2 / t-1，不涉及未来）
#
# 用法: bash ab_ar.sh <none|target_log>
set -u
arm=$1
PY=/tmp/ab/price_ar.py
CSV=/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv

echo "===== arm $arm START $(date -u '+%F %T UTC') ====="
rm -rf /tmp/ab/$arm
timeout 3000 python3 $PY \
  --csv_path $CSV --trade_type in --target price --split_mode time \
  --max_rows 12000 --epochs 30 --threads 4 --seed 42 \
  --ar_feature $arm --model_output_path /tmp/ab/$arm \
  > /tmp/ab/$arm.log 2>&1
echo "===== arm $arm END $(date -u '+%F %T UTC') EXIT=$? ====="
