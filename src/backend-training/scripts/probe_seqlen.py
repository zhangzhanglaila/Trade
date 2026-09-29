#!/usr/bin/env python3
"""probe_seqlen.py —— 四键分组下的「组长度分布」与各 seq_length 的可用序列数

动机：评测报告 §6.6.5 的待办 1 写着「seq_length 从 2 提到 12」。但序列是在
四键分组（贸易伙伴×商品×贸易方式×注册地）内部按时间滑动窗口构造的，组长度
一旦小于 seq_length+1 就整组作废。进口 40,524 行摊到 6,410 个四键组，平均每组
只有 6.3 行 —— 那么 seq_length=12 很可能一组都凑不出来。本脚本把这件事量化。

用法：
    python3 probe_seqlen.py --csv <merged_output.csv> --label 进口
    python3 probe_seqlen.py --csv <merged_output.csv> --label 出口 --cap_k 24
"""
import argparse
import sys

import numpy as np
import pandas as pd

KEYS = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv', required=True)
    ap.add_argument('--label', default='')
    ap.add_argument('--cap_k', type=int, default=0,
                    help='>0 时按训练脚本同口径先做 cap 抽样（每商品最近 k 个不同月份）')
    a = ap.parse_args()

    df = pd.read_csv(a.csv, dtype=str, low_memory=False)
    print(f"[{a.label}] 原始行数 {len(df):,}")
    print(f"  列名前 15: {list(df.columns)[:15]}")

    need = ['数据年月'] + KEYS
    missing = [c for c in need if c not in df.columns]
    if missing:
        print(f"  ! 缺列: {missing}")
        sys.exit(1)

    df = df[need].copy()
    df['ym'] = pd.to_numeric(df['数据年月'], errors='coerce')
    n_bad = int(df['ym'].isna().sum())
    df = df.dropna(subset=['ym']).copy()
    df['ym'] = df['ym'].astype(int)
    if n_bad:
        print(f"  丢弃无法解析的数据年月: {n_bad:,} 行")

    # cap 抽样：与训练脚本 --sample_mode cap 同口径（按 ym 密集排名取最近 k 个不同月份）
    if a.cap_k:
        g = df.groupby('商品编码', sort=False)['ym']
        rk = g.rank(method='dense', ascending=False)
        before = len(df)
        df = df[rk <= a.cap_k].copy()
        print(f"  cap_k={a.cap_k}: {before:,} -> {len(df):,} 行")

    sk = df[KEYS[0]].astype(str)
    for c in KEYS[1:]:
        sk = sk.str.cat(df[c].astype(str), sep='|')
    df['_k'] = sk

    lens = df.groupby('_k', sort=False).size()
    print(f"  四键组数 {len(lens):,}")
    print(f"  组长度分布: min={lens.min()}  p25={lens.quantile(.25):.0f}  "
          f"median={lens.median():.0f}  mean={lens.mean():.2f}  "
          f"p90={lens.quantile(.9):.0f}  p99={lens.quantile(.99):.0f}  max={lens.max()}")
    n1 = int((lens == 1).sum())
    print(f"  长度=1 的组（永远产不出序列）: {n1:,} ({n1 / len(lens) * 100:.1f}%)")

    print(f"  {'seq_length':>10}  {'可用序列':>12}  {'可用组':>10}  {'组覆盖率':>8}")
    for sl in (2, 3, 4, 6, 8, 12, 24):
        n = int(np.maximum(lens - sl, 0).sum())
        ok = int((lens >= sl + 1).sum())
        print(f"  {sl:>10}  {n:>12,}  {ok:>10,}  {ok / len(lens) * 100:>7.1f}%")


if __name__ == '__main__':
    main()
