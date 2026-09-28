#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
diag_learnability.py — 目标可学性归因诊断

回答《模型评测报告》§6.4.5 提出的问题：
    八个「目标 x 协议」组合里，为什么只有「进口-数量 + time」真正学会（R2=+0.3733）？

做法：完全不走训练，直接在数据上度量四类与「可学性」直接相关的量。

  A. 规模与「序列」合法性
     训练脚本只按「商品编码」单键分组构序列（见 Trade_Transformer_LSTM_*.py
     第 278 行 groupby('_orig_comm')）。若同一商品编码在同一 数据年月 有多行
     （不同贸易伙伴/贸易方式/注册地），那么所谓"时间序列"里存在并列时间点，
     相邻样本可能属于同一个月 —— 序列语义不成立。此项量化该问题。

  B. 目标分布形态
     长尾程度、log 空间的偏度峰度、以及平方和集中度（最大 1%/10% 样本占
     总平方和的比重）。集中度越高，R2 越会被极少数样本支配。

  C. 可分性（可学性的上界）
     组间/组内方差分解、lag-1 自相关、以及三条朴素基线在 log 空间的 R2。
     若「上一期值」在 log 空间已有正 R2，说明目标有持续性、可学；
     若它深度为负，说明目标接近不可从自身历史预测。

  D. 输入与目标的确定性关系
     单价 ≡ 人民币 / 数量 是恒等式，而 数量 与 人民币 均为模型输入特征。
     此项度量该恒等式的残差，即"目标固有的噪声地板"。

用法:
    python3 diag_learnability.py --csv_path <csv> --label 出口 [--target both]
