#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""probe_sweep.py —— 扫「每商品保留最近 k 期」的 k，给出规模与词表覆盖

目标：在有限计算预算内让「商品编码词表覆盖 = 100%」。
现行 max_rows 整组抽样只留 446/6974 = 6.4% 的商品，推理端 93.6% 的商品
会退化成兜底 embedding 下标，等于该商品的预测失去信息量。

本脚本对出口 CSV 扫描 k，输出：
  行数 / 商品覆盖 / 四键分组数 / seq_length=2 可产出序列数 / 估计训练-测试划分
"""
import argparse
import numpy as np
import pandas as pd

NEED = ['数据年月', '贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']
CATS = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']
SEQ_LEN = 2


def load(path):
    df = pd.read_csv(path, usecols=NEED, low_memory=False)
    df['数据年月'] = pd.to_numeric(df['数据年月'], errors='coerce')
    df = df.dropna(subset=['数据年月'])
    df['数据年月'] = df['数据年月'].astype('int64')
    for c in CATS:
        df[c] = df[c].astype(str)
    return df.reset_index(drop=True)


def series_codes(df):
    codes = np.zeros(len(df), dtype='int64')
    mult = 1
    for c in CATS:
        f = pd.factorize(df[c])[0].astype('int64')
        codes += f * mult
        mult *= (int(f.max()) + 1)
    return codes


def summarize(tag, df):
    if len(df) == 0:
        print(f"  {tag:<22} 空")
        return
    sk = series_codes(df)
    d = df.assign(_sk=sk)
    sizes = d.groupby('_sk').size()
    n_seq = int((sizes - SEQ_LEN).clip(lower=0).sum())
    ym = d['数据年月'].values
    cutoff = int(np.quantile(ym, 0.8))
    # 序列级：目标月 = 序列内第 seq_length 行起的时间
    order = np.lexsort((ym, sk))
    sk_s, ym_s = sk[order], ym[order]
    starts = np.flatnonzero(np.r_[True, sk_s[1:] != sk_s[:-1]])
    bounds = np.r_[starts, len(sk_s)]
    tgt_ym = []
    for gi in range(len(starts)):
        aa, bb = int(bounds[gi]), int(bounds[gi + 1])
        if bb - aa < SEQ_LEN + 1:
            continue
        tgt_ym.append(ym_s[aa + SEQ_LEN:bb])
    tgt = np.concatenate(tgt_ym)
    n_test = int((tgt > cutoff).sum())
    print(f"  {tag:<22} 行 {len(df):>9,}  商品 {df['商品编码'].nunique():>6,}"
          f"  分组 {len(sizes):>8,}  序列 {n_seq:>9,}"
          f"  训练 {len(tgt)-n_test:>9,} / 测试 {n_test:>8,}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv_path', required=True)
    ap.add_argument('--label', default='')
    a = ap.parse_args()

    full = load(a.csv_path)
    n_codes = full['商品编码'].nunique()
    print('=' * 108)
    print(f"  {a.label}  原始行 {len(full):,}  商品编码基数 {n_codes:,}")
    print('=' * 108)
    summarize('全量（上界）', full)
    sym = full.sort_values(['商品编码', '数据年月'], kind='mergesort')
    for k in (8, 12, 17, 24, 36, 48, 72, 120):
        sub = sym.groupby('商品编码', sort=False).tail(k).reset_index(drop=True)
        summarize(f'每商品最近 {k} 期', sub)


if __name__ == '__main__':
    main()
