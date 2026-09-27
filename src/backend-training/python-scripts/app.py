import argparse
import os
import pickle
from datetime import datetime

import pandas as pd
import pymysql
from flask import Flask, request, jsonify
import torch
import torch.nn as nn
import numpy as np
from sklearn.preprocessing import StandardScaler

app = Flask(__name__)

# 加载模型参数
# 进口
# 单价
IN_PRICE_MODEL_PATH = 'model/trade_Transformer/in/model_price.pth'
IN_PRICE_SCALER_X_PATH = 'model/trade_Transformer/in/scaler_X_price.pkl'
IN_PRICE_SCALER_Y_PATH = 'model/trade_Transformer/in/scaler_y_price.pkl'
IN_PRICE_LABEL_ENCODERS_PATH = 'model/trade_Transformer/in/label_encoders_price.pkl'

# 数量
IN_QUANTITY_MODEL_PATH = 'model/trade_Transformer/in/model_quantity.pth'
IN_QUANTITY_SCALER_X_PATH = 'model/trade_Transformer/in/scaler_X_quantity.pkl'
IN_QUANTITY_SCALER_Y_PATH = 'model/trade_Transformer/in/scaler_y_quantity.pkl'
IN_QUANTITY_LABEL_ENCODERS_PATH = 'model/trade_Transformer/in/label_encoders_quantity.pkl'

# 出口
# 单价
OUT_PRICE_MODEL_PATH = 'model/trade_Transformer/out/model_price.pth'
OUT_PRICE_SCALER_X_PATH = 'model/trade_Transformer/out/scaler_X_price.pkl'
OUT_PRICE_SCALER_Y_PATH = 'model/trade_Transformer/out/scaler_y_price.pkl'
OUT_PRICE_LABEL_ENCODERS_PATH = 'model/trade_Transformer/out/label_encoders_price.pkl'

# 数量
OUT_QUANTITY_MODEL_PATH = 'model/trade_Transformer/out/model_quantity.pth'
OUT_QUANTITY_SCALER_X_PATH = 'model/trade_Transformer/out/scaler_X_quantity.pkl'
OUT_QUANTITY_SCALER_Y_PATH = 'model/trade_Transformer/out/scaler_y_quantity.pkl'
OUT_QUANTITY_LABEL_ENCODERS_PATH = 'model/trade_Transformer/out/label_encoders_quantity.pkl'

# 定义类别特征列和连续特征列（必须与训练时一致）
categorical_cols = ['贸易伙伴编码', '商品编码', '贸易方式编码', '注册地编码', '计量单位']
continuous_cols = ['year', 'month', '数量', '人民币']
seq_length = 2  # 时间序列长度，必须与训练时一致

# 加载 scaler 和 label encoders
with open(IN_PRICE_SCALER_X_PATH, 'rb') as f:
    in_price_scaler_X = pickle.load(f)
with open(IN_PRICE_SCALER_Y_PATH, 'rb') as f:
    in_price_scaler_y = pickle.load(f)
with open(IN_PRICE_LABEL_ENCODERS_PATH, 'rb') as f:
    in_price_label_encoders = pickle.load(f)

with open(IN_QUANTITY_SCALER_X_PATH, 'rb') as f:
    in_quantity_scaler_X = pickle.load(f)
with open(IN_QUANTITY_SCALER_Y_PATH, 'rb') as f:
    in_quantity_scaler_y = pickle.load(f)
with open(IN_QUANTITY_LABEL_ENCODERS_PATH, 'rb') as f:
    in_quantity_label_encoders = pickle.load(f)

with open(OUT_PRICE_SCALER_X_PATH, 'rb') as f:
    out_price_scaler_X = pickle.load(f)
with open(OUT_PRICE_SCALER_Y_PATH, 'rb') as f:
    out_price_scaler_y = pickle.load(f)
with open(OUT_PRICE_LABEL_ENCODERS_PATH, 'rb') as f:
    out_price_label_encoders = pickle.load(f)

with open(OUT_QUANTITY_SCALER_X_PATH, 'rb') as f:
    out_quantity_scaler_X = pickle.load(f)
with open(OUT_QUANTITY_SCALER_Y_PATH, 'rb') as f:
    out_quantity_scaler_y = pickle.load(f)
