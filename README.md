# 中哈贸易数据智能分析系统

> 面向中国—哈萨克斯坦（及中亚五国）外贸数据的**采集 → 知识图谱 → 智能问答 → 预测**一体化系统。
> 涵盖多国语料采集、RDF 知识图谱构建与可视化、基于大模型的贸易问答、以及进出口价格/数量的时序预测。

本仓库为**单一 monorepo**，已把原先散落在 13 个嵌套 Git 仓库中的全部子工程合并到统一版本控制之下，
并完成凭据集中治理、路径可移植化与目录整理。整理前后零数据丢失（每处删除均以全文件 MD5 在保留侧找到同源原件）。

---

## 目录

- [系统组成](#系统组成)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [配置：`config/.env`](#配置configenv)
- [数据与模型（不入库）](#数据与模型不入库)
- [文档索引](#文档索引)
- [项目现状与已知问题](#项目现状与已知问题)
- [版本控制说明](#版本控制说明)

---

## 系统组成

系统不是单体应用，而是「前端 + 主后端 + 训练后端 + Python 推理服务 + 多种数据存储 + 可选大屏/图谱服务」的组合。

```
                        ┌──────────────────────────────┐
                        │   前端 Vue 3  ·  :3000 / :80  │
                        │  业务界面 · 图谱可视化 · 大屏  │
                        └───────┬──────────────┬───────┘
                                │              │
                 ┌──────────────▼───┐   ┌──────▼──────────┐
                 │ 主后端 :8080      │   │ 训练后端 :8081   │
                 │ Spring Boot 3.1.5 │   │ Spring Boot 2.7  │
                 │ 鉴权/业务/图谱查询 │   │ 训练任务编排      │
                 │ 智能问答 RAG      │   └──────┬──────────┘
                 └───┬────┬────┬─────┘          │ 手动启动
                     │    │    │          ┌─────▼──────────┐
                     │    │    │          │ Flask :5000     │
                     │    │    │          │ 预测/意图路由    │
                     │    │    │          └─────────────────┘
        ┌────────────▼┐ ┌─▼──────┐ ┌───────▼──────────┐
        │ MySQL       │ │ Redis  │ │ Jena TDB（嵌入式）│
        │ foreign_qa  │ │ 缓存   │ │ 图谱三元组存储    │
        │ trade/aj    │ │        │ └──────────────────┘
        └─────────────┘ └────────┘
                     ┌──────────────┐  ┌──────────────┐
        （可选）      │ Neo4j        │  │ Milvus       │
                     │ 图谱构建链路  │  │ 向量检索 RAG  │
                     └──────────────┘  └──────────────┘

     知识图谱工程 src/pjcait（Java + Go + Python）：采集 → 清洗 → R2RML 映射 → RDF
```

### 模块一览

| 模块 | 目录 | 作用 | 端口 |
| --- | --- | --- | --- |
| 前端主工程 | `src/frontend-vue` | 业务界面、图谱可视化、数据看板 | 3000（开发）/ 80（生产） |
| Vite 脚手架 | `src/frontend-vite-scaffold` | 早期前端原型，保留参考 | — |
| 主后端 | `src/backend-main` | 业务 API、鉴权（JWT）、图谱查询、智能问答 | 8080 |
| 训练后端 | `src/backend-training` | 训练任务编排、调用 Python 脚本 | 8081 |
| 推理脚本 | `src/backend-training/python-scripts` | Flask 预测服务、意图路由、训练脚本 | 5000 |
| 数据大屏 | `src/dashboard-ajreport` | AJ-Report 1.3.0 大屏 | 9095 |
| 知识图谱工程 | `src/pjcait` | 采集 / 加工 / 图谱构建 / 面板（Java + Go + Python） | 多端口 |
| 图查询工具（可选） | `deliverables/查询工具/apache-jena-fuseki-5.4.0` | SPARQL 端点 | 3030 |

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3 · Vue CLI 5 · Element Plus · Ant Design Vue · ECharts · AntV G6 · Axios · Neo4j Driver |
| 主后端 | JDK 17 · Spring Boot 3.1.5 · MyBatis-Plus 3.5.6 · Apache Jena 4.10 · JWT (jjwt 0.12) |
| 训练后端 | JDK 17 · Spring Boot 2.7.14 |
| 推理/训练 | Python · Flask · PyTorch（LSTM-Transformer） |
| 大屏 | AJ-Report 1.3.0 |
| 存储 | MySQL 8 · Redis · Apache Jena TDB（嵌入式 RDF） · Neo4j（可选） · Milvus（可选） |
| 大模型 | Qwen3.5-9B（本地推理，VLM 架构，见 [模型服务器配置要求](docs/模型服务器配置要求.md)） |
| 数据规范 | RDF / R2RML / SPARQL |

---

## 快速开始

### 环境要求

| 依赖 | 版本 | 必需 |
| --- | --- | --- |
| JDK | 17 | ✔ |
| Maven | 3.9+ | ✔ |
| Node.js | 22（18+ 亦可） | ✔ |
| MySQL | 8.0+ | ✔ |
| Python | 3.10+ | ✔（预测/训练用） |
| Redis | 6+ | 建议 |
| Neo4j / Milvus | — | 可选（图谱构建 / RAG 向量检索） |

### 三步启动

```bash
# 1. 克隆
git clone https://github.com/zhangzhanglaila/Trade.git
cd Trade

# 2. 准备配置（仓库是公开的，真实凭据不入库）
cp config/.env.example config/.env
# 编辑 config/.env，填写数据库口令、JWT 密钥、API Key 等

# 3. 一键启动
deploy/start-all.bat      # Windows
./deploy/start-all.sh     # Linux / macOS
```

一键启停脚本会自动加载 `config/.env`、按依赖顺序拉起各服务。
健康检查：`./deploy/check-health.sh`。

### 手动分步启动

```bash
# 主后端 → 8080
cd src/backend-main && mvn package && java -jar target/*.jar

# 训练后端 → 8081
cd src/backend-training && mvn package && java -jar target/*.jar

# Python 推理服务 → 5000（⚠ 不会被训练后端自动拉起，须手动启动）
cd src/backend-training/python-scripts && python app.py

# 前端 → 3000
cd src/frontend-vue && npm install && npm run serve

# 大屏 → 9095
cd src/dashboard-ajreport && bin/start.bat
```

> **重要**：`src/backend-training/python-scripts/app.py`（Flask 预测服务）**不会随训练后端自动启动**，
> 它只在训练任务的「步骤 5/6」被调用。需要在业务中直接使用 `/predict_*` 时，必须手动起，且**每次重启后都要再起一次**。

### 建立数据库

```bash
python deploy/init_mysql.py                # 建库建表 + 导入数据
python deploy/init_mysql.py --schema-only  # 只建库建表，不导数据
python deploy/init_mysql.py --news-only    # 只导新闻语料与汇总
```

---

## 配置：`config/.env`

**本目录是项目里唯一存放明文凭据的地方**，源代码、YAML、文档中一律只保留环境变量引用。

| 文件 | 是否入库 | 说明 |
| --- | --- | --- |
| `config/.env` | 否（gitignore） | 真实凭据，本机/服务器实际生效的那一份 |
| `config/.env.example` | 是 | 模板，新环境从它复制 |
| `config/README.md` | 是 | 完整变量清单与泄露应急流程 |

各运行单元统一读同一份文件：Shell 脚本启动前自动 `source`；Spring Boot 用 `${VAR:默认值}` 占位符；
Python 脚本由 `_trade_config.py` 自动向上查找加载。

**完整变量清单见 [`config/README.md`](config/README.md)。** 核心几项：

| 变量 | 作用 |
| --- | --- |
| `TRADE_HOME` | 运行期根目录（服务器建议 `/opt/trade`） |
| `DB_PASSWORD` / `TRAIN_DB_PASSWORD` / `AJ_DB_PASSWORD` | 各库口令 |
| `JWT_SECRET` | JWT 签名密钥（**无默认值**，未设则启动自检失败） |
| `QWEN_BASE_URL` / `QWEN_CHAT_ENDPOINT` / `QWEN_EMBEDDING_ENDPOINT` | 本地大模型推理服务 |
| `DEEPSEEK_API_KEY` / `SILICONFLOW_API_KEY` | 图谱抽取用 LLM |

> ⚠️ 仓库为**公开仓库**。`config/.env` 已被忽略，但若历史上曾误提交，请立即吊销并重新生成密钥
> （改密码无效），流程见 `config/README.md` 第 4 节。

### 路径可移植化

项目原先在配置里写死了开发机盘符（`D:/Code/Trade/...`、`D:\programs\Python\...`），现已**全部消除**。
统一基准 `trade.home: ${TRADE_HOME:${user.dir}}` —— **只要在项目根启动进程，所有路径自动正确**，
换机器、搬目录无需改任何文件。详见 [`目录结构说明.md`](目录结构说明.md)。

---

## 数据与模型（不入库）

本仓库**只跟踪代码、配置模板与文档**；数据本体与模型权重因体积原因排除在版本控制之外。
仓库跟踪约 820 个文件，而项目目录实际占 **21.67 GiB**。

| 目录 | 内容 | 是否入库 |
| --- | --- | --- |
| `data/01_原始_海关进出口明细` | 五国月度进出口 CSV（295 文件 / 790 MB）—— 唯一权威源 | 否 |
| `data/02_派生_贸易知识图谱RDF` | `*_mapped_data.rdf`（275 文件 / 940 MB） | 否 |
| `data/03..05_*` | 新闻图谱、语料、运行期进出口 CSV | 否（仅 README 入库） |
| `data/06_外部公开数据集` | 说明文件（数据在 `models/` 内） | 仅 README |
| `models/` | Qwen3.5-9B 两个微调版本（18.07 GB） | 否 |
| `deliverables/查询工具` | Jena Fuseki、d2r-server 发行包 | 否 |
| `node_modules/` `target/` `runtime/` | 依赖、构建产物、运行期 TDB 图库 | 否 |

**恢复方式**见 [`docs/版本控制说明.md`](docs/版本控制说明.md)（含 `.gitignore` 全量排除项与还原步骤）。

### 上传到服务器

**不要整个目录直接拷** —— 其中 `node_modules`（含 Windows 原生 `.node` 二进制，Linux 加载会失败）
和 `models/`（18 GB，仅大模型推理需要）必须剔除。用打包脚本：

```bash
python deploy/package-for-server.py --dry-run      # 预览会打包什么
python deploy/package-for-server.py                # 出包 → ../_upload/trade-server-<日期>.tar.gz
python deploy/package-for-server.py --with-models  # 需要问答大模型时加上
```

实测上传包 **2.51 GB / 18,622 文件**（压缩后约 700 MB，已排除 `config/.env`，服务器上现填）。
完整步骤见 [`docs/服务器部署指南.md`](docs/服务器部署指南.md)。

---

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [`docs/服务器部署指南.md`](docs/服务器部署指南.md) | **主文档**。环境要求、五步部署、数据初始化、验证清单、结项指标对照、故障排查、图谱三陷阱 |
| [`docs/版本控制说明.md`](docs/版本控制说明.md) | 仓库组织、跟踪范围、被排除内容的恢复方法、换行符策略、凭据管理 |
| [`config/README.md`](config/README.md) | 凭据变量全清单、加载方式、泄露应急处理 |
| [`目录结构说明.md`](目录结构说明.md) | 顶层结构、运行入口速查、路径配置、命名对照表（原始 → 现在） |
| [`docs/模型评测报告.md`](docs/模型评测报告.md) | **模型评估章节**。4 个预测模型指标为何不可采信的根因分析与整改方案 |
| [`docs/模型服务器配置要求.md`](docs/模型服务器配置要求.md) | Qwen3.5-9B 实测硬件规格与显存测算 |
| [`docs/服务器修复方案与交接单.md`](docs/服务器修复方案与交接单.md) | P0/P1/P2 分级的问题清单、改动点与验收命令（改动记录 / 结项材料） |
| [`docs/pjcait系统技术文档.md`](docs/pjcait系统技术文档.md) | 知识图谱工程（采集/加工/构建）技术细节 |
| [`docs/部署全流程指南.md`](docs/部署全流程指南.md) | ⚠️ 目录整理前的旧版指南，**部分内容已过时**，端口用途与模块职责部分仍有效 |

---

## 项目现状与已知问题

为便于接手，这里如实列出当前状态。**结项材料撰写前请务必先看 [`docs/模型评测报告.md`](docs/模型评测报告.md)。**

### 预测模型 —— 现有指标不可采信，流程缺陷已定位

四个 LSTM-Transformer 模型日志中的 R² 全为负值（`in_price` −70.38、`in_quantity` −77.68、
`out_price` −0.42、`out_quantity` −456.05）。**但"模型没有预测能力"这个结论过强** —— 根因是评测协议缺陷：

1. **切分缺陷（头号根因）**：样本按 `商品编码` 升序拼接后 `train_test_split(shuffle=False)`，
   等于**按商品划分**，测试集恒为"商品编码最大的那批商品"。而 `商品编码` 正是做 Embedding 的特征，
   这些行从未获得梯度 → 输出等同随机 → **R² 必为负，与数据量无关**。实测真实出口数据 **99.9%
   的测试样本（379,653/380,201）商品在训练集中从未出现**。
2. **训练用的是样例文件**（出口仅 29 条样本），证据是 `src/backend-training/logs/trade-service.log`
   中逐行记录的 `--csv_path`。
3. **另 3 个真 bug**：评估加载最后 epoch 模型而非早停最佳模型；标准化器/LabelEncoder 在全量上 fit
   造成泄漏；`import winsound` 在 Linux 上直接 ImportError。
4. **`src/backend-training/data/进口/merged_input.csv` 实为 xlsx**（文件头 `50 4B 03 04`），
   `pandas.read_csv` 无法解析。

> **不要在未修复上述缺陷的情况下直接用真实数据重训** —— 否则会再次得到 R²<0，
> 且因数据量更大而显得更可信。应先修复，再用 3~5 万行验证评估链路可信，最后才上全量；
> 评测须加入**朴素基线**（上一期值 / 季节均值），因为本任务本质接近随机游走。

### 接口可用性

- `POST /predict_*` 当前返回 500：`trade` 库及 `trade_in` / `trade_out` 两张表**全项目无脚本创建**，
  且 `TRAIN_DB_PASSWORD` 与目标机 MySQL 口令需对齐。
- 智能问答的 LLM 兜底依赖本地 Qwen 推理服务与 Milvus，未部署时仅剩关键词路由。

### 图谱可视化（已修复，但需牢记两个陷阱）

- 节点渲染曾恒为 0 的根因是**类型断言不匹配**：数据里的类型是自定义 `vocab#货物`，
  没有 `owl:Class` / `owl:NamedIndividual`，而查询只认后两者。已改为**类型聚合 + Top-N 抽样**。
- **命名图 URI 会多一个 `v`**：种子值写 `'v1.0'`，而代码又拼 `/v` + 版本 → 实际 URI 是
  `.../中哈贸易知识图谱本体/vv1.0`。导入目标必须与之逐字节一致。
- 图谱导入**必须写命名图**，应用只读命名图。

### 待办

- 数据修复：3 个截断 RDF（实测可救，砍残尾补根标签可保留 99.2%~99.9%）、
  2 个零填充 CSV（互为错误副本，2021 年 9—10 月出口数据缺失）。
- 修复 4 个预测模型代码缺陷 → 换真实数据 → 重训与重新评测。
- 生产部署前更换 `admin/admin123` 与全部 `123456` 类默认口令；`config/.env` 中仍为旧凭据。
- 是否单独打包 `models/`（18 GB）随服务器上传；大模型推理服务用 vLLM 还是 ollama 待定。

---

## 版本控制说明

- **单一 Git 仓库**：原 13 个嵌套仓库（含 3 个 submodule、2 份 go-openai、2 个模型仓库）
  已合并为顶层 monorepo，内部 `.git` 已移除。合并前的完整备份保留在项目外（含 SHA256 清单）。
- **凭据不入库**：所有明文集中于 `config/.env`（已忽略），代码中只出现环境变量引用。
  `JWT_SECRET` 刻意不设默认值，靠启动自检失败强制配置。
- **大文件不入库**：`models/`、`data/` 数据本体、`*.pth`、`node_modules/`、`target/`、`runtime/`、
  第三方发行包。详见 [`docs/版本控制说明.md`](docs/版本控制说明.md)。
- **换行符**：`.gitattributes` 对文本统一 `text=auto`，仅脚本强制（`.sh`→LF、`.bat`→CRLF）；
  内容与扩展名不符的文件（如实为 xlsx 的 `merged_input.csv`）单独标记 `binary`。

---

<div align="center">

**相关仓库**：<https://github.com/zhangzhanglaila/Trade>

部署问题请先查 [`docs/服务器部署指南.md`](docs/服务器部署指南.md) 的故障排查章节。

</div>
