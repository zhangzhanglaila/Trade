# 贸易预测服务 - SpringBoot 后端

基于 SpringBoot 框架开发的贸易预测模型训练和部署管理系统。

## 项目结构

```
TradeSpringBoot/
├── src/
│   └── main/
│       ├── java/com/trade/
│       │   ├── TradeApplication.java          # 主启动类
│       │   ├── config/
│       │   │   └── PythonScriptProperties.java # Python 脚本配置类
│       │   ├── controller/
│       │   │   ├── TrainingController.java     # 训练任务控制器
│       │   │   └── DataUploadController.java   # 数据上传控制器
│       │   ├── service/
│       │   │   ├── PythonScriptService.java    # Python/Flask 执行服务
│       │   │   ├── TrainingTaskService.java    # 训练任务管理服务
│       │   │   └── DataUploadService.java      # CSV 替换服务
│       │   ├── model/
│       │   │   ├── ScriptExecutionResult.java  # 脚本执行结果模型
│       │   │   └── TrainingTaskStatus.java     # 训练任务状态模型
│       │   └── enums/
│       │       └── ScriptStatus.java           # 脚本状态枚举
│       └── resources/
│           └── application.yml                 # 配置文件
├── python-scripts/
│   ├── Trade_Transformer_LSTM_price.py        # 单价训练脚本
│   ├── Trade_Transformer_LSTM_quantity.py     # 数量训练脚本
│   └── app.py                                  # Flask API 部署脚本（需从原项目复制）
├── pom.xml                                     # Maven 配置文件
└── README.md                                   # 项目说明文档
```

## 功能特性

1. **自动化训练流程**：按顺序执行进口数量、进口单价、出口数量、出口单价四个模型的训练
2. **参数化配置**：通过配置文件管理 CSV 路径、模型目录以及 Flask 服务参数
3. **自动部署 Flask**：训练到第 5 步自动后台启动 `app.py`，并做健康检查
4. **远程数据上传**：内置 `/api/data/upload` 接口，可远程替换 `merged_input/output.csv`
5. **状态跟踪**：实时跟踪训练任务执行状态，支持查询任务进度
6. **异步执行**：训练任务异步执行，不阻塞主线程

## 环境要求

- JDK 1.8+
- Maven 3.6+
- Python 3.9+
- PyTorch
- Flask（用于 API 部署）

## 配置说明

编辑 `src/main/resources/application.yml` 文件，配置以下参数：

```yaml
python:
  interpreter: ${PYTHON_EXE:python}     # 或直接写 /usr/bin/python3

  script:
    base-path: ${trade.home}/src/backend-training/python-scripts
    quantity-train: Trade_Transformer_LSTM_quantity.py
    price-train: Trade_Transformer_LSTM_price.py
    api-deploy: app.py

  data:
    import-data:
      csv-path: ${trade.home}/src/backend-training/data/进口/merged_input.csv
    export-data:
      csv-path: ${trade.home}/src/backend-training/data/进口/merged_output.csv

  model:
    import-model:
      output-path: model/trade_Transformer/in
    export-model:
      output-path: model/trade_Transformer/out

  flask:
    enabled: true
    host: 0.0.0.0
    port: 5000
    readiness-path: /health
    startup-timeout-seconds: 60
```

### 重要配置项说明

1. **interpreter**：Python 解释器路径，如果 Python 不在系统 PATH 中，需要指定完整路径（如：`C:/Python39/python.exe`）
2. **data.import.csv-path**：进口数据 CSV 文件路径
3. **data.export.csv-path**：出口数据 CSV 文件路径
4. **model.import.output-path**：进口模型输出目录
5. **model.export.output-path**：出口模型输出目录

## 编译和运行

### 1. 编译项目

```bash
mvn clean package
```

### 2. 运行项目

```bash
java -jar target/trade-springboot-1.0.0.jar
```

或者使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

### 3. 访问服务

服务启动后，默认运行在 `http://localhost:8080/api`

## API 接口

### 1. 启动训练任务

**接口地址**：`POST /api/training/start`

**请求示例**：
```bash
curl -X POST http://localhost:8080/api/training/start
```

**响应示例**：
```json
{
  "success": true,
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "训练任务已启动"
}
```

