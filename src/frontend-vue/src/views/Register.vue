<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-card">
        <div class="register-header">
          <h2 class="register-title">创建账号</h2>
          <p class="register-subtitle">填写以下信息完成注册</p>
        </div>

        <a-form
          :model="registerForm"
          :rules="rules"
          @finish="handleRegister"
          layout="vertical"
        >
          <a-form-item name="username" label="用户名">
            <a-input
              v-model:value="registerForm.username"
              size="large"
              placeholder="请输入用户名"
            >
              <template #prefix>
                <UserOutlined style="color: rgba(0,0,0,.25)" />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="nickname" label="昵称">
            <a-input
              v-model:value="registerForm.nickname"
              size="large"
              placeholder="请输入昵称"
            >
              <template #prefix>
                <SmileOutlined style="color: rgba(0,0,0,.25)" />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="password" label="密码">
            <a-input-password
              v-model:value="registerForm.password"
              size="large"
              placeholder="请输入密码"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0,0,0,.25)" />
              </template>
            </a-input-password>
            <div class="password-strength" v-if="registerForm.password">
              <span class="strength-label">密码强度：</span>
              <div class="strength-bar">
                <div 
                  class="strength-fill" 
                  :class="passwordStrengthClass"
                  :style="{ width: passwordStrengthPercent + '%' }"
                ></div>
              </div>
              <span class="strength-text" :class="passwordStrengthClass">{{ passwordStrengthText }}</span>
            </div>
          </a-form-item>

          <a-form-item name="confirmPassword" label="确认密码">
            <a-input-password
              v-model:value="registerForm.confirmPassword"
              size="large"
              placeholder="请再次输入密码"
            >
              <template #prefix>
                <SafetyOutlined style="color: rgba(0,0,0,.25)" />
              </template>
            </a-input-password>
          </a-form-item>

          <a-form-item name="email" label="邮箱（可选）">
            <a-input
              v-model:value="registerForm.email"
              size="large"
              placeholder="请输入邮箱"
            >
              <template #prefix>
                <MailOutlined style="color: rgba(0,0,0,.25)" />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="agreement">
            <a-checkbox v-model:checked="registerForm.agreement">
              我已阅读并同意
              <a @click.prevent="showAgreement">用户协议</a>
              和
              <a @click.prevent="showPrivacy">隐私政策</a>
            </a-checkbox>
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="loading"
              class="register-btn"
            >
              注册
            </a-button>
          </a-form-item>

          <div class="form-footer">
            <span>已有账号？</span>
            <router-link to="/login" class="login-link">立即登录</router-link>
          </div>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { 
  UserOutlined, 
  LockOutlined, 
  MailOutlined, 
  SafetyOutlined,
  SmileOutlined
} from '@ant-design/icons-vue'
import { register } from '@/api/user'

const router = useRouter()
const loading = ref(false)

// 注册表单
const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  email: '',
  agreement: false
})

// 密码强度计算
const passwordStrength = computed(() => {
  const pwd = registerForm.password
  if (!pwd) return 0
  
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/[0-9]/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  
  return Math.min(score, 4)
})

const passwordStrengthClass = computed(() => {
  const strength = passwordStrength.value
  if (strength <= 1) return 'weak'
  if (strength === 2) return 'medium'
  if (strength === 3) return 'strong'
  return 'very-strong'
})

const passwordStrengthText = computed(() => {
  const strength = passwordStrength.value
  if (strength <= 1) return '弱'
  if (strength === 2) return '中'
  if (strength === 3) return '强'
  return '非常强'
})

const passwordStrengthPercent = computed(() => {
  return (passwordStrength.value / 4) * 100
})

// 自定义验证：确认密码
const validateConfirmPassword = async (rule, value) => {
  if (value !== registerForm.password) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

// 自定义验证：用户协议
const validateAgreement = async (rule, value) => {
  if (!value) {
    return Promise.reject('请阅读并同意用户协议')
  }
  return Promise.resolve()
}

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度应为3-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 20, message: '昵称不能超过20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  agreement: [
    { validator: validateAgreement, trigger: 'change' }
  ]
}

// 处理注册
const handleRegister = async (values) => {
  loading.value = true
  try {
    const res = await register({
      username: values.username,
      nickname: values.nickname,
      password: values.password,
      email: values.email
    })
    
    console.log('注册响应:', res)
    
    if (res.code !== 200) {
      throw new Error(res.message || '注册失败')
    }
    
    message.success('注册成功，请登录')
    
    // 延迟跳转
    setTimeout(() => {
      router.push('/login')
    }, 500)
  } catch (error) {
    console.error('注册错误:', error)
    message.error('注册失败：' + (error.message || '请稍后重试'))
  } finally {
    loading.value = false
  }
}

// 显示用户协议
const showAgreement = () => {
  message.info('用户协议功能开发中')
}

// 显示隐私政策
const showPrivacy = () => {
  message.info('隐私政策功能开发中')
}
</script>

<style scoped>
.register-page {
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
.register-page::before,
.register-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  opacity: 0.3;
  animation: float 20s infinite ease-in-out;
}

.register-page::before {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(255,255,255,0.3) 0%, transparent 70%);
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.register-page::after {
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

.register-container {
  width: 100%;
  max-width: 480px;
}

.register-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.register-title {
  font-size: 28px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
}

.register-subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

/* 密码强度指示器 */
.password-strength {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.strength-label {
  color: #8c8c8c;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: #f0f0f0;
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: 2px;
  transition: all 0.3s;
}

.strength-fill.weak {
  background: #ff4d4f;
}

.strength-fill.medium {
  background: #faad14;
}

.strength-fill.strong {
  background: #52c41a;
}

.strength-fill.very-strong {
  background: #1890ff;
}

.strength-text {
  min-width: 48px;
}

.strength-text.weak {
  color: #ff4d4f;
}

.strength-text.medium {
  color: #faad14;
}

.strength-text.strong {
  color: #52c41a;
}

.strength-text.very-strong {
  color: #1890ff;
}

.register-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  margin-top: 8px;
}

.register-btn:hover {
  background: linear-gradient(135deg, #5a6fd6 0%, #6a4190 100%);
  opacity: 0.9;
}

.form-footer {
  text-align: center;
  color: #8c8c8c;
  font-size: 14px;
  margin-top: 16px;
}

.login-link {
  color: #6B8DD6;
  margin-left: 8px;
  font-weight: 500;
}

.login-link:hover {
  color: #8E37D7;
}
</style>
