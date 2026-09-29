import argparse
import os
import pickle
import sys
from datetime import datetime

import pandas as pd
import pymysql
from flask import Flask, request, jsonify
import torch
import torch.nn as nn
import numpy as np

app = Flask(__name__)


class PredictInputError(Exception):
    """预测入参/数据缺失类错误。

    原先 db_process 直接 `return jsonify({...}), 400`，而调用方写的是
    `result, columns = db_process("in")` —— 会把 Flask 响应对象当成查询结果解包，
    错误路径反而变成更难排查的二次异常。改成抛异常 + 全局处理器后，
    任何入参问题都能稳定返回结构化的 4xx。
    """

    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


@app.errorhandler(PredictInputError)
def _handle_predict_input_error(e):
    return jsonify({'error': str(e)}), getattr(e, 'status', 400)


# =====================================================================
# 模型产物路径
# =====================================================================
_DIR_IN = 'model/trade_Transformer/in'
_DIR_OUT = 'model/trade_Transformer/out'

PATHS = {
    ('in', 'price'): dict(
        model=f'{_DIR_IN}/model_price.pth',
        scaler_x=f'{_DIR_IN}/scaler_X_price.pkl',
        scaler_y=f'{_DIR_IN}/scaler_y_price.pkl',
        encoders=f'{_DIR_IN}/label_encoders_price.pkl',
        spec=f'{_DIR_IN}/feature_spec_price.pkl',
    ),
    ('in', 'quantity'): dict(
        model=f'{_DIR_IN}/model_quantity.pth',
        scaler_x=f'{_DIR_IN}/scaler_X_quantity.pkl',
        scaler_y=f'{_DIR_IN}/scaler_y_quantity.pkl',
        encoders=f'{_DIR_IN}/label_encoders_quantity.pkl',
        spec=f'{_DIR_IN}/feature_spec_quantity.pkl',
    ),
    ('out', 'price'): dict(
        model=f'{_DIR_OUT}/model_price.pth',
        scaler_x=f'{_DIR_OUT}/scaler_X_price.pkl',
        scaler_y=f'{_DIR_OUT}/scaler_y_price.pkl',
        encoders=f'{_DIR_OUT}/label_encoders_price.pkl',
        spec=f'{_DIR_OUT}/feature_spec_price.pkl',
    ),
    ('out', 'quantity'): dict(
        model=f'{_DIR_OUT}/model_quantity.pth',
        scaler_x=f'{_DIR_OUT}/scaler_X_quantity.pkl',
        scaler_y=f'{_DIR_OUT}/scaler_y_quantity.pkl',
        encoders=f'{_DIR_OUT}/label_encoders_quantity.pkl',
        spec=f'{_DIR_OUT}/feature_spec_quantity.pkl',
    ),
}

# 历史窗口与字段口径的兜底默认值。
#
# 正常路径下这些值全部来自训练脚本落盘的 feature_spec_{target}.pkl —— 训练侧
# 一旦改了列顺序/是否含目标滞后特征/窗口长度，评测口径随之变化，app.py 必须
# 跟着变。写死常量会造成「形状能加载、语义已错位」的静默错误，比报错更难查。
# 兜底值仅用于兼容未重新训练过的旧产物目录。
_LEGACY_SPEC = {
    'seq_length': 2,
    'categorical_cols': ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码', '计量单位'],
    'continuous_cols': ['year', 'month', '数量', '人民币'],
    'target_raw_col': '单价',
    'ar_feature': 'none',
}

# 每 12 个月取一次，保证「最近 seq_length 个不同月份」都有机会被取到。
_HISTORY_LIMIT = 12


# =====================================================================
# 模型结构（必须与训练时一致）
# =====================================================================
class LearnablePositionalEncoding(nn.Module):
    def __init__(self, d_model, max_len=5000):
        super().__init__()
        self.position_embeddings = nn.Embedding(max_len, d_model)

    def forward(self, x):
        positions = torch.arange(0, x.size(1), device=x.device).expand(x.size(0), -1)
        x = x + self.position_embeddings(positions)
        return x


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


def _load_pickle(path):
    with open(path, 'rb') as f:
        return pickle.load(f)


