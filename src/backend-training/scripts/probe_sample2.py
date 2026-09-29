#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""probe_sample2.py —— 重选出口侧抽样策略（按「月份」而非「行数」）

上一版 cap 按「每商品最后 k 行」截取，但同一商品同一月有多条四键记录，
24 行往往只覆盖 1~2 个月 → 序列全挤最近、训练集只剩 8,980 条，划分失衡。

本脚本对比三类策略（均以 cutoff=202412 为固定切分点）：
  A. 全局时间窗：只保留最近 N 个月的全部行
  B. 每商品月份上限：每商品只保留最近 K 个「不同月份」
  C. 商品活跃度窗：保留「最近 N 个月内有交易」的全部商品 × 其完整历史
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


def report(tag, df, cutoff, base_codes):
    if len(df) == 0:
        print(f"  {tag:<34} 空")
        return
    sk = np.zeros(len(df), dtype='int64')
    mult = 1
    for c in CATS:
        f = pd.factorize(df[c])[0].astype('int64')
        sk += f * mult
        mult *= (int(f.max()) + 1)
    ym = df['数据年月'].values.astype('int64')
    order = np.lexsort((ym, sk))
    sk_s, ym_s = sk[order], ym[order]
    starts = np.flatnonzero(np.r_[True, sk_s[1:] != sk_s[:-1]])
    bounds = np.r_[starts, len(sk_s)]
    tr = te = 0
    for gi in range(len(starts)):
        aa, bb = int(bounds[gi]), int(bounds[gi + 1])
        if bb - aa < SEQ_LEN + 1:
            continue
        t = ym_s[aa + SEQ_LEN:bb]
        m = t > cutoff
        te += int(m.sum())
        tr += int((~m).sum())
    cov = df['商品编码'].nunique() / base_codes * 100
    tot = tr + te
    print(f"  {tag:<34} 行 {len(df):>9,}  商品覆盖 {cov:>5.1f}%"
          f"  序列 {tot:>8,}  训练 {tr:>8,} ({tr/max(tot,1)*100:>4.1f}%)"
          f"  测试 {te:>7,}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv_path', required=True)
    ap.add_argument('--label', default='')
    ap.add_argument('--cutoff', type=int, default=202412)
    a = ap.parse_args()

    full = load(a.csv_path)
    base_codes = full['商品编码'].nunique()
    ymax = int(full['数据年月'].max())
    print('=' * 122)
    print(f"  {a.label}  行 {len(full):,}  商品基数 {base_codes:,}"
          f"  年月 {int(full['数据年月'].min())}~{ymax}  切分点 {a.cutoff}")
    print('=' * 122)

    def ym_minus(n):
        y, m = ymax // 100, ymax % 100
        t = y * 12 + (m - 1) - (n - 1)
        return (t // 12) * 100 + (t % 12) + 1

    print('\n  A. 全局时间窗（只留最近 N 个月的全部行，商品覆盖随 N 上升）')
    for n in (12, 24, 36, 48, 60):
        c = ym_minus(n)
        report(f'A1 最近 {n} 月（>={c}）', full[full['数据年月'] >= c], a.cutoff, base_codes)

    print('\n  B. 每商品「不同月份」上限（保留全部商品编码）')
    sym = full.sort_values(['商品编码', '数据年月'], kind='mergesort')
    for k in (12, 24, 36, 48, 60):
        g = sym.groupby('商品编码', sort=False)['数据年月']
        rk = g.rank(method='dense')
        nm = g.transform('nunique')
        sub = sym[rk > nm - k].reset_index(drop=True)
        report(f'B1 每商品最近 {k} 个不同月份', sub, a.cutoff, base_codes)

    print('\n  C. 活跃商品 × 完整历史（覆盖 = 活跃商品占比）')
    for n in (12, 24, 36, 48, 60):
        c = ym_minus(n)
        active = set(full.loc[full['数据年月'] >= c, '商品编码'].unique())
        sub = full[full['商品编码'].isin(active)].reset_index(drop=True)
        report(f'C1 最近 {n} 月活跃商品的完整历史', sub, a.cutoff, base_codes)

    print('\n  D. 对照：全量')
    report('D1 全量', full, a.cutoff, base_codes)


if __name__ == '__main__':
    main()
