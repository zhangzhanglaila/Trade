
import request from '@/utils/request'   // 你的 axios 封装

// 分页查询  /logistics/page
export const pageLogistics = params =>
  request.get('/logistics/queryPage', { params })

// 单条查询  /logistics/{id}
export const getLogistics = id =>
  request.get(`/logistics/selectMessageById/${id}`)

// 新增      /logistics
export const addLogistics = data =>
  request.post('/logistics/addMessage', data)

// 修改      /logistics
export const updateLogistics = data =>
  request.put('/logistics/updateMessage', data)

//删除
export const delLogistics = id =>
  request.delete(`/logistics/deleteMessage/${id}`)

// 条件列表  /logistics/country-importType
export const listByCountryIE = (country, importExportType) =>
  request.get('/logistics/country-importType', {
    params: { country, importExportType }
  })
 