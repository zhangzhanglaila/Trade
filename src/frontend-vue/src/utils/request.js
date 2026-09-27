// @/utils/request.js
import axios from 'axios'
import { message } from 'ant-design-vue'

// 1. 创建 axios 实例
const request = axios.create({
  baseURL: '',   // 8080 8081 8082
  timeout: 60000  // 增加到60秒，CSV转换和导入可能需要较长时间
})

// 2. 请求拦截器（发请求前统一加 token）
request.interceptors.request.use(
  config => {
    const url = config.url || ''
    // 登录/注册不要带 Authorization，避免旧 token 导致预检/鉴权异常
    if (url.includes('/auth/login') || url.includes('/auth/register')) {
      return config
    }

    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 3. 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data

    // 全局错误提示
    if (res && res.success === false) {
      message.error(res.message || '操作失败')
      return Promise.reject(new Error(res.message))
    }

    // 只把真正的业务数据抛给页面
    return res // ✅ 关键改动
  },
  error => {
    console.error('API请求错误:', {
      url: error.config?.url,
      status: error.response?.status,
      message: error.message
    })
    
    if (error.response) {
      // 服务器返回了错误状态码
      const status = error.response.status
      const data = error.response.data
      
      switch (status) {
        case 400:
          message.error(data?.message || '请求参数错误')
          break
        case 401:
          message.error('登录已过期，请重新登录')
          // 可以在这里跳转到登录页
          // router.push('/login')
          break
        case 403:
          message.error('没有权限访问')
          break
        case 404:
          message.error('请求地址不存在')
          break
        case 500:
          message.error('服务器内部错误')
          break
        default:
          message.error(data?.message || `网络错误 (${status})`)
      }
    } else if (error.request) {
      // 请求发送了但没有收到响应
      message.error('网络连接失败，请检查网络')
    } else {
      // 其他错误
      message.error(error.message || '请求配置错误')
    }
    
    return Promise.reject(error)
  }
)

// 4. 导出这个"已经配置好"的 axios
export default request