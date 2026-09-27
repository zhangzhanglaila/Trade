import logging
import os
import argparse
from datetime import datetime

import pandas as pd
import numpy as np
import torch
import torch.nn as nn
import winsound
from sklearn.metrics import r2_score
from torch.utils.data import Dataset, DataLoader
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.model_selection import train_test_split
import matplotlib.pyplot as plt
import pickle

from tqdm import tqdm

import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


# 解析命令行参数
parser = argparse.ArgumentParser(description='训练贸易预测模型')
parser.add_argument('--csv_path', type=str, required=True, help='CSV数据文件路径')
parser.add_argument('--model_output_path', type=str, required=True, help='模型输出路径（目录）')
parser.add_argument('--trade_type', type=str, required=True, choices=['in', 'out'], help='贸易类型：in(进口) 或 out(出口)')
parser.add_argument('--target', type=str, required=True, choices=['price', 'quantity'], help='预测目标：price(单价) 或 quantity(数量)')
parser.add_argument('--sound_file', type=str, default='', help='训练完成提示音文件路径（可选）')
args = parser.parse_args()

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
    '人民币': ['人民币', '金额', '总金额', '金额(人民币)']
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
continuous_cols = ['year', 'month', '数量', '人民币']  # 包含连续变量（已包含年月）
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

# 标准化 log 变换后的目标变量
scaler_y = StandardScaler()
df[target_cols_log] = scaler_y.fit_transform(df[target_cols_log])

# 清理连续变量中的异常值
for col in continuous_cols:
    df[col] = df[col].replace({'-': np.nan, '': np.nan}, regex=False)
    df[col] = pd.to_numeric(df[col], errors='coerce')

# 删除含有 NaN 的行（包括类别、连续和目标变量）
df.dropna(subset=categorical_cols + continuous_cols + target_cols_log, inplace=True)

# 对类别变量做 Label Encoding
label_encoders = {}
for col in categorical_cols:
    le = LabelEncoder()
    df[col] = le.fit_transform(df[col].astype(str))  # 强制转字符串防止报错
    label_encoders[col] = le

# 归一化连续变量（包括 year 和 month）
scaler_X = StandardScaler()
df[continuous_cols] = scaler_X.fit_transform(df[continuous_cols])

# 按商品分组创建时间序列
grouped = df.groupby('商品编码')
sequences = []
seq_length = 2#历史窗口长度

for name, group in grouped:
    group = group.sort_values(['year', 'month'])
    if len(group) < seq_length + 1:  # 确保有足够数据点
        continue

    # 创建序列
    X_cat_group = group[categorical_cols].values
    X_cont_group = group[continuous_cols].values
    y_group = group[target_cols_log].values

    for i in range(len(X_cat_group) - seq_length):
        x_cat_seq = X_cat_group[i:i + seq_length]
        x_cont_seq = X_cont_group[i:i + seq_length]
        y_val = y_group[i + seq_length]
        sequences.append((x_cat_seq, x_cont_seq, y_val))

# 转换为数组
if sequences:
    X_cat, X_cont, y = zip(*sequences)
    X_cat = np.array(X_cat)
    X_cont = np.array(X_cont)
    y = np.array(y)
else:
    raise ValueError("没有足够数据创建序列，请检查数据或减小seq_length")

# 分割训练集和测试集（不打乱）
X_cat_train, X_cat_test, X_cont_train, X_cont_test, y_train, y_test = train_test_split(
    X_cat, X_cont, y, test_size=0.2, shuffle=False
)

# 转换为PyTorch张量
X_cat_train_tensor = torch.tensor(X_cat_train, dtype=torch.long)
X_cont_train_tensor = torch.tensor(X_cont_train, dtype=torch.float32)
y_train_tensor = torch.tensor(y_train, dtype=torch.float32)

X_cat_test_tensor = torch.tensor(X_cat_test, dtype=torch.long)
X_cont_test_tensor = torch.tensor(X_cont_test, dtype=torch.float32)
y_test_tensor = torch.tensor(y_test, dtype=torch.float32)

