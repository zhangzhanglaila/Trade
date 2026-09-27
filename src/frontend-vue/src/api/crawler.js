import request from '@/utils/request'

export function getCrawlerSources() {
  return request({
    url: '/crawler/sources',
    method: 'get'
  })
}

export function fetchCrawlerList(data) {
  return request({
    url: '/crawler/fetch-list',
    method: 'post',
    data
  })
}

export function fetchCrawlerContent(data) {
  return request({
    url: '/crawler/fetch-content',
    method: 'post',
    data
  })
}

export function getCrawlerTask(taskId) {
  return request({
    url: `/crawler/tasks/${taskId}`,
    method: 'get'
  })
}

export function importCrawlerResults(data) {
  return request({
    url: '/crawler/import',
    method: 'post',
    data
  })
}