with open(OUT_QUANTITY_LABEL_ENCODERS_PATH, 'rb') as f:
    out_quantity_label_encoders = pickle.load(f)

# 定义模型结构（必须与训练时一致）
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


# 实例化模型并加载权重
num_embeddings_list_in_price = [in_price_label_encoders[col].classes_.shape[0] for col in categorical_cols]
model_in_price = LSTMTransformer(
    num_embeddings_list=num_embeddings_list_in_price,
    continuous_dim=len(continuous_cols),
    model_dim=128,
    hidden_size=128,
    num_heads=16,
    num_layers=5,
    dropout=0.6
)
model_in_price.load_state_dict(torch.load(IN_PRICE_MODEL_PATH, map_location='cpu'))
model_in_price.eval()

num_embeddings_list_in_quantity = [in_quantity_label_encoders[col].classes_.shape[0] for col in categorical_cols]
model_in_quantity = LSTMTransformer(
    num_embeddings_list=num_embeddings_list_in_quantity,
    continuous_dim=len(continuous_cols),
    model_dim=128,  # 输入维度大小
    hidden_size=128,  # 隐藏层大小
    num_heads=16,  # 注意力头数
    num_layers=5,  # Transformer层的数量
    dropout=0.6
)
model_in_quantity.load_state_dict(torch.load(IN_QUANTITY_MODEL_PATH, map_location='cpu'))
model_in_quantity.eval()

num_embeddings_list_out_price = [out_price_label_encoders[col].classes_.shape[0] for col in categorical_cols]
model_out_price = LSTMTransformer(
    num_embeddings_list=num_embeddings_list_out_price,
    continuous_dim=len(continuous_cols),
    model_dim=128,
    hidden_size=128,
    num_heads=16,
    num_layers=5,
    dropout=0.6
)
model_out_price.load_state_dict(torch.load(OUT_PRICE_MODEL_PATH, map_location='cpu'))
model_out_price.eval()

num_embeddings_list_out_quantity = [out_quantity_label_encoders[col].classes_.shape[0] for col in categorical_cols]
model_out_quantity = LSTMTransformer(
    num_embeddings_list=num_embeddings_list_out_quantity,
    continuous_dim=len(continuous_cols),
    model_dim=128,  # 输入维度大小
    hidden_size=128,  # 隐藏层大小
    num_heads=16,  # 注意力头数
    num_layers=5,  # Transformer层的数量
    dropout=0.6
)
model_out_quantity.load_state_dict(torch.load(OUT_QUANTITY_MODEL_PATH, map_location='cpu'))
model_out_quantity.eval()

# 数据库连接配置（凭据统一见项目根 config/.env，该文件不入库；详见 config/README.md）
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

def db_process(type):
    request_data = request.get_json(force=True)
    friend = request_data.get('贸易伙伴名称')
    good = request_data.get('商品名称')
    trade = request_data.get('贸易方式')
    register = request_data.get('注册地名称')
    year = request_data.get('year')
    month = request_data.get('month')

    # 构建目标日期
    try:
        target_date = datetime(year=int(year), month=int(month), day=1).strftime('%Y-%m-%d')
    except ValueError:
        return jsonify({'error': '无效的年/月'}), 400

    # SQL 查询语句
    if type == "in":
        query = """
                SELECT * 
                FROM trade_in 
                WHERE 贸易伙伴名称 = %s
                  AND 商品名称 = %s
                  AND 贸易方式名称 = %s
                  AND 注册地名称 = %s
                  AND 数据年月 < %s
                ORDER BY 数据年月 DESC
                LIMIT 12
            """
    elif type == "out":
        query = """
                SELECT * 
                FROM trade_out 
                WHERE 贸易伙伴名称 = %s
                  AND 商品名称 = %s
                  AND 贸易方式名称 = %s
                  AND 注册地名称 = %s
                  AND 数据年月 < %s
                ORDER BY 数据年月 DESC
                LIMIT 12
            """

    # 连接数据库并执行查询
    connection = pymysql.connect(**db_config)
    try:
        with connection.cursor() as cursor:
            cursor.execute(query, (friend, good, trade, register, target_date))
            result = cursor.fetchall()
            columns = [desc[0] for desc in cursor.description]
    finally:
        connection.close()
    return result, columns


