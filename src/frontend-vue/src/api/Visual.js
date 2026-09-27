/* api/graphService.js  统一图谱服务 */
import request from '@/utils/request'

const BASE_URL = '/api/v1/graph'

/* -------------------------- 1. 节点管理 -------------------------- */
export const createNode = (data) =>
  request.post(`${BASE_URL}/nodes`, data)

export const getNodeById = (id) =>
  request.get(`${BASE_URL}/nodes/${id}`)

export const updateNodeById = (id, data) =>
  request.put(`${BASE_URL}/nodes/${id}`, data)

export const deleteNodeById = (id) =>
  request.delete(`${BASE_URL}/nodes/${id}`)

export const getNodesByType = (nodeType) =>
  request.get(`${BASE_URL}/nodes/type/${nodeType}`)

export const getNodesByOntology = (ontologyName) =>
  request.get(`${BASE_URL}/nodes/ontology/${ontologyName}`)

export const searchNodes = (keyword) =>
  request.get(`${BASE_URL}/nodes/search`, { params: { keyword } })

/* -------------------------- 2. 关系管理 -------------------------- */
export const createRelationship = (data) =>
  request.post(`${BASE_URL}/relationships`, data)

export const getRelationshipById = (id) =>
  request.get(`${BASE_URL}/relationships/${id}`)

export const updateRelationshipById = (id, data) =>
  request.put(`${BASE_URL}/relationships/${id}`, data)

export const deleteRelationshipById = (id) =>
  request.delete(`${BASE_URL}/relationships/${id}`)

export const getRelationshipsBySource = (sourceNodeId) =>
  request.get(`${BASE_URL}/relationships/source/${sourceNodeId}`)

export const getRelationshipsByTarget = (targetNodeId) =>
  request.get(`${BASE_URL}/relationships/target/${targetNodeId}`)

export const getRelationshipsBetween = (sourceNodeId, targetNodeId) =>
  request.get(`${BASE_URL}/relationships/between`, {
    params: { sourceNodeId, targetNodeId }
  })

export const getRelationshipsByType = (relationshipType) =>
  request.get(`${BASE_URL}/relationships/type/${relationshipType}`)

export const searchRelationships = (keyword) =>
  request.get(`${BASE_URL}/relationships/search`, { params: { keyword } })

export const getRelationshipsByNode = (nodeId) =>
  request.get(`${BASE_URL}/relationships/node/${nodeId}`)

/* -------------------------- 3. 属性过滤 -------------------------- */
export const filterNodes = (data) =>
  request.post(`${BASE_URL}/filter/nodes`, data)

export const filterRelationships = (data) =>
  request.post(`${BASE_URL}/filter/relationships`, data)

/* -------------------------- 4. 路径分析 -------------------------- */
export const findShortestPath = (data) =>
  request.post(`${BASE_URL}/path/shortest`, data)

export const findAllPaths = (data) =>
  request.post(`${BASE_URL}/path/all`, data)

/* -------------------------- 5. 中心度分析 -------------------------- */
export const getDefaultCentrality = () =>
  request.get(`${BASE_URL}/centrality`)

export const getDegreeCentrality = () =>
  request.get(`${BASE_URL}/centrality/degree`)

export const getClosenessCentrality = () =>
  request.get(`${BASE_URL}/centrality/closeness`)

export const getBetweennessCentrality = () =>
  request.get(`${BASE_URL}/centrality/betweenness`)

/* -------------------------- 6. 模式匹配 -------------------------- */
export const matchPattern = (data) =>
  request.post(`${BASE_URL}/pattern/match`, data)

/* -------------------------- 7. 图谱搜索 -------------------------- */
export const searchAll = (keyword) =>
  request.get(`${BASE_URL}/search`, { params: { keyword } })

export const searchOnlyNodes = (keyword) =>
  request.get(`${BASE_URL}/search/nodes`, { params: { keyword } })

export const searchOnlyRelationships = (keyword) =>
  request.get(`${BASE_URL}/search/relationships`, { params: { keyword } })

/* -------------------------- 8. 导入导出 -------------------------- */
export const importGraph = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`${BASE_URL}/io/import`, fd) // 让 axios 自动生成 multipart
}

export const exportGraph = (format = 'json') =>
  request.get(`${BASE_URL}/io/export`, {
    params: { format },
    responseType: 'blob'
  })

/* ========================== 统一导出 ========================== */
export default {
  node: {
    create: createNode,
    getById: getNodeById,
    updateById: updateNodeById,
    deleteById: deleteNodeById,
    getByType: getNodesByType,
    getByOntology: getNodesByOntology,
    search: searchNodes
  },
  relationship: {
    create: createRelationship,
    getById: getRelationshipById,
    updateById: updateRelationshipById,
    deleteById: deleteRelationshipById,
    getBySource: getRelationshipsBySource,
    getByTarget: getRelationshipsByTarget,
    getBetween: getRelationshipsBetween,
    getByType: getRelationshipsByType,
    search: searchRelationships,
    getByNode: getRelationshipsByNode
  },
  filter: {
    nodes: filterNodes,
    relationships: filterRelationships
  },
  path: {
    shortest: findShortestPath,
    all: findAllPaths
  },
  centrality: {
    default: getDefaultCentrality,
    degree: getDegreeCentrality,
    closeness: getClosenessCentrality,
    betweenness: getBetweennessCentrality
  },
  pattern: {
    match: matchPattern
  },
  search: {
    all: searchAll,
    nodes: searchOnlyNodes,
    relationships: searchOnlyRelationships
  },
  io: {
    import: importGraph,
    export: exportGraph
  }
}