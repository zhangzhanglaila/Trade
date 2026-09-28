#!/usr/bin/env bash
# 多种子重测：检验「只有进口-数量能学会」这一结论在可复现条件下是否成立
#
# 背景：修复 --seed 之前，同一配置两次运行 raw R2 相差 (0.0025 vs 0.0719)，
# 与组合之间的差异同量级。因此必须用多颗种子 + 固定种子重测，才能判断
# 「某组合能学会」是真实效应还是单次抽样。
#
# 设计：进口数据的 单价 / 数量 两个目标 x time 协议 x 5 颗种子 = 10 次运行。
# 其余参数与 §6.4 冒烟保持一致（max_rows=12000 / 30 轮），口径可比。
# 并发 3 路 x 3 线程，占 9 线程（机器 10 核）。
set -u
export PY=/tmp/fix2/price.py
export CSV=/home/wust_1/Trade/data/05_运行_进出口CSV/进口/merged_output.csv
export OUT=/tmp/ms
mkdir -p $OUT

run_one() {
  local tgt=$1 seed=$2
  local tag="in_${tgt}_time_s${seed}"
  rm -rf "$OUT/model_$tag"
  timeout 3000 python3 "$PY" \
    --csv_path "$CSV" --trade_type in --target "$tgt" --split_mode time \
    --max_rows 12000 --epochs 30 --threads 3 --seed "$seed" \
    --model_output_path "$OUT/model_$tag" > "$OUT/$tag.log" 2>&1
  echo "$tag EXIT=$? $(date -u '+%T')" >> "$OUT/progress.txt"
}
export -f run_one

echo "MULTISEED_START $(date -u '+%F %T UTC')" > "$OUT/progress.txt"

{
  for tgt in price quantity; do
    for seed in 42 43 44 45 46; do
      echo "$tgt $seed"
    done
  done
} | xargs -P 3 -n 2 bash -c 'run_one "$0" "$1"'

echo "MULTISEED_ALL_DONE $(date -u '+%F %T UTC')" >> "$OUT/progress.txt"
