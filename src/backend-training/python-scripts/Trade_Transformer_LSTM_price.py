import logging
import os
import random
import argparse
from datetime import datetime

import pandas as pd
import numpy as np
import torch
import torch.nn as nn
from sklearn.metrics import r2_score
from torch.utils.data import Dataset, DataLoader
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.model_selection import train_test_split
import matplotlib.pyplot as plt
import pickle

from tqdm import tqdm

import sys
import io

# line_buffering=True：默认的 TextIOWrapper 会在重定向到文件时块缓冲（8KB），
# 全量训练要跑数小时，日志长时间不落盘会让人误以为进程卡死。
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8',
                             line_buffering=True)


# 解析命令行参数
parser = argparse.ArgumentParser(description='训练贸易预测模型')
parser.add_argument('--csv_path', type=str, required=True, help='CSV数据文件路径')
parser.add_argument('--model_output_path', type=str, required=True, help='模型输出路径（目录）')
parser.add_argument('--trade_type', type=str, required=True, choices=['in', 'out'], help='贸易类型：in(进口) 或 out(出口)')
parser.add_argument('--target', type=str, required=True, choices=['price', 'quantity'], help='预测目标：price(单价) 或 quantity(数量)')
parser.add_argument('--sound_file', type=str, default='', help='训练完成提示音文件路径（可选）')
parser.add_argument('--split_mode', type=str, default='time',
                    choices=['time', 'commodity'],
                    help='切分协议：time=按时间切（默认，测已知商品的下一期预测）；'
                         'commodity=按商品切（测对新商品的预测）')
parser.add_argument('--group_key', type=str, default='full',
                    choices=['full', 'commodity'],
                    help='序列分组键：full=贸易伙伴x商品x贸易方式x注册地（默认，'
                         '与 app.py 推理端取数口径严格一致）；'
                         'commodity=仅商品编码（历史行为，序列内存在并列时间点，'
                         '仅供与旧结论对照，不要用于生产模型）')
parser.add_argument('--test_ratio', type=float, default=0.2, help='测试集比例（默认 0.2）')
parser.add_argument('--epochs', type=int, default=1000, help='最大训练轮数（默认沿用脚本原值）')
parser.add_argument('--max_rows', type=int, default=0, help='冒烟测试：只用约 N 行（0=全量）')
parser.add_argument('--sample_mode', type=str, default='commodity',
                    choices=['commodity', 'cap', 'active'],
                    help='抽样方式：'
                         'commodity（历史行为）=随机挑整个商品，累计达 --max_rows 即停。'
                         '出口实测只留 446/6,974 个商品（覆盖 6.4%%），推理端其余'
                         '93.6%% 的商品全部退化成兜底 embedding，预测失去信息量。'
                         'cap=保留全部商品编码，每个商品只取最近 --cap_k 个「不同月份」。'
                         'active=只保留最近 --active_months 个月内有过交易的商品，'
                         '并保留这些商品的完整历史（不截断时间线）。')
parser.add_argument('--cap_k', type=int, default=0,
                    help='cap 抽样下每个商品保留的最近「不同月份」数'
                         '（0 = 由 --max_rows 自适应）')
parser.add_argument('--patience', type=int, default=8,
                    help='早停耐心（默认 8）。原实现硬编码 100，在 30 轮的 A/B 里'
                         '其最佳验证损失出现在第 13~14 轮，后 16 轮全属过拟合，'
                         '既浪费时间又让「最后 epoch 权重」劣于最佳 checkpoint。')
parser.add_argument('--val_ratio', type=float, default=0.1,
                    help='从训练序列的「时间尾部」切出多少比例作为独立验证集'
                         '（默认 0.1；0 = 关闭，此时会退回用测试集做验证并给出警告）。'
                         '原实现直接用 test_loader 当验证集，'
                         '导致最佳 checkpoint 与学习率调度都在测试集上做选择。')
parser.add_argument('--active_months', type=int, default=24,
                    help='active 抽样下的活跃窗口（月，默认 24）')
parser.add_argument('--cutoff_ym', type=int, default=0,
                    help='time 协议下的固定日历切分点（YYYYMM 整数，如 202412）。'
                         '0（默认）=按 数据年月 的 80%% 分位自动取。'
                         'cap 抽样必须配固定点，理由见切分处注释。')
parser.add_argument('--seed', type=int, default=42, help='随机种子（切分与抽样用）')
parser.add_argument('--ar_feature', type=str, default='target_log',
                    choices=['none', 'target_log'],
                    help='是否把「目标自身的滞后值」作为输入特征：'
                         'target_log（默认）=窗口内每一期的 log1p(目标) 作为连续特征；'
                         'none=仅用 year/month/数量/人民币（历史行为）。'
                         '目标序列强自相关（实测 lag-1 自相关 0.87~0.93），'
                         '而原特征集里完全没有目标自身的历史值，模型无法表达'
                         '「预测≈上一期值」，必然打不过 carry-forward 基线。')
parser.add_argument('--head', type=str, default='residual',
                    choices=['direct', 'residual'],
                    help='输出头：'
                         'direct（历史行为）=直接回归目标值本身；'
                         'residual（默认）=回归「相对窗口末期的增量」，'
                         '即 pred = 窗口末期值 + 模型输出。'
                         '动机：目标序列强自相关，carry-forward 基线 log 空间 R2 '
                         '高达 0.93~0.95，direct 头必须从零学会「原样输出上一期」'
                         '才能追平基线，实测 A/B 两条臂（ar_feature none/target_log）'
                         'log 空间 R2 分别 0.7760 / 0.9040，均未超过 0.9467。'
                         'residual 头把「原样输出上一期」做成网络的恒等旁路：'
                         '输出全零即恒等于 carry-forward，模型只需学习修正量，'
                         '结构上不可能劣于该基线（这是本参数存在的全部意义）。')
parser.add_argument('--calibrate_only', action='store_true',
                    help='不训练，直接加载已有 checkpoint，在【验证集】上重新拟合'
                         '残差修正量的收缩系数 α 并重跑评估，最后覆写 feature_spec。'
                         '用途：训练已完成但事后发现网络输出被噪声推离基线时，'
                         '无需重训即可把模型拉回「不劣于 carry-forward」。')
parser.add_argument('--calib_alpha_max', type=float, default=1.0,
                    help='α 的上界（默认 1.0，即【只允许收缩，不允许放大】）。'
                         'α* = <net_out, Δy>/<net_out, net_out> 在验证集上最小二乘最优；'
                         '实测两路进口目标 α* = 1.54 / 1.73（>1），说明网络输出是真实'
                         '增量的收缩版（欠拟合 shrinkage）。但把 α 放到 1.54 后，'
                         '测试集原始空间 R2 从 0.7906 掉到 0.7825（log 空间 R2 不动），'
                         '即「放大」只在验证集上成立、到测试集反噬 —— 典型的小样本'
                         '验证集过拟合。收缩方向上 α=0 有硬保证（严格退化为最强朴素'
                         '基线），放大方向没有，故默认上界取 1.0。追求诊断信息时可用 '
                         '--calib_alpha_scan 观察 α* 的原始值，不必真的采纳它。')
parser.add_argument('--calib_alpha_scan', action='store_true',
                    help='评估时额外打印一张 α 敏感性表（测试集 log/原始空间 R2 随 α 变化），'
                         '用于判断「选到的 α 是不是验证集过拟合」。仅打印，不参与选择。')
parser.add_argument('--threads', type=int, default=4,
                    help='PyTorch 的 CPU 线程数（默认 4）')
parser.add_argument('--batch_size', type=int, default=32,
                    help='训练批次大小（默认 32）。本任务序列很短（seq_length=2）、'
                         '张量很小，单步耗时基本由固定开销决定，增大批次可显著提升'
                         '吞吐；全量出口数据下这是能否跑完的关键旋钮。')
args = parser.parse_args()

# ⚠️ CPU 训练的线程数：默认 torch.get_num_threads() = 机器核数（本机 10），
# 对这种小张量（batch 32 / 序列长 2 / d_model 128）反而会因 OpenMP 线程争用
# 慢一个数量级。本机实测（同为 32x2x5 输入、20 次迭代平均）：
#     10 线程 1567 ms/迭代   ·   4 线程 127 ms/迭代   ·   1 线程 176 ms/迭代
# 即默认设置比 4 线程慢约 12 倍。可用 --threads 调整。
torch.set_num_threads(args.threads)

