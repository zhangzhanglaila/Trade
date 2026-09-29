#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""probe_split.py —— 为 cap 抽样选「固定日历切分点」

发现：cap 抽样把每个商品截到「最近 k 期」后，全体商品的尾部都挤在最近若干月，
于是 `np.quantile(年月, 0.8)` 这个分位切分点被推到很晚，测试目标月几乎全在
切分点之前 —— k≤24 时测试序列数直接为 0，模型无法评测。

因此 cap 抽样必须配「固定日历切分点」（--cutoff_ym）。本脚本给出：
  · 原始数据的逐月行数分布（决定切分点的合理区间）
  · 若干 k × 若干候选 cutoff 下的 训练/测试序列数 与「测试目标月跨度」
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


def codes_of(df):
    codes = np.zeros(len(df), dtype='int64')
    mult = 1
    for c in CATS:
        f = pd.factorize(df[c])[0].astype('int64')
        codes += f * mult
        mult *= (int(f.max()) + 1)
    return codes


def split_stats(df, cutoff):
    sk = codes_of(df)
    ym = df['数据年月'].values.astype('int64')
    order = np.lexsort((ym, sk))
    sk_s, ym_s = sk[order], ym[order]
    starts = np.flatnonzero(np.r_[True, sk_s[1:] != sk_s[:-1]])
    bounds = np.r_[starts, len(sk_s)]
    tr = te = 0
    te_months = []
    for gi in range(len(starts)):
        aa, bb = int(bounds[gi]), int(bounds[gi + 1])
        if bb - aa < SEQ_LEN + 1:
            continue
        t = ym_s[aa + SEQ_LEN:bb]
        m = t > cutoff
        te += int(m.sum())
        tr += int((~m).sum())
        if m.any():
            te_months.append(t[m])
    if te_months:
        allte = np.concatenate(te_months)
        span = f"{int(allte.min())}~{int(allte.max())}"
    else:
        span = '-'
    return tr, te, span


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv_path', required=True)
    ap.add_argument('--label', default='')
    ap.add_argument('--ks', default='17,24,36,48,72')
    ap.add_argument('--cutoffs', default='202312,202406,202412,202501,202506')
    a = ap.parse_args()

    df = load(a.csv_path)
    print('=' * 108)
    print(f"  {a.label}   行 {len(df):,}   年月 {df['数据年月'].min()} ~ {df['数据年月'].max()}")
    print('=' * 108)

    print('  --- 逐年行数（观察数据重心，判断切分点可行性） ---')
    yr = df['数据年月'] // 100
    vc = yr.value_counts().sort_index()
    line = '   '
    for y, n in vc.items():
        line += f" {y}:{n//1000}k"
    print(line)

    ks = [int(x) for x in a.ks.split(',')]
    cuts = [int(x) for x in a.cutoffs.split(',')]
    sym = df.sort_values(['商品编码', '数据年月'], kind='mergesort')

    print('\n  --- k × cutoff 下的 序列划分 ---')
    head = f"  {'k':>5} | " + " | ".join(f"{c}(测试/跨度)" for c in cuts)
    print(head)
    print('  ' + '-' * (len(head) + 6))
    for k in ks:
        sub = sym.groupby('商品编码', sort=False).tail(k).reset_index(drop=True)
        cells = []
        for c in cuts:
            tr, te, span = split_stats(sub, c)
            cells.append(f"{te:>7,}/{span}")
        print(f"  {k:>5} | " + " | ".join(cells))
        print(f"        | 行 {len(sub):,}  商品 {sub['商品编码'].nunique():,}")

    # 对照：全量数据的测试规模
    print('\n  --- 全量对照（切分点由 80% 分位决定） ---')
    c80 = int(np.quantile(df['数据年月'].values, 0.8))
    tr, te, span = split_stats(df, c80)
    print(f"    分位切分点 {c80}  →  训练 {tr:,} / 测试 {te:,}  跨 {span}")


if __name__ == '__main__':
    main()
