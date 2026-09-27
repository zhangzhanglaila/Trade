import request from '@/utils/request'

/**
 * 查找最短路径
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { sourceNode, targetNode, maxDepth }
 * @returns {Promise}
 */
export function findShortestPath(ontologyId, params) {
  return request({
    url: `/ontology/${ontologyId}/graph/shortest-path`,
    method: 'post',
    data: params
  })
}

/**
 * 查找所有路径
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { sourceNode, targetNode, maxDepth, maxPaths }
 * @returns {Promise}
 */
export function findAllPaths(ontologyId, params) {
  return request({
    url: `/ontology/${ontologyId}/graph/all-paths`,
    method: 'post',
    data: params
  })
}

/**
 * 社区发现
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { algorithm, resolution, maxIterations, minCommunitySize }
 * @returns {Promise}
 */
export function detectCommunities(ontologyId, params = {}) {
  const { algorithm = 'LOUVAIN', resolution = 1.0, maxIterations = 100, minCommunitySize = 2 } = params
  return request({
    url: `/ontology/${ontologyId}/graph/communities`,
    method: 'get',
    params: { algorithm, resolution, maxIterations, minCommunitySize }
  })
}

/**
 * 中心性分析
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { centralityType, limit, minThreshold }
 * @returns {Promise}
 */
export function analyzeCentrality(ontologyId, params = {}) {
  const { centralityType = 'DEGREE', limit = 20, minThreshold = 0.0 } = params
  return request({
    url: `/ontology/${ontologyId}/graph/centrality`,
    method: 'get',
    params: { centralityType, limit, minThreshold }
  })
}

/**
 * 查询邻居节点
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { nodeUri, depth }
 * @returns {Promise}
 */
export function queryNeighbors(ontologyId, params = {}) {
  const { nodeUri, depth = 1 } = params
  return request({
    url: `/ontology/${ontologyId}/graph/neighbors`,
    method: 'get',
    params: { nodeUri, depth }
  })
}

/**
 * 关联查询
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { entityIds, queryType }
 * @returns {Promise}
 */
export function queryAssociations(ontologyId, params = {}) {
  const { entityIds, queryType = 'DIRECT' } = params
  return request({
    url: `/ontology/${ontologyId}/graph/associations`,
    method: 'post',
    params: { queryType },
    data: entityIds
  })
}

/**
 * 实体扩线
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - 查询参数 { entityId, expandLevel, direction }
 * @returns {Promise}
 */
export function expandEntity(ontologyId, params = {}) {
  const { entityId, expandLevel = 2, direction = 'BOTH' } = params
  return request({
    url: `/ontology/${ontologyId}/graph/expand`,
    method: 'get',
    params: { entityId, expandLevel, direction }
  })
}

/**
 * 图谱结构分析
 * @param {number} ontologyId - 本体ID
 * @returns {Promise}
 */
export function analyzeGraphStructure(ontologyId) {
  return request({
    url: `/ontology/${ontologyId}/graph/structure`,
    method: 'get'
  })
}

/**
 * 模式匹配
 * @param {number} ontologyId - 本体ID
 * @param {Object} data - 模式匹配请求数据
 * @returns {Promise}
 */
export function patternMatching(ontologyId, data) {
  return request({
    url: `/ontology/${ontologyId}/graph/pattern/matching`,
    method: 'post',
    data
  })
}