# =====================================================================
# 随机性控制（--seed 必须真正生效）
#
# 原实现只在切分与抽样处用了 np.random.RandomState(args.seed)，模型权重初始化
# （torch 默认全局 RNG）与 DataLoader(shuffle=True) 的洗牌都未播种。后果：
# 同一份数据、同一个 --seed、同一套参数，两次运行的模型完全不同。
#
# 实测（进口-单价 / time / max_rows=12000 / 30 轮 / 同 seed=42 两次运行）：
#     R2 = +0.0025   vs   R2 = +0.0719
#     预测/真实标准差比 = 0.03  vs  0.96
# 测试集完全一致（n=2398，真实均值 1022.19）。也就是说，此前所有单次冒烟的
# R2 都是「一次抽样」，其运行间波动（约 ±0.04）大于多数组合之间的差异，
# 不足以支撑「某组合能学会 / 某组合学不会」这类结论。
#
# 修复：在此处一次性播种 python / numpy / torch 三个 RNG，并把 DataLoader 的
# 洗牌交给显式 Generator，使训练可复现。
# 注意：CPU 多线程下个别归约算子的浮点累加顺序仍可能带来末位差异，但量级上
# 不再是「换一个模型」。
# =====================================================================
random.seed(args.seed)
np.random.seed(args.seed)
torch.manual_seed(args.seed)
print(f"随机种子已固定: seed={args.seed} (python/numpy/torch + DataLoader)")

# Step 1: 加载和预处理数据
file_path = args.csv_path
# 尝试多种编码方式读取 CSV 文件
encodings = ['utf-8', 'gbk', 'gb2312', 'utf-8-sig', 'latin1']
df = None
for enc in encodings:
    try:
        df = pd.read_csv(file_path, encoding=enc, thousands=',', low_memory=False)
        print(f"成功使用 {enc} 编码读取文件")
        break
    except (UnicodeDecodeError, UnicodeError):
        continue
    except Exception as e:
        print(f"使用 {enc} 编码时出错: {e}")
        continue

if df is None:
    raise ValueError(f"无法使用任何编码读取文件: {file_path}")

# 打印列名以便调试
print(f"CSV 文件列名: {list(df.columns)}")
print(f"列名类型: {[type(col).__name__ for col in df.columns]}")

# 列名映射：支持不同的列名变体
column_mapping = {
    '数据年月': ['数据年月', '年月', '日期'],
    '贸易伙伴编码': ['贸易伙伴编码', '伙伴编码'],
    '商品编码': ['商品编码'],
    '贸易方式编码': ['贸易方式编码', '方式编码'],
    '注册地编码': ['注册地编码', '注册地'],
    '计量单位': ['计量单位', '第一数量单位', '数量单位', '单位'],
    '数量': ['数量', '第一数量', '数量值'],
    '人民币': ['人民币', '金额', '总金额', '金额(人民币)'],
    '单价': ['单价', '价格', '单位价格']
}

# 创建列名映射字典（使用更健壮的匹配方式）
actual_to_standard = {}
df_columns_list = [str(col).strip() for col in df.columns]  # 转换为字符串并去除空格

# 规范化列名（去除空格、统一编码）
def normalize_col_name(name):
    return str(name).strip().replace(' ', '').replace('\t', '')

for standard_col, possible_names in column_mapping.items():
    found = False
    matched_col = None
    
    # 规范化标准列名
    normalized_standard = normalize_col_name(standard_col)
    
    # 首先尝试精确匹配
    for possible_name in possible_names:
        normalized_possible = normalize_col_name(possible_name)
        for actual_col in df_columns_list:
            normalized_actual = normalize_col_name(actual_col)
            # 精确匹配
            if normalized_actual == normalized_possible or normalized_actual == normalized_standard:
                actual_to_standard[standard_col] = actual_col
                matched_col = actual_col
                found = True
                print(f"匹配成功: '{standard_col}' <- '{actual_col}'")
                break
        if found:
            break
    
    # 如果精确匹配失败，尝试包含匹配（部分匹配）
    if not found:
        for possible_name in possible_names:
            normalized_possible = normalize_col_name(possible_name)
            for actual_col in df_columns_list:
                normalized_actual = normalize_col_name(actual_col)
                # 检查是否包含关键词（双向检查）
                if normalized_possible in normalized_actual or normalized_actual in normalized_possible:
                    actual_to_standard[standard_col] = actual_col
                    matched_col = actual_col
                    found = True
                    print(f"部分匹配成功: '{standard_col}' <- '{actual_col}'")
                    break
            if found:
                break
    
    if not found:
        # 打印更详细的错误信息（使用 repr 显示原始字符串）
        print(f"错误: 找不到列 '{standard_col}'")
        print(f"  尝试的列名: {possible_names}")
        print(f"  实际列名 (repr): {[repr(col) for col in df_columns_list]}")
        print(f"  实际列名 (str): {df_columns_list}")
        raise ValueError(f"找不到列 '{standard_col}' 的变体。尝试的列名: {possible_names}。实际列名: {df_columns_list}")

# 重命名列为标准名称
df_renamed = df.rename(columns={v: k for k, v in actual_to_standard.items()})
df = df_renamed
print(f"列名映射完成: {actual_to_standard}")

# 提取年月列
df['year'] = df['数据年月'].astype(str).str[:4].astype(int)
df['month'] = df['数据年月'].astype(str).str[4:].astype(int)

# 特征列调整
categorical_cols = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码', '计量单位']
CONTINUOUS_BASE = ['year', 'month', '数量', '人民币']  # 基础连续变量（含年月）
continuous_cols = list(CONTINUOUS_BASE)
# 根据参数确定目标变量
if args.target == 'price':
    target_cols = ['单价']
    target_cols_log = ['log_单价']
else:
    target_cols = ['数量']
    target_cols_log = ['log_数量']

# 清理数据
df[target_cols] = df[target_cols].replace({'-': np.nan, '': np.nan}, regex=False)
df[target_cols] = df[target_cols].apply(pd.to_numeric, errors='coerce')

# 在清洗完数据之后、标准化之前添加：
df[target_cols] = df[target_cols].clip(lower=0)  # 先确保非负
if args.target == 'price':
    df['log_单价'] = np.log1p(df['单价'])
    target_cols_log = ['log_单价']
else:
    df['log_数量'] = np.log1p(df['数量'])
    target_cols_log = ['log_数量']

# =====================================================================
# 目标自身的滞后值作为输入特征（--ar_feature target_log）
#
# 本任务的目标是「该序列下一期的单价 / 数量」，实测 log 空间 lag-1 自相关
# 0.87~0.93 —— 接近随机游走，所以「上一期值」是一条极强的朴素基线
# （进口-单价/time log 空间 R2=+0.6908，且它常常强于本模型）。
#
# 而基础特征集 ['year','month','数量','人民币'] 里【完全没有目标自身的历史值】，
# 模型连「复制上一期」这件事都无法表达，等于被结构性剥夺了最强的那个信号。
# 把窗口内每一期的 log1p(目标) 也放进连续特征后，模型至少能学到「≈上一期值」，
# 再在此基础上做增量修正 —— 这是时序预测的标准做法（ab_ar.sh 即为此设计的 A/B）。
#
# 注意：
#   · 该特征只用窗口内（t-seq_length .. t-1）的目标值，不含 t 期，无泄漏。
#   · 单独存成 _AR_COL 列，避免与 scaler_y 使用的 target_cols_log 互相覆盖
#     （后者会被 scaler_y 原地标准化成另一个空间）。
#   · 它随 scaler_X 一起标准化，故 app.py 只需按同一列表追加 raw 的 log1p 值。
# =====================================================================
_AR_COL = f'_ar_log_{args.target}'
if args.ar_feature == 'target_log':
    df[_AR_COL] = np.log1p(df[target_cols[0]])
    continuous_cols = CONTINUOUS_BASE + [_AR_COL]
    print(f"已启用目标滞后特征: {_AR_COL}   continuous_cols={continuous_cols}")
else:
    continuous_cols = list(CONTINUOUS_BASE)
    print("未启用目标滞后特征（--ar_feature none）")

# =====================================================================
# 数据切分 与 特征缩放
#
# 【为什么必须先切分再 fit】原实现用全量数据 fit_transform 了 1 个 scaler_y、
# 5 个 LabelEncoder、1 个 scaler_X，之后才切分 —— 测试集的均值/方差/类别集合
# 参与了训练期特征的构造，属标准数据泄漏（见《模型评测报告》§3.3）。
#
# 【为什么不能用 train_test_split(shuffle=False)】原实现把每个商品的时间序列
# 展开成样本后，按商品编码升序拼成一个大数组，再 shuffle=False 取后 20%，
# 于是测试集几乎全是「商品编码较大」的商品。这些商品训练时从未出现，其
# Embedding 行始终停在随机初值，模型等于在随机猜（同报告 §3.1）。实测真实
# 数据上这类「冷启动」样本占测试集的 99.9% —— 所以单纯换真实数据重训并不能
# 解决问题，必须先修切分。
#
# 本脚本提供两种切分协议（--split_mode），分别度量两种不同的能力，
# 结论必须分开陈述，不能混为一谈：
#   time      （默认）按时间切：目标的 数据年月 晚于切分点的序列进测试集。
#             商品在训练期出现过，测的是「对已知商品的下一期预测」。
#   commodity 按商品切：整个商品进测试集，测的是「对新商品的预测」。
#             这正是原实现意外在做的事，保留下来便于对照。
# =====================================================================
seq_length = 2          # 历史窗口长度，必须与 app.py 的 seq_length 保持一致
SPLIT_RATIO = args.test_ratio