### 2. 查询任务状态

**接口地址**：`GET /api/training/status/{taskId}`

**请求示例**：
```bash
curl http://localhost:8080/api/training/status/550e8400-e29b-41d4-a716-446655440000
```

**响应示例**：
```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "overallStatus": "RUNNING",
  "startTime": "2024-01-01T10:00:00",
  "endTime": null,
  "totalDuration": null,
  "currentStep": 2,
  "totalSteps": 6,
  "progress": 33.33,
  "scriptResults": [
    {
      "scriptName": "Trade_Transformer_LSTM_quantity.py",
      "status": "SUCCESS",
      "startTime": "2024-01-01T10:00:00",
      "endTime": "2024-01-01T10:30:00",
      "duration": 1800,
      "errorMessage": null,
      "output": "..."
    }
  ],
  "errorMessage": null
}
```

### 3. 查询所有任务状态

**接口地址**：`GET /api/training/status`

**请求示例**：
```bash
curl http://localhost:8080/api/training/status
```

**响应示例**：
```json
[
  {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "overallStatus": "SUCCESS",
    ...
  }
]
```

### 4. 上传/替换数据文件

**接口地址**：`POST /api/data/upload`

**请求方式**：`multipart/form-data`

| 字段名 | 说明 | 是否必填 |
| --- | --- | --- |
| `mergedInput` | 新的 `merged_input.csv` 文件 | 否 |
| `mergedOutput` | 新的 `merged_output.csv` 文件 | 否 |

**示例**：

```bash
curl -X POST http://localhost:8080/api/data/upload ^
  -F "mergedInput=@D:/tmp/merged_input.csv" ^
  -F "mergedOutput=@D:/tmp/merged_output.csv"
```

**响应示例**：

```json
{
  "success": true,
  "message": "数据文件上传成功",
  "files": [
    {
      "alias": "merged_input",
      "originalFilename": "merged_input.csv",
      "size": 123456,
      "savedPath": "/opt/trade/src/backend-training/data/进口/merged_input.csv"
    }
  ]
}
```

## 状态说明

- **PENDING**：等待执行
- **RUNNING**：运行中
- **SUCCESS**：执行成功
- **FAILED**：执行失败
- **CANCELLED**：已取消

## 训练流程

系统会按以下顺序执行训练任务：

1. **步骤 1**：训练进口数量模型
2. **步骤 2**：训练进口单价模型
3. **步骤 3**：训练出口数量模型
4. **步骤 4**：训练出口单价模型
5. **步骤 5**：自动部署 Flask API（健康检查就绪后返回服务 URL）
6. **步骤 6**：任务完成

每个步骤都会自动读取对应的 CSV 数据文件，并将训练好的模型保存到指定的输出目录。

## 注意事项

1. **路径配置**：确保配置的 CSV 文件路径和模型输出路径正确，路径中的反斜杠需要使用正斜杠或双反斜杠
2. **Python 环境**：确保 Python 环境已安装所有必需的依赖包（pandas, numpy, torch, sklearn 等）
3. **模型输出目录**：系统会自动创建模型输出目录，但需要确保有写入权限
4. **训练时间**：模型训练可能需要较长时间，建议在服务器环境下运行
5. **Flask API**：系统会自动通过 Python 进程后台启动 `app.py` 并保持运行

## 日志

日志文件保存在 `logs/trade-service.log`，可以通过日志查看详细的执行信息。

## 故障排查

1. **Python 脚本执行失败**：检查 Python 环境是否正确配置，依赖包是否安装完整
2. **文件路径错误**：检查 `application.yml` 中的路径配置是否正确
3. **模型保存失败**：检查模型输出目录是否有写入权限
4. **任务状态不更新**：检查日志文件，查看是否有异常信息

## 开发说明

### 添加新的训练脚本

1. 将脚本文件放入 `python-scripts` 目录
2. 在 `application.yml` 中添加脚本配置
3. 在 `PythonScriptService` 中添加脚本执行方法
4. 在 `TrainingTaskService` 中集成到训练流程

### 自定义配置

可以通过修改 `PythonScriptProperties` 类来添加新的配置项，并在 `application.yml` 中配置。

## 许可证

本项目仅供学习和研究使用。

