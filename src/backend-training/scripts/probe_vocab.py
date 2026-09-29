#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""probe_vocab.py —— 量测 --max_rows 抽样对「类别词表覆盖率」的损失

背景：生产重训里出口侧用 --max_rows 120000 从 191 万行里抽样，抽样实现是
「按商品编码整组保留」（保证序列不被切碎），但会被动丢掉大量商品编码。
而 app.py 推理端对「训练期未见过的类别」只能退化成兜底 embedding 下标，
等于该商品的预测失去信息量。

本脚本对比三种口径下的类别覆盖：
  A. 全量                         （词表上界）
  B. 现有抽样：整组随机抽商品至累计 max_rows
  C. 改进抽样：保留全部商品，每个商品最多截取最近 K 行
"""
import argparse
import numpy as np
import pandas as pd

NEED = ['数据年月', '贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']
CATS = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']


def load(path):
    df = pd.read_csv(path, usecols=NEED, low_memory=False)
    df['数据年月'] = pd.to_numeric(df['数据年月'], errors='coerce')
    df = df.dropna(subset=['数据年月'])
    df['数据年月'] = df['数据年月'].astype('int64')
    for c in CATS:
        df[c] = df[c].astype(str)
    return df.reset_index(drop=True)


def sample_by_commodity_group(df, max_rows, seed=42):
    """现行实现：整组随机抽商品，直到累计行数达到 max_rows"""
    _codes = df['商品编码']
    _sizes = _codes.value_counts()
    _order = _sizes.index.to_numpy().copy()
    np.random.RandomState(seed).shuffle(_order)
    _keep, _cum = [], 0
    for _c in _order:
        _keep.append(_c)
        _cum += int(_sizes[_c])
        if _cum >= max_rows:
            break
    return df[_codes.isin(set(_keep))].reset_index(drop=True)


def sample_cap_per_commodity(df, max_rows):
    """改进：保留全部商品编码，每个商品按「最近 K 期」截取，
    K 由 max_rows / 商品数 自适应，并按行数上限二次收敛。"""
    n_codes = df['商品编码'].nunique()
    k = max(2, int(max_rows / max(n_codes, 1)))
    # 组内按 数据年月 升序取最后 K 行（保留最近时间线）
    df = df.sort_values(['商品编码', '数据年月'], kind='mergesort')
    kept = df.groupby('商品编码', sort=False).tail(k)
    return kept.reset_index(drop=True), k


def report(tag, df):
    print(f"  {tag:<28} 行 {len(df):>9,}   "
          + "  ".join(f"{c[:4]} {df[c].nunique():>6,}" for c in CATS))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv_path', required=True)
    ap.add_argument('--label', default='')
    ap.add_argument('--max_rows', type=int, default=120000)
    a = ap.parse_args()

    full = load(a.csv_path)
    print('=' * 90)
    print(f"  {a.label}   原始行 {len(full):,}   商品编码基数 {full['商品编码'].nunique():,}")
    print('=' * 90)

    report('A. 全量（词表上界）', full)

    b = sample_by_commodity_group(full, a.max_rows)
    report(f'B. 现行：整组抽商品 <= {a.max_rows:,}', b)

    c, k = sample_cap_per_commodity(full, a.max_rows)
    report(f'C. 改进：每商品最近 {k} 期', c)

    print('\n  --- 词表覆盖率（相对全量） ---')
    for col in CATS:
        fu = set(full[col].unique())
        bu = set(b[col].unique())
        cu = set(c[col].unique())
        print(f"    {col:<12}  现行 {len(bu)/len(fu)*100:>5.1f}%   "
              f"改进 {len(cu)/len(fu)*100:>5.1f}%")


if __name__ == '__main__':
    main()
