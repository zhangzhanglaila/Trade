# 项目结构说明

## 目录结构

```
TradeSpringBoot/
│
├── src/main/
│   ├── java/com/trade/
│   │   ├── TradeApplication.java              # SpringBoot 主启动类
│   │   │
│   │   ├── config/
│   │   │   └── PythonScriptProperties.java    # Python 脚本配置属性类
│   │   │                                       # 从 application.yml 读取配置
│   │   │
│   │   ├── controller/
│   │   │   ├── TrainingController.java        # 训练任务接口
│   │   │   │                                   # - POST /training/start
│   │   │   │                                   # - GET  /training/status/{taskId}
│   │   │   │                                   # - GET  /training/status
│   │   │   └── DataUploadController.java      # 数据上传接口
│   │   │                                       # - POST /data/upload
│   │   │
│   │   ├── service/
│   │   │   ├── PythonScriptService.java       # Python/Flask 执行服务
│   │   │   │                                   # - 执行训练脚本
│   │   │   │                                   # - 自动部署 Flask 并做健康检查
│   │   │   ├── TrainingTaskService.java       # 训练任务管理服务
│   │   │   │                                   # - 管理训练任务生命周期
│   │   │   │                                   # - 按顺序执行训练步骤
│   │   │   │                                   # - 跟踪任务状态
│   │   │   └── DataUploadService.java         # 数据文件替换服务
│   │   │                                       # - 校验 & 写入 merged_input/output.csv
│   │   │
│   │   ├── model/
│   │   │   ├── ScriptExecutionResult.java     # 脚本执行结果模型
│   │   │   │                                   # - 脚本名称、状态、耗时等
│   │   │   │
│   │   │   └── TrainingTaskStatus.java        # 训练任务状态模型
│   │   │                                       # - 任务ID、总体状态、进度等
│   │   │
│   │   └── enums/
│   │       └── ScriptStatus.java              # 脚本状态枚举
│   │                                           # PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
│   │
│   └── resources/
│       └── application.yml                     # 应用配置文件
│                                               # - Python 脚本路径配置
│                                               # - CSV 数据路径配置
│                                               # - 模型输出路径配置
│
├── python-scripts/                             # Python 脚本目录
│   ├── Trade_Transformer_LSTM_price.py        # 单价训练脚本
│   ├── Trade_Transformer_LSTM_quantity.py     # 数量训练脚本
│   └── app.py                                  # Flask API 部署脚本（需从原项目复制）
│
├── pom.xml                                     # Maven 项目配置文件
├── .gitignore                                  # Git 忽略文件配置
├── README.md                                   # 项目说明文档
├── QUICKSTART.md                               # 快速启动指南
└── PROJECT_STRUCTURE.md                        # 本文件
```

## 核心组件说明

### 1. TradeApplication
SpringBoot 应用主类，负责启动整个应用。

### 2. PythonScriptProperties
配置属性类，从 `application.yml` 读取配置：
- Python 解释器路径
- 脚本文件路径
- CSV 数据文件路径
- 模型输出路径

### 3. TrainingController
REST API 控制器，提供以下接口：
- `POST /api/training/start` - 启动训练任务
- `GET /api/training/status/{taskId}` - 查询指定任务状态
- `GET /api/training/status` - 查询所有任务状态

### 4. PythonScriptService
Python 脚本执行服务，负责：
- 执行 Python 脚本
- 构建命令行参数
- 捕获脚本输出和错误
- 处理脚本执行超时

### 5. TrainingTaskService
训练任务管理服务，负责：
- 创建和管理训练任务
- 按顺序执行训练步骤
- 跟踪任务执行状态
- 更新任务进度

## 数据流

```
前端请求
    ↓
TrainingController
    ↓
TrainingTaskService (创建任务，异步执行)
    ↓
PythonScriptService (执行 Python 脚本)
    ↓
Python 训练脚本 (训练模型)
    ↓
模型文件保存到指定目录
    ↓
任务状态更新
    ↓
前端查询状态接口获取结果
```

## 配置流程

1. **读取配置**：`PythonScriptProperties` 从 `application.yml` 读取配置
2. **构建参数**：`PythonScriptService` 根据配置构建脚本执行参数
3. **执行脚本**：使用 `ProcessBuilder` 执行 Python 脚本
4. **保存模型**：Python 脚本将模型保存到配置的输出路径

## 训练流程

1. **步骤 1**：训练进口数量模型
   - 读取：`data.import.csv-path`
   - 输出：`model.import.output-path/model_quantity.pth`

2. **步骤 2**：训练进口单价模型
   - 读取：`data.import.csv-path`
   - 输出：`model.import.output-path/model_price.pth`

3. **步骤 3**：训练出口数量模型
   - 读取：`data.export.csv-path`
   - 输出：`model.export.output-path/model_quantity.pth`

4. **步骤 4**：训练出口单价模型
   - 读取：`data.export.csv-path`
   - 输出：`model.export.output-path/model_price.pth`

5. **步骤 5**：自动部署 Flask API（健康检查 /health）

6. **步骤 6**：任务完成

## 扩展说明

### 添加新的训练脚本

1. 将脚本文件放入 `python-scripts` 目录
2. 在 `application.yml` 中添加脚本配置
3. 在 `PythonScriptService` 中添加参数构建方法
4. 在 `TrainingTaskService` 中集成到训练流程

### 修改训练顺序

编辑 `TrainingTaskService.executeTrainingTask()` 方法，调整步骤顺序。

### 添加新的配置项

1. 在 `PythonScriptProperties` 中添加属性
2. 在 `application.yml` 中添加配置
3. 在相关服务中使用配置

