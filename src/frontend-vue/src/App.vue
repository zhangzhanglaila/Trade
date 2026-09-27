<!-- App.vue -->
<template>
  <div class="app" :class="{ 'with-sidebar': !isPublicPage }">
    <!-- 登录/注册页不显示侧边栏 -->
    <template v-if="isPublicPage">
      <router-view />
    </template>
    
    <!-- 其他页面显示侧边栏 -->
    <template v-else>
      <SideNav />
      <section class="right">
        <router-view />
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SideNav from '@/components/SideNav.vue'

const route = useRoute()

// 判断是否为公开页面（登录/注册）
const isPublicPage = computed(() => {
  return route.meta.public === true
})
</script>

<style scoped>
/* 根布局 */
.app {
  height: 100vh;
  background: #f0f2f5;
}

/* 带侧边栏的布局 */
.app.with-sidebar {
  display: flex;
}

/* 右侧内容区 */
.right {
  flex: 1;
  overflow: auto;
  padding: 0;
  margin: 0;
}
</style>
