# 本体管理新接口测试指南

## 一、启动后端服务

### 1. 编译启动
```bash
# 进入项目目录
cd D:\trade-back\TDProject-main

# 编译（需要安装Maven）
mvn clean compile

# 启动服务
mvn spring-boot:run
```

### 2. 检查服务启动
- 服务默认运行在 `http://localhost:8080`
- 查看控制台日志确认无报错
- 确认图数据库目录 `${trade.home}/runtime/ontology-graph-store` 已创建

---

## 二、接口测试方法

### 方法1: 使用 Swagger UI（推荐）

**访问地址**: http://localhost:8080/swagger-ui.html

**操作步骤**:
1. 浏览器打开 Swagger UI 页面
2. 找到 `本体管理` 标签
3. 展开新接口进行测试：
   - `POST /ontology/create-with-file`
   - `GET /ontology/check-name/{ontologyName}`
   - `GET /ontology/{ontologyName}/versions`
   - `POST /ontology/import-to-house`
   - `POST /ontology/rollback-version`
   - `GET /ontology/{id}/export-file`

---

### 方法2: 使用 curl 命令行测试

#### 1. 测试创建本体（带文件）
```bash
# 准备测试文件 test.owl（可以是一个空的XML文件）
curl -X POST "http://localhost:8080/ontology/create-with-file" \
  -F "ontologyName=测试本体" \
  -F "creatorName=管理员" \
  -F "version=1.0" \
  -F "namespaceUri=http://example.org/test" \
  -F "fileFormat=OWL" \
  -F "file=@test.owl"
```

#### 2. 测试检查名称是否存在
```bash
curl -X GET "http://localhost:8080/ontology/check-name/测试本体"
```

**预期响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "exists": true,
    "namedGraphs": ["http://example.org/ontology/测试本体/v1.0"],
    "currentVersion": "1.0"
  }
}
```

#### 3. 测试获取版本历史
```bash
curl -X GET "http://localhost:8080/ontology/测试本体/versions"
```

**预期响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "version": "1.0",
      "creator": "管理员",
      "status": 1,
      "createTime": "2026-03-11 10:00:00"
    }
  ]
}
```

#### 4. 测试本体入库
```bash
curl -X POST "http://localhost:8080/ontology/import-to-house" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceOntologyId": 1,
    "newVersion": "1.1",
    "remark": "版本更新测试"
  }'
```

#### 5. 测试版本回滚
```bash
curl -X POST "http://localhost:8080/ontology/rollback-version" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceOntologyId": 1,
    "newVersion": "1.0"
  }'
```

#### 6. 测试导出文件
```bash
# 导出OWL格式
curl -X GET "http://localhost:8080/ontology/1/export-file?format=OWL" \
  -o exported_ontology.owl

# 导出Turtle格式
curl -X GET "http://localhost:8080/ontology/1/export-file?format=TTL" \
  -o exported_ontology.ttl
```

---

### 方法3: 使用 PowerShell 测试脚本

创建测试脚本 `test_api.ps1`:

```powershell
$baseUrl = "http://localhost:8080"

Write-Host "=== 测试本体管理新接口 ===" -ForegroundColor Green

# 1. 检查名称（假设不存在）
Write-Host "`n1. 检查名称 'TestOntology' 是否存在..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$baseUrl/ontology/check-name/TestOntology" -Method GET
Write-Host "响应: $($response | ConvertTo-Json -Depth 3)"

# 2. 创建本体（不带文件）
Write-Host "`n2. 创建本体..." -ForegroundColor Yellow
$boundary = [System.Guid]::NewGuid().ToString()
$bodyLines = @(
    "--$boundary",
    'Content-Disposition: form-data; name="ontologyName"',
    "",
    "TestOntology",
    "--$boundary",
    'Content-Disposition: form-data; name="creatorName"',
    "",
    "TestUser",
    "--$boundary",
    'Content-Disposition: form-data; name="version"',
    "",
    "1.0",
    "--$boundary--"
)
$body = $bodyLines -join "`r`n"
$response = Invoke-RestMethod -Uri "$baseUrl/ontology/create-with-file" `
    -Method POST `
    -ContentType "multipart/form-data; boundary=$boundary" `
    -Body $body
Write-Host "响应: $($response | ConvertTo-Json -Depth 3)"
$ontologyId = $response.data.id

# 3. 再次检查名称（应该存在）
Write-Host "`n3. 再次检查名称（应该存在）..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$baseUrl/ontology/check-name/TestOntology" -Method GET
Write-Host "响应: $($response | ConvertTo-Json -Depth 3)"

# 4. 获取版本历史
Write-Host "`n4. 获取版本历史..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$baseUrl/ontology/TestOntology/versions" -Method GET
Write-Host "响应: $($response | ConvertTo-Json -Depth 3)"

# 5. 入库测试
Write-Host "`n5. 入库测试..." -ForegroundColor Yellow
$body = @{
    sourceOntologyId = $ontologyId
    newVersion = "1.1"
    remark = "测试入库"
} | ConvertTo-Json
$response = Invoke-RestMethod -Uri "$baseUrl/ontology/import-to-house" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
Write-Host "响应: $($response | ConvertTo-Json -Depth 3)"

Write-Host "`n=== 测试完成 ===" -ForegroundColor Green
```

运行脚本:
```powershell
.\test_api.ps1
```

---

## 三、常见问题排查

### 1. 端口冲突
```bash
# 检查8080端口是否被占用
netstat -ano | findstr :8080

# 修改端口（application.yaml）
server:
  port: 8081  # 改为其他端口
```

### 2. 图数据库目录权限
```bash
# 检查目录是否存在，没有则创建
mkdir ${trade.home}\runtime\ontology-graph-store

# 检查写入权限（PowerShell）
Test-Path "${trade.home}\runtime\ontology-graph-store" -PathType Container
```

### 3. 依赖下载失败
```bash
# 清理Maven缓存重新下载
mvn clean dependency:purge-local-repository
mvn clean install
```

### 4. 编译错误
检查以下类是否正确创建：
- `com.example.tdproject.ontology.enums.RdfFileFormat`
- `com.example.tdproject.ontology.dto.*`
- `com.example.tdproject.ontology.repository.*`

---

## 四、验证清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 服务启动无报错 | ⬜ | 控制台无异常堆栈 |
| Swagger UI 可访问 | ⬜ | http://localhost:8080/swagger-ui.html |
| 创建本体接口 | ⬜ | 返回200，data包含id |
| 检查名称接口 | ⬜ | 返回exists字段 |
| 版本历史接口 | ⬜ | 返回版本列表 |
| 入库接口 | ⬜ | 返回"入库成功" |
| 回滚接口 | ⬜ | 返回"回滚成功" |
| 导出接口 | ⬜ | 下载文件成功 |

---

## 五、前端联调

后端确认无误后，前端需要修改API配置：

```javascript
// 确保前端请求的baseURL正确
const API_BASE = 'http://localhost:8080';

// 测试跨域是否配置正确
fetch(`${API_BASE}/ontology/check-name/test`)
  .then(r => r.json())
  .then(data => console.log(data));
```

如果跨域问题，检查后端 `CorsConfig.java` 是否允许前端域名。