def _load_bundle(key):
    """加载某个 (贸易类型, 目标) 组合的全部产物。

    <p>Embedding 行数不靠猜，而是**直接从模型权重形状反推**：

    · 训练脚本把「训练期未见过的类别」映射到预留槽位 `len(le.classes_)`，
      因此新产物的行数是 `len(classes_)+1`；
    · 更早的产物用 `max(df[col])+1` 建模，训练集恰好覆盖全部类别时行数退化为
      `len(classes_)`，没有预留槽位。

    若在推理端用固定公式构造模型，就必然与其中一种产物不匹配（实测直接抛
    `RuntimeError: Error(s) in loading state_dict ... size mismatch`）。
    从 `cat_embeddings.{i}.weight` 的 shape 读取行数、从 `cont_proj.weight`
    读取连续特征维度，才是与产物自身严格自洽的取法。
    """
    p = PATHS[key]
    try:
        spec = _load_pickle(p['spec'])
    except FileNotFoundError:
        print(f"[WARN] 未找到 {p['spec']}，回退到内置默认口径 {_LEGACY_SPEC}",
              file=sys.stderr)
        spec = dict(_LEGACY_SPEC)

    encoders = _load_pickle(p['encoders'])
    state = torch.load(p['model'], map_location='cpu')

    vocab_sizes = []
    i = 0
    while f'cat_embeddings.{i}.weight' in state:
        vocab_sizes.append(int(state[f'cat_embeddings.{i}.weight'].shape[0]))
        i += 1
    if len(vocab_sizes) != len(spec['categorical_cols']):
        raise RuntimeError(
            f"{key}: 模型内有 {len(vocab_sizes)} 个类别特征 Embedding，"
            f"而特征口径声明了 {len(spec['categorical_cols'])} 个，产物不一致")
    cont_dim = int(state['cont_proj.weight'].shape[1])
    if cont_dim != len(spec['continuous_cols']):
        raise RuntimeError(
            f"{key}: 模型连续特征维度 {cont_dim} 与口径声明 "
            f"{len(spec['continuous_cols'])} 不一致，产物不匹配")

    model = LSTMTransformer(
        num_embeddings_list=vocab_sizes,
        continuous_dim=cont_dim,
        model_dim=128,
        hidden_size=128,
        num_heads=16,
        num_layers=5,
        dropout=0.6,
    )
    model.load_state_dict(state)
    model.eval()

    return dict(
        model=model,
        encoders=encoders,
        spec=spec,
        vocab_sizes=vocab_sizes,
        scaler_x=_load_pickle(p['scaler_x']),
        scaler_y=_load_pickle(p['scaler_y']),
    )


BUNDLES = {k: _load_bundle(k) for k in PATHS}
for _k, _b in BUNDLES.items():
    _s = _b['spec']
    print(f"[INFO] {_k}: seq_length={_s['seq_length']} ar={_s.get('ar_feature')} "
          f"cont={len(_s['continuous_cols'])} cols={_s['continuous_cols']}")


# =====================================================================
# 数据库配置（凭据统一见项目根 config/.env，该文件不入库；详见 config/README.md）
# =====================================================================
def _load_trade_env():
    """向上查找 config/.env 并载入环境变量。

    已在环境中存在的变量不会被覆盖（服务器上用 systemd EnvironmentFile
    或 deploy/start-all.sh 注入时即为此情况）。找不到文件则静默跳过。
    """
    from pathlib import Path
    for parent in Path(__file__).resolve().parents:
        candidate = parent / 'config' / '.env'
        if candidate.is_file():
            for raw in candidate.read_text(encoding='utf-8').splitlines():
                line = raw.strip()
                if not line or line.startswith('#') or '=' not in line:
                    continue
                key, _, value = line.partition('=')
                key, value = key.strip(), value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
                    value = value[1:-1]
                if key and key not in os.environ:
                    os.environ[key] = value
            return


_load_trade_env()

db_config = {
    'host': os.getenv('TRAIN_DB_HOST', 'localhost'),
    'user': os.getenv('TRAIN_DB_USERNAME', 'root'),
    'password': os.getenv('TRAIN_DB_PASSWORD', ''),
    'database': os.getenv('TRAIN_DB_NAME', 'trade'),
    'charset': 'utf8mb4'
}


# =====================================================================
# 历史数据取数与特征构造
# =====================================================================
_SQL_TEMPLATE = """
    SELECT *
    FROM {table}
    WHERE 贸易伙伴名称 = %s
      AND 商品名称 = %s
      AND 贸易方式名称 = %s
      AND 注册地名称 = %s
      AND 数据年月 < %s
    ORDER BY 数据年月 DESC
    LIMIT {limit}
"""


