# 本体管理后端修改说明

## 概述
根据前端需求，后端新增了6个核心接口，支持本体文件上传解析、图数据库存储、版本控制和导出功能。

## 修改内容汇总

### 1. 依赖添加 (pom.xml)
- 添加了 Apache Jena 4.10.0 相关依赖
  - `jena-core`: RDF核心功能
  - `jena-arq`: SPARQL查询支持
  - `jena-tdb`: 本地图数据库存储
  - `jena-ontology`: OWL本体支持
  - `jena-rdfconnection`: RDF连接管理

### 2. 配置文件 (application.yaml)
- 新增图数据库配置项
```yaml
ontology:
  graph:
    store-path: ${trade.home}/runtime/ontology-graph-store  # 图数据存储路径
    named-graph-prefix: http://example.org/ontology/  # 命名图URI前缀
```

### 3. 新增枚举类
**路径**: `com.example.tdproject.ontology.enums.RdfFileFormat`

支持4种RDF文件格式：
- `OWL` (.owl) - OWL/XML格式
- `RDF` (.rdf) - RDF/XML格式
- `TTL` (.ttl) - Turtle格式
- `NT` (.nt) - N-Triples格式

### 4. 新增DTO类
**路径**: `com.example.tdproject.ontology.dto`

| 类名 | 用途 |
|------|------|
| `CheckNameResult` | 本体名称检查结果（exists, namedGraphs, currentVersion） |
| `VersionInfo` | 版本信息（id, version, creator, status, 时间等） |
| `ImportToHouseRequest` | 入库请求（sourceOntologyId, newVersion, remark） |
| `RollbackVersionRequest` | 回滚请求（sourceOntologyId, newVersion） |

### 5. 图数据库操作层
**接口**: `com.example.tdproject.ontology.repository.GraphRepository`
**实现**: `com.example.tdproject.ontology.repository.JenaGraphRepositoryImpl`

提供以下功能：
- 命名图CRUD操作
- RDF文件解析和加载
- 模型导入导出
- 命名图复制
- 三元组统计

### 6. OntologyService 新增方法

```java
// 1. 创建本体并解析文件到图数据库
Ontology createOntologyWithFile(String ontologyName, String creatorName, String version, 
                                String namespaceUri, String fileFormat, MultipartFile file)

// 2. 检查本体名称在图数据库中是否存在
CheckNameResult checkOntologyNameInGraph(String ontologyName)

// 3. 获取本体版本历史（用于入库弹窗）
List<VersionInfo> getOntologyVersionHistory(String ontologyName)

// 4. 将本体导入图数据库（入库操作）
boolean importOntologyToHouse(ImportToHouseRequest request)

// 5. 按版本号回滚本体版本
boolean rollbackOntologyByVersion(RollbackVersionRequest request)

// 6. 导出本体文件（OWL/RDF/TTL/NT格式）
void exportOntologyFile(Long id, RdfFileFormat format, HttpServletResponse response)
```

### 7. OntologyController 新增接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/ontology/create-with-file` | 创建本体（带文件上传） |
| GET | `/ontology/check-name/{ontologyName}` | 检查本体名称是否存在 |
| GET | `/ontology/{ontologyName}/versions` | 获取本体版本历史 |
| POST | `/ontology/import-to-house` | 本体入库到图数据库 |
| POST | `/ontology/rollback-version` | 按版本号回滚 |
| GET | `/ontology/{id}/export-file` | 导出本体文件 |

## 数据映射关系

| 前端字段 | 后端字段 | 说明 |
|---------|---------|------|
| ontologyName | projectName | 本体名称/项目名称 |
| creatorName | creator | 创建人 |
| version | versionNumber | 版本号 |
| updateTime | modifyTime | 更新时间 |

## 数据库设计

### MySQL (ontology表)
- 存储本体元数据（名称、创建人、版本号等）
- 版本控制字段：versionStatus(0=历史, 1=当前), parentId

### 图数据库 (Apache Jena TDB)
- 存储RDF三元组数据
- 命名图URI格式：`http://example.org/ontology/{ontologyName}/v{version}`
- 本地存储路径：配置项 `ontology.graph.store-path`

## 接口详细说明

### 1. POST /ontology/create-with-file
**Content-Type**: `multipart/form-data`

**参数**:
- `ontologyName` (required): 本体名称
- `creatorName` (required): 创建人
- `version` (optional): 版本号，默认1.0
- `namespaceUri` (optional): 命名空间URI
- `fileFormat` (optional): 文件格式(OWL/RDF/TTL/NT)
- `file` (optional): 上传的本体文件

**响应**: `Result<Ontology>`

### 2. GET /ontology/check-name/{ontologyName}
**响应**: `Result<CheckNameResult>`
```json
{
  "exists": true,
  "namedGraphs": ["http://example.org/ontology/Test/v1.0"],
  "currentVersion": "1.0"
}
```

### 3. GET /ontology/{ontologyName}/versions
**响应**: `Result<List<VersionInfo>>`

### 4. POST /ontology/import-to-house
**Content-Type**: `application/json`

**请求体**:
```json
{
  "sourceOntologyId": 1,
  "newVersion": "1.1",
  "remark": "更新说明"
}
```

### 5. POST /ontology/rollback-version
**Content-Type**: `application/json`

**请求体**:
```json
{
  "sourceOntologyId": 1,
  "newVersion": "1.0"
}
```

### 6. GET /ontology/{id}/export-file?format=OWL
**Query参数**:
- `format` (required): 导出格式(OWL/RDF/TTL/NT)

**响应**: 文件流下载

## 使用说明

1. **首次部署**:
   - 确保 `${trade.home}/runtime/ontology-graph-store` 目录存在或有写入权限
   - 启动时会自动初始化图数据库

2. **文件上传限制**:
   - 已在application.yaml中配置: `max-file-size: 10MB`, `max-request-size: 100MB`

3. **并发处理**:
   - 入库和回滚操作使用`@Transactional`保证MySQL和图数据库一致性
   - 图数据库操作异常时会回滚MySQL事务

## 技术栈

- **图数据库**: Apache Jena TDB (嵌入式，无需额外服务)
- **RDF处理**: Apache Jena
- **存储**: MySQL (元数据) + 本地文件 (图数据)

## 注意事项

1. 图数据库使用本地文件存储，生产环境建议定期备份 `store-path` 目录
2. 大文件解析可能需要较长时间，建议前端添加加载状态
3. 如果图数据库目录损坏，可能需要重新入库本体数据
