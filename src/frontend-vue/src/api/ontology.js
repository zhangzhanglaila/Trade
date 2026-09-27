/* ---------------- 统一请求 ---------------- */
import request from '@/utils/request'   // axios 封装实例
const BASE_URL = '/ontology'

// ==================== 原有接口（保持不变）====================

// 1. 新增本体
export const addOntology = data => request.post(`${BASE_URL}/add`, data)

// 2. 修改本体（自动升版）
export const updateOntology = data => request.put(`${BASE_URL}/update`, data)

// 3. 单条删除
export const delOntology = id => request.delete(`${BASE_URL}/${id}`)

// 4. 批量删除
export const batchDelOntology = ids =>
  request.delete(`${BASE_URL}/batch`, { data: ids })

// 5. 分页查询（当前版本）
export const getPageOntology = params =>
  request.get(`${BASE_URL}/page`, { params })

// 6. 按 ID 查询详情
export const getOntologyById = id =>
  request.get(`${BASE_URL}/${id}`)

// 7. 查询某项目的全部版本
export const getVersions = projectName =>
  request.get(`${BASE_URL}/versions/${encodeURIComponent(projectName)}`)

// 8. 版本回滚
export const rollbackVersion = versionId =>
  request.post(`${BASE_URL}/rollback`, null, { params: { versionId } })

/* 9. 按创建人搜索 */
export const searchByCreator = (params) =>
  request.get(`${BASE_URL}/search-by-creator`, { params })

/* 10. 按项目名模糊搜索 */
export const searchByProjectName = (params) =>
  request.get(`${BASE_URL}/search-by-project`, { params })

// 11. 批量标记历史版本
export const markAllAsHistory = projectName =>
  request.post(`${BASE_URL}/mark-all-history`, null, {
    params: { projectName }
  })

// 12. 导入本体（存在则更新，不存在则新增）- 支持 RDF 和 CSV
export const importOntology = (file, projectName, creator, csvMode = 'original') => {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('projectName', projectName)
  fd.append('creator', creator)
  fd.append('csvMode', csvMode)
  return request.post(`${BASE_URL}/import`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000  // CSV转换可能需要较长时间，设置2分钟超时
  })
}

// 12.1 验证 RDF 文件格式
export const validateRdfFormat = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`${BASE_URL}/validate-rdf`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000  // 验证接口30秒超时
  })
}

// 13. 按 ID 导出 Excel
export const exportOntology = id =>
  request.get(`${BASE_URL}/export/${id}`, {
    responseType: 'blob'
  })

// 14. 导出全部当前版本 Excel
export const exportAllOntology = () =>
  request.get(`${BASE_URL}/export/all`, {
    responseType: 'blob'
  })

// ==================== 新增接口（本体管理页面改造）====================

/**
 * 创建本体（支持文件上传）
 * @param {FormData} formData - 包含本体信息和文件
 * @returns {Promise}
 */
