import request from '@/utils/request'

function unwrapResult(result) {
  if (!result) throw new Error('接口返回为空')
  if (result.code !== 200) {
    throw new Error(result.message || `接口调用失败(code=${result.code})`)
  }
  return result.data
}

/**
 * 统一 AI 聊天（意图识别 -> RAG / 预测）
 * @param {Object} payload - 对应后端 AiChatRequest
 */
export function chat(payload) {
  return request.post('/ai/chat', payload).then(unwrapResult)
}

/**
 * 预测服务健康检查
 */
export function predictHealth() {
  return request.get('/ai/predict/health').then(unwrapResult)
}

/**
 * 全量构建新闻向量索引
 */
export function ragIndexFull() {
  return request.post('/ai/rag/index/full').then(unwrapResult)
}
