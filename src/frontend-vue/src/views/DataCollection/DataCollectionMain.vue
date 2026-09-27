<template>
  <div class="data-collection-layout">
    <!-- 左侧二级导航 -->
    <aside class="sub-sidebar">
      <div class="sub-nav-header">
        <h3>数据采集</h3>
      </div>
      <nav class="sub-nav">
        <router-link
          v-for="item in subMenu"
          :key="item.path"
          :to="item.path"
          class="sub-nav-item"
          active-class="active"
        >
          <span class="sub-nav-icon" v-html="item.icon"></span>
          <span class="sub-nav-label">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- 右侧内容区 -->
    <main class="content-area">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
const subMenu = [
  {
    label: '控制台',
    path: '/dataCollection',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>`
  },
  {
    label: '任务监控',
    path: '/dataCollection/monitor',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`
  },
  {
    label: '采集配置',
    path: '/dataCollection/config',
    icon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`
  }
]
</script>

<style scoped>
.data-collection-layout {
  display: flex;
  height: 100%;
  min-height: calc(100vh - 64px);
}

/* 左侧二级导航 */
.sub-sidebar {
  width: 200px;
  background: linear-gradient(180deg, #fafbfc 0%, #f0f2f5 100%);
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.sub-nav-header {
  padding: 20px 16px;
  border-bottom: 1px solid #e8e8e8;
}

.sub-nav-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.sub-nav {
  flex: 1;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sub-nav-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  color: #4a5568;
  text-decoration: none;
  transition: all 0.3s ease;
  cursor: pointer;
}

.sub-nav-item:hover {
  background: rgba(24, 144, 255, 0.08);
  color: #1890ff;
}

.sub-nav-item.active {
  background: linear-gradient(90deg, rgba(24, 144, 255, 0.12), rgba(24, 144, 255, 0.06));
  color: #1890ff;
  font-weight: 500;
}

.sub-nav-icon {
  width: 18px;
  height: 18px;
  margin-right: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sub-nav-icon svg {
  width: 100%;
  height: 100%;
}

.sub-nav-label {
  font-size: 14px;
}

/* 右侧内容区 */
.content-area {
  flex: 1;
  padding: 24px;
  background: #f5f7fa;
  overflow: auto;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