"""

import argparse
import json
import sys

import numpy as np
import pandas as pd

NEED_COLS = ['数据年月', '贸易伙伴编码', '商品编码', '贸易方式编码',
             '注册地编码', '数量', '人民币', '单价']
GROUP_KEY = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']


def _corr(a, b):
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    if len(a) < 3 or a.std() == 0 or b.std() == 0:
        return float('nan')
    return float(np.corrcoef(a, b)[0, 1])


def _r2(y_true, y_pred):
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    ss_res = float(((y_true - y_pred) ** 2).sum())
    ss_tot = float(((y_true - y_true.mean()) ** 2).sum())
    if ss_tot == 0:
        return float('nan')
    return 1.0 - ss_res / ss_tot


def _ss_share(v, frac):
    """绝对值最大的 frac 比例样本，其平方和占总平方和的比重"""
    a = np.sort(np.abs(np.asarray(v, dtype=float)))[::-1]
    k = max(1, int(round(len(a) * frac)))
    tot = float((a ** 2).sum())
    if tot == 0:
        return float('nan')
    return float((a[:k] ** 2).sum() / tot)


def load(path):
    df = pd.read_csv(path, usecols=NEED_COLS, low_memory=False)
    df['数据年月'] = pd.to_numeric(df['数据年月'], errors='coerce')
    for c in ['数量', '人民币', '单价']:
        df[c] = pd.to_numeric(df[c].replace({'-': np.nan, '': np.nan}),
                              errors='coerce')
    df = df.dropna(subset=['数据年月', '数量', '人民币', '单价'])
    df['数据年月'] = df['数据年月'].astype('int64')
    # 只保留三个量都为正的行（与训练脚本 clip(lower=0) + log1p 的前提一致）
    df = df[(df['数量'] > 0) & (df['人民币'] > 0) & (df['单价'] > 0)]
    for c in ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']:
        df[c] = df[c].astype(str)
    # 关键性能处理：object dtype 的字符串数组在 numpy 里做 sort / unique 会退化成
    # Python 级逐元素比较。出口数据 191 万行时，仅 np.unique 就要数分钟。统一转成
    # 整数编码，分组与排序语义完全等价（只要求"相等即同组"）。
    for c in ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']:
        df[c] = pd.factorize(df[c])[0].astype('int32')
    return df.reset_index(drop=True)


def scale_block(df):
    """A. 规模与序列合法性"""
    s = {}
    s['rows'] = int(len(df))
    s['n_commodity'] = int(df['商品编码'].nunique())
    s['n_series_key'] = int(df.groupby(GROUP_KEY).ngroups)
    s['ym_min'] = int(df['数据年月'].min())
    s['ym_max'] = int(df['数据年月'].max())
    s['n_months'] = int(df['数据年月'].nunique())

    # 序列合法性：同一商品编码 + 同一 数据年月 是否有重复行
    dup_mask = df.duplicated(subset=['商品编码', '数据年月'], keep=False)
    s['dup_ym_row_ratio'] = float(dup_mask.mean())
    s['dup_ym_commodities'] = int(df.loc[dup_mask, '商品编码'].nunique())

    rows_per_comm = df.groupby('商品编码').size()
    s['rows_per_commodity_median'] = float(rows_per_comm.median())
    s['rows_per_commodity_mean'] = float(rows_per_comm.mean())

    # 逐步去重后的"真实时间步"数（同月多行取均值）
    steps = df.groupby(['商品编码', '数据年月']).size().reset_index() \
              .groupby('商品编码').size()
    s['steps_per_commodity_median'] = float(steps.median())
    s['steps_per_commodity_mean'] = float(steps.mean())
    s['ratio_steps_over_rows'] = float(steps.sum() / len(df))
    # seq_length=2 需要每商品 >=3 个时间步才能产出至少 1 条序列
    s['commodity_share_steps_ge3'] = float((steps >= 3).mean())
    return s


def target_block(df, target):
    """B/C/D. 目标分布、可分性、确定性关系"""
    s = {}
    y_raw = df[target].values.astype(float)
    y_log = np.log1p(y_raw)
    s['target'] = target
    s['n_target'] = int(len(y_raw))

    # ---- B. 分布形态 ----
    s['raw_mean'] = float(np.mean(y_raw))
    s['raw_std'] = float(np.std(y_raw))
    s['raw_cv'] = float(s['raw_std'] / s['raw_mean']) if s['raw_mean'] else float('nan')
    s['raw_p1'], s['raw_p25'], s['raw_p50'], s['raw_p75'], s['raw_p99'] = \
        [float(v) for v in np.percentile(y_raw, [1, 25, 50, 75, 99])]
    s['raw_max'] = float(np.max(y_raw))
    s['raw_orders_of_magnitude'] = float(np.log10(max(s['raw_max'], 1.0)) -
                                         np.log10(max(s['raw_p1'], 1e-9)))
    s['log_skew'] = float(pd.Series(y_log).skew())
    s['log_kurt'] = float(pd.Series(y_log).kurt())
    s['log_std'] = float(np.std(y_log))

    # 平方和集中度（用去均值后的值，占 ss_tot 的比例）
    s['raw_ss_share_top1'] = _ss_share(y_raw - y_raw.mean(), 0.01)
    s['raw_ss_share_top10'] = _ss_share(y_raw - y_raw.mean(), 0.10)
    s['log_ss_share_top1'] = _ss_share(y_log - y_log.mean(), 0.01)
    s['log_ss_share_top10'] = _ss_share(y_log - y_log.mean(), 0.10)

    # ---- C. 可分性 ----
    # 组间/组内方差分解（log 空间，按商品编码分组）
    gm = float(y_log.mean())
    g = pd.Series(y_log).groupby(df['商品编码'].values)
    nb = g.mean()
    ss_between = float((((nb - gm) ** 2) * g.size()).sum())
    ss_total = float(((y_log - gm) ** 2).sum())
    s['var_ratio_between_commodity'] = ss_between / ss_total if ss_total else float('nan')
    s['var_ratio_within_commodity'] = 1.0 - s['var_ratio_between_commodity']

    # 商品内相对于该商品自身均值的离散度（去均值后的 std），衡量"可预测的波动"
    resid = y_log - nb.reindex(df['商品编码'].values).values
    s['within_commodity_resid_std'] = float(np.std(resid))

    # ---- lag-1 自相关：两种口径 ----
    # (i) 严格按训练脚本所见的行序：按 (商品编码, 数据年月) 排序后的相邻行
    order = df.sort_values(['商品编码', '数据年月']).index
    d2 = df.loc[order]
    y2 = np.log1p(d2[target].values.astype(float))
    same = d2['商品编码'].values[1:] == d2['商品编码'].values[:-1]
    s['lag1_autocorr_model_order'] = _corr(y2[1:][same], y2[:-1][same])
    s['carryforward_r2_model_order'] = _r2(y2[1:][same], y2[:-1][same])

    # (ii) 洁净口径：先把 (商品编码, 数据年月) 去重为月均值，再算相邻月
    dm = df.groupby(['商品编码', '数据年月'], as_index=False)[target].mean() \
           .sort_values(['商品编码', '数据年月'])
    y3 = np.log1p(dm[target].values.astype(float))
    same3 = dm['商品编码'].values[1:] == dm['商品编码'].values[:-1]
    s['lag1_autocorr_clean'] = _corr(y3[1:][same3], y3[:-1][same3])
    s['carryforward_r2_clean'] = _r2(y3[1:][same3], y3[:-1][same3])

    # lag-12（同比季节性，月频）
    ym = dm['数据年月'].values
    lag12 = np.zeros(len(dm), dtype=bool)
    lag12[12:] = (ym[12:] == ym[:-12] + 100) & \
                 (dm['商品编码'].values[12:] == dm['商品编码'].values[:-12])
    _i12 = np.where(lag12)[0]
    s['lag12_autocorr_clean'] = _corr(y3[_i12], y3[_i12 - 12])
    s['n_pairs_lag12'] = int(lag12.sum())

    # ---- D. 确定性关系：单价 ≡ 人民币 / 数量 ----
    calc = df['人民币'].values / df['数量'].values
    relerr = np.abs(df['单价'].values - calc) / df['单价'].values
    s['price_identity_rel_err_p50'] = float(np.percentile(relerr, 50))
    s['price_identity_rel_err_p90'] = float(np.percentile(relerr, 90))
    s['price_identity_rel_err_p99'] = float(np.percentile(relerr, 99))
    s['price_identity_rel_err_max'] = float(np.max(relerr))
    s['price_identity_exact_ratio'] = float(np.mean(np.abs(df['单价'].values - calc) < 0.005))
    s['corr_log_qty_log_val'] = _corr(np.log1p(df['数量'].values),
                                      np.log1p(df['人民币'].values))
    s['corr_log_target_log_val'] = _corr(y_log, np.log1p(df['人民币'].values))
    s['corr_log_target_log_qty'] = _corr(y_log, np.log1p(df['数量'].values))

    return s


def build_sequences(df, target, seq_length=2):
    """完全复现训练脚本的序列构造：按「商品编码」单键分组、按 (year,month) 排序"""
    d = df.sort_values(['商品编码', '数据年月'])
    ym = d['数据年月'].values.astype('int64')
    comms = d['商品编码'].values
    y_log = np.log1p(d[target].values.astype(float))

    _, starts = np.unique(comms, return_index=True)
    starts = np.sort(starts)
    bounds = np.append(starts, len(comms))

    seq_comm, seq_ym, seq_y, seq_prev = [], [], [], []
    for gi in range(len(starts)):
        a, b = int(bounds[gi]), int(bounds[gi + 1])
        if b - a < seq_length + 1:
            continue
        tgt = np.arange(a + seq_length, b)
        seq_comm.append(comms[tgt])
        seq_ym.append(ym[tgt])
        seq_y.append(y_log[tgt])
        seq_prev.append(y_log[tgt - 1])
    return (np.concatenate(seq_comm), np.concatenate(seq_ym),
            np.concatenate(seq_y), np.concatenate(seq_prev), ym)


def split_and_baselines(df, target, split_mode, test_ratio=0.2, seed=42):
    """复现训练脚本的切分与三条朴素基线，分别在 log 空间与原始空间算 R2。

    训练脚本第 558-559 行把预测与真值 expm1 还原后算 R2（原始空间）；
    而其损失函数是对「标准化后的 log 目标」做 MSE。这项对照就是为了量化
    这个空间错配到底吃掉了多少信号。
    """
    seq_comm, seq_ym, seq_y, seq_prev, ym_all = build_sequences(df, target)

    if split_mode == 'time':
        cutoff = int(np.quantile(ym_all, 1 - test_ratio))
        test_mask = seq_ym > cutoff
    else:
        codes = np.array(sorted(np.unique(seq_comm)))
        np.random.RandomState(seed).shuffle(codes)
        n_test = max(1, int(round(len(codes) * test_ratio)))
        # 注意：np.isin 的第二个参数不能传 set（会被当作 0 维 object 数组），
        # 必须传 list / ndarray。
        test_mask = np.isin(seq_comm, list(codes[:n_test]))

    tr = np.where(~test_mask)[0]
    te = np.where(test_mask)[0]
    if len(tr) == 0 or len(te) == 0:
        return None

    y_te = seq_y[te]
    y_tr = seq_y[tr]

    preds = {
        '上一期值': seq_prev[te],
        '全局均值': np.full(len(te), float(y_tr.mean())),
    }
    m_tr = seq_ym[tr] % 100
    m_te = seq_ym[te] % 100
    mm = {int(m): float(y_tr[m_tr == m].mean()) for m in np.unique(m_tr)}
    # 注意：dict.get(key, default) 的 default 会被【逐次求值】。这里必须先把
    # 全局均值提到循环外，否则 y_tr.mean() 会在列表推导里被调用 n_test 次，
    # 每次扫一遍训练集 —— 复杂度 O(n_test x n_train)。出口全量下实测会挂住
    # 数分钟到数小时。训练脚本第 338/337 行有完全相同的写法，已一并修复。
    _gm = float(y_tr.mean())
    preds['季节均值'] = np.array([mm.get(int(m), _gm) for m in m_te])

    train_comms = set(seq_comm[tr].tolist())
    cold = float(np.mean([c not in train_comms for c in seq_comm[te]]))

    rows = []
    for name, p in preds.items():
        r2_log = _r2(y_te, p)                       # log 空间
        r2_raw = _r2(np.expm1(y_te), np.expm1(p))   # 原始空间（训练脚本的口径）
        rows.append((name, r2_log, r2_raw))

    return {
        'split_mode': split_mode,
        'n_train': int(len(tr)),
        'n_test': int(len(te)),
        'cold_ratio': cold,
        'rows': rows,
        'y_te_log_std': float(np.std(y_te)),
    }


def fmt(s, label, title):
    L = []
    A = L.append
    A('=' * 78)
    A(f'  {label} / {title}')
    A('=' * 78)
    A('[A] 规模与序列合法性')
    A(f"  行数                       {s['rows']:,}")
    A(f"  商品编码数                 {s['n_commodity']:,}")
    A(f"  全维组合数(伙伴x商品x方式x地) {s['n_series_key']:,}")
    A(f"  时间跨度                   {s['ym_min']} ~ {s['ym_max']}  共 {s['n_months']} 个月")
    A(f"  同一(商品,年月)重复行占比   {s['dup_ym_row_ratio']*100:.2f}%  "
      f"(涉及 {s['dup_ym_commodities']:,} 个商品)")
    A(f"  每商品行数 中位/均值        {s['rows_per_commodity_median']:.1f} / "
      f"{s['rows_per_commodity_mean']:.1f}")
    A(f"  每商品唯一时间步 中位/均值  {s['steps_per_commodity_median']:.1f} / "
      f"{s['steps_per_commodity_mean']:.1f}")
    A(f"  唯一时间步数 / 行数         {s['ratio_steps_over_rows']*100:.2f}%")
    A(f"  时间步>=3 的商品占比        {s['commodity_share_steps_ge3']*100:.1f}%"
      f"   (seq_length=2 需 >=3 才能出 1 条序列)")
    A('')
    A('[B] 目标分布形态  (目标 = %s)' % s['target'])
    A(f"  原始: 均值 {s['raw_mean']:,.2f}  标准差 {s['raw_std']:,.2f}  变异系数 {s['raw_cv']:.3f}")
    A(f"  原始分位: p1={s['raw_p1']:,.2f}  p25={s['raw_p25']:,.2f}  p50={s['raw_p50']:,.2f}"
      f"  p75={s['raw_p75']:,.2f}  p99={s['raw_p99']:,.2f}  max={s['raw_max']:,.2f}")
    A(f"  跨度(数量级)               {s['raw_orders_of_magnitude']:.2f} 个数量级")
    A(f"  log 空间: 偏度 {s['log_skew']:.3f}  峰度 {s['log_kurt']:.3f}  标准差 {s['log_std']:.3f}")
    A(f"  平方和集中度(去均值后):")
    A(f"    原始空间  top1%={s['raw_ss_share_top1']*100:.1f}%   top10%={s['raw_ss_share_top10']*100:.1f}%")
    A(f"    log  空间  top1%={s['log_ss_share_top1']*100:.1f}%   top10%={s['log_ss_share_top10']*100:.1f}%")
    A('')
    A('[C] 可分性  (log 空间)')
    A(f"  组间方差占比(按商品)        {s['var_ratio_between_commodity']*100:.2f}%")
    A(f"  组内方差占比                {s['var_ratio_within_commodity']*100:.2f}%"
      f"   组内残差 std={s['within_commodity_resid_std']:.3f}")
    A(f"  lag-1 自相关  模型见到的行序 {s['lag1_autocorr_model_order']:.4f}"
      f"   -> 上一期值 R2 = {s['carryforward_r2_model_order']:+.4f}")
    A(f"  lag-1 自相关  月度去重口径   {s['lag1_autocorr_clean']:.4f}"
      f"   -> 上一期值 R2 = {s['carryforward_r2_clean']:+.4f}")
    A(f"  lag-12 自相关 (同比)         {s['lag12_autocorr_clean']:.4f}"
      f"   (样本对 {s['n_pairs_lag12']:,})")
    A('')
    A('[D] 确定性关系  单价 ≡ 人民币/数量')
    A(f"  相对误差 p50={s['price_identity_rel_err_p50']:.6f}  p90={s['price_identity_rel_err_p90']:.6f}"
      f"  p99={s['price_identity_rel_err_p99']:.6f}  max={s['price_identity_rel_err_max']:.4f}")
    A(f"  恒等式严格成立(<0.005)占比  {s['price_identity_exact_ratio']*100:.2f}%")
    A(f"  corr(log数量, log人民币)     {s['corr_log_qty_log_val']:.4f}")
    A(f"  corr(log目标, log人民币)     {s['corr_log_target_log_val']:.4f}")
    A(f"  corr(log目标, log数量)       {s['corr_log_target_log_qty']:.4f}")
    A('')
    return '\n'.join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv_path', required=True)
    ap.add_argument('--label', required=True)
    ap.add_argument('--target', default='both', choices=['price', 'quantity', 'both'])
    ap.add_argument('--json_out', default='')
    args = ap.parse_args()

    df = load(args.csv_path)
    print(f'载入 {args.label}: {len(df):,} 行（已过滤三个量均为正）', file=sys.stderr)

    targets = []
    if args.target in ('price', 'both'):
        targets.append(('price', '单价'))
    if args.target in ('quantity', 'both'):
        targets.append(('quantity', '数量'))

    out = {}
    for key, cn in targets:
        d = dict(scale_block(df))
        d.update(target_block(df, cn))
        print(fmt(d, args.label, f'目标 = {cn}'))

        print('=' * 78)
        print(f'  {args.label} / 目标 = {cn}   [E] 评测口径对照：同一基线在两个空间下的 R2')
        print('=' * 78)
        e_out = {}
        for mode in ('time', 'commodity'):
            r = split_and_baselines(df, cn, mode)
            if r is None:
                print(f'  {mode}: 切分后有空集，跳过')
                continue
            e_out[mode] = {k: v for k, v in r.items() if k != 'rows'}
            e_out[mode]['baselines'] = {n: [l, w] for n, l, w in r['rows']}
            print(f"  协议 {mode}:  训练序列 {r['n_train']:,} / 测试序列 {r['n_test']:,}"
                  f"   冷启动 {r['cold_ratio']*100:.1f}%")
            print(f"    {'基线':<14}{'log空间R2':>12}{'原始空间R2':>14}   两个空间的差")
            for n, l, w in r['rows']:
                print(f'    {n:<14}{l:>+12.4f}{w:>+14.4f}{l - w:>+15.4f}')
            print()
        out[key + '_raweval'] = e_out

    if args.json_out:
        with open(args.json_out, 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f'JSON 已写入 {args.json_out}', file=sys.stderr)


if __name__ == '__main__':
    main()