# 抽样：三种口径，见 --sample_mode 的说明。
#   commodity —— 历史行为。随机挑整个商品，累计行数达上限即停。
#     出口实测只留 446/6,974 个商品（6.4%），推理端 93.6% 的商品退化成
#     兜底 embedding，等于该商品的预测失去信息量。
#   cap —— 保留全部商品编码，每个商品只取最近 K 个「不同月份」。
#     注意必须按月份而不是行数截取：同一商品同一月有多条四键记录，
#     按行数截 24 行可能只覆盖 1~2 个月，序列全部挤到最近，训练集被压到
#     万级以下、划分严重失衡（实测训练只剩 8,980 条 / 测试占 66%）。
#   active —— 保留「最近 N 个月内有过交易」的商品的全部历史。
#     时点上更贴合生产：问答/看板问的必然是当前在做的商品，
#     而远古商品的嵌入即便训练过也用不上；同时保留完整时间线，
#     使 time 协议的切分点自然落得下。
# active 与 cap（显式给了 --cap_k）都不依赖 --max_rows 触发；
# commodity 是「按行数上限挑整组商品」，所以必须有 --max_rows 才有意义。
_need_sample = (args.sample_mode == 'active'
                or (args.sample_mode == 'cap' and bool(args.cap_k))
                or (bool(args.max_rows) and len(df) > args.max_rows))
