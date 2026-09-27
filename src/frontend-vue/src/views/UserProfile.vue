<template>
  <div class="user-profile-page">
    <div class="page-header">
      <h1 class="page-title">个人中心</h1>
      <p class="page-subtitle">管理您的个人信息和账号安全</p>
    </div>

    <div class="profile-content">
      <!-- 左侧：头像和基本信息 -->
      <div class="profile-left">
        <a-card class="avatar-card">
          <div class="avatar-section">
            <div class="avatar-wrapper">
              <a-avatar 
                :size="120" 
                :src="userInfo.avatar"
                class="user-avatar"
              >
                <template v-if="!userInfo.avatar">
                  <UserOutlined style="font-size: 48px" />
                </template>
              </a-avatar>
              <div class="avatar-upload">
                <a-upload
                  name="avatar"
                  :show-upload-list="false"
                  :before-upload="beforeAvatarUpload"
                  @change="handleAvatarChange"
                >
                  <a-button type="primary" shape="circle" size="small">
                    <CameraOutlined />
                  </a-button>
                </a-upload>
              </div>
            </div>
            <h3 class="user-name">{{ userInfo.nickname || userInfo.username }}</h3>
            <p class="user-role">普通用户</p>
          </div>

          <a-divider />

          <div class="user-stats">
            <div class="stat-item">
              <div class="stat-value">{{ stats.ontologyCount }}</div>
              <div class="stat-label">本体</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ stats.graphCount }}</div>
              <div class="stat-label">图谱</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ stats.queryCount }}</div>
              <div class="stat-label">查询</div>
            </div>
          </div>
        </a-card>

        <a-card class="menu-card">
          <a-menu
            v-model:selectedKeys="activeMenu"
            mode="inline"
            @click="handleMenuClick"
          >
            <a-menu-item key="profile">
              <UserOutlined />
              <span>个人资料</span>
            </a-menu-item>
            <a-menu-item key="password">
              <SafetyOutlined />
              <span>修改密码</span>
            </a-menu-item>
            <a-menu-item key="security">
              <SecurityScanOutlined />
              <span>账号安全</span>
            </a-menu-item>
          </a-menu>
        </a-card>
      </div>

      <!-- 右侧：详细内容 -->
      <div class="profile-right">
        <!-- 个人资料 -->
        <a-card v-if="activeMenu[0] === 'profile'" title="个人资料" class="content-card">
          <a-form
            :model="profileForm"
            :rules="profileRules"
            @finish="updateProfile"
            layout="vertical"
          >
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item name="username" label="用户名">
                  <a-input v-model:value="profileForm.username" disabled />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item name="nickname" label="昵称">
                  <a-input v-model:value="profileForm.nickname" placeholder="请输入昵称" />
                </a-form-item>
              </a-col>
            </a-row>

            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item name="email" label="邮箱">
                  <a-input v-model:value="profileForm.email" placeholder="请输入邮箱" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item name="phone" label="手机号">
                  <a-input v-model:value="profileForm.phone" placeholder="请输入手机号" />
                </a-form-item>
              </a-col>
            </a-row>

            <a-form-item name="bio" label="个人简介">
              <a-textarea
                v-model:value="profileForm.bio"
                :rows="4"
                placeholder="请输入个人简介"
                :max-length="200"
                show-count
              />
            </a-form-item>

            <a-form-item>
              <a-space>
                <a-button type="primary" html-type="submit" :loading="profileLoading">
                  保存修改
                </a-button>
                <a-button @click="resetProfile">重置</a-button>
              </a-space>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- 修改密码 -->
        <a-card v-if="activeMenu[0] === 'password'" title="修改密码" class="content-card">
          <a-form
            :model="passwordForm"
            :rules="passwordRules"
            @finish="updatePassword"
            layout="vertical"
            style="max-width: 400px"
          >
            <a-form-item name="oldPassword" label="当前密码">
              <a-input-password
                v-model:value="passwordForm.oldPassword"
                placeholder="请输入当前密码"
              />
            </a-form-item>

            <a-form-item name="newPassword" label="新密码">
              <a-input-password
                v-model:value="passwordForm.newPassword"
                placeholder="请输入新密码"
              />
              <div class="password-tips">
                <div class="tip-item" :class="{ valid: passwordValid.length }">
                  <CheckCircleOutlined v-if="passwordValid.length" />
                  <CloseCircleOutlined v-else />
                  长度至少6位
                </div>
                <div class="tip-item" :class="{ valid: passwordValid.number }">
                  <CheckCircleOutlined v-if="passwordValid.number" />
                  <CloseCircleOutlined v-else />
                  包含数字
                </div>
                <div class="tip-item" :class="{ valid: passwordValid.letter }">
                  <CheckCircleOutlined v-if="passwordValid.letter" />
                  <CloseCircleOutlined v-else />
                  包含字母
                </div>
              </div>
            </a-form-item>

            <a-form-item name="confirmPassword" label="确认新密码">
              <a-input-password
                v-model:value="passwordForm.confirmPassword"
                placeholder="请再次输入新密码"
              />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" html-type="submit" :loading="passwordLoading">
                确认修改
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- 账号安全 -->
        <a-card v-if="activeMenu[0] === 'security'" title="账号安全" class="content-card">
          <div class="security-list">
            <div class="security-item">
              <div class="security-info">
                <div class="security-title">登录密码</div>
                <div class="security-desc">定期修改密码可以保护账号安全</div>
              </div>
              <a-button @click="activeMenu = ['password']">修改</a-button>
            </div>

            <a-divider />

            <div class="security-item">
              <div class="security-info">
                <div class="security-title">邮箱绑定</div>
                <div class="security-desc">{{ userInfo.email || '未绑定邮箱' }}</div>
              </div>
              <a-button type="primary" ghost>绑定</a-button>
            </div>

            <a-divider />

            <div class="security-item">
              <div class="security-info">
                <div class="security-title">手机绑定</div>
                <div class="security-desc">{{ userInfo.phone || '未绑定手机' }}</div>
              </div>
              <a-button type="primary" ghost>绑定</a-button>
            </div>

            <a-divider />

            <div class="security-item danger">
              <div class="security-info">
                <div class="security-title">注销账号</div>
                <div class="security-desc">注销后将无法恢复，请谨慎操作</div>
              </div>
              <a-button danger @click="confirmDeleteAccount">注销</a-button>
            </div>
          </div>
        </a-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  UserOutlined,
  SafetyOutlined,
  SecurityScanOutlined,
  CameraOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined
} from '@ant-design/icons-vue'
import {
  updateUserProfile,
  changePassword,
  uploadAvatar,
  getUserInfo
} from '@/api/user'

