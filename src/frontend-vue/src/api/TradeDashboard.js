// src/api/trade.js
/**
 * 数据管理模块——Dashboard 接口
 * 与后端 /api/* 完全对应
 */
import request from '@/utils/request'; // 你的 Axios 实例

/* ========================== 综合数据 CRUD ========================== */
export function saveComprehensive(data) {
  return request.post('/api/comprehensive', data);
}

export function updateComprehensive(data) {
  return request.put('/api/comprehensive', data);
}

export function deleteComprehensive(id) {
  return request.delete(`/api/comprehensive/${id}`);
}

export function getComprehensivePage({ pageNum, pageSize, country, year }) {
  return request.get('/api/comprehensive/page', {
    params: { pageNum, pageSize, country, year }
  });
}

/* ========================== 国家月度贸易 CRUD ========================== */
export function saveCountryTrade(data) {
  return request.post('/api/country-trade', data);
}

export function updateCountryTrade(data) {
  return request.put('/api/country-trade', data);
}

export function deleteCountryTrade(id) {
  return request.delete(`/api/country-trade/${id}`);
}

export function getCountryTradePage({ pageNum, pageSize, country, yearMonth }) {
  return request.get('/api/country-trade/page', {
    params: { pageNum, pageSize, country, yearMonth }
  });
}

/* ========================== 贸易方式统计 CRUD ========================== */
export function saveTradeMethod(data) {
  return request.post('/api/trade-method', data);
}

export function updateTradeMethod(data) {
  return request.put('/api/trade-method', data);
}

export function deleteTradeMethod(id) {
  return request.delete(`/api/trade-method/${id}`);
}

export function getTradeMethodPage({ pageNum, pageSize, methodType, year }) {
  return request.get('/api/trade-method/page', {
    params: { pageNum, pageSize, methodType, year }
  });
}

/* ========================== 语料条目 CRUD ========================== */
export function saveCorpus(data) {
  return request.post('/api/corpus', data);
}

export function updateCorpus(data) {
  return request.put('/api/corpus', data);
}

export function deleteCorpus(id) {
  return request.delete(`/api/corpus/${id}`);
}

export function getCorpusPage({ pageNum, pageSize, corpusType }) {
  return request.get('/api/corpus/page', {
    params: { pageNum, pageSize, corpusType }
  });
}

/* ========================== 看板统计接口 ========================== */
// 后端已就绪，使用真实 API
// export * from './mock'

// 真正的接口（后端已 ready）
export function getCurrentComprehensive() {
  return request.get('/api/dashboard/comprehensive');
}

export function getCountryRatio(year, month) {
  return request.get('/api/dashboard/country-ratio', { params: { year, month } });
}

export function getMethodRatio(year, month, country) {
  return request.get('/api/dashboard/method-ratio', { params: { year, month, country } });
}

export function getLast12Months(country) {
  return request.get('/api/dashboard/last12months', { params: { country } });
}

export function getYearlyCorpus() {
  return request.get('/api/dashboard/corpus-yearly');
}

// 新增：获取大屏所有统计数据（新接口）
export function getDashboardStatistics() {
  return request.get('/api/statistics/dashboard');
}