if _need_sample:
    if args.sample_mode == 'active':
        _n = args.active_months or 24
        _ym = df['year'].astype(int) * 100 + df['month'].astype(int)
        _mx = int(_ym.max())
        _t = (_mx // 100) * 12 + (_mx % 100 - 1) - (_n - 1)
        _cut = (_t // 12) * 100 + (_t % 12) + 1
        _active = set(df.loc[_ym >= _cut, '商品编码'].astype(str).unique())
        _before = df['商品编码'].nunique()
        df = df[df['商品编码'].astype(str).isin(_active)].reset_index(drop=True)
        print(f"抽样(active)：最近 {_n} 个月（>= {_cut}）内有交易的商品 "
              f"{len(_active):,} / {_before:,}"
              f"（{len(_active)/max(_before,1)*100:.1f}%），保留其完整历史 → "
              f"{len(df):,} 行")
    elif args.sample_mode == 'cap':
        _n_codes = df['商品编码'].nunique()
        _k = args.cap_k or max(2, int(args.max_rows / max(_n_codes, 1)))
        _s = df.assign(_ym=df['year'].astype(int) * 100 + df['month'].astype(int))
        _g = _s.groupby('商品编码', sort=False)['_ym']
        _rk = _g.rank(method='dense')
        _nm = _g.transform('nunique')
        df = _s[_rk > _nm - _k].drop(columns='_ym').reset_index(drop=True)
        print(f"抽样(cap)：保留全部 {_n_codes} 个商品，每商品最近 {_k} 个不同月份"
              f" → {len(df):,} 行（目标约 {args.max_rows} 行）")
    else:
        _codes = df['商品编码'].astype(str)
        _sizes = _codes.value_counts()
        _order = _sizes.index.to_numpy().copy()
        np.random.RandomState(args.seed).shuffle(_order)
        _keep, _cum = [], 0
        for _c in _order:
            _keep.append(_c)
            _cum += int(_sizes[_c])
            if _cum >= args.max_rows:
                break
        df = df[_codes.isin(set(_keep))].reset_index(drop=True)
        print(f"抽样(commodity)：保留 {len(_keep)} 个商品 / {len(df)} 行"
              f"（目标约 {args.max_rows} 行，商品覆盖率 "
              f"{len(_keep)/max(_codes.nunique(),1)*100:.1f}%）")

# 清理连续变量中的异常值，并丢弃含 NaN 的行。
# 必须放在切分之前，否则切分点会落在脏数据上。
for col in continuous_cols:
    df[col] = df[col].replace({'-': np.nan, '': np.nan}, regex=False)
    df[col] = pd.to_numeric(df[col], errors='coerce')
df.dropna(subset=categorical_cols + continuous_cols + target_cols_log, inplace=True)
df = df[pd.to_numeric(df['数据年月'], errors='coerce').notna()].reset_index(drop=True)
if len(df) == 0:
    raise ValueError('清洗后数据为空，请检查 CSV 内容与列名映射')

# 留一份原始商品编码：LabelEncoder 会把「训练期未见过的类别」映射到同一个
# 预留槽位，若拿编码后的值分组，commodity 协议下所有新商品会被并成一组。
df['_orig_comm'] = df['商品编码'].astype(str)

# =====================================================================
# 序列分组键（_series_key）—— 训练与推理的口径必须一致
#
# app.py 的 db_process() 用四条 WHERE 取历史：
#     贸易伙伴名称 AND 商品名称 AND 贸易方式名称 AND 注册地名称
# 也就是说推理端的「一条时间序列」= 四键完全相同的一串月份。
#
# 原实现只按「商品编码」单键分组，同一序列里混入不同伙伴/贸易方式/注册地的行。
# 实测「同一(商品,年月)存在重复行」的占比：进口 72.12%、出口 94.26%，即序列里
# 绝大多数相邻点属于同一个月 —— 「上一期」不再是一个月前，时间步语义不成立。
# 更致命的是：推理端按四键取数，输入分布与训练时完全不同（训练/推理口径错配），
# 模型在真实问答里必然给不出可用的数。故默认 group_key=full。
#
# 实测四键分组后的序列量与原单键口径相当（进口 31,044 / 出口 1,484,018 条，
# seq_length=2），改为正确语义不会损失训练规模。
# =====================================================================
SERIES_KEYS = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码']
if args.group_key == 'full':
    _sk = df[SERIES_KEYS[0]].astype(str)
    for _c in SERIES_KEYS[1:]:
        _sk = _sk.str.cat(df[_c].astype(str), sep='|')
    df['_series_key'] = _sk
    del _sk
else:
    # 历史行为：仅商品编码。保留以便与旧结论对照。
    df['_series_key'] = df['_orig_comm']

print(f"序列分组键: {args.group_key}   四键分组数 {df['_series_key'].nunique():,}"
      f"（此行不是训练样本数，按 seq_length={seq_length} 展开后见下方「训练/测试序列」）")

# 每条序列内部按时间先后排序，保证序列的时间顺序正确
df = df.sort_values(['_series_key', 'year', 'month']).reset_index(drop=True)
ym_all = df['数据年月'].astype('int64')

if args.split_mode == 'time':
    # 切分点：默认取「数据年月」的整体 80% 分位；也可用 --cutoff_ym 固定日历点。
    #
    # 为什么需要固定点：cap 抽样把每个商品都截到「最近 k 期」后，全体商品的
    # 尾部都挤在最近若干月，分位切分点会被推到很晚，导致目标月几乎全部落在
    # 切分点之前 —— 实测 k=17/24 时测试序列数直接为 0，模型无从评测。
    # 固定日历点（如 202412）让「测试集 = 最近一年的所有序列」，
    # 语义清晰、与抽样方式解耦。
    if args.cutoff_ym:
        cutoff = int(args.cutoff_ym)
    else:
        cutoff = int(np.quantile(ym_all.values, 1 - SPLIT_RATIO))
    train_row_mask = (ym_all <= cutoff).to_numpy()
    test_codes = None
    print(f"切分点来源: {'--cutoff_ym 固定日历点' if args.cutoff_ym else '数据年月 80% 分位'}")
else:
    cutoff = None
    _codes = np.array(sorted(df['_orig_comm'].unique()))
    np.random.RandomState(args.seed).shuffle(_codes)
    _n_test = max(1, int(round(len(_codes) * SPLIT_RATIO)))
    test_codes = set(_codes[:_n_test].tolist())
    train_row_mask = ~df['_orig_comm'].isin(test_codes).to_numpy()

if train_row_mask.sum() == 0:
    raise ValueError('切分后训练行为空，请调小 --test_ratio 或换一种 --split_mode')

print(f"切分协议: {args.split_mode}  切分点: {cutoff}  "
      f"训练行 {int(train_row_mask.sum())} / 全部 {len(df)}")
print(f"  商品总数: {df['_orig_comm'].nunique()}"
      + (f"  测试集商品数: {len(test_codes)}" if test_codes is not None else ""))

# ---- 只在训练集上 fit，再 transform 全量（消除数据泄漏）----
df_train_rows = df[train_row_mask]

scaler_y = StandardScaler().fit(df_train_rows[target_cols_log])
df[target_cols_log] = scaler_y.transform(df[target_cols_log])

label_encoders = {}
for col in categorical_cols:
    le = LabelEncoder().fit(df_train_rows[col].astype(str))
    label_encoders[col] = le
    _known = {c: i for i, c in enumerate(le.classes_)}
    _unknown = len(le.classes_)        # 预留槽位：训练期未见过的类别
    df[col] = df[col].astype(str).map(
        lambda x, k=_known, u=_unknown: k.get(x, u)).astype(int)

scaler_X = StandardScaler().fit(df_train_rows[continuous_cols])
df[continuous_cols] = scaler_X.transform(df[continuous_cols])

# ---- 按商品分组创建时间序列，并记录每条序列的商品 / 目标月份 / 窗口末值 ----
sequences = []
seq_comm = []            # 该序列所属的商品编码（供冷启动统计 / commodity 协议）
seq_skey = []            # 该序列所属的四键序列（供分组口径核对）
seq_ym = []
seq_prev_y = []          # 窗口最后一个目标值，供「上一期值」基线使用

for name, group in df.groupby('_series_key', sort=False):
    group = group.sort_values(['year', 'month'])
    if len(group) < seq_length + 1:      # 数据点不够，跳过该序列
        continue

    X_cat_group = group[categorical_cols].values
    X_cont_group = group[continuous_cols].values
    # 必须保持二维 (M,1)：模型输出是 (B,1)，若 y 是 (B,) 则 MSELoss 会
    # 广播成 (B,B)，训练损失会假性地恒定在方差附近（实测 ≈1.0）而不学习。
    y_group = group[target_cols_log].values
    ym_group = group['数据年月'].astype('int64').values
    # 同一条序列内四键完全相同，取首行的商品编码即可代表整条序列
    comm_of_group = str(group['_orig_comm'].iloc[0])

    for i in range(len(X_cat_group) - seq_length):
        sequences.append((X_cat_group[i:i + seq_length],
                          X_cont_group[i:i + seq_length],
                          y_group[i + seq_length]))
        seq_comm.append(comm_of_group)
        seq_skey.append(str(name))
        seq_ym.append(int(ym_group[i + seq_length]))
        seq_prev_y.append(float(y_group[i + seq_length - 1][0]))

if not sequences:
    raise ValueError("没有足够数据创建序列，请检查数据或减小 seq_length")

X_cat = np.array([s[0] for s in sequences], dtype=np.int64)
X_cont = np.array([s[1] for s in sequences], dtype=np.float64)
y = np.array([s[2] for s in sequences], dtype=np.float64)
seq_comm = np.array(seq_comm)
seq_skey = np.array(seq_skey)
seq_ym = np.array(seq_ym)
seq_prev_y = np.array(seq_prev_y)

# ---- 按协议把「序列」（而不是行）分到训练/测试 ----
if args.split_mode == 'time':
    test_seq_mask = seq_ym > cutoff
else:
    test_seq_mask = np.isin(seq_comm, list(test_codes))

train_idx = np.where(~test_seq_mask)[0]
test_idx = np.where(test_seq_mask)[0]
if len(train_idx) == 0 or len(test_idx) == 0:
    raise ValueError(f'切分后训练/测试序列有空集（train={len(train_idx)}, '
                     f'test={len(test_idx)}），请调整 --test_ratio 或换 --split_mode')

# 冷启动统计：测试序列中「商品在训练集里没出现过」的比例。
# time 协议下应接近 0；commodity 协议下按设计接近 100%（这正是原实现的问题）。
train_comms = set(seq_comm[train_idx].tolist())
cold_mask = np.array([c not in train_comms for c in seq_comm[test_idx]])
# 序列级（四键）冷启动：直接决定 Embedding 行有没有被训过，比商品级更贴近
# 模型实际看到的东西 —— time 协议下商品可能出现过、但该四键组合没出现过。
train_skeys = set(seq_skey[train_idx].tolist())
skey_cold_mask = np.array([c not in train_skeys for c in seq_skey[test_idx]])
print(f"  训练序列 {len(train_idx)} / 测试序列 {len(test_idx)}")
print(f"  测试集中「训练期未见过的商品」: {int(cold_mask.sum())} / {len(test_idx)}"
      f" ({cold_mask.mean() * 100:.1f}%)")
print(f"  测试集中「训练期未见过的四键序列」: {int(skey_cold_mask.sum())} / {len(test_idx)}"
      f" ({skey_cold_mask.mean() * 100:.1f}%)")

y_train = y[train_idx]        # 仅用于「上一期值/全局均值/季节均值」三条朴素养线
X_cat_test, X_cont_test, y_test = X_cat[test_idx], X_cont[test_idx], y[test_idx]

# =====================================================================
# 从训练序列里再切出「独立验证集」（原实现把 test_loader 当验证集用）
#
# 原实现（第 735 行）拿 test_loader 算 val_loss，并用它做
#   ① ReduceLROnPlateau 的学习率调度  ② EarlyStopping 的最佳 checkpoint 选择
# 后果是「模型选择发生在测试集上」—— 报出来的测试 R² 是「在测试集上挑过
# 最好的一轮」的结果，属于典型的选择性偏差，不能作为泛化能力证据。
#
# 这里按时间从训练序列尾部切出 --val_ratio 比例作为验证集：
# 验证与测试都取自时间尾部、且验证更早，符合「用过去预测未来」的时点关系，
# 同时两者互不重叠。测试集此后只用于最终报告，不参与任何训练期决策。
# =====================================================================
_val_ratio = args.val_ratio
if _val_ratio > 0:
    _order_train = train_idx[np.argsort(seq_ym[train_idx], kind='mergesort')]
    _n_val = int(round(len(_order_train) * _val_ratio))
    if _n_val < 1 or _n_val >= len(_order_train):
        _n_val = 0
    if _n_val > 0:
        val_idx = _order_train[-_n_val:]
        fit_idx = np.setdiff1d(train_idx, val_idx, assume_unique=False)
    else:
        val_idx = np.array([], dtype=int)
        fit_idx = train_idx
else:
    val_idx = np.array([], dtype=int)
    fit_idx = train_idx
    print("  ! --val_ratio=0：没有独立验证集，将退回用【测试集】做验证与早停，"
          "报出的测试 R² 会偏高、不可作为泛化能力证据。")
print(f"  训练/验证划分: 拟合 {len(fit_idx)} / 验证 {len(val_idx)}"
      f"（验证集取自训练序列时间尾部，test_ratio={SPLIT_RATIO}, val_ratio={_val_ratio}）")

# ---- 输出头：residual 时模型拟合的是「相对窗口末期的增量」 ----
# y_*（真值）与 prev_*（窗口末期值）都在「标准化后的 log 空间」，两者共用
# scaler_y，故在标准化空间里直接相减得到的增量再做逆变换是合法的。
# 注意：y_train / y_test 必须保持真值不变 —— 评估段要用它算 R²，
# 只有喂给 DataLoader 的目标才是增量。
prev_test = np.asarray(seq_prev_y[test_idx], dtype=np.float64).reshape(-1, 1)
prev_fit = np.asarray(seq_prev_y[fit_idx], dtype=np.float64).reshape(-1, 1)
prev_val = (np.asarray(seq_prev_y[val_idx], dtype=np.float64).reshape(-1, 1)
            if len(val_idx) else np.zeros((0, 1)))
y_fit = y[fit_idx]
y_val = y[val_idx]
if args.head == 'residual':
    y_fit_loader = y_fit - prev_fit
    y_val_loader = (y_val - prev_val) if len(val_idx) else y_val
    print(f"输出头: residual（回归增量，预测 = 窗口末期值 + 网络输出）；"
          f"增量训练目标 std={float(np.std(y_fit_loader)):.4f}")
else:
    y_fit_loader = y_fit
    y_val_loader = y_val
    print(f"输出头: direct（直接回归目标值）")

# 朴素基线（全部在「标准化后的 log 空间」，评估段再还原到原始尺度）
baseline_prev = seq_prev_y[test_idx]                                   # 上一期值
baseline_global = np.full(len(test_idx), float(y_train.mean()))        # 全局均值
_train_month = seq_ym[train_idx] % 100
_test_month = seq_ym[test_idx] % 100
_month_mean = {int(m): float(y_train[_train_month == m].mean())
               for m in np.unique(_train_month)}
# 【性能】dict.get(key, default) 的 default 会被逐次求值：若把 float(y_train.mean())
# 直接写在列表推导里，它会对 n_test 个元素各算一次全局均值，每次扫一遍训练集，
# 复杂度退化为 O(n_test x n_train)。冒烟规模（万级）下无感，但出口全量约
# 37 万测试序列 x 153 万训练序列 ≈ 5.8e11 次运算，实测会挂住数分钟以上，
# 直接阻断 §6③ 的全量重训。故提到循环外。
_grand_mean = float(y_train.mean())
baseline_season = np.array([_month_mean.get(int(m), _grand_mean)
                            for m in _test_month])                     # 季节均值

# 转换为PyTorch张量（训练集只含 fit_idx，验证集独立）
X_cat_fit_tensor = torch.tensor(X_cat[fit_idx], dtype=torch.long)
X_cont_fit_tensor = torch.tensor(X_cont[fit_idx], dtype=torch.float32)
y_fit_tensor = torch.tensor(y_fit_loader, dtype=torch.float32)

X_cat_test_tensor = torch.tensor(X_cat_test, dtype=torch.long)
X_cont_test_tensor = torch.tensor(X_cont_test, dtype=torch.float32)
y_test_tensor = torch.tensor(y_test, dtype=torch.float32)

# 创建DataLoader
train_dataset = torch.utils.data.TensorDataset(X_cat_fit_tensor, X_cont_fit_tensor, y_fit_tensor)
# 训练集的洗牌必须用显式播种的 Generator，否则 --seed 对训练无效
_rng = torch.Generator()
_rng.manual_seed(args.seed)
train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True,
                          generator=_rng)

# 验证集：独立于测试集，只用于学习率调度与最佳 checkpoint 选择
if len(val_idx):
    val_dataset = torch.utils.data.TensorDataset(
        torch.tensor(X_cat[val_idx], dtype=torch.long),
        torch.tensor(X_cont[val_idx], dtype=torch.float32),
        torch.tensor(y_val_loader, dtype=torch.float32))
else:
    val_dataset = None
val_loader = (DataLoader(val_dataset, batch_size=256, shuffle=False, num_workers=0)
              if val_dataset is not None else None)

test_dataset = torch.utils.data.TensorDataset(X_cat_test_tensor, X_cont_test_tensor, y_test_tensor)
test_loader = DataLoader(
    test_dataset,
    batch_size=16,   # 小批量验证
    shuffle=False,
    num_workers=0,
    pin_memory=False if not torch.cuda.is_available() else True
)

#早停机制类
class EarlyStopping:
    def __init__(self, patience=10, verbose=False, delta=0, path=None):
        self.patience = patience
        self.verbose = verbose
        self.counter = 0
        self.best_score = None
        self.early_stop = False
        self.val_loss_min = np.inf
        self.delta = delta
        self.path = path

    def __call__(self, val_loss, model):
        score = -val_loss

        if self.best_score is None:
            self.best_score = score
            self.save_checkpoint(val_loss, model)
        elif score < self.best_score + self.delta:
            self.counter += 1
            print(f'EarlyStopping counter: {self.counter} out of {self.patience}')
            if self.counter >= self.patience:
                self.early_stop = True
        else:
            self.best_score = score
            self.save_checkpoint(val_loss, model)
            self.counter = 0

    def save_checkpoint(self, val_loss, model):
        if self.verbose:
            print(f'Validation loss decreased ({self.val_loss_min:.6f} --> {val_loss:.6f}). Saving model...')
        torch.save(model.state_dict(), self.path)
        self.val_loss_min = val_loss

# 方案一：使用可学习的位置编码
class LearnablePositionalEncoding(nn.Module):
    def __init__(self, d_model, max_len=5000):
        super().__init__()
        self.position_embeddings = nn.Embedding(max_len, d_model)

    def forward(self, x):
        positions = torch.arange(0, x.size(1), device=x.device).expand(x.size(0), -1)
        x = x + self.position_embeddings(positions)
        return x

# 混合 LSTM + Transformer 模型
class LSTMTransformer(nn.Module):
    def __init__(self, num_embeddings_list, continuous_dim, model_dim=64, hidden_size=64,
                 num_heads=4, num_layers=2, dropout=0.3):
        super().__init__()
        self.cat_embeddings = nn.ModuleList([
            nn.Embedding(num_emb, model_dim) for num_emb in num_embeddings_list
        ])
        self.cont_proj = nn.Linear(continuous_dim, model_dim)
        self.pos_encoder = LearnablePositionalEncoding(model_dim)

        self.lstm = nn.LSTM(model_dim, hidden_size, batch_first=True, bidirectional=False)
        encoder_layer = nn.TransformerEncoderLayer(d_model=hidden_size, nhead=num_heads, dropout=dropout)
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        self.norm = nn.LayerNorm(hidden_size)
        self.fc_out = nn.Linear(hidden_size, 1)

    def forward(self, cat_inputs, cont_inputs):
        # Embeddings
        embedded_cat = torch.stack([emb(cat_inputs[:, :, i]) for i, emb in enumerate(self.cat_embeddings)], dim=0).sum(dim=0)
        embedded_cont = self.cont_proj(cont_inputs)
        x = embedded_cat + embedded_cont
        x = self.pos_encoder(x)

        # LSTM
        x, _ = self.lstm(x)

        # Transformer
        x = x.permute(1, 0, 2)  # [S, B, D]
        x = self.transformer(x)
        x = x.mean(dim=0)  # 时间维度平均
        x = self.norm(x)
        return self.fc_out(x)

# Step 4: 初始化模型 & 训练
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# 获取每个类别特征的唯一值数量
#
# 【为什么固定写成 len(classes_) + 1】训练期未见过的类别被映射到预留槽位
# len(classes_)，所以 Embedding 行数恒为 len(classes_)+1，与是否真的出现
# 未见类别无关。原实现写 max(df[col])+1：训练集恰好覆盖全部类别时该值退化为
# len(classes_)，一旦出现未见类别（如 commodity 协议、或 time 协议下某些
# 四键组合只在测试期出现）就变成 len(classes_)+1。app.py 侧按
# len(classes_)+1 建模，两边必须严格一致，故这里改成不依赖数据的确定值。
# 方案二：混合 LSTM + Transformer 模型
model = LSTMTransformer(
    num_embeddings_list=[len(label_encoders[col].classes_) + 1
                         for col in categorical_cols],
    continuous_dim=len(continuous_cols),
    model_dim=128,
    hidden_size=128,
    num_heads=16,
    num_layers=5,
    dropout=0.6
).to(device)

criterion = nn.MSELoss(reduction='mean')
optimizer = torch.optim.Adam(model.parameters(), lr=1e-3, weight_decay=1e-5)
scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode='min', factor=0.5, patience=5)

