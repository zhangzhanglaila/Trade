<template>
  <aside class="side-nav">
    <!-- Logo区域 -->
    <div class="logo-area">
      <h1 class="logo-text">中哈贸易</h1>
    </div>

    <nav class="nav">
      <!-- 顶部菜单 -->
      <div class="nav-section">
        <router-link
          v-for="item in topMenu"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </div>

      <!-- 分割线 -->
      <div class="divider"></div>

      <!-- 信息管理（可折叠） -->
      <div class="nav-section">
        <div
          class="nav-item sub-wrap"
          :class="{ open: isInfoOpen }"
          @click="isInfoOpen = !isInfoOpen"
        >
          <span class="nav-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
          </span>
          <span class="nav-label">信息管理</span>
          <svg viewBox="0 0 24 24" class="arrow">
            <path d="M7 10l5 5 5-5z" />
          </svg>
        </div>

        <transition name="sub">
          <div v-show="isInfoOpen" class="sub-menu">
            <router-link to="/data" class="nav-item sub" active-class="active">
              <span class="sub-dot"></span>
              <span class="nav-label">数据管理</span>
            </router-link>
            <router-link to="/corpusManagement" class="nav-item sub" active-class="active">
              <span class="sub-dot"></span>
              <span class="nav-label">语料管理</span>
            </router-link>
          </div>
        </transition>
      </div>

      <!-- 分割线 -->
      <div class="divider"></div>

      <!-- 底部菜单 -->
      <div class="nav-section">
        <router-link
          v-for="item in bottomMenu"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </div>
    </nav>

    <!-- 用户信息区域 -->
    <div class="user-section">
      <div class="user-main" @click="goToProfile">
        <a-avatar :size="36" :src="userInfo.avatar" class="user-avatar">
          <template v-if="!userInfo.avatar">
            {{ userInfo.nickname?.charAt(0) || userInfo.username?.charAt(0) || 'U' }}
          </template>
        </a-avatar>
        <div class="user-info">
          <div class="user-name">{{ userInfo.nickname || userInfo.username || '用户' }}</div>
          <div class="user-role">{{ userInfo.email || '点击管理账号' }}</div>
        </div>
      </div>
      <a-button type="text" size="small" class="logout-btn" @click="handleLogout" title="退出登录">
        <LogoutOutlined />
      </a-button>
    </div>

    <!-- 底部装饰 -->
    <div class="bottom-decoration">
      <div class="version">v2.0.1</div>
    </div>
  </aside>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { logout } from '@/api/user'

const router = useRouter()

// 用户信息
const userInfo = reactive({
  username: '',
  nickname: '',
  avatar: '',
  email: ''
})

// 加载用户信息
const loadUserInfo = () => {
  const savedUserInfo = localStorage.getItem('userInfo')
  if (savedUserInfo) {
    Object.assign(userInfo, JSON.parse(savedUserInfo))
  }
}

// 跳转到个人中心
const goToProfile = () => {
  router.push('/user/profile')
}

// 退出登录
const handleLogout = async () => {
  try {
    await logout()
  } catch (error) {
    console.error('退出登录失败:', error)
  } finally {
    // 清除本地存储
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    message.success('已退出登录')
    router.push('/login')
  }
}

onMounted(() => {
  loadUserInfo()
})

/* ---------- 菜单 ---------- */
const topMenu = [
  {
    label: '数据看板',
    path: '/tradeDashboard',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`
  },
  {
    label: 'AJ大屏',
    path: '/aj-report',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="4" width="20" height="14" rx="2"/><path d="M8 20h8"/><path d="M12 18v2"/></svg>`
  }
]

