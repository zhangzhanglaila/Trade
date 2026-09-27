# pjcait知识图谱系统详细技术文档

## 1. 项目概述

pjcait是一个专门用于从新闻文本中抽取贸易和物流相关知识，并构建知识图谱的综合性系统。该系统主要聚焦于中亚地区（特别是哈萨克斯坦）与中国之间的贸易和物流信息，通过自然语言处理和知识图谱技术，将非结构化的新闻文本转化为结构化的知识网络。

## 2. 系统架构与组件

### 2.1 整体架构

系统采用多语言混合架构（Java、Go、Python）和分布式设计，主要分为四大核心模块：

1. **数据采集模块**：负责从各类新闻源爬取原始数据
2. **信息抽取模块**：使用LLM技术从文本中提取实体和关系
3. **数据存储模块**：管理原始新闻数据和知识图谱数据
4. **数据处理与分析模块**：提供数据转换和分析功能

### 2.2 目录结构

```
khkg/
├── code/
│   ├── pjcait/
│   │   ├── collector/          # 数据收集相关组件
│   │   ├── doc/                # 项目文档
│   │   ├── graph-builder/  # 知识图谱构建核心代码
│   │   ├── crawler/   # 新闻爬虫模块
│   │   ├── panel/              # 用户界面相关
│   │   ├── processor/          # 数据处理组件
│   │   └── processor-v2/        # 数据处理组件（第二版）
│   └── pjcait.zip              # 代码压缩包
├── kg/                         # 知识图谱数据
│   ├── neo4j/                  # Neo4j数据库导出
│   │   └── neo4j.dump          # 知识图谱数据库备份
│   └── raw/                    # 原始抽取结果
│       ├── kh-trade-graph/     # 知识图谱数据
│       ├── results/            # 抽取结果文本文件
│       └── 数据格式.txt        # 数据格式说明文档
└── news/                       # 新闻数据
    ├── archived-khnews-related-filtered.csv  # 过滤后的新闻数据
    ├── news_content_03-kazinform.csv         # 哈萨克国际通讯社新闻
    └── news_content_03-mofcom.csv            # 商务部新闻
```

## 3. 核心功能模块详解

### 3.1 新闻爬虫模块

爬虫模块负责从多个数据源获取贸易和物流相关新闻，支持以下主要功能：

#### 3.1.1 Kazinform爬虫

```python
# KazinformCrawler类继承自基础Crawler类，实现对哈萨克国际通讯社网站的爬取
class KazinformCrawler(Crawler):
    def __init__(self):
        # 初始化存储组件为Elasticsearch
        self.storage = EsStorage()
    
    def get_urls(self):
        # 从指定页面爬取新闻URL列表
        # 提取标题、日期、封面和链接信息
    
    def get_content(self, url):
        # 获取单个新闻的详细内容
        # 提取标题、日期、描述、标签、HTML内容和纯文本内容
```

#### 3.1.2 Mofcom爬虫

```python
# MofcomCrawler类实现对中国商务部网站的爬取
class MofcomCrawler(Crawler):
    def __init__(self):
        # 初始化存储组件为Elasticsearch
        self.storage = EsStorage()
    
    def get_urls(self):
        # 通过API接口获取新闻列表
        # 从https://kz.mofcom.gov.cn/api-gateway获取数据
    
    def get_content(self, url):
        # 使用Chromium浏览器获取新闻内容
        # 提取完整的新闻信息
```

### 3.2 知识抽取模块

#### 3.2.1 LLM调用与提示工程

系统使用DeepSeek API进行知识抽取，通过精心设计的提示工程确保抽取质量：

```python
# 系统提示定义了知识抽取的规则和标准
system_prompt = """
# 知识图谱构建指令

## 1. 概述
你是一个专为结构化信息抽取而设计的顶级算法和专家模型，目标是根据输入的新闻文本，抽取贸易和物流相关或者有影响的知识信息，用于构建知识图谱。
...
## 6. 严格性要求
必须严格遵守上述规则，违反规则将导致处理终止
"""

# 调用LLM进行知识抽取
def call_llm(text, insert_no_think=None):
    prompt = generate_prompt(text, insert_no_think)
    response = openclient.chat.completions.create(
        stream=True,
        temperature=0,  # 零温度确保结果一致性
        max_tokens=4000,
        model="deepseek-chat",
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": prompt}
        ],
    )
    
    # 处理流式响应
    result = ''
    for chunk in response:
        content = chunk.choices[0].delta.content
        result += content
    
    return result
```

#### 3.2.2 输出解析与处理

系统实现了高效的正则表达式解析器，用于从LLM输出中提取实体和关系：

```python
# 关系模式正则表达式
relation_pattern = re.compile(r"\(([^()]+)\);\(([^()]*)\)")

# 解析关系信息
def parse_relations(relations_text):
    results = []
    for match in relation_pattern.finditer(relations_text):
        # 提取三元组和属性信息
        # 构建关系对象
    return results

# 解析LLM输出结果
def parse_llm_output(output_text: str):
    # 处理代码块格式
    output_text = output_text.strip()
    if output_text.startswith("```"):
        output_text = result_pattern.search(output_text).group(1)
    
    # 分割实体和关系部分
    lines = output_text.strip().split('---')
    if len(lines) < 2:
        return [], []
    
    # 解析实体和关系
    # 返回结构化数据