// 路由实例
const router = useRouter()

// 当前激活的菜单
const activeMenu = ref(['profile'])

// 用户信息
const userInfo = reactive({
  id: null,
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  bio: ''
})

// 统计数据
const stats = reactive({
  ontologyCount: 0,
  graphCount: 0,
  queryCount: 0
})

// 个人资料表单
const profileForm = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  bio: ''
})

const profileLoading = ref(false)

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordLoading = ref(false)

// 密码验证状态
const passwordValid = computed(() => {
  const pwd = passwordForm.newPassword
  return {
    length: pwd && pwd.length >= 6,
    number: pwd && /\d/.test(pwd),
    letter: pwd && /[a-zA-Z]/.test(pwd)
  }
})

// 表单验证规则
const profileRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }
  ]
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: async (rule, value) => {
        if (value !== passwordForm.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
}

// 加载用户信息
const loadUserInfo = async () => {
  // 先从 localStorage 获取
  const savedUserInfo = localStorage.getItem('userInfo')
  if (savedUserInfo) {
    const info = JSON.parse(savedUserInfo)
    Object.assign(userInfo, info)
    Object.assign(profileForm, info)
  }
  
  // 然后从 API 获取最新信息
  try {
    const res = await getUserInfo()
    if (res.data) {
      Object.assign(userInfo, res.data)
      Object.assign(profileForm, res.data)
      localStorage.setItem('userInfo', JSON.stringify(res.data))
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
  
  // TODO: 从 API 获取统计数据
  stats.ontologyCount = 5
  stats.graphCount = 12
  stats.queryCount = 128
}

// 更新个人资料
const updateProfile = async (values) => {
  profileLoading.value = true
  try {
    const res = await updateUserProfile(values)
    Object.assign(userInfo, values)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
    message.success('个人资料更新成功')
  } catch (error) {
    message.error('更新失败：' + (error.message || '请稍后重试'))
  } finally {
    profileLoading.value = false
  }
}

// 重置个人资料
const resetProfile = () => {
  Object.assign(profileForm, userInfo)
}

// 更新密码
const updatePassword = async (values) => {
  passwordLoading.value = true
  try {
    const res = await changePassword({
      oldPassword: values.oldPassword,
      newPassword: values.newPassword
    })
    
    if (res.code !== 200) {
      throw new Error(res.message || '修改失败')
    }
    
    message.success('密码修改成功，请重新登录')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    
    // 清除登录状态
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    
    // 延迟跳转到登录页
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (error) {
    message.error('修改失败：' + (error.message || '原密码可能不正确'))
  } finally {
    passwordLoading.value = false
  }
}

// 头像上传前检查
const beforeAvatarUpload = (file) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('只支持 JPG/PNG 格式的图片')
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
  }
  return isJpgOrPng && isLt2M
}

// 处理头像变更
const handleAvatarChange = async (info) => {
  if (info.file.status === 'uploading') {
    return
  }
  if (info.file.status === 'done') {
    message.success('头像上传成功')
  } else if (info.file.status === 'error') {
    message.error('头像上传失败')
  }
}

// 自定义上传头像
const customUploadAvatar = async ({ file, onSuccess, onError }) => {
  try {
    const formData = new FormData()
    formData.append('avatar', file)
    const res = await uploadAvatar(formData)
    userInfo.avatar = res.data?.avatarUrl || URL.createObjectURL(file)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
    onSuccess?.()
    message.success('头像上传成功')
  } catch (error) {
    onError?.(error)
    message.error('头像上传失败：' + (error.message || '请稍后重试'))
  }
}

// 菜单点击
const handleMenuClick = ({ key }) => {
  activeMenu.value = [key]
}

// 确认注销账号
const confirmDeleteAccount = () => {
  Modal.confirm({
    title: '确认注销账号？',
    content: '账号注销后将无法恢复，所有数据将被删除，请谨慎操作。',
    okText: '确认注销',
    okType: 'danger',
    cancelText: '取消',
    onOk() {
      message.success('账号注销申请已提交')
    }
  })
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.user-profile-page {
  padding: 24px;
  background: #f0f2f5;
  min-height: 100%;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 4px;
}

.page-subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

.profile-content {
  display: flex;
  gap: 24px;
}

.profile-left {
  width: 280px;
  flex-shrink: 0;
}

.profile-right {
  flex: 1;
}

.avatar-card {
  margin-bottom: 16px;
  text-align: center;
}

.avatar-section {
  padding: 16px 0;
}

.avatar-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: 16px;
}

.user-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: 4px solid #f0f0f0;
}

.avatar-upload {
  position: absolute;
  bottom: 0;
  right: 0;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 4px;
}

.user-role {
  font-size: 14px;
  color: #8c8c8c;
}

.user-stats {
  display: flex;
  justify-content: space-around;
  padding: 16px 0;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #1890ff;
}

.stat-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 4px;
}

.menu-card :deep(.ant-card-body) {
  padding: 0;
}

.content-card {
  min-height: 500px;
}

.password-tips {
  margin-top: 8px;
  padding: 12px;
  background: #f6ffed;
  border-radius: 4px;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.tip-item.valid {
  color: #52c41a;
}

.tip-item:last-child {
  margin-bottom: 0;
}

.security-list {
  padding: 8px 0;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
}

.security-item.danger {
  background: #fff2f0;
  margin: 0 -24px;
  padding: 16px 24px;
}

.security-info {
  flex: 1;
}

.security-title {
  font-size: 16px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 4px;
}

.security-desc {
  font-size: 14px;
  color: #8c8c8c;
}

@media (max-width: 768px) {
  .profile-content {
    flex-direction: column;
  }
  
  .profile-left {
    width: 100%;
  }
}
</style>