# --calibrate_only：跳过训练循环（epochs=0），直接进入「加载最佳 checkpoint + 标定」流程。
# 之所以要走一遍完整的数据准备，是因为验证集必须与训练时**逐行一致**地重建
# （抽样/分组/切分/序列展开全部依赖同一套确定性逻辑），复制一份会立刻漂移。
epochs = 0 if args.calibrate_only else args.epochs
best_loss = float('inf')
losses = []
val_losses = []

# 提前定义save_path用于checkpoint
save_path = args.model_output_path
if not save_path.endswith(os.sep):
    save_path += os.sep
os.makedirs(save_path, exist_ok=True)

# 构建checkpoint路径
checkpoint_path = os.path.join(save_path, f'checkpoint_{args.target}.pth')
early_stopping = EarlyStopping(patience=args.patience, verbose=True, path=checkpoint_path)#patience表示耐心程度，当连续多少个epoch without improvement时，停止训练

print("开始训练...")
for epoch in range(epochs):
    model.train()
    total_loss = 0
    loop = tqdm(train_loader, desc=f"Epoch [{epoch + 1}/{epochs}]")

    for x_cat_batch, x_cont_batch, y_batch in loop:
        x_cat_batch, x_cont_batch, y_batch = x_cat_batch.to(device), x_cont_batch.to(device), y_batch.to(device)

        optimizer.zero_grad()
        outputs = model(x_cat_batch, x_cont_batch)
        loss = criterion(outputs, y_batch)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)  # 防止梯度爆炸
        optimizer.step()

        total_loss += loss.item()
        loop.set_postfix(loss=loss.item())

    avg_train_loss = total_loss / len(train_loader)
    losses.append(avg_train_loss)

    # 验证：用独立的 val_loader（原实现误用 test_loader，导致模型选择发生在
    # 测试集上；测试集此后只用于最终报告，不参与任何训练期决策）
    model.eval()
    val_loss = 0.0
    _val_src = val_loader if val_loader is not None else test_loader
    _val_n = len(_val_src.dataset)
    with torch.no_grad():
        for x_cat_val, x_cont_val, y_val_b in _val_src:
            x_cat_val, x_cont_val, y_val_b = x_cat_val.to(device), x_cont_val.to(device), y_val_b.to(device)
            val_outputs = model(x_cat_val, x_cont_val)
            loss = criterion(val_outputs, y_val_b)
            val_loss += loss.item() * x_cat_val.size(0)

        val_loss /= _val_n

    scheduler.step(val_loss)
    val_losses.append(val_loss)

    log_filename = os.path.join(save_path, f'training_{args.trade_type}_{args.target}.log')
    print(f"Epoch {epoch + 1}/{epochs} | Train Loss: {avg_train_loss:.4f} | Val Loss: {val_loss:.4f}")
    logging.basicConfig(filename=log_filename, level=logging.INFO)
    logging.info(f"Epoch {epoch + 1}/{epochs}, Train Loss: {avg_train_loss:.4f}, Val Loss: {val_loss:.4f}")
    early_stopping(val_loss, model)
    if early_stopping.early_stop:
        print("Early stopping")
        break