@app.route('/predict_in_price', methods=['POST'])
def predict_in_price():
    warning_msg = None
    result, columns = db_process("in")
    # 转换为 DataFrame
    df = pd.DataFrame(result, columns=columns)

    if len(df) < seq_length:
        warning_msg = "历史数据不足12条，预测结果准确度受影响"
        needed_rows = seq_length - len(df)
        # 复制数据补齐到seq_length条
        df = pd.concat([df] * ((seq_length // len(df)) + 1), ignore_index=True).head(seq_length)

    # 先提取"数据年月"字段，并做预处理
    if '数据年月' not in df.columns:
        return jsonify({'error': '缺少 数据年月 字段'}), 400

    # 确保 '数据年月' 列是字符串类型
    df['数据年月'] = df['数据年月'].astype(str)
    # 如果数据是 "YYYY-MM-DD" 格式但含 day=0
    df['数据年月'] = df['数据年月'].str.replace('-00', '-01', regex=False)

    # 转换为 datetime，无法解析的设为 NaT
    df['数据年月'] = pd.to_datetime(df['数据年月'], errors='coerce')

    # 检查是否有无效日期
    invalid_dates = df[df['数据年月'].isna()]
    if not invalid_dates.empty:
        return jsonify({
            'error': f'存在无法解析的日期，请检查以下记录:\n{invalid_dates.head().to_dict()}'
        }), 400

    # 提取 year/month
    df['year'] = df['数据年月'].dt.year.astype(int)
    df['month'] = df['数据年月'].dt.month.astype(int)

    # 检查类别特征列是否存在
    missing_cols = [col for col in categorical_cols if col not in df.columns]
    if missing_cols:
        return jsonify({'error': f'缺失以下类别特征列: {", ".join(missing_cols)}'}), 400

    processed_rows = []

    for _, row in df.iterrows():
        sample_df = pd.DataFrame([row])

        # 类别特征编码
        for col in categorical_cols:
            le = in_price_label_encoders[col]
            sample_df[col] = le.transform(sample_df[col].astype(str))

        # 连续特征标准化
        sample_cont = sample_df[continuous_cols].copy()
        sample_cont_scaled = in_price_scaler_X.transform(sample_cont)

        # 保存处理后的结果
        processed_rows.append({
            'cat': sample_df[categorical_cols].values[0],
            'cont': sample_cont_scaled[0]
        })

    # 构造 tensor 输入
    cat_array = np.array([r['cat'] for r in processed_rows])
    cont_array = np.array([r['cont'] for r in processed_rows])

    cat_tensor = torch.tensor(cat_array, dtype=torch.long).unsqueeze(0)
    cont_tensor = torch.tensor(cont_array, dtype=torch.float32).unsqueeze(0)

    # 模型预测
    with torch.no_grad():
        output = model_in_price(cat_tensor, cont_tensor)

    # 反变换
    pred_unscaled = in_price_scaler_y.inverse_transform(output.numpy())
    pred_original = np.expm1(pred_unscaled)  # 如果用了 log1p

    unit = '人民币/' + df['计量单位'][0]
    response = {
        'predicted_in_price': float(pred_original[0][0]),
        'unit': unit
    }
    if warning_msg is not None:
        response['warning'] = warning_msg

    return jsonify(response)

@app.route('/predict_in_quantity', methods=['POST'])
def predict_in_quantity():
    warning_msg = None
    result, columns = db_process("in")
    # 转换为 DataFrame
    df = pd.DataFrame(result, columns=columns)

    if len(df) < 12:
        warning_msg = "历史数据不足12条，预测结果准确度受影响"
        needed_rows = 12 - len(df)
        # 复制数据补齐到12条
        df = pd.concat([df] * ((12 // len(df)) + 1), ignore_index=True).head(12)

    # 先提取"数据年月"字段，并做预处理
    if '数据年月' not in df.columns:
        return jsonify({'error': '缺少 数据年月 字段'}), 400

    # 确保 '数据年月' 列是字符串类型
    df['数据年月'] = df['数据年月'].astype(str)
    # 如果数据是 "YYYY-MM-DD" 格式但含 day=0
    df['数据年月'] = df['数据年月'].str.replace('-00', '-01', regex=False)

    # 转换为 datetime，无法解析的设为 NaT
    df['数据年月'] = pd.to_datetime(df['数据年月'], errors='coerce')

    # 检查是否有无效日期
    invalid_dates = df[df['数据年月'].isna()]
    if not invalid_dates.empty:
        return jsonify({
            'error': f'存在无法解析的日期，请检查以下记录:\n{invalid_dates.head().to_dict()}'
        }), 400

    # 提取 year/month
    df['year'] = df['数据年月'].dt.year.astype(int)
    df['month'] = df['数据年月'].dt.month.astype(int)

    # 检查类别特征列是否存在
    missing_cols = [col for col in categorical_cols if col not in df.columns]
    if missing_cols:
        return jsonify({'error': f'缺失以下类别特征列: {", ".join(missing_cols)}'}), 400

    processed_rows = []

    for _, row in df.iterrows():
        sample_df = pd.DataFrame([row])

        # 类别特征编码
        for col in categorical_cols:
            le = in_quantity_label_encoders[col]
            sample_df[col] = le.transform(sample_df[col].astype(str))

        # 连续特征标准化
        sample_cont = sample_df[continuous_cols].copy()
        sample_cont_scaled = in_quantity_scaler_X.transform(sample_cont)

        # 保存处理后的结果
        processed_rows.append({
            'cat': sample_df[categorical_cols].values[0],
            'cont': sample_cont_scaled[0]
        })

    # 构造 tensor 输入
    cat_array = np.array([r['cat'] for r in processed_rows])
    cont_array = np.array([r['cont'] for r in processed_rows])

    cat_tensor = torch.tensor(cat_array, dtype=torch.long).unsqueeze(0)
    cont_tensor = torch.tensor(cont_array, dtype=torch.float32).unsqueeze(0)

    # 模型预测
    with torch.no_grad():
        output = model_in_quantity(cat_tensor, cont_tensor)

    # 反变换
    pred_unscaled = in_quantity_scaler_y.inverse_transform(output.numpy())
    pred_original = np.expm1(pred_unscaled)  # 如果用了 log1p

    response = {
        'predicted_in_quantity': float(pred_original[0][0]),
        'unit': df['计量单位'][0]
    }
    if warning_msg is not None:
        response['warning'] = warning_msg

    return jsonify(response)

@app.route('/predict_out_price', methods=['POST'])
def predict_out_price():
    warning_msg = None
    result, columns = db_process("out")
    # 转换为 DataFrame
    df = pd.DataFrame(result, columns=columns)

    if len(df) < 12:
        warning_msg = "历史数据不足12条，预测结果准确度受影响"
        needed_rows = 12 - len(df)
        # 复制数据补齐到12条
        df = pd.concat([df] * ((12 // len(df)) + 1), ignore_index=True).head(12)

    # 先提取"数据年月"字段，并做预处理
    if '数据年月' not in df.columns:
        return jsonify({'error': '缺少 数据年月 字段'}), 400

    # 确保 '数据年月' 列是字符串类型
    df['数据年月'] = df['数据年月'].astype(str)
    # 如果数据是 "YYYY-MM-DD" 格式但含 day=0
    df['数据年月'] = df['数据年月'].str.replace('-00', '-01', regex=False)

    # 转换为 datetime，无法解析的设为 NaT
    df['数据年月'] = pd.to_datetime(df['数据年月'], errors='coerce')

    # 检查是否有无效日期
    invalid_dates = df[df['数据年月'].isna()]
    if not invalid_dates.empty:
        return jsonify({
            'error': f'存在无法解析的日期，请检查以下记录:\n{invalid_dates.head().to_dict()}'
        }), 400

    # 提取 year/month
    df['year'] = df['数据年月'].dt.year.astype(int)
    df['month'] = df['数据年月'].dt.month.astype(int)

    # 检查类别特征列是否存在
    missing_cols = [col for col in categorical_cols if col not in df.columns]
    if missing_cols:
        return jsonify({'error': f'缺失以下类别特征列: {", ".join(missing_cols)}'}), 400

    processed_rows = []

    for _, row in df.iterrows():
        sample_df = pd.DataFrame([row])

        # 类别特征编码
        for col in categorical_cols:
            le = out_price_label_encoders[col]
            sample_df[col] = le.transform(sample_df[col].astype(str))

        # 连续特征标准化
        sample_cont = sample_df[continuous_cols].copy()
        sample_cont_scaled = out_price_scaler_X.transform(sample_cont)

        # 保存处理后的结果
        processed_rows.append({
            'cat': sample_df[categorical_cols].values[0],
            'cont': sample_cont_scaled[0]
        })

    # 构造 tensor 输入
    cat_array = np.array([r['cat'] for r in processed_rows])
    cont_array = np.array([r['cont'] for r in processed_rows])

    cat_tensor = torch.tensor(cat_array, dtype=torch.long).unsqueeze(0)
    cont_tensor = torch.tensor(cont_array, dtype=torch.float32).unsqueeze(0)

    # 模型预测
    with torch.no_grad():
        output = model_out_price(cat_tensor, cont_tensor)

    # 反变换
    pred_unscaled = out_price_scaler_y.inverse_transform(output.numpy())
    pred_original = np.expm1(pred_unscaled)  # 如果用了 log1p

    unit = '人民币/'+df['计量单位'][0]
    response = {
        'predicted_out_price': float(pred_original[0][0]),
        'unit': unit
    }
    if warning_msg is not None:
        response['warning'] = warning_msg

    return jsonify(response)

@app.route('/predict_out_quantity', methods=['POST'])
def predict_out_quantity():
    warning_msg = None
    result, columns = db_process("out")
    # 转换为 DataFrame
    df = pd.DataFrame(result, columns=columns)

    if len(df) < 12:
        warning_msg = "历史数据不足12条，预测结果准确度受影响"
        needed_rows = 12 - len(df)
        # 复制数据补齐到12条
        df = pd.concat([df] * ((12 // len(df)) + 1), ignore_index=True).head(12)


    # 先提取"数据年月"字段，并做预处理
    if '数据年月' not in df.columns:
        return jsonify({'error': '缺少 数据年月 字段'}), 400

    # 确保 '数据年月' 列是字符串类型
    df['数据年月'] = df['数据年月'].astype(str)
    # 如果数据是 "YYYY-MM-DD" 格式但含 day=0
    df['数据年月'] = df['数据年月'].str.replace('-00', '-01', regex=False)

    # 转换为 datetime，无法解析的设为 NaT
    df['数据年月'] = pd.to_datetime(df['数据年月'], errors='coerce')

    # 检查是否有无效日期
    invalid_dates = df[df['数据年月'].isna()]
    if not invalid_dates.empty:
        return jsonify({
            'error': f'存在无法解析的日期，请检查以下记录:\n{invalid_dates.head().to_dict()}'
        }), 400

    # 提取 year/month
    df['year'] = df['数据年月'].dt.year.astype(int)
    df['month'] = df['数据年月'].dt.month.astype(int)

    # 检查类别特征列是否存在
    missing_cols = [col for col in categorical_cols if col not in df.columns]
    if missing_cols:
        return jsonify({'error': f'缺失以下类别特征列: {", ".join(missing_cols)}'}), 400

    processed_rows = []

    for _, row in df.iterrows():
        sample_df = pd.DataFrame([row])

        # 类别特征编码
        for col in categorical_cols:
            le = out_quantity_label_encoders[col]
            sample_df[col] = le.transform(sample_df[col].astype(str))

        # 连续特征标准化
        sample_cont = sample_df[continuous_cols].copy()
        sample_cont_scaled = out_quantity_scaler_X.transform(sample_cont)

        # 保存处理后的结果
        processed_rows.append({
            'cat': sample_df[categorical_cols].values[0],
            'cont': sample_cont_scaled[0]
        })

    # 构造 tensor 输入
    cat_array = np.array([r['cat'] for r in processed_rows])
    cont_array = np.array([r['cont'] for r in processed_rows])

    cat_tensor = torch.tensor(cat_array, dtype=torch.long).unsqueeze(0)
    cont_tensor = torch.tensor(cont_array, dtype=torch.float32).unsqueeze(0)

    # 模型预测
    with torch.no_grad():
        output = model_out_quantity(cat_tensor, cont_tensor)

    # 反变换
    pred_unscaled = out_quantity_scaler_y.inverse_transform(output.numpy())
    pred_original = np.expm1(pred_unscaled)  # 如果用了 log1p

    response = {
        'predicted_out_quantity': float(pred_original[0][0]),
        'unit': df['计量单位'][0]
    }
    if warning_msg is not None:
        response['warning'] = warning_msg

    return jsonify(response)

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

