# `config/` —— 统一凭据配置目录

本目录是项目里**唯一**存放明文凭据的地方。源代码、YAML、文档中都只保留环境变量引用，
不再出现任何明文口令或 API Key。

| 文件 | 是否入库 | 用途 |
|---|---|---|
| `config/.env` | **否**（已在 `.gitignore` 中排除） | 真实凭据。本机/服务器上实际生效的那一份 |
| `config/.env.example` | 是 | 模板。新环境从它复制出 `.env` 再填写 |
| `config/README.md` | 是 | 本文件，说明与变量清单 |

> 仓库是**公开的**。任何时候都不要把 `config/.env` 提交进 Git。
> 如果误提交了，参考本文件末尾「凭据泄露应急处理」。

---

## 1. 怎么用

### 首次准备（本机或服务器）

```bash
cp config/.env.example config/.env
# 编辑 config/.env，填写各项真实值
```

### 加载方式

不同的运行单元用不同方式拿到这些值，都指向同一份 `config/.env`：

| 运行单元 | 加载方式 |
|---|---|
| `deploy/start-all.sh`（Linux/macOS 一键启动） | 脚本启动前自动 `source config/.env` |
| `deploy/start-all.bat`（Windows 一键启动） | 脚本启动前逐行读取 `config\.env` 并 `set` |
| Spring Boot（`backend-main` / `backend-training` / `dashboard-ajreport` / `pjcait/collector`） | 从**环境变量**读取，配置文件中写 `${VAR:默认值}` 占位符 |
| Python 脚本（`src/pjcait/graph-builder/*`） | 由同目录的 `_trade_config.py` 自动向上查找并加载 `config/.env` |
| `src/backend-training/python-scripts/app.py` | 启动时自行加载 `config/.env` |
| `tools/` 下的独立脚本 | 读取环境变量；先 `set -a; . ./config/.env; set +a` |

手动加载（想在 shell 里直接跑某个脚本时）：

```bash
set -a; . ./config/.env; set +a
```

---

## 2. 变量清单

### 运行期

| 变量 | 默认 | 说明 |
|---|---|---|
| `TRADE_HOME` | Java 进程工作目录 | 运行期数据与外部资源的定位基准。systemd 部署建议设为 `/opt/trade` |
| `PYTHON_EXE` | `PATH` 中的 `python` | Flask 预测服务使用的解释器 |

### 数据库

| 变量 | 对应位置 | 说明 |
|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | `backend-main` 主库 | 库名 `foreign_trade_qa_db` |
| `TRAIN_DB_HOST` `TRAIN_DB_NAME` `TRAIN_DB_USERNAME` `TRAIN_DB_PASSWORD` | 训练后端与 Flask | 库名 `trade` |
| `AJ_DB_HOST` `AJ_DB_PORT` `AJ_DB_NAME` `AJ_DB_USERNAME` `AJ_DB_PASSWORD` | AJ-Report 9085/9095 | 库名 `aj_report` |
| `AJ_USER_DEFAULT_PASSWORD` | AJ-Report | 新建用户初始密码 |
| `ADMIN_USERNAME` `ADMIN_PASSWORD` | `backend-main` 的 `sys_user` 初始账号 | 系统管理员；`deploy/check-health.sh` 登录检查会用到。**正式部署务必改掉** |
| `CAIT_DB_*` | `pjcait/collector` | 库名 `cait` |

### 图数据库与缓存

| 变量 | 对应位置 | 说明 |
|---|---|---|
| `NEO4J_URI` `NEO4J_USERNAME` `NEO4J_PASSWORD` `NEO4J_DATABASE` | `graph-builder/*.py` | 图谱构建链路 |
| `NEO4J_URI_REMOTE` `NEO4J_DATABASE_REMOTE` | `builder3.py` | 历史脚本中另一台图库地址 |
| `VUE_APP_NEO4J_URI` `VUE_APP_NEO4J_USER` `VUE_APP_NEO4J_PASSWORD` | `frontend-vue/src/api/neo4j.js` | 前端「图谱可视化」直连 |
| `REDIS_HOST` `REDIS_PORT` `REDIS_USERNAME` `REDIS_PASSWORD` | `pjcait/panel/server` | 面板服务缓存 |

### 鉴权与模型

| 变量 | 对应位置 | 说明 |
|---|---|---|
| `JWT_SECRET` | `backend-main` | **重要**。JWT 签名密钥，泄露即可伪造任意用户令牌 |
| `JWT_EXPIRATION` | `backend-main` | token 有效期（毫秒），默认 1 天 |
| `DEEPSEEK_API_KEY` `DEEPSEEK_BASE_URL` | `graph-builder/builder2.py` | LLM 图谱抽取 |
| `SILICONFLOW_API_KEY` `SILICONFLOW_BASE_URL` | 历史脚本注释 | 备用中转 |
| `QWEN_BASE_URL` `QWEN_CHAT_ENDPOINT` `QWEN_EMBEDDING_ENDPOINT` | `backend-main` 智能问答 | 本地 Qwen 推理服务；留空则问答只剩关键词路由 |
| `DASHSCOPE_API_KEY` | `DashScopeClient` | 阿里云 DashScope |
| `MILVUS_*` | 智能问答 RAG | 向量检索，可选 |
| `COMTRADE_API_KEY` | `tools/spider-comtrade` | UN Comtrade 官方数据接口 |

### 地址与开发代理

| 变量 | 说明 |
|---|---|
| `AJ_REPORT_BASE_URL` | 大屏地址，默认 `http://127.0.0.1:9095` |
| `CRAWLER_BASE_URL` `CRAWLER_CALLBACK_BASE_URL` | 采集服务地址 |
| `VUE_APP_BACKEND_URL` `VUE_APP_TRAINING_URL` | 仅 `npm run serve` 开发代理时生效 |

---

## 3. 设计说明

**为什么集中在一个目录？**

原始项目把凭据散落在 20 多个文件里（Python 脚本、Spring YAML、Go 配置、Vue 源码、Markdown 文档），
既容易漏改，也容易在公开仓库里泄露。现在所有明文只存在于 `config/.env` 一处，
其余位置统一用环境变量占位符 `${VAR:默认值}` 或 `os.getenv()` 读取。

**为什么源码里还留着默认值？**

`${DB_PASSWORD:123456}` 这类默认值是**本地开发用的公开默认值**，目的是让「clone 下来直接跑」
不至于因缺环境变量而启动失败。生产环境在 `config/.env` 里覆盖即可，默认值不会生效。

**唯一例外 —— Go 配置的两个文件**

`src/pjcait/panel/server/config.yml` 与 `src/pjcait/processor{,-v2}` 的 Go 程序不解析环境变量，
其中的口令字段已置空，需要时请手动填写（这些是 pjcait 上游遗留服务，不属于主系统部署范围）。

---

## 4. 凭据泄露应急处理

如果 `config/.env` 中的某个值不慎被提交并推送到远端，**改密码没有用，必须作废重发**：

1. **立刻去服务商后台吊销并重新生成**（这是唯一有效的止损动作）
   - DeepSeek：控制台 → API Keys → 删除旧 key，新建
   - SiliconFlow：同上
   - UN Comtrade：重新申请 API Key
2. 把新值写回 `config/.env`
3. 更换 `JWT_SECRET`（所有用户需重新登录）
4. 清除 Git 历史中的那份文件（`git filter-repo` 或 BFG），并强推
5. 检查账单与调用记录是否有异常

> 参考：GitHub 的密钥扫描机器人通常会在**推送到公开仓库后的几分钟内**识别并通知服务商，
> 部分服务商会自动吊销密钥。