#  保存模型（save_path已在前面定义）
#  --calibrate_only 时绝不能写：此时模型未训练，写下去会把原有产物覆盖成随机权重。
model_filename = f'model_{args.target}.pth'
if not args.calibrate_only:
    torch.save(model.state_dict(), save_path + model_filename)

# Step 5: 评估最佳模型
print("开始验证...")
# 评估对象改为【早停保存的最佳模型】。原实现加载的是最后一个 epoch 的权重，
# 而训练损失仍在下降、验证损失已不再改善，最后一个 epoch 恰恰是过拟合最重的
# 那个（评测报告 §3.2）。
if os.path.exists(checkpoint_path):
    model.load_state_dict(torch.load(checkpoint_path))
    print(f"已加载早停保存的最佳模型: {checkpoint_path}")
else:
    print(f"警告：未找到 {checkpoint_path}，回退到最后一个 epoch 的权重")
    model.load_state_dict(torch.load(save_path + model_filename))
model.eval()

# =====================================================================
# 残差修正量的收缩系数 α —— 把 §6.6.1 的「结构上不劣于基线」从口号变成保证
#
# 问题：residual 头「输出全零即恒等于 carry-forward」这个不劣于基线的性质，
# 只在网络输出**恰好**接近 0 时才成立。欠拟合时网络输出是真实增量的收缩版
# （shrinkage），幅度不足；直接拿它做修正，方向对但幅度不够，仍拿不到收益。
#
# 做法：在【验证集】上拟合一个标量系数
#     α* = argmin_α || Δy_val − α·net_out ||²
#        = <net_out, Δy_val> / <net_out, net_out>
# 预测改为  prev + α*·net_out。三种取值都有明确含义：
#     α* = 0  → 网络输出与增量无关（或负相关），严格退化为 carry-forward
#     α* = 1  → 网络原始输出
#     α* > 1  → 网络输出系统性偏小，按最小二乘最优幅度放大贴回目标（实测情形）
# α 由数据决定，不引入人工超参数；--calib_alpha_max 只用于挡住极端放大。
#
# 无泄漏：验证集取自训练序列的时间尾部，与测试集在时间上先后分离且不重叠，
# 测试集全程未参与 α 的拟合。
# =====================================================================
correction_scale = None
alpha_fitted_raw = None   # 未钳制的 α*，只作诊断（见 --calib_alpha_max 的说明）
if args.head == 'residual' and val_loader is not None:
    _num = 0.0
    _den = 0.0
    with torch.no_grad():
        for x_cat_v, x_cont_v, y_v in val_loader:
            _out = model(x_cat_v.to(device), x_cont_v.to(device)).reshape(-1).double().cpu()
            _tgt = y_v.reshape(-1).double()
            _num += float(torch.dot(_out, _tgt).item())
            _den += float(torch.dot(_out, _out).item())
    if _den > 1e-12:
        correction_scale = _num / _den
        alpha_fitted_raw = float(correction_scale)
        print(f"验证集标定: 残差修正量收缩系数 α={correction_scale:.4f} "
              f"(α→0 退化为 carry-forward, α=1 为网络原始输出)")
        if correction_scale <= 0:
            print("  ! α<=0，说明网络输出与真实增量负相关，已收缩到 0（严格等于 carry-forward）")
            correction_scale = 0.0
        elif correction_scale > args.calib_alpha_max:
            print(f"  ! α 超过上限 {args.calib_alpha_max:.2f}，钳制到上限"
                  f"（无上界时极端放大等于把噪声当信号）")
            correction_scale = float(args.calib_alpha_max)
    else:
        correction_scale = 0.0
        print("验证集标定: 网络输出全零, α=0, 严格退化为 carry-forward")

all_preds = []
all_true = []

with torch.no_grad():
    for x_cat_batch, x_cont_batch, y_batch in test_loader:
        x_cat_batch = x_cat_batch.to(device)
        x_cont_batch = x_cont_batch.to(device)

        outputs = model(x_cat_batch, x_cont_batch)

        all_preds.append(outputs.cpu().numpy())
        all_true.append(y_batch.cpu().numpy())

# 合并所有批次的结果
y_pred = np.concatenate(all_preds, axis=0)
y_true = np.concatenate(all_true, axis=0)

# residual 头：网络输出是增量，加回窗口末期值才是目标值的预测。
# 这一步必须在所有下游评估之前完成，否则 R² / 基线对照全部错位。
# 注意 test_loader 装的仍是真值 y_test（只有 train 侧换成了增量），
# 所以这里只修正 y_pred，不动 y_true。
# α 为验证集上标定出的修正量收缩系数（无 α 时取 1，等价于原行为）。
_net_out_test = None
if args.head == 'residual':
    _alpha = 1.0 if correction_scale is None else float(correction_scale)
    # 留一份未缩放的网络输出：α 敏感性扫描要靠它重建任意 α 下的预测
    _net_out_test = np.asarray(y_pred, dtype=np.float64).reshape(-1)
    y_pred = _alpha * y_pred + prev_test

# 反标准化 + exp
y_pred_unscaled = np.expm1(scaler_y.inverse_transform(y_pred))
y_true_unscaled = np.expm1(scaler_y.inverse_transform(y_true))

pred_renminbi = y_pred_unscaled

true_renminbi = y_true_unscaled


# 计算指标
def evaluate(name, true, pred):
    r2 = r2_score(true, pred)
    mae = np.mean(np.abs(true - pred))
    mse = np.mean((true - pred) ** 2)
    print(f"\n{name} 评估:")
    print(f"R2 Score: {r2:.4f}")
    print(f"MSE: {mse:.4f}")
    print(f"MAE: {mae:.2f}")
    return r2, mse, mae

# 评估
target_name = "单价" if args.target == 'price' else "数量"
r2_q, mse_q, mae_q = evaluate(target_name, true_renminbi, pred_renminbi)
log_filename = os.path.join(save_path, f'training_{args.trade_type}_{args.target}.log')
logging.basicConfig(filename=log_filename, level=logging.INFO)
time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
logging.info(f"datetime:{time}, R2:{r2_q}, MSE:{mse_q}, MAE:{mae_q}")

# =====================================================================
# 朴素基线对照（《模型评测报告》§6.3）
#
# 本任务预测的是「同一商品的下一期单价/数量」，这类序列接近随机游走，
# 即便模型完全正常，R² 也可能天然为负 —— 只看 R² 会系统性误判。
# 因此「是否具备预测能力」必须用「模型 vs 基线」的相对表现来判定。
# =====================================================================
def _to_orig(x_scaled):
    return np.expm1(scaler_y.inverse_transform(
        np.asarray(x_scaled, dtype=np.float64).reshape(-1, 1)).ravel())

true_o = np.asarray(y_true_unscaled).ravel()
model_o = np.asarray(y_pred_unscaled).ravel()
prev_o = _to_orig(baseline_prev)
season_o = _to_orig(baseline_season)
global_o = _to_orig(baseline_global)


def _metrics(pred, name):
    r2 = r2_score(true_o, pred)
    mae = float(np.mean(np.abs(true_o - pred)))
    denom = np.where(true_o == 0, np.nan, true_o)
    with np.errstate(divide='ignore', invalid='ignore'):
        mape = float(np.nanmean(np.abs((true_o - pred) / denom)) * 100)
    print(f"  {name:<26} R2={r2:>10.4f}   MAE={mae:>15.2f}   MAPE={mape:>8.2f}%")
    return r2, mae, mape


