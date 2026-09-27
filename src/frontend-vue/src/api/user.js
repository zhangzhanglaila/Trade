import request from '@/utils/request'

// API 基础路径
// 开发环境：使用 vue.config.js 的 devServer.proxy 代理到后端，避免 CORS/403
// 生产环境：建议用环境变量配置 API 地址（如 VUE_APP_API_BASE），这里先保持相对路径
const API_BASE = ''

/**
 * 用户登录
 * @param {Object} data - 登录参数 { username, password }
 * @returns {Promise}
 */
export function login(data) {
  return request({
    url: `${API_BASE}/auth/login`,
    method: 'post',
    data
  })
}

/**
 * 用户注册
 * @param {Object} data - 注册参数 { username, password, nickname, email }
 * @returns {Promise}
 */
export function register(data) {
  return request({
    url: `${API_BASE}/auth/register`,
    method: 'post',
    data
  })
}

/**
 * 用户退出登录
 * @returns {Promise}
 */
export function logout() {
  return request({
    url: `${API_BASE}/auth/logout`,
    method: 'post'
  })
}

/**
 * 获取当前用户信息
 * @returns {Promise}
 */
export function getUserInfo() {
  return request({
    url: `${API_BASE}/auth/info`,
    method: 'get'
  })
}

/**
 * 更新用户信息
 * @param {Object} data - 用户信息 { nickname, email, phone, bio }
 * @returns {Promise}
 */
export function updateUserProfile(data) {
  return request({
    url: `${API_BASE}/user/profile`,
    method: 'put',
    data
  })
}

/**
 * 上传头像
 * @param {FormData} formData - 包含头像文件的 FormData
 * @returns {Promise}
 */
export function uploadAvatar(formData) {
  return request({
    url: `${API_BASE}/user/avatar`,
    method: 'post',
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data: formData
  })
}

/**
 * 修改密码
 * @param {Object} data - { oldPassword, newPassword }
 * @returns {Promise}
 */
export function changePassword(data) {
  return request({
    url: `${API_BASE}/user/password`,
    method: 'put',
    data
  })
}

/**
 * 获取用户统计数据
 * @returns {Promise}
 */
export function getUserStats() {
  return request({
    url: `${API_BASE}/user/stats`,
    method: 'get'
  })
}

/**
 * 检查用户名是否可用
 * @param {string} username - 用户名
 * @returns {Promise}
 */
export function checkUsername(username) {
  return request({
    url: `${API_BASE}/auth/check-username`,
    method: 'get',
    params: { username }
  })
}

/**
 * 刷新 token
 * @returns {Promise}
 */
export function refreshToken() {
  return request({
    url: `${API_BASE}/auth/refresh`,
    method: 'post'
  })
}

/**
 * 发送验证码（邮箱/手机）
 * @param {Object} data - { type: 'email'|'phone', target }
 * @returns {Promise}
 */
export function sendVerifyCode(data) {
  return request({
    url: `${API_BASE}/auth/verify-code`,
    method: 'post',
    data
  })
}

/**
 * 重置密码
 * @param {Object} data - { username, verifyCode, newPassword }
 * @returns {Promise}
 */
export function resetPassword(data) {
  return request({
    url: `${API_BASE}/auth/reset-password`,
    method: 'post',
    data
  })
}