def fetch_history(trade_type, spec):
    """按四键取出该组合在目标月份之前的历史，返回按「数据年月」升序的 DataFrame。

    取数口径必须与训练一致：训练按「贸易伙伴 x 商品 x 贸易方式 x 注册地」
    四键分组建序列（--group_key full），这里也按同样四条 WHERE 取历史。

    另外两个历史 bug：
    1. `数据年月` 是 INT 型 YYYYMM。原实现用
       `datetime(...).strftime('%Y-%m-%d')` 得到 '2026-01-01'，MySQL 比较时
       把字符串转成数字 2026，条件退化成 `数据年月 < 2026`，而库里的值形如
       201501，恒不成立 —— 一条都查不到，下游再因 len(df)==0 触发除零，
       最终表现为 /predict_* 一直 500。这里直接构造成 YYYYMM 整数。
    2. SQL 按 DESC 取最近 N 条，拿到后必须反转成升序，才与训练时
       「按 (年,月) 升序构序列」的方向一致。
    """
    if trade_type not in ('in', 'out'):
        raise PredictInputError(f'未知的贸易类型: {trade_type}')

    request_data = request.get_json(force=True)
    friend = request_data.get('贸易伙伴名称')
    good = request_data.get('商品名称')
    trade = request_data.get('贸易方式')
    register = request_data.get('注册地名称')
    year = request_data.get('year')
    month = request_data.get('month')

    try:
        year_i = int(year)
        month_i = int(month)
        if not (1 <= month_i <= 12):
            raise ValueError('月份超出范围')
        if not (1900 <= year_i <= 2999):
            raise ValueError('年份超出范围')
    except (TypeError, ValueError):
        raise PredictInputError('无效的年/月，需要同时提供 year 与 month（例如 year=2026, month=1）')

    target_date = year_i * 100 + month_i

    table = 'trade_in' if trade_type == 'in' else 'trade_out'
    query = _SQL_TEMPLATE.format(table=table, limit=_HISTORY_LIMIT)

    connection = pymysql.connect(**db_config)
    try:
        with connection.cursor() as cursor:
            cursor.execute(query, (friend, good, trade, register, target_date))
            result = cursor.fetchall()
            columns = [desc[0] for desc in cursor.description]
    finally:
        connection.close()

    if not columns:
        raise PredictInputError('查询未返回任何列，无法构造特征', 500)

    df = pd.DataFrame(result, columns=columns)
    if len(df) == 0:
        raise PredictInputError(
            '该「贸易伙伴 + 商品 + 贸易方式 + 注册地」组合在库中没有历史数据，无法预测。'
            '请改用库中存在的组合（例如 贸易伙伴=哈萨克斯坦、商品=其他未列名冻鱼、'
            '贸易方式=一般贸易、注册地=新疆维吾尔自治区）',
            404,
        )

    # 「数据年月」是 INT 型 YYYYMM，拆成 (year, month) 两个连续特征
    ym = pd.to_numeric(df['数据年月'], errors='coerce')
    if ym.isna().any():
        bad = df.loc[ym.isna(), '数据年月'].head().tolist()
        raise PredictInputError(f'存在无法解析的「数据年月」: {bad}')
    df['year'] = (ym // 100).astype(int)
    df['month'] = (ym % 100).astype(int)

    # 升序：最近的一期落在最后一行，与训练时的序列方向一致
    df = df.iloc[::-1].reset_index(drop=True)

    need = list(spec['categorical_cols'])
    base = spec.get('continuous_base', ['year', 'month', '数量', '人民币'])
    need += [c for c in base if c not in need]
    if spec.get('ar_feature') == 'target_log':
        need.append(spec['target_raw_col'])
    missing_cols = [c for c in need if c not in df.columns]
    if missing_cols:
        raise PredictInputError(f'缺失以下特征列: {", ".join(missing_cols)}')

    num_cols = [c for c in df.columns if c in set(base + [spec['target_raw_col']])]
    df[num_cols] = df[num_cols].apply(pd.to_numeric, errors='coerce')
    bad_cols = [c for c in num_cols if df[c].isna().any()]
    if bad_cols:
        raise PredictInputError(f'以下特征存在无法解析的值: {bad_cols}')

    return df


def encode_categorical(encoders, col, value, vocab_size):
    """类别值 -> 整数索引，与训练脚本的映射规则严格一致。

    训练脚本用 `_known.get(x, len(classes_))` 把未见类别映射到预留槽位；
    这里必须复现同一规则，**不能**直接调用 `LabelEncoder.transform` ——
    后者遇到未见类别会抛
        ValueError: y contains previously unseen labels
    这正是 /predict_* 曾经稳定 500 的直接原因：库中真实组合的「贸易伙伴编码」
    /「商品编码」几乎都不在那份只在 29 行样例上拟合的编码器里。

    `vocab_size` 为该列 Embedding 的实际行数（由模型权重反推）。旧产物没有预留
    槽位时 `vocab_size == len(classes_)`，此时把未见类别压回最后一个合法下标，
    否则会越界。
    """
    le = encoders[col]
    classes = le.classes_
    s = str(value)
    hit = np.where(classes == s)[0]
    if len(hit) > 0:
        return int(hit[0])
    return int(min(len(classes), max(vocab_size - 1, 0)))


def build_inputs(df, bundle):
    """由历史行构造模型输入张量。返回 (cat_tensor, cont_tensor, warning)。

    列顺序严格按 feature_spec['continuous_cols'] 组装 —— 这正是把口径落盘成
    产物的意义：只要顺序与训练时一致，scaler_X.transform 才是有意义的。
    """
    spec = bundle['spec']
    seq_length = spec['seq_length']
    warning = None

    if len(df) < seq_length:
        warning = (f"该组合仅有 {len(df)} 期历史，不足模型所需的 {seq_length} 期，"
                   f"已用最早一期补齐，预测结果仅供参考")
        pad = seq_length - len(df)
        df = pd.concat([df.iloc[[0]]] * pad + [df], ignore_index=True)

    d = df.tail(seq_length).reset_index(drop=True)

    # 目标自身的滞后值：窗口每一期的 log1p(目标)，与训练侧 _AR_COL 同口径。
    # 只用 t-seq_length..t-1 的值，不含待预测的 t 期，无泄漏。
    if spec.get('ar_feature') == 'target_log':
        tcol = spec['target_raw_col']
        d['_ar_log'] = np.log1p(d[tcol].astype(float))

    cat_cols = spec['categorical_cols']
    vocab = bundle['vocab_sizes']
    cat_rows = [[encode_categorical(bundle['encoders'], col, row[col], vocab[i])
                 for i, col in enumerate(cat_cols)]
                for _, row in d.iterrows()]

    cont = d[spec['continuous_cols']].astype(float).to_numpy()

    cat_tensor = torch.tensor(np.array(cat_rows, dtype=np.int64),
                              dtype=torch.long).unsqueeze(0)
    cont_tensor = torch.tensor(np.asarray(cont, dtype=np.float32),
                               dtype=torch.float32).unsqueeze(0)
    return cat_tensor, cont_tensor, warning


def run_predict(trade_type, target):
    """四个预测端点共用的实现（原先是四段几乎完全相同的复制粘贴代码）。"""
    bundle = BUNDLES[(trade_type, target)]
    spec = bundle['spec']

    df = fetch_history(trade_type, spec)
    cat_tensor, cont_tensor, warning = build_inputs(df, bundle)

    with torch.no_grad():
        output = bundle['model'](cat_tensor, cont_tensor)

    # 目标在训练期是 log1p 后再标准化，故反变换顺序为 inverse_transform -> expm1
    pred_unscaled = bundle['scaler_y'].inverse_transform(output.numpy())
    pred_original = float(np.expm1(pred_unscaled)[0][0])

    if not np.isfinite(pred_original):
        raise PredictInputError('模型输出非有限值，请检查模型产物与输入数据', 500)

    # 值域约束：训练目标是 log1p(clip(目标, lower=0))，其取值域为 [0, +inf)，
    # 因此 expm1 的结果理论上不应为负。实测重训早期（欠拟合）会输出很小的
    # 负值（如 -0.07），直接透出会变成「单价 = -0.07 元/千克」这种荒谬结果。
    # 这里按下界 0 截断，把训练目标的定义域作为硬约束施加回去。
    if pred_original < 0:
        pred_original = 0.0

    # 计量单位取最近一期（升序后的最后一行），与原实现 SQL 按 DESC 取 [0] 等价
    unit_raw = df['计量单位'].iloc[-1]
    unit = f'人民币/{unit_raw}' if target == 'price' else str(unit_raw)

    response = {f'predicted_{trade_type}_{target}': pred_original, 'unit': unit}
    if warning is not None:
        response['warning'] = warning
    return jsonify(response)


@app.route('/predict_in_price', methods=['POST'])
def predict_in_price():
    return run_predict('in', 'price')


@app.route('/predict_in_quantity', methods=['POST'])
def predict_in_quantity():
    return run_predict('in', 'quantity')


@app.route('/predict_out_price', methods=['POST'])
def predict_out_price():
    return run_predict('out', 'price')


@app.route('/predict_out_quantity', methods=['POST'])
def predict_out_quantity():
    return run_predict('out', 'quantity')


@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'timestamp': datetime.utcnow().isoformat()
    })


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Trade prediction Flask service')
    parser.add_argument('--host', type=str, default='0.0.0.0', help='Flask 绑定 host')
    parser.add_argument('--port', type=int, default=5000, help='Flask 监听端口')
    parser.add_argument('--debug', action='store_true', help='是否启用调试模式')
    args = parser.parse_args()
    app.run(host=args.host, port=args.port, debug=args.debug)