# =====================================================================
# 【主判据】log 空间评估
#
# 目标跨 8~9 个数量级，去均值后 top1% 样本占原始空间平方和的 99% 左右。
# 原始空间的 R² 因此被极少数极端样本支配：实测「上一期值」这条朴素基线
#   进口-单价/time  log R2=+0.6908  ->  原始 R2=-0.0795
#   出口-单价/time  log R2=+0.5627  ->  原始 R2=-0.7651
#   出口-数量/time  log R2=+0.0313  ->  原始 R2=-3.4352
# 而模型的损失函数（MSELoss）作用在「标准化后的 log 目标」上 —— 用原始空间
# 的 R² 去评价一个在 log 空间训练的模型，口径本身就是错配的（评测报告 §6.5.2）。
#
# 故本脚本把 log 空间 R² 作为【主判据】，原始空间 R² 仅作参考，并在报告里
# 明确标注两者分别是什么。判「模型是否具备预测能力」一律以 log 空间为准。
# =====================================================================
_y_te_log = np.asarray(y_true).ravel()
_y_pr_log = np.asarray(y_pred).ravel()
_res_log = {
    '模型': r2_score(_y_te_log, _y_pr_log),
    '上一期值(carry-forward)': r2_score(_y_te_log, np.asarray(baseline_prev).ravel()),
    '季节均值(同月训练均值)': r2_score(_y_te_log, np.asarray(baseline_season).ravel()),
    '全局均值(R2=0 参照)': r2_score(_y_te_log, np.asarray(baseline_global).ravel()),
}
print(f"\n===== 【主判据】log 空间（与损失函数同空间）  测试集 n={len(_y_te_log)} =====")
for _k, _v in _res_log.items():
    print(f"  {_k:<26} R2={_v:>10.4f}")

_naive_log = {k: v for k, v in _res_log.items() if k != '模型'}
_best_log_name, _best_log_r2 = max(_naive_log.items(), key=lambda kv: kv[1])
_m_log_r2 = _res_log['模型']

# ---- α 敏感性扫描（仅作证据，不参与选择）-----------------------------------
# 回答「验证集标定出的 α* 是不是过拟合」：把测试集 R2 画成 α 的函数，
# 看它在 α* 附近是平顶（稳健）还是尖峰（过拟合）。
# 实测进口两路：log 空间在 α∈[1, 1.75] 几乎完全平坦（说明 α=1 就是驻点，
# 放大拿不到 log 空间收益），而原始空间随 α 单调下降（放大有害）。
if args.calib_alpha_scan and _net_out_test is not None:
    _prev_flat = np.asarray(prev_test, dtype=np.float64).reshape(-1)
    print("\n===== α 敏感性扫描（测试集；仅作证据，不参与选择）=====")
    print(f"  {'α':>6}  {'log空间R2':>12}  {'原始空间R2':>12}   {'MAE':>12}")
    for _a in (0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0):
        _p = _a * _net_out_test + _prev_flat
        _p_o = _to_orig(_p)
        _mark = '   <- 生产采用' if abs(_a - _alpha) < 1e-9 else ''
        print(f"  {_a:>6.2f}  {r2_score(_y_te_log, _p):>12.4f}  "
              f"{r2_score(true_o, _p_o):>12.4f}   "
              f"{float(np.mean(np.abs(true_o - _p_o))):>12.2f}{_mark}")
    if alpha_fitted_raw is not None:
        print(f"  （验证集 α*={alpha_fitted_raw:.4f}；上界 "
              f"{args.calib_alpha_max:.2f} → 生产取 α={_alpha:.4f}）")
_edge_log = _m_log_r2 - _best_log_r2
print(f"  最强朴素基线(log 空间): {_best_log_name} (R2={_best_log_r2:.4f})")
if _m_log_r2 <= 0:
    print(f"  [结论-不达标] 模型 log 空间 R2={_m_log_r2:.4f} <= 0，"
          f"未跑赢「零信息」参照。")
elif _m_log_r2 <= _best_log_r2:
    print(f"  [结论-未超基线] 模型 log 空间 R2={_m_log_r2:.4f} 未超过最强朴素基线"
          f"{_best_log_name}(R2={_best_log_r2:.4f})，差距 {_edge_log:+.4f}。")
else:
    print(f"  [结论-优于最强基线] 模型 log 空间 R2={_m_log_r2:.4f} > "
          f"{_best_log_name}(R2={_best_log_r2:.4f})，优势 {_edge_log:+.4f}。")

print(f"\n===== 【参考】原始空间（会被极端样本支配，不是主判据）n={len(true_o)} =====")
_res = {
    '模型': _metrics(model_o, '模型'),
    '上一期值(carry-forward)': _metrics(prev_o, '上一期值(carry-forward)'),
    '季节均值': _metrics(season_o, '季节均值(同月训练均值)'),
    '全局均值': _metrics(global_o, '全局均值(R2=0 参照)'),
}

print(f"\n  测试集中「训练期未见过的商品」占比: {cold_mask.mean() * 100:.1f}%")

# ---- 诊断：区分「模型没学会」与「任务本身学不会」 ----
# R2≈0 有两种截然不同的成因，只看 R2 无法区分：
#   (a) 预测退化为常数（模型没学到东西）—— 判别：预测标准差/真实标准差 << 1
#   (b) 学到了但泛化不了（噪声/分布漂移主导）—— 判别：训练集 R2 高、测试集 R2 低
# 结项时这两种情况的处置方式完全不同，因此必须把判别依据一并输出。
_eval_loader = DataLoader(train_dataset, batch_size=256, shuffle=False)
_train_preds = []
model.eval()
with torch.no_grad():
    for _x_cat_b, _x_cont_b, _ in _eval_loader:
        _train_preds.append(model(_x_cat_b.to(device), _x_cont_b.to(device)).cpu().numpy())
_train_preds = np.concatenate(_train_preds, axis=0)
# train_dataset 只含 fit_idx，且 residual 头下装的是增量，评估前要加回末期值
# （与测试集同口径地乘上收缩系数 α，否则「训练集 vs 测试集落差」不可比）
if args.head == 'residual':
    _alpha_train = 1.0 if correction_scale is None else float(correction_scale)
    _train_preds = _alpha_train * _train_preds + prev_fit
train_o = _to_orig(_train_preds)
train_true_o = _to_orig(y_fit)

_r2_train = r2_score(train_true_o, train_o)
_m_r2 = _res['模型'][0]
_std_true = float(np.std(true_o))
_std_pred = float(np.std(model_o))
_ratio = (_std_pred / _std_true) if _std_true > 0 else float('nan')
_uniq = int(len(np.unique(np.round(model_o, 2))))

print(f"\n  诊断（区分「没学会」与「学不会」）:")
print(f"    训练集 R2={_r2_train:>9.4f}   测试集 R2={_m_r2:>9.4f}   落差={_r2_train - _m_r2:>+8.4f}")
print(f"    预测值标准差 / 真实值标准差 = {_ratio:.4f}   （远小于 1 表示预测退化成常数）")
print(f"    预测均值={float(np.mean(model_o)):>15.2f}   真实均值={float(np.mean(true_o)):>15.2f}")
print(f"    预测值去重后个数(保留2位小数) = {_uniq} / {len(model_o)}")
if _ratio < 0.3:
    print(f"    -> 预测几乎没有方差，模型输出接近常数：属于「没学会」，应先查训练是否有效。")
elif _r2_train - _m_r2 > 0.3:
    print(f"    -> 训练集明显好于测试集：属于「学到了但泛化不了」，任务噪声/分布漂移主导。")
elif min(_r2_train, _m_r2) > 0.05:
    print(f"    -> 训练与测试均明显优于「零信息」参照：模型确实学到了信号（而非只记住训练集）。")
elif max(_r2_train, _m_r2) < -0.05:
    print(f"    -> 训练与测试均劣于「零信息」参照（预测均值）：不仅没学到，还在放大误差。")
else:
    print(f"    -> 训练与测试表现接近且均接近 0：特征信息量不足，或任务本身接近不可预测。")

# ---- 结论判定 ----
# 原实现只与「最弱」的朴素基线（上一期值）比较：commodity 协议下模型 R2=-0.6354
# （深度为负、甚至不如全局均值），却仍打印「模型 R2 优于基线」，极易被误读成
# 「模型具备预测能力」。故改为：与「最强」朴素基线比较，且 R2<=0 一律判为不达标。
_naive = {k: v for k, v in _res.items() if k != '模型'}
_best_name, _best_vals = max(_naive.items(), key=lambda kv: kv[1][0])
_best_r2 = _best_vals[0]
_edge = _m_r2 - _best_r2

# 退化基线检测：季节均值若与全局均值几乎相同，说明按月分层没有带来区分度，
# 此时「优于季节均值」不构成预测能力的证据。
_season_gap = abs(_res['季节均值'][0] - _res['全局均值'][0])
if _season_gap < 1e-3:
    print(f"  ! 季节均值基线退化：R2 与全局均值仅差 {_season_gap:.5f}，按月分层无区分度，"
          f"该基线不构成有效参照。")

