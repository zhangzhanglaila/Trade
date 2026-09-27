# 快速启动指南

## 前置条件

1. **Java 环境**：确保已安装 JDK 1.8 或更高版本
   ```bash
   java -version
   ```

2. **Maven**：确保已安装 Maven 3.6 或更高版本
   ```bash
   mvn -version
   ```

3. **Python 环境**：确保已安装 Python 3.9+ 及所需依赖
   ```bash
   python --version
   pip install pandas numpy torch scikit-learn matplotlib tqdm flask pymysql
   ```

## 配置步骤

### 1. 修改配置文件

编辑 `src/main/resources/application.yml`，修改以下路径：

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
    port: 5000
```

### 2. 准备数据文件

确保 CSV 文件路径正确，文件格式符合要求。

## 启动服务

### 方式一：使用 Maven 运行

```bash
cd TradeSpringBoot
mvn spring-boot:run
```

### 方式二：打包后运行

```bash
# 打包
mvn clean package

# 运行
java -jar target/trade-springboot-1.0.0.jar
```

## 使用 API

### 1. 启动训练任务

```bash
curl -X POST http://localhost:8080/api/training/start
```

响应示例：
```json
{
  "success": true,
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "训练任务已启动"
}
```

### 2. 查询任务状态

使用返回的 `taskId` 查询状态：

```bash
curl http://localhost:8080/api/training/status/550e8400-e29b-41d4-a716-446655440000
```

### 3. 查看所有任务

```bash
curl http://localhost:8080/api/training/status
```

## 训练流程说明

系统会自动按以下顺序执行：

1. ✅ 训练进口数量模型
2. ✅ 训练进口单价模型
3. ✅ 训练出口数量模型
4. ✅ 训练出口单价模型
5. 🤖 自动部署 Flask API（检测到健康后返回访问地址）
6. ✅ 任务完成

## 常见问题

### Q: Python 脚本执行失败
**A**: 检查 Python 环境是否正确配置，确保所有依赖包已安装。

### Q: 文件路径错误
**A**: 确保 `application.yml` 中的路径使用正斜杠 `/` 或双反斜杠 `\\`，路径必须存在。

### Q: 模型保存失败
**A**: 检查模型输出目录是否有写入权限，系统会自动创建目录。

### Q: 端口被占用
**A**: 修改 `application.yml` 中的 `server.port` 配置。

### 4. 上传/替换 CSV 数据

```bash
curl -X POST http://localhost:8080/api/data/upload \
  -F "mergedInput=@你的路径/merged_input.csv" \
  -F "mergedOutput=@你的路径/merged_output.csv"
```

系统会将文件保存到 `application.yml` 中配置的路径，下次训练会直接使用新数据。

