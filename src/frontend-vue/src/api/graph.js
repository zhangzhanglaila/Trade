import request from '@/utils/request'

const BASE_URL = '/ontology'

/**
 * 查找最短路径
 * @param {number} ontologyId - 本体ID
 * @param {Object} data - { sourceNode, targetNode, maxDepth, relationType }
 * @returns {Promise} 路径分析结果
 */
export const findShortestPath = (ontologyId, data) => {
  return request.post(`${BASE_URL}/${ontologyId}/graph/shortest-path`, data)
}

/**
 * 查找所有路径
 * @param {number} ontologyId - 本体ID
 * @param {Object} data - { sourceNode, targetNode, maxDepth, maxPaths, relationType }
 * @returns {Promise} 路径分析结果
 */
export const findAllPaths = (ontologyId, data) => {
  return request.post(`${BASE_URL}/${ontologyId}/graph/all-paths`, data)
}

/**
 * 查找邻居节点
 * @param {number} ontologyId - 本体ID
 * @param {Object} params - { nodeUri, depth, relationType }
 * @returns {Promise} 邻居节点列表
 */
export const findNeighbors = (ontologyId, params) => {
  return request.get(`${BASE_URL}/${ontologyId}/graph/neighbors`, { params })
}

/**
 * 统一路径分析
 * @param {number} ontologyId - 本体ID
 * @param {Object} data - { sourceNode, targetNode, algorithm, maxDepth, maxPaths, relationType }
 * @returns {Promise} 路径分析结果
 */
export const analyzePath = (ontologyId, data) => {
  return request.post(`${BASE_URL}/${ontologyId}/graph/analyze`, data)
}
