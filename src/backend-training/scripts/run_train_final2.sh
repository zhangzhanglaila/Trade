#!/bin/bash
# run_train_final2.sh —— 生产重训（第二轮，口径最终定版）
#
# 相较第一轮（/tmp/fix3/run_train_final.sh）的三处改动：
#   1) --head residual  输出头改为回归增量，结构上不可能劣于 carry-forward；
#   2) 出口侧改 --sample_mode cap --cap_k 12：保留全部 6,974 个商品编码
#      （第一轮整组抽商品只留 446 个 = 6.4%，推理端 93.6% 的商品退化成兜底
#       embedding），且按「不同月份」而非「行数」截取；
#   3) --cutoff_ym 202412 固定日历切分点：cap 抽样把每商品的尾部都挤到最近，
#      分位切分点会被推后到测试集为空（实测 k=24 时测试序列 = 0）。
#
# 预算：out 两作业各 108,942 训练序列 / 32 ≈ 3,400 步每轮，8 轮封顶 + 早停 3。
set -u
PY=/tmp/fix3/price.py
IN_CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv"
OUT_CSV="/home/wust_1/Trade/data/05_运行_进出口CSV/出口/merged_output.csv"
OUT=/tmp/fix3/final2
mkdir -p $OUT
: > $OUT/progress.txt
echo "FINAL2_START $(date -u '+%F %T')" >> $OUT/progress.txt

# 轮数预算按「梯度步数」而非「epoch 数」对齐 A/B 的收敛经验：
#   A/B（进口 price，7,398 训练序列 / batch 32 = 231 步每轮）最佳验证损失
#   出现在第 13~14 轮 ≈ 3,200 步。据此：
#     进口全量 24,949 序列 → 780 步每轮 → 收敛约在第 4~6 轮，封顶 20 + 早停 5
#     出口 cap  323,812 序列 → 10,119 步每轮 → 一轮即超出收敛所需步数，
#                             故封顶 4 + 早停 1（只作安全网，正常 1~2 轮就停）
COMMON="--split_mode time --group_key full --ar_feature target_log --head residual \
--val_ratio 0.1 --batch_size 32 --seed 42 --threads 2"

# 进口：数据量小（40,524 行），全量训练，不抽样
for T in price quantity; do
  timeout 21600 python3 $PY --csv_path "$IN_CSV" --trade_type in --target $T \
      --epochs 20 --patience 5 $COMMON \
      --model_output_path $OUT/in_$T > $OUT/in_$T.log 2>&1 &
  echo "in_$T pid=$! $(date -u '+%T')" >> $OUT/progress.txt
done

# 出口：cap 抽样，每商品最近 24 个不同月份（→ 训练期 21 个月），固定切分点 202412
for T in price quantity; do
  timeout 21600 python3 $PY --csv_path "$OUT_CSV" --trade_type out --target $T \
      --sample_mode cap --cap_k 24 --cutoff_ym 202412 \
      --epochs 4 --patience 1 $COMMON \
      --model_output_path $OUT/out_$T > $OUT/out_$T.log 2>&1 &
  echo "out_$T pid=$! $(date -u '+%T')" >> $OUT/progress.txt
done

wait
echo "FINAL2_DONE $(date -u '+%F %T')" >> $OUT/progress.txt
for d in in_price in_quantity out_price out_quantity; do
  echo "=== $d ===" >> $OUT/progress.txt
  tr '\r' '\n' < $OUT/$d.log | grep -aE "抽样|四键分组数|切分点来源|切分协议|训练序列|输出头|主判据|最强朴素基线|LOG_SPACE" \
      >> $OUT/progress.txt 2>/dev/null
done
