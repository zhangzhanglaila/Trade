# 查询工具（随交付包分发，不入 Git）

本目录存放图谱查询工具，属于**第三方发行包**，体积较大（约 60 MB）且可公开下载，
因此不纳入 Git 版本控制。克隆仓库后本目录为空，按下方说明补齐即可。

## 需要补齐的两个目录

### 1. apache-jena-fuseki-5.4.0/

| 项目 | 说明 |
|---|---|
| 用途 | 提供 SPARQL 端点，加载 `data/02_派生_贸易知识图谱RDF/*.rdf` 后对外查询 |
| 版本 | Apache Jena Fuseki 5.4.0（内置 TDB2） |
| 下载 | https://jena.apache.org/download/ → `apache-jena-fuseki-5.4.0.zip` |
| 体积 | 约 46 MB |

补齐方式：

```bash
cd deliverables/查询工具
# 下载 apache-jena-fuseki-5.4.0.zip 后解压到当前目录
unzip apache-jena-fuseki-5.4.0.zip
```

> 注意：Fuseki 使用 **TDB2** 格式，而主后端依赖的 Jena 是 **TDB1**，两者存储格式不兼容。
> 向 Fuseki 灌库必须用 Fuseki 自带的 `tdb2.tdbloader`，不能直接复用后端生成的 TDB 目录。

### 2. d2r-server-0.7/

| 项目 | 说明 |
|---|---|
| 用途 | 基于 R2RML 映射规则（`deliverables/本体与映射规则/`）将关系库发布为虚拟 RDF |
| 版本 | D2R Server 0.7 |
| 下载 | http://d2rq.org/ → `d2rq-0.8.1.zip`（内含 d2r-server） |
| 体积 | 约 14 MB |

## 相关文件（已入库）

以下文件体积小、属于项目自研内容，已在 Git 中跟踪：

- `../本体与映射规则/` —— 本体（Protege 导出）、R2RML 映射规则、映射操作说明
- `../数据备份/非结构化数据（新闻）.dump` —— 新闻语料库备份
- `../评测集/测试集.xlsx` —— 问答评测集
