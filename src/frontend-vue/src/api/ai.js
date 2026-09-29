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
 * SSE 流式聊天。
 *
 * 后端 /ai/chat/stream 返回 text/event-stream，每行 `data:` 是一个 JSON：
 *   {type:'stage', msg:'...'}  阶段进度
 *   {type:'done', data:{...}}  最终结果（AiChatResponse）
 *   {type:'error', msg:'...'}  出错
 *
 * @param {Object} payload - 对应后端 AiChatRequest
 * @param {Function} onStage - 阶段进度回调 (msg) => void
 * @returns {Promise<Object>} 最终 AiChatResponse.data
 */
export async function chatStream(payload, onStage) {
  const token = localStorage.getItem('token')

  const resp = await fetch('/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  })

  if (!resp.ok || !resp.body) {
    const text = await resp.text().catch(() => '')
    throw new Error(text || `流式请求失败(${resp.status})`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // 按行切分，SSE 每条事件以空行分隔，这里只关心 `data:` 行
    let idx
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim()
      buffer = buffer.slice(idx + 1)
      if (!line.startsWith('data:')) continue
      const raw = line.slice(5).trim()
      if (!raw) continue
      let evt
      try {
        evt = JSON.parse(raw)
      } catch (e) {
        continue
      }
      if (evt.type === 'stage') {
        onStage && onStage(evt.msg)
      } else if (evt.type === 'done') {
        return evt.data
      } else if (evt.type === 'error') {
        throw new Error(evt.msg || '流式问答失败')
      }
    }
  }

  // 流结束但没收到 done，视为异常
  throw new Error('流式响应中断')
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
