<template>
  <main class="aj-report-page">
    <section class="page-header">
      <div>
        <h2>AJ-Report 大屏入口</h2>
        <p>通过前端统一入口查看或跳转到独立运行的 AJ-Report 服务。</p>
      </div>
      <div class="actions">
        <a-button @click="loadConfig" :loading="loading">刷新配置</a-button>
        <a-button type="primary" @click="openDirect" :disabled="!config.targetUrl">直接打开</a-button>
      </div>
    </section>

    <section class="info-grid">
      <div class="info-card">
        <div class="label">AJ-Report 地址</div>
        <div class="value">{{ config.baseUrl || '-' }}</div>
      </div>
      <div class="info-card">
        <div class="label">入口路径</div>
        <div class="value">{{ config.entryPath || '-' }}</div>
      </div>
      <div class="info-card">
        <div class="label">大屏1地址</div>
        <div class="value break-all">{{ config.screen1Url || '-' }}</div>
      </div>
      <div class="info-card">
        <div class="label">大屏2地址</div>
        <div class="value break-all">{{ config.screen2Url || '-' }}</div>
      </div>
    </section>

    <section class="action-row">
      <a-button @click="openUrl(config.screen1Url)" :disabled="!config.screen1Url">打开大屏1</a-button>
      <a-button @click="openUrl(config.screen2Url)" :disabled="!config.screen2Url">打开大屏2</a-button>
    </section>

    <a-alert
      v-if="errorMessage"
      type="error"
      show-icon
      :message="errorMessage"
      class="status-alert"
    />
    <a-alert
      v-else
      type="info"
      show-icon
      class="status-alert"
      message="如果下方 iframe 空白，请直接点击“直接打开”，并检查 AJ-Report 是否允许被嵌入。"
    />

    <section v-if="config.embedEnabled && currentFrameUrl" class="frame-card">
      <div class="frame-toolbar">
        <a-button size="small" :type="currentFrameUrl === config.screen1Url ? 'primary' : 'default'" @click="currentFrameUrl = config.screen1Url">大屏1</a-button>
        <a-button size="small" :type="currentFrameUrl === config.screen2Url ? 'primary' : 'default'" @click="currentFrameUrl = config.screen2Url">大屏2</a-button>
      </div>
      <iframe :src="currentFrameUrl" :title="config.frameTitle || 'AJ-Report 大屏'" />
    </section>

    <section v-else class="empty-card">
      <p>当前未启用 iframe 内嵌，或尚未获取到 AJ-Report 地址。</p>
      <a-button type="primary" @click="openDirect" :disabled="!config.targetUrl">打开 AJ-Report</a-button>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getAjReportConfig } from '@/api/ajReport'

const loading = ref(false)
const errorMessage = ref('')
const currentFrameUrl = ref('')
const config = reactive({
  baseUrl: '',
  entryPath: '',
  targetUrl: '',
  screen1Path: '',
  screen2Path: '',
  screen1Url: '',
  screen2Url: '',
  embedEnabled: false,
  frameTitle: 'AJ-Report 大屏入口'
})

async function loadConfig() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getAjReportConfig()
    Object.assign(config, res || {})
    currentFrameUrl.value = res?.screen1Url || res?.targetUrl || ''
  } catch (error) {
    errorMessage.value = '获取 AJ-Report 配置失败，请确认后端 8080 和 AJ-Report 服务均已启动。'
  } finally {
    loading.value = false
  }
}

function openDirect() {
  openUrl(config.targetUrl)
}

function openUrl(url) {
  if (!url) {
    message.warning('AJ-Report 地址尚未加载成功')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.aj-report-page {
  min-height: 100vh;
  padding: 24px 32px;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  color: #1f2937;
}

.page-header p {
  margin: 0;
  color: #6b7280;
}

.actions {
  display: flex;
  gap: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.info-card,
.frame-card,
.empty-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.info-card {
  padding: 18px;
}

.label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}

.value {
  font-size: 15px;
  color: #111827;
  font-weight: 500;
}

.break-all {
  word-break: break-all;
}

.status-alert {
  margin-bottom: 16px;
}

.action-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.frame-card {
  height: calc(100vh - 320px);
  min-height: 640px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.frame-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px 12px 0;
}

.frame-card iframe {
  width: 100%;
  height: 100%;
  border: 0;
  flex: 1;
}

.empty-card {
  padding: 32px;
  text-align: center;
}

@media (max-width: 1200px) {
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .aj-report-page {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .frame-card {
    height: 70vh;
    min-height: 480px;
  }
}
</style>