# 创建DataLoader
train_dataset = torch.utils.data.TensorDataset(X_cat_train_tensor, X_cont_train_tensor, y_train_tensor)
train_loader = DataLoader(train_dataset, batch_size=32, shuffle=True)  # 增大batch_size
test_dataset = torch.utils.data.TensorDataset(X_cat_test_tensor, X_cont_test_tensor, y_test_tensor)
test_loader = DataLoader(
    test_dataset,
    batch_size=32,   # 小批量验证
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
num_embeddings_list = [df[col].nunique() for col in categorical_cols]

model = LSTMTransformer(
    num_embeddings_list=[df[col].nunique() for col in categorical_cols],
    continuous_dim=len(continuous_cols),
    model_dim=128,#输入维度大小
    hidden_size=128,#隐藏层大小
    num_heads=16,#注意力头数
    num_layers=5,#Transformer层的数量
    dropout=0.6
).to(device)

criterion = nn.MSELoss(reduction='mean')
optimizer = torch.optim.Adam(model.parameters(), lr=1e-3, weight_decay=1e-5)
scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode='min', factor=0.5, patience=5)

epochs = 150
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
early_stopping = EarlyStopping(patience=100, verbose=True, path=checkpoint_path)#patience表示耐心程度，当连续多少个epoch without improvement时，停止训练

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

    # 验证
    model.eval()
    val_loss = 0.0
    with torch.no_grad():
        for x_cat_val, x_cont_val, y_val in test_loader:
            x_cat_val, x_cont_val, y_val = x_cat_val.to(device), x_cont_val.to(device), y_val.to(device)
            val_outputs = model(x_cat_val, x_cont_val)
            loss = criterion(val_outputs, y_val)
            val_loss += loss.item() * x_cat_val.size(0)

        val_loss /= len(test_loader.dataset)

    scheduler.step(val_loss)
    val_losses.append(val_loss)

    log_filename = f'training_Trade_Transformer_{args.trade_type}_{args.target}.log'
    print(f"Epoch {epoch + 1}/{epochs} | Train Loss: {avg_train_loss:.4f} | Val Loss: {val_loss:.4f}")
    logging.basicConfig(filename=log_filename, level=logging.INFO)
    logging.info(f"Epoch {epoch + 1}/{epochs}, Train Loss: {avg_train_loss:.4f}, Val Loss: {val_loss:.4f}")
    early_stopping(val_loss, model)
    if early_stopping.early_stop:
        print("Early stopping")
        break

#  保存模型（save_path已在前面定义）
model_filename = f'model_{args.target}.pth'
torch.save(model.state_dict(), save_path + model_filename)

# Step 5: 评估最佳模型
print("开始验证...")
model.load_state_dict(torch.load(save_path + model_filename))
model.eval()

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

# 反标准化 + exp
y_pred_unscaled = np.expm1(scaler_y.inverse_transform(y_pred))
y_true_unscaled = np.expm1(scaler_y.inverse_transform(y_true))

pred_quantity = y_pred_unscaled

true_quantity = y_true_unscaled

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
r2_q, mse_q, mae_q = evaluate(target_name, true_quantity, pred_quantity)
log_filename = f'training_Trade_Transformer_{args.trade_type}_{args.target}.log'
logging.basicConfig(filename=log_filename, level=logging.INFO)
time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
logging.info(f"datetime:{time}, R2:{r2_q}, MSE:{mse_q}, MAE:{mae_q}")


# 打印部分样本
print("部分预测值 VS 真实值:")
# 关键：取预测值和真实值的最小长度，避免越界
max_print = min(len(pred_quantity), len(true_quantity))
# 最多打印10条（可选，避免样本多时打印太多）
max_print = min(max_print, 10)
for i in range(max_print):
    print(f"预测: {target_name}={pred_quantity[i].item():.2f} 真实: {target_name}={true_quantity[i].item():.2f}")
# --- 绘图部分 ---
plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False

# 绘制预测 vs 真实曲线
plt.figure(figsize=(12, 6))
plt.plot(true_quantity, label=f'真实{target_name}', color='blue')
plt.plot(pred_quantity, label=f'预测{target_name}', color='red', linestyle='--')
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

print("✅ 模型和预处理器已成功保存！")
# 同步播放（程序会暂停直到播放结束）
if args.sound_file and os.path.exists(args.sound_file):
    winsound.PlaySound(args.sound_file, winsound.SND_FILENAME)