const bottomMenu = [
  { 
    label: '图谱可视化', 
    path: '/dataVisualization',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="5" r="3"/><circle cx="19" cy="12" r="3"/><circle cx="5" cy="12" r="3"/><circle cx="12" cy="19" r="3"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>`
  },
  { 
    label: '智能问答', 
    path: '/qa',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`
  },
  {
    label: '数据采集',
    path: '/dataCollection',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`
  },
  {
    label: '新闻采集',
    path: '/crawlerNews',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><path d="M17 21v-8H7v8"/><path d="M7 3v5h8"/></svg>`
  },
  {
    label: '本体管理',
    path: '/ontologyManagement',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>`
  }
]

/* ---------- 下拉状态 ---------- */
const isInfoOpen = ref(true)
</script>

<style scoped>
/* ---------- 侧边栏 ---------- */
.side-nav {
  width: 240px;
  height: 100vh;
  background: linear-gradient(180deg, #fafbfc 0%, #f0f2f5 100%);
  display: flex;
  flex-direction: column;
  padding: 0;
  box-shadow: 0 0 20px rgba(0, 0, 0, 0.08);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  position: relative;
  overflow: hidden;
}

/* Logo区域 */
.logo-area {
  padding: 32px 20px;
  text-align: center;
  background: transparent;
}

.logo-text {
  font-size: 36px;
  font-weight: 700;
  margin: 0;
  letter-spacing: 8px;
  font-family: 'STXingkai', 'LiSu', 'KaiTi', 'STKaiti', 'FangSong', 'SimSun', cursive;
  background-image: linear-gradient(-225deg, #6B8DD6 0%, #8E37D7 48%, #2CD8D5 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
  display: inline-block;
  transform: skewX(-5deg);
  white-space: nowrap;
}

.logo-text::after {
  content: '中哈贸易';
  position: absolute;
  left: 3px;
  top: 3px;
  width: 100%;
  height: 100%;
  background-image: linear-gradient(-225deg, rgba(107, 141, 214, 0.2) 0%, rgba(142, 55, 215, 0.15) 48%, rgba(44, 216, 213, 0.1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  z-index: -1;
  filter: blur(2px);
  white-space: nowrap;
  pointer-events: none;
}

/* 导航区域 */
.nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  overflow-y: auto;
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 分割线 */
.divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #d9d9d9, transparent);
  margin: 16px 8px;
}

/* ---------- 导航项 ---------- */
.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 10px;
  color: #4a5568;
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  user-select: none;
  font-size: 15px;
  position: relative;
  overflow: hidden;
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%) scaleY(0);
  width: 3px;
  height: 60%;
  background: #1890ff;
  border-radius: 0 3px 3px 0;
  transition: transform 0.3s ease;
}

.nav-item:hover {
  background: rgba(24, 144, 255, 0.08);
  color: #1890ff;
  transform: translateX(4px);
}

.nav-item:hover::before {
  transform: translateY(-50%) scaleY(0.6);
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(102, 126, 234, 0.12), rgba(118, 75, 162, 0.08));
  color: #667eea;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.nav-item.active::before {
  transform: translateY(-50%) scaleY(1);
}

/* 导航图标 */
.nav-icon {
  width: 20px;
  height: 20px;
  margin-right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-icon svg {
  width: 100%;
  height: 100%;
}

.nav-label {
  flex: 1;
}

/* ---------- 折叠箭头 ---------- */
.sub-wrap {
  position: relative;
}

.sub-wrap .arrow {
  width: 16px;
  height: 16px;
  fill: #a0aec0;
  transition: all 0.3s ease;
}

.sub-wrap.open .arrow {
  transform: rotate(180deg);
  fill: #1890ff;
}

/* ---------- 子菜单 ---------- */
.sub-menu {
  overflow: hidden;
  padding-left: 8px;
  position: relative;
}

.sub-menu::before {
  content: '';
  position: absolute;
  left: 26px;
  top: 8px;
  bottom: 8px;
  width: 1px;
  background: linear-gradient(180deg, #e2e8f0, transparent);
}

.nav-item.sub {
  padding-left: 44px;
  font-size: 14px;
  color: #718096;
  position: relative;
}

.nav-item.sub:hover {
  color: #1890ff;
}

.nav-item.sub.active {
  color: #1890ff;
  background: rgba(24, 144, 255, 0.06);
}

.sub-dot {
  position: absolute;
  left: 22px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  background: #cbd5e0;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.nav-item.sub:hover .sub-dot,
.nav-item.sub.active .sub-dot {
  background: #1890ff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.2);
}

/* ---------- 用户信息区域 ---------- */
.user-section {
  padding: 12px 16px;
  margin: 0 12px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-radius: 10px;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(107, 141, 214, 0.2);
}

.user-section:hover {
  background: rgba(107, 141, 214, 0.1);
  border-color: rgba(107, 141, 214, 0.4);
}

.user-main {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  flex: 1;
  min-width: 0;
}

.user-avatar {
  background: linear-gradient(135deg, #6B8DD6 0%, #8E37D7 100%);
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 12px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  color: #ff4d4f;
  padding: 4px 8px;
  flex-shrink: 0;
  font-size: 16px;
}

.logout-btn:hover {
  color: #ff7875;
  background: rgba(255, 77, 79, 0.1);
}

/* ---------- 底部装饰 ---------- */
.bottom-decoration {
  padding: 16px;
  text-align: center;
}

.version {
  font-size: 12px;
  color: #a0aec0;
  letter-spacing: 1px;
}

/* ---------- 动画 ---------- */
.sub-enter-active,
.sub-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sub-enter-from,
.sub-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-10px);
}

.sub-enter-to,
.sub-leave-from {
  max-height: 120px;
  opacity: 1;
  transform: translateY(0);
}

/* 滚动条美化 */
.nav::-webkit-scrollbar {
  width: 4px;
}

.nav::-webkit-scrollbar-track {
  background: transparent;
}

.nav::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 2px;
}

.nav::-webkit-scrollbar-thumb:hover {
  background: #cbd5e0;
}
</style>
