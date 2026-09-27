# TDProject
大数据项目
看板建表语句
-- 1. 语料条目表：存储各年份的语料条目数量
CREATE TABLE corpus_entries (
    year INT NOT NULL COMMENT '年份',
    entry_count INT NOT NULL COMMENT '该年份对应的条目数量',
    PRIMARY KEY (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语料条目统计表';

-- 2. 综合数据表：存储各类统计数据的综合信息
CREATE TABLE comprehensive_data (
    id INT AUTO_INCREMENT PRIMARY KEY,
    corpus_entry_count INT NOT NULL COMMENT '语料条目数',
    data_entry_count INT NOT NULL COMMENT '数据条目数',
    query_visit_count INT NOT NULL COMMENT '问答访问数',
    trade_country_count INT NOT NULL COMMENT '贸易国家数',
    update_time DATETIME NOT NULL COMMENT '数据更新时间',
    UNIQUE KEY uk_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合统计数据表';

-- 3. 贸易方式统计表：按国家和贸易方式统计贸易金额
CREATE TABLE trade_method_stats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    trade_method VARCHAR(100) NOT NULL COMMENT '贸易方式',
    trade_amount DECIMAL(18, 2) NOT NULL COMMENT '该贸易方式对应的贸易金额',
    country_name VARCHAR(100) NOT NULL COMMENT '国家名称',
    KEY idx_country_method (country_name, trade_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贸易方式统计数据表';

-- 4. 国家月度贸易表：按国家和月份统计进出口数据
CREATE TABLE country_monthly_trade (
    id INT AUTO_INCREMENT PRIMARY KEY,
    country_name VARCHAR(100) NOT NULL COMMENT '国家名称',
    monthly_import_volume DECIMAL(18, 2) NOT NULL COMMENT '月进口量',
    monthly_export_volume DECIMAL(18, 2) NOT NULL COMMENT '月出口量',
    monthly_export_amount DECIMAL(18, 2) NOT NULL COMMENT '月出口金额',
    monthly_import_amount DECIMAL(18, 2) NOT NULL COMMENT '月进口金额',
    month INT NOT NULL COMMENT '月份(1-12)',
    year INT NOT NULL COMMENT '年份',
    UNIQUE KEY uk_country_date (country_name, year, month),
    KEY idx_country (country_name),
    KEY idx_year_month (year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国家月度贸易数据表';

---

## AI 能力与联调操作手册

本项目（TDProject-main）已提供统一的 AI 能力出口 ` /ai/* `，用于对接 **意图识别 →（RAG 新闻问答 / 预测）**、训练编排转发、Milvus 向量索引管理等能力。

### 1. 整体架构与职责

- **TDProject-main（Spring Boot，默认 8080）**：统一 AI 出口
  - `/ai/chat`：意图识别 → 路由到 RAG/预测
  - `/ai/train/*`：转发训练编排到 TradeSpringBoot
  - `/ai/rag/index/*`：Milvus 索引管理（全量/增量/重建/删除）
  - `/ai/predict*`：预测能力（内部调用 Flask）
- **TradeSpringBoot（默认 8081）**：训练编排与训练过程状态查询（被 TDProject-main 转发调用）
- **Flask（默认 5000）**：推理服务（预测 / health）
- **Milvus（默认 19530）**：新闻语料向量库（RAG 检索）
- **DashScope**：提供大模型聊天/Embedding（通过 `application.yaml` + 环境变量配置）

> 端口/地址以 `application.yaml` 与环境变量为准。

### 2. 启动顺序（建议）

1) **Milvus**（向量库）
2) **TradeSpringBoot**（训练编排服务）
3) **Flask**（推理服务，确保 `/health` 可用）
4) **TDProject-main**（统一 `/ai/*` 出口）

### 3. 关键配置（TDProject-main）

配置位置：`TDProject-main/src/main/resources/application.yaml`

```yaml
ai:
  tradeSpringBoot:
    base-url: http://127.0.0.1:8081/api
  flask:
    base-url: http://127.0.0.1:5000
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:}
    base-url: ${DASHSCOPE_BASE_URL:}
    chat:
      model: ${DASHSCOPE_CHAT_MODEL:}
    embedding:
      model: ${DASHSCOPE_EMBEDDING_MODEL:}
    endpoints:
      chat: ${DASHSCOPE_CHAT_ENDPOINT:}
      embedding: ${DASHSCOPE_EMBEDDING_ENDPOINT:}
  milvus:
    host: ${MILVUS_HOST:127.0.0.1}
    port: ${MILVUS_PORT:19530}
    collection: ${MILVUS_COLLECTION:news_corpus}
    dimension: ${MILVUS_DIMENSION:1024}
    topK: ${MILVUS_TOPK:5}
```

说明：
- `ai.tradeSpringBoot.base-url`：训练编排服务基地址（TDProject-main 会把 `/ai/train/*` 转发到该服务）
- `ai.flask.base-url`：Flask 推理服务基地址（预测能力依赖）
- `ai.dashscope.*`：大模型与 Embedding 配置（建议通过环境变量注入敏感信息）
- `ai.milvus.dimension`：**Embedding 向量维度**，必须与实际 embedding 模型输出维度一致

### 4. 环境变量示例（仅列变量名与含义）

- `DASHSCOPE_API_KEY`：DashScope API Key
- `DASHSCOPE_BASE_URL`：DashScope base url（如有私有部署/代理）
- `DASHSCOPE_CHAT_MODEL`：聊天模型名
- `DASHSCOPE_EMBEDDING_MODEL`：Embedding 模型名
- `DASHSCOPE_CHAT_ENDPOINT`：聊天接口路径（相对/绝对取决于实现配置）
- `DASHSCOPE_EMBEDDING_ENDPOINT`：Embedding 接口路径
- `MILVUS_HOST` / `MILVUS_PORT`：Milvus 地址
- `MILVUS_COLLECTION`：collection 名称
- `MILVUS_DIMENSION`：向量维度
- `MILVUS_TOPK`：检索 topK

### 5. Milvus 索引构建

#### 5.1 重建 collection（可选）

```bash
curl -X POST http://127.0.0.1:8080/ai/rag/index/recreate
```

#### 5.2 全量向量化 + upsert

```bash
curl -X POST http://127.0.0.1:8080/ai/rag/index/full
```

返回：统一 `Result{code,message,data}`，其中 `data` 通常为字符串，例如：`OK, indexed count=xxx`。

### 6. 对外 API 示例（统一返回 Result）

> 所有 `/ai/*` 接口统一返回：
>
> ```json
> {"code":200,"message":"成功","data":{}}
> ```
>
> `code != 200` 时表示失败，前端应提示 `message`。

#### 6.1 智能问答：POST /ai/chat

请求（最小）：
```bash
curl -X POST http://127.0.0.1:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"text":"今年某国某商品价格走势如何？"}'
```

请求（带 RAG 过滤，可选）：
```bash
curl -X POST http://127.0.0.1:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"text":"今年有哪些与出口相关的新闻？","country":"美国","year":2024}'
```

请求（显式预测槽位，可选；填了槽位会更倾向走 PREDICT）：
```bash
curl -X POST http://127.0.0.1:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "text":"预测 2024 年 12 月进口价格",
    "tradeType":"in",
    "target":"price",
    "year":2024,
    "month":12,
    "tradePartnerName":"xxx",
    "productName":"xxx",
    "tradeMode":"xxx",
    "registerName":"xxx"
  }'
```

响应 data：`AiChatResponse`
- `route`：`PREDICT` / `RAG_NEWS`
- `answer`：最终回答
- `sources[]`：RAG 命中来源（仅 `RAG_NEWS`）
- `predictResult`：预测结果（仅 `PREDICT`，包含 `value/unit/raw`）

#### 6.2 预测：POST /ai/predict + 健康检查

```bash
curl -X GET http://127.0.0.1:8080/ai/predict/health
```

```bash
curl -X POST http://127.0.0.1:8080/ai/predict \
  -H "Content-Type: application/json" \
  -d '{"tradeType":"in","target":"price","year":2024,"month":12,"tradePartnerName":"xxx","productName":"xxx","tradeMode":"xxx","registerName":"xxx"}'
```

#### 6.3 训练转发：/ai/train/*

上传（multipart，字段名需匹配）：
```bash
curl -X POST http://127.0.0.1:8080/ai/train/upload \
  -F "mergedInput=@merged_input.csv" \
  -F "mergedOutput=@merged_output.csv"
```

启动训练：
```bash
curl -X POST http://127.0.0.1:8080/ai/train/start
```

查询状态：
```bash
curl -X GET http://127.0.0.1:8080/ai/train/status/{taskId}
```

### 7. 前端联调要点

- 开发环境建议使用代理：`/ai -> http://localhost:8080`（TDProject-main）
- 前端在 API 层解包 `Result.data`，并在 `code != 200` 时统一抛错提示

### 8. FAQ / 常见问题排查

1) **Embedding 维度不匹配**
- 现象：构建索引或检索时报错（向量维度相关）
- 排查：`ai.milvus.dimension` 必须与 embedding 模型输出维度一致

2) **Milvus collection 未创建 / 未 load**
- 现象：`/ai/rag/index/full` 失败或检索无结果
- 排查：先执行 `/ai/rag/index/recreate`，确保 Milvus 正常启动

3) **Flask 未启动 / health 不通**
- 现象：`/ai/predict/health` 或预测接口失败
- 排查：直接访问 Flask 的 `/health`（地址以 `ai.flask.base-url` 为准）

4) **DashScope endpoint/model 未配置**
- 现象：调用 `/ai/chat` 或索引构建返回 500
- 排查：检查 `ai.dashscope.*` 与相关环境变量是否注入