```

### 3.3 图数据库存储模块

系统使用Neo4j作为图数据库，实现了高效的数据存储和查询功能：

```python
# Neo4j连接配置（口令不入库，统一见项目根 config/.env，详见 config/README.md）
from _trade_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD), database=NEO4J_DATABASE)

# 插入数据到Neo4j
def insert_to_neo4j(driver, nodes, relations):
    with driver.session() as session:
        # 创建实体节点
        node_types = {}
        with session.begin_transaction() as tx:
            # 插入所有实体节点
            # 创建节点类型映射
            
            # 创建关系
            for relation in relations:
                # 构建Cypher查询
                # 处理属性
                # 执行插入操作
```

### 3.4 并行处理模块

为了提高处理效率，系统实现了基于多线程的并行处理机制：

```python
# 并行处理函数
def process(chunk_size=20, worker=0):
    dataframe = read_next_chunk(chunk_size)
    
    while dataframe is not None:
        # 数据预处理
        # 分块处理
        for idx, row in tqdm(rows, desc=f'chunk {_chunk_cnt} worker {worker}'):
            # 生成文件ID和路径
            # 检查文件是否已存在
            
            # 调用LLM进行知识抽取（带重试机制）
            retry_times = 0
            llm_result = None
            while retry_times < 4:
                try:
                    llm_result = builder2.call_llm(content)
                    break
                except Exception as e:
                    retry_times += 1
            
            # 处理结果
            if llm_result:
                # 保存输出
                # 解析实体和关系
                # 插入到Neo4j
```

## 4. 数据流程与工作流

### 4.1 整体数据流程

1. **数据采集阶段**：
   - 爬虫模块从Kazinform和Mofcom等网站爬取新闻数据
   - 原始数据存储到Elasticsearch中，支持后续检索和处理

2. **数据处理阶段**：
   - parallel_builder从CSV文件读取新闻内容
   - 使用线程池并行处理多个新闻文本
   - 每个文本通过call_llm函数调用LLM进行知识抽取

3. **知识存储阶段**：
   - 抽取结果保存为文本文件，存储在results目录
   - 同时将实体和关系插入到Neo4j图数据库
   - 构建完整的知识图谱网络

### 4.2 数据格式规范

#### 4.2.1 知识抽取结果格式

系统严格遵循以下输出格式规范：

```
# 实体部分
实体1,实体类型1
实体2,实体类型2
...
---
# 关系部分
(源实体,关系类型,目标实体);(属性1:"属性值1",属性2:"属性值2")
...
```

例如：
```
哈萨克斯坦,地点
哈农业部长萨帕罗夫,人物
小麦,货物
---
(哈萨克斯坦,收获,小麦);(时间:"2023年",数量:"1140万吨",原因:"主要产粮区遭遇25天长降雨")
(粮食收储企业,接收,小麦);(时间:"2023年",数量:"470万吨")
```

## 5. 技术特点与创新

### 5.1 多语言混合架构

系统采用Java、Go和Python混合开发，充分利用各语言优势：
- Python：用于数据处理、LLM调用和知识抽取
- Java/Go：用于系统核心组件和高性能服务

### 5.2 分布式与高并发设计

- 基于线程池的并行处理机制，显著提高处理效率
- 使用锁机制确保多线程环境下的数据一致性
- 实现了错误重试和异常处理机制，提高系统稳定性

### 5.3 知识抽取质量保障

- 精心设计的系统提示，确保LLM输出符合规范
- 严格的格式验证和错误处理
- 实体一致性保障和指代消解规范

### 5.4 完整的数据生命周期管理

- 从原始数据采集到知识图谱构建的全流程支持
- 数据备份和导出功能（neo4j.dump）
- 结构化和非结构化数据的统一管理

## 6. 应用场景与价值

### 6.1 贸易关系分析

系统可以自动从海量新闻中提取贸易相关实体和关系，帮助分析：
- 国家间贸易流量和趋势
- 主要贸易商品和类别
- 贸易政策和协议影响

### 6.2 物流网络可视化

通过构建的知识图谱，可以直观展示：
- 运输路线和节点
- 物流基础设施分布
- 运输方式偏好和变化

### 6.3 决策支持

为政策制定者和企业提供：
- 基于数据的贸易趋势分析
- 潜在风险和机会识别
- 政策影响评估

## 7. 系统配置与部署

### 7.1 主要配置参数

- Neo4j连接配置：主机、端口、认证信息和数据库名
- Elasticsearch配置：索引名称和映射定义
- LLM API配置：基础URL和API密钥
- 爬虫配置：浏览器路径、服务器端口和Consul配置

### 7.2 扩展性考虑

- 支持添加新的数据源和爬虫
- 可替换不同的LLM模型
- 模块化设计便于功能扩展和维护

## 8. 总结与展望

pjcait系统是一个功能完善的知识图谱构建平台，特别专注于贸易和物流领域。通过结合爬虫技术、大语言模型和图数据库，实现了从非结构化文本到结构化知识的高效转换。系统的并行处理能力和错误恢复机制确保了在处理大规模数据时的稳定性和效率。

未来，系统可以进一步扩展：
- 增加更多数据源和语言支持
- 改进实体链接和关系推理能力
- 开发更直观的可视化界面和分析工具
- 集成实时数据更新和知识图谱增量更新机制