export const createOntologyWithFile = (formData) => {
  return request.post(`${BASE_URL}/create-with-file`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 检查本体名称是否已存在于图数据库
 * @param {string} ontologyName - 本体名称
 * @returns {Promise} { exists: boolean, namedGraphs: string[] }
 */
export const checkOntologyName = (ontologyName) => {
  return request.get(`${BASE_URL}/check-name/${encodeURIComponent(ontologyName)}`)
}

/**
 * 获取本体版本历史
 * @param {string} ontologyName - 本体名称
 * @returns {Promise} 版本历史列表
 */
export const getOntologyVersion = (ontologyName) => {
  return request.get(`${BASE_URL}/${encodeURIComponent(ontologyName)}/versions`)
}

/**
 * 本体入库（新增/更新版本）
 * @param {Object} data - 包含 sourceOntologyId 和 newVersion
 * @returns {Promise}
 */
export const importOntologyToHouse = (data) => {
  return request.post(`${BASE_URL}/import-to-house`, data)
}

/**
 * 版本回滚
 * @param {Object} data - { sourceOntologyId, newVersion }
 * @returns {Promise}
 */
export const rollbackOntology = (data) => {
  return request.post(`${BASE_URL}/rollback-version`, data)
}

/**
 * 导出本体文件（OWL/RDF/Turtle/N-Triples格式）
 * @param {number} ontologyId - 本体ID
 * @param {string} format - 导出格式：OWL/RDF/Turtle/N-Triples
 * @returns {Promise} 文件流
 */
export const exportOntologyFile = (ontologyId, format) => {
  return request.get(`${BASE_URL}/${ontologyId}/export-file`, {
    params: { format },
    responseType: 'blob'
  })
}

/**
 * 获取数据源列表（用于入库时选择目标数据源）
 * @returns {Promise} 数据源列表
 */
export const getDatabase = () => {
  return request.get('/datasource/list')
}

// ==================== 本体详情页接口 ====================

/**
 * 获取本体统计数据
 * @param {number} ontologyId - 本体ID
 * @returns {Promise} { classCount, individualCount, propertyCount, tripleCount }
 */
export const getOntologyStats = (ontologyId) => {
  return request.get(`${BASE_URL}/${ontologyId}/stats`)
}

/**
 * 获取本体类列表
 * @param {number} ontologyId - 本体ID
 * @param {string} keyword - 搜索关键词（可选）
 * @returns {Promise} 类信息列表
 */
export const getOntologyClasses = (ontologyId, keyword) => {
  return request.get(`${BASE_URL}/${ontologyId}/classes`, {
    params: { keyword }
  })
}

/**
 * 获取本体实例列表
 * @param {number} ontologyId - 本体ID
 * @param {string} classUri - 所属类URI（可选）
 * @param {string} keyword - 搜索关键词（可选）
 * @returns {Promise} 实例信息列表
 */
export const getOntologyIndividuals = (ontologyId, classUri, keyword) => {
  return request.get(`${BASE_URL}/${ontologyId}/individuals`, {
    params: { classUri, keyword }
  })
}

/**
 * 获取本体属性列表
 * @param {number} ontologyId - 本体ID
 * @param {string} type - 属性类型：object/datatype/annotation（可选）
 * @param {string} keyword - 搜索关键词（可选）
 * @returns {Promise} 属性信息列表
 */
export const getOntologyProperties = (ontologyId, type, keyword) => {
  return request.get(`${BASE_URL}/${ontologyId}/properties`, {
    params: { type, keyword }
  })
}

// ==================== 类管理CRUD接口 ====================

/**
 * 创建类
 * @param {number} ontologyId - 本体ID
 * @param {Object} classInfo - 类信息 { name, parentId, description }
 * @returns {Promise} 类的URI
 */
export const createOntologyClass = (ontologyId, classInfo) => {
  return request.post(`${BASE_URL}/${ontologyId}/classes`, classInfo)
}

/**
 * 更新类
 * @param {number} ontologyId - 本体ID
 * @param {string} classUri - 类URI
 * @param {Object} classInfo - 类信息
 * @returns {Promise}
 */
export const updateOntologyClass = (ontologyId, classUri, classInfo) => {
  return request.put(`${BASE_URL}/${ontologyId}/classes?classUri=${encodeURIComponent(classUri)}`, classInfo)
}

/**
 * 删除类
 * @param {number} ontologyId - 本体ID
 * @param {string} classUri - 类URI
 * @returns {Promise}
 */
export const deleteOntologyClass = (ontologyId, classUri) => {
  return request.delete(`${BASE_URL}/${ontologyId}/classes?classUri=${encodeURIComponent(classUri)}`)
}

// ==================== 实例管理CRUD接口 ====================

/**
 * 创建实例
 * @param {number} ontologyId - 本体ID
 * @param {Object} individualInfo - 实例信息 { name, classId, description, properties }
 * @returns {Promise} 实例的URI
 */
export const createOntologyIndividual = (ontologyId, individualInfo) => {
  return request.post(`${BASE_URL}/${ontologyId}/individuals`, individualInfo)
}

/**
 * 更新实例
 * @param {number} ontologyId - 本体ID
 * @param {string} individualUri - 实例URI
 * @param {Object} individualInfo - 实例信息
 * @returns {Promise}
 */
export const updateOntologyIndividual = (ontologyId, individualUri, individualInfo) => {
  return request.put(`${BASE_URL}/${ontologyId}/individuals?individualUri=${encodeURIComponent(individualUri)}`, individualInfo)
}

/**
 * 删除实例
 * @param {number} ontologyId - 本体ID
 * @param {string} individualUri - 实例URI
 * @returns {Promise}
 */
export const deleteOntologyIndividual = (ontologyId, individualUri) => {
  return request.delete(`${BASE_URL}/${ontologyId}/individuals?individualUri=${encodeURIComponent(individualUri)}`)
}

/**
 * 获取实例关系（对象属性、数据属性、反向关系）
 * @param {number} ontologyId - 本体ID
 * @param {string} individualUri - 实例URI
 * @returns {Promise} { objectProperties, datatypeProperties, inverseRelations }
 */
export const getIndividualRelations = (ontologyId, individualUri) => {
  return request.get(`${BASE_URL}/${ontologyId}/individuals/relations`, {
    params: { individualUri }
  })
}

// ==================== 属性管理CRUD接口 ====================

/**
 * 创建属性
 * @param {number} ontologyId - 本体ID
 * @param {Object} propertyInfo - 属性信息 { name, type, domain, range, description }
 * @returns {Promise} 属性的URI
 */
export const createOntologyProperty = (ontologyId, propertyInfo) => {
  return request.post(`${BASE_URL}/${ontologyId}/properties`, propertyInfo)
}

/**
 * 更新属性
 * @param {number} ontologyId - 本体ID
 * @param {string} propertyUri - 属性URI
 * @param {Object} propertyInfo - 属性信息
 * @returns {Promise}
 */
export const updateOntologyProperty = (ontologyId, propertyUri, propertyInfo) => {
  return request.put(`${BASE_URL}/${ontologyId}/properties?propertyUri=${encodeURIComponent(propertyUri)}`, propertyInfo)
}

/**
 * 删除属性
 * @param {number} ontologyId - 本体ID
 * @param {string} propertyUri - 属性URI
 * @returns {Promise}
 */
export const deleteOntologyProperty = (ontologyId, propertyUri) => {
  return request.delete(`${BASE_URL}/${ontologyId}/properties?propertyUri=${encodeURIComponent(propertyUri)}`)
}

/**
 * 获取本体可视化数据
 * @param {number} ontologyId - 本体ID
 * @returns {Promise} { nodes: [], edges: [] }
 */
export const getOntologyVisualization = (ontologyId) => {
  return request.get(`${BASE_URL}/${ontologyId}/visualization`)
}

/**
 * 图谱入库
 * @param {number} ontologyId - 本体ID
 * @param {Object} data - 入库数据 { ontologyName, version, remark, nodes, relationships }
 * @returns {Promise} { status, message, nodeCount, relationshipCount, version, timestamp }
 */
export const warehouseGraph = (ontologyId, data) => {
  return request.post(`${BASE_URL}/${ontologyId}/warehouse`, data)
}