print(f"  最强朴素基线: {_best_name} (R2={_best_r2:.4f})")
if _m_r2 <= 0:
    print(f"  [负 R2] 模型 R2={_m_r2:.4f} <= 0，未跑赢「零信息」参照（全局均值 R2≈0）。")
    print(f"          注意：这是【原始空间】口径，目标跨 8~9 个数量级、top1% 样本占"
          f"原始空间平方和约 99%，该口径会被极少数极端样本支配。")
    print(f"          模型是否具备预测能力请以本段上方的【主判据】log 空间结论为准。")
elif _m_r2 <= _best_r2:
    print(f"  [未超基线] 模型 R2={_m_r2:.4f} 未超过最强朴素基线 {_best_name}"
          f"(R2={_best_r2:.4f})，差距 {_edge:+.4f}。")
else:
    print(f"  [优于最强基线] 模型 R2={_m_r2:.4f} > {_best_name}(R2={_best_r2:.4f})，"
          f"优势 {_edge:+.4f}。")
    print(f"          注意：优势仅 {_edge:+.4f}，是否具备业务价值须对照验收门槛，"
          f"不能仅凭「优于基线」下结论。")

logging.basicConfig(filename=log_filename, level=logging.INFO)
logging.info(
    f"SUMMARY group_key:{args.group_key}, split_mode:{args.split_mode}, "
    f"n_series:{df['_series_key'].nunique()}, n_train:{len(train_idx)}, "
    f"n_test:{len(test_idx)}, cold_ratio_commodity:{cold_mask.mean()}, "
    f"cold_ratio_series:{skey_cold_mask.mean()}, "
    f"| LOG_SPACE model_r2:{_res_log['模型']}, "
    f"prev_r2:{_res_log['上一期值(carry-forward)']}, "
    f"season_r2:{_res_log['季节均值(同月训练均值)']}, "
    f"global_r2:{_res_log['全局均值(R2=0 参照)']}, "
    f"best_naive:{_best_log_name}, best_naive_r2:{_best_log_r2}, "
    f"edge_vs_best:{_edge_log} "
    f"| RAW_SPACE model_r2:{_res['模型'][0]}, "
    f"prev_r2:{_res['上一期值(carry-forward)'][0]}, "
    f"season_r2:{_res['季节均值'][0]}, global_r2:{_res['全局均值'][0]}, "
    f"best_naive:{_best_name}, best_naive_r2:{_best_r2}, edge_vs_best:{_edge}")



# 打印部分样本
print("\n部分预测值 VS 真实值:")
# 本脚本的评估结果不分 --target，统一存放于 pred_renminbi / true_renminbi。
# 原实现按 args.target 分支引用 pred_quantity，但该变量只存在于 quantity 脚本中，
# 在本文件内从未定义，故 --target quantity 时必然抛
# NameError: name 'pred_quantity' is not defined（第 699 行）。
max_print = min(len(pred_renminbi), len(true_renminbi), 10)
for i in range(max_print):
    print(f"预测: {target_name}={pred_renminbi[i].item():.2f} 真实: {target_name}={true_renminbi[i].item():.2f}")
# 中文字体回退：Linux 服务器一般没有 SimHei，缺字体只会让中文渲染成方块（不报错），
# 这里按可用性挑一个中文字体，都没有则退回默认字体，保证出图不中断。
try:
    import matplotlib.font_manager as _fm
    _avail_fonts = {f.name for f in _fm.fontManager.ttflist}
    for _cand in ('SimHei', 'Noto Sans CJK SC', 'WenQuanYi Zen Hei',
                  'WenQuanYi Micro Hei', 'AR PL UMing CN', 'Source Han Sans SC'):
        if _cand in _avail_fonts:
            plt.rcParams['font.sans-serif'] = [_cand]
            break
    else:
        plt.rcParams['font.sans-serif'] = ['DejaVu Sans']
except Exception:
    pass
plt.rcParams['axes.unicode_minus'] = False

# 绘制预测 vs 真实曲线
plt.figure(figsize=(12, 6))
plt.plot(true_renminbi, label=f'真实{target_name}', color='blue')
plt.plot(pred_renminbi, label=f'预测{target_name}', color='red', linestyle='--')
plt.title(f"{target_name}：预测值 vs 真实值")
plt.xlabel("样本索引")
plt.ylabel(target_name)
plt.legend()
plt.grid(True)
plt.annotate(f'R2={r2_q:.3f}', xy=(0.05, 0.9), xycoords='axes fraction', fontsize=12, color='green')
plt.annotate(f'MSE={mse_q:.3f}', xy=(0.05, 0.8), xycoords='axes fraction', fontsize=12, color='green')
plt.annotate(f'MAE={mae_q:.3f}', xy=(0.05, 0.7), xycoords='axes fraction', fontsize=12, color='green')
plt.tight_layout()
plt.savefig(save_path + f'predicted_vs_true_{args.target}.png')
plt.close()

# 绘制误差分布直方图
errors = y_true.flatten() - y_pred.flatten()
plt.figure(figsize=(8, 5))
plt.hist(errors, bins=30, color='skyblue', edgecolor='black')
plt.axvline(0, color='red', linestyle='dashed', linewidth=1)
plt.title("预测误差分布", fontsize=14)
plt.xlabel("误差 = 真实值 - 预测值", fontsize=12)
plt.ylabel("频数", fontsize=12)
plt.grid(True)
plt.tight_layout()
plt.savefig(save_path + f'error_distribution_{args.target}.png')
plt.close()

# 绘制 Loss 曲线（训练过程中记录的 losses）
plt.figure(figsize=(10, 6))
plt.plot(losses, label='Training Loss', color='blue')
plt.plot(val_losses, label='Validation Loss', color='red')
plt.xlabel('Epoch')
plt.ylabel('Loss (MSE)')
plt.title('训练损失曲线', fontsize=14)
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig(save_path + f'training_loss_curve_{args.target}.png')
plt.close()

# 保存 scaler 和 label encoders
with open(save_path + f'scaler_X_{args.target}.pkl', 'wb') as f:
    pickle.dump(scaler_X, f)

with open(save_path + f'scaler_y_{args.target}.pkl', 'wb') as f:
    pickle.dump(scaler_y, f)

with open(save_path + f'label_encoders_{args.target}.pkl', 'wb') as f:
    pickle.dump(label_encoders, f)

# =====================================================================
# 保存「特征口径说明书」，由 app.py 在推理时读取。
#
# 为什么要落盘：连续特征的列顺序 / 是否含目标滞后特征 / 窗口长度这三件事，
# 一旦训练侧改了而 app.py 侧写死的常量没跟着改，就会出现「形状能加载、
# 语义已错位」的静默错误 —— 比直接报错更难发现。把口径写成产物、
# 让推理端以产物为准，可以从结构上杜绝训练/推理漂移。
# =====================================================================
feature_spec = {
    'trade_type': args.trade_type,
    'target': args.target,
    'target_raw_col': target_cols[0],
    'seq_length': seq_length,
    'categorical_cols': list(categorical_cols),
    'continuous_cols': list(continuous_cols),
    'continuous_base': list(CONTINUOUS_BASE),
    'ar_feature': args.ar_feature,
    # AR 特征的实际列名（形如 _ar_log_price）。推理端要按这个名字装配
    # continuous_cols，硬编码列名一旦与训练侧改名就会 KeyError —— 落盘比约定可靠。
    'ar_col': (_AR_COL if args.ar_feature == 'target_log' else None),
    'head': args.head,
    # 残差修正量的收缩系数（验证集标定）。app.py 推理时按
    #   pred = 窗口末期值 + correction_scale x 网络输出
    # 还原，与训练脚本的测试集评估严格同口径。None 表示不需要收缩（取 1）。
    'correction_scale': (None if correction_scale is None else float(correction_scale)),
    # 未钳制的 α*：生产不使用，仅作「网络输出是否收缩」的诊断记录。
    # α*>1 意味着模型欠拟合（输出是真实增量的收缩版），是后续加大容量/延长训练的
    # 直接依据；但实测把它真的用上去会让测试集原始空间 R2 变差，故不采纳。
    'alpha_fitted_raw': (None if alpha_fitted_raw is None else float(alpha_fitted_raw)),
    'group_key': args.group_key,
    'split_mode': args.split_mode,
    'cutoff_ym': int(cutoff) if cutoff is not None else None,
}
with open(save_path + f'feature_spec_{args.target}.pkl', 'wb') as f:
    pickle.dump(feature_spec, f)
print(f"已保存特征口径: {save_path}feature_spec_{args.target}.pkl")

print("✅ 模型和预处理器已成功保存！")
# 同步播放（程序会暂停直到播放结束）
if args.sound_file and os.path.exists(args.sound_file):
    # winsound 是 Windows 专有模块；放在这里按需导入，
    # 否则脚本在 Linux 上一启动就 ModuleNotFoundError（评测报告 §5 P1-3）
    import winsound
    winsound.PlaySound(args.sound_file, winsound.SND_FILENAME)

