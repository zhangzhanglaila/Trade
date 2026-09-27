import request from '@/utils/request'

export function searchNews(params) {
  return request({
    url: '/news/mix-query-page',
    method: 'get',
    params
  })
}

// 新增
export function addNews(data) {
  return request({
    url: '/news/addNews',
    method: 'post',
    data
  })
}

// 更新
export function updateNews(data) {
  return request({
    url: '/news/updateNews',
    method: 'put',
    data
  })
}

// 删除
export function deleteNews(id) {
  return request({
    url: `/news/deleteNews/${id}`,
    method: 'delete'
  })
}

/* 根据 ID 查询新闻语料详情 */
export function getNewsById(id) {
  return request({
    url: `/news/selectNewsById/${id}`,
    method: 'get'
  })
}