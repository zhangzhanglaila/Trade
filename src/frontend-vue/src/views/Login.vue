<template>
  <div class="login-page">
    <div class="login-container">
      <!-- 左侧装饰 -->
      <div class="login-decoration">
        <div class="decoration-content">
          <h1 class="decoration-title">中哈贸易</h1>
          <p class="decoration-subtitle">知识图谱管理系统</p>
          <div class="decoration-features">
            <div class="feature-item">
              <span class="feature-icon">📊</span>
              <span>数据可视化</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">🌐</span>
              <span>智能问答</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">📈</span>
              <span>图谱分析</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="login-form-wrapper">
        <div class="login-form-card">
          <h2 class="form-title">欢迎登录</h2>
          <p class="form-subtitle">请使用您的账号密码登录系统</p>

          <a-form
            :model="loginForm"
            :rules="rules"
            @finish="handleLogin"
            layout="vertical"
          >
            <a-form-item name="username">
              <a-input
                v-model:value="loginForm.username"
                size="large"
                placeholder="请输入用户名"
              >
                <template #prefix>
                  <UserOutlined style="color: rgba(0,0,0,.25)" />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item name="password">
              <a-input-password
                v-model:value="loginForm.password"
                size="large"
                placeholder="请输入密码"
              >
                <template #prefix>
                  <LockOutlined style="color: rgba(0,0,0,.25)" />
                </template>
              </a-input-password>
            </a-form-item>

            <div class="form-options">
              <a-checkbox v-model:checked="rememberMe">记住密码</a-checkbox>
              <a class="forgot-link" @click="forgotPassword">忘记密码？</a>
            </div>

            <a-form-item>
              <a-button
                type="primary"
                html-type="submit"
                size="large"
                block
                :loading="loading"
                class="login-btn"
              >
                登录
              </a-button>
            </a-form-item>

            <div class="form-footer">
              <span>还没有账号？</span>
              <router-link to="/register" class="register-link">立即注册</router-link>
            </div>
          </a-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { login } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(false)

// 登录表单
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

// 处理登录
const handleLogin = async (values) => {
  loading.value = true
  try {
    const res = await login(values)
    
    // 注意：响应拦截器已经返回 response.data
    // res 结构：{ code: 200, message: '成功', data: { token, user } }
    console.log('登录响应:', res)
    
    if (res.code !== 200) {
      throw new Error(res.message || '登录失败')
    }
    
    // 保存 token 和用户信息
    const token = res.data?.token || ''
    const user = res.data?.user || {
      id: 1,
      username: values.username,
      nickname: values.username,
      avatar: ''
    }
    
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify(user))
    
    // 记住用户名
    if (rememberMe.value) {
      localStorage.setItem('savedUsername', values.username)
    } else {
      localStorage.removeItem('savedUsername')
    }
    
    message.success('登录成功')
    
    // 延迟跳转确保消息显示
    setTimeout(() => {
      router.push('/tradeDashboard')
    }, 500)
  } catch (error) {
    console.error('登录错误:', error)
    message.error('登录失败：' + (error.message || '用户名或密码错误'))
  } finally {
    loading.value = false
  }
}

// 忘记密码
const forgotPassword = () => {
  message.info('请联系管理员重置密码')
}

// 页面加载时检查是否已有登录信息
onMounted(() => {
  const savedUsername = localStorage.getItem('savedUsername')
  if (savedUsername) {
    loginForm.username = savedUsername
    rememberMe.value = true
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(-45deg, #8fa8ff, #9b87d6, #9bb8ff, #b48aff, #7ee8e6);
  background-size: 400% 400%;
  animation: gradientFlow 15s ease infinite;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

/* 动态渐变动画 */
@keyframes gradientFlow {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

/* 浮动气泡装饰 */
.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  opacity: 0.3;
  animation: float 20s infinite ease-in-out;
}

.login-page::before {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(255,255,255,0.3) 0%, transparent 70%);
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.login-page::after {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255,255,255,0.2) 0%, transparent 70%);
  bottom: -50px;
  right: -50px;
  animation-delay: -5s;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(30px, -30px) scale(1.1);
  }
  66% {
    transform: translate(-20px, 20px) scale(0.9);
  }
}

.login-container {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 600px;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

/* 左侧装饰区域 */
.login-decoration {
  flex: 1;
  background: linear-gradient(135deg, #2CD8D5 0%, #6B8DD6 48%, #8E37D7 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #fff;
}

.decoration-content {
  text-align: center;
}

.decoration-title {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 16px;
  font-family: 'STXingkai', 'KaiTi', cursive;
  letter-spacing: 8px;
  text-shadow: 2px 2px 4px rgba(0,0,0,0.2);
}

.decoration-subtitle {
  font-size: 20px;
  opacity: 0.9;
  margin-bottom: 60px;
}

.decoration-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 16px;
  opacity: 0.9;
}

.feature-icon {
  font-size: 24px;
}

/* 右侧表单区域 */
.login-form-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: #fff;
}

.login-form-card {
  width: 100%;
  max-width: 360px;
}

.form-title {
  font-size: 28px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
  text-align: center;
}

.form-subtitle {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 32px;
  text-align: center;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-link {
  color: #6B8DD6;
  cursor: pointer;
}

.forgot-link:hover {
  color: #8E37D7;
}

.login-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

.login-btn:hover {
  background: linear-gradient(135deg, #5a6fd6 0%, #6a4190 100%);
  opacity: 0.9;
}

.form-footer {
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
}

.register-link {
  color: #6B8DD6;
  margin-left: 8px;
  font-weight: 500;
}

.register-link:hover {
  color: #8E37D7;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .login-decoration {
    display: none;
  }
  
  .login-container {
    max-width: 400px;
  }
}
</style>
