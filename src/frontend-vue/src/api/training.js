// @/api/training.js
import request from '@/utils/request'

function unwrapResult(result) {
  if (!result) throw new Error('接口返回为空')
  if (result.code !== 200) {
    throw new Error(result.message || `接口调用失败(code=${result.code})`)
  }
  return result.data
}

// 1. 上传训练文件
export const uploadTrainingFiles = (formData) => {
  return request
    .post('/ai/train/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    .then(unwrapResult)
}

/* 2. 启动训练 */
export const startTraining = async () => {
  console.log('发送训练启动请求...')
  const result = await request.post('/ai/train/start')
  const data = unwrapResult(result)
  console.log('训练启动响应:', data)
  return data
}

/* 3. 获取训练状态 */
export const getTrainingStatus = async (taskId) => {
  console.log('查询训练状态，taskId:', taskId)
  const result = await request.get(`/ai/train/status/${taskId}`)
  const data = unwrapResult(result)
  console.log('训练状态响应:', data)
  return data
}

/* 4. 一键训练：上传+启动（可选封装）*/
export async function trainWithFile(file, exportFile) {
  // ① 覆盖训练文件
  const fd = new FormData()
  fd.append('mergedInput', file) // 覆盖 data/进口/merged_input.csv
  if (exportFile) fd.append('mergedOutput', exportFile) // 可选出口文件
  await uploadTrainingFiles(fd)

  // ② 启动训练
  const data = await startTraining()
  return data.taskId // 返回 taskId 给外层轮询
}
