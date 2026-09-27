<template>
  <div class="console-page">
    <h2 class="page-title">采集控制台</h2>
    
    <!-- 定时任务状态卡片 -->
    <a-card class="status-card" title="定时任务状态">
      <div class="status-content">
        <div class="status-item">
          <span class="status-label">当前状态</span>
          <span class="status-value">
            <a-badge :status="timerStatus === '运行中' ? 'processing' : 'default'" :text="timerStatus" />
          </span>
        </div>
        <div class="status-item">
          <span class="status-label">上次执行</span>
          <span class="status-value">{{ lastExecuteTime || '暂无' }}</span>
        </div>
        <div class="status-item">
          <span class="status-label">下次执行</span>
          <span class="status-value">{{ nextExecuteTime || '暂无' }}</span>
        </div>
        <div class="status-item">
          <span class="status-label">累计采集</span>
          <span class="status-value">{{ totalCollected }} 条</span>
        </div>
      </div>
      <div class="status-actions">
        <a-button type="primary" @click="handleExecuteNow">立即执行</a-button>
        <a-button @click="handlePause" v-if="timerStatus === '运行中'">暂停</a-button>
        <a-button @click="handleResume" v-else>恢复</a-button>
        <a-button @click="showCronModal = true">修改定时策略</a-button>
      </div>
    </a-card>

    <!-- 快速采集入口 -->
    <a-card class="collection-card">
      <a-tabs v-model:activeKey="activeTab">
        <!-- Tab 1: 快速录入 -->
        <a-tab-pane key="quick" tab="快速录入">
          <div class="tab-content">
            <div class="form-item">
              <label>新闻内容</label>
              <a-textarea
                v-model:value="quickForm.content"
                :rows="8"
                placeholder="在此粘贴新闻文本内容..."
              />
            </div>
            <div class="form-row">
              <div class="form-item">
                <label>采集类型</label>
                <a-select v-model:value="quickForm.crawlType" style="width: 200px">
                  <a-select-option :value="0">HTML直接请求</a-select-option>
                  <a-select-option :value="1">Selenium</a-select-option>
                  <a-select-option :value="4">自定义抓取</a-select-option>
                </a-select>
              </div>
              <div class="form-item">
                <label>选项</label>
                <a-checkbox v-model:checked="quickForm.cleanHtml">清洗HTML</a-checkbox>
              </div>
            </div>
            <div class="form-actions">
              <a-button type="primary" size="large" @click="handleQuickSubmit">提交处理</a-button>
            </div>
          </div>
        </a-tab-pane>

        <!-- Tab 2: 批量URL采集 -->
        <a-tab-pane key="batch" tab="批量URL采集">
          <div class="tab-content">
            <div class="form-item">
              <label>URL列表（每行一个）</label>
              <a-textarea
                v-model:value="batchForm.urls"
                :rows="6"
                placeholder="https://example.com/news/1&#10;https://example.com/news/2"
              />
            </div>
            <div class="form-item">
              <label>高级参数（Key=Value格式，一行一项）</label>
              <a-textarea
                v-model:value="batchForm.additionalInfo"
                :rows="3"
                placeholder="crawler_id=102&#10;source=custom"
              />
            </div>
            <div class="form-row">
              <div class="form-item">
                <label>采集类型</label>
                <a-select v-model:value="batchForm.crawlType" style="width: 200px">
                  <a-select-option :value="0">HTML直接请求</a-select-option>
                  <a-select-option :value="1">Selenium</a-select-option>
                  <a-select-option :value="3">EasySpider</a-select-option>
                  <a-select-option :value="4">自定义抓取</a-select-option>
                </a-select>
              </div>
              <div class="form-item">
                <label>后续处理</label>
                <a-checkbox v-model:checked="batchForm.commitPublish">提交数据处理器</a-checkbox>
              </div>
            </div>
            <div class="form-actions">
              <a-button type="primary" size="large" @click="handleBatchSubmit">发布采集任务</a-button>
            </div>
          </div>
        </a-tab-pane>

        <!-- Tab 3: 定时策略（可折叠） -->
        <a-tab-pane key="cron" tab="定时策略">
          <div class="tab-content">
            <div class="cron-display">
              <div class="cron-item">
                <span class="cron-label">当前Cron表达式</span>
                <span class="cron-value">{{ currentCron }}</span>
              </div>
              <div class="cron-item">
                <span class="cron-label">下次执行时间预览</span>
                <ul class="cron-preview">
                  <li v-for="(time, index) in nextExecutions" :key="index">{{ time }}</li>
                </ul>
              </div>
            </div>
            <div class="form-actions">
              <a-button type="primary" @click="showCronModal = true">修改Cron表达式</a-button>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 最近采集记录 -->
    <a-card class="recent-card" title="最近采集记录">
      <a-list :data-source="recentRecords" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <div class="record-item">
              <span class="record-id">#{{ item.id }}</span>
              <span class="record-type">{{ item.type }}</span>
              <a-badge :status="getStatusType(item.status)" :text="item.status" />
              <span class="record-time">{{ item.time }}</span>
            </div>
          </a-list-item>
        </template>
      </a-list>
    </a-card>

    <!-- 修改Cron弹窗 -->
    <a-modal
      v-model:open="showCronModal"
      title="修改定时策略"
      @ok="handleUpdateCron"
      okText="保存"
      cancelText="取消"
    >
      <div class="cron-modal-content">
        <div class="form-item">
          <label>Cron表达式</label>
          <a-input v-model:value="editCron" placeholder="0 0 2 * * ?" />
          <p class="form-hint">格式：秒 分 时 日 月 周 （示例：每天凌晨2点执行 = 0 0 2 * * ?）</p>
        </div>
        <div class="cron-preview-section" v-if="editCron">
          <label>下次执行预览</label>
          <ul>
            <li v-for="(time, index) in previewExecutions" :key="index">{{ time }}</li>
          </ul>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'

// 定时任务状态
const timerStatus = ref('运行中')
const lastExecuteTime = ref('2026-03-14 10:30:00')
const nextExecuteTime = ref('2026-03-15 02:00:00')
const totalCollected = ref(1280)
const currentCron = ref('0 0 2 * * ?')

// Tab状态
const activeTab = ref('quick')

// 快速录入表单
const quickForm = reactive({
  content: '',
  crawlType: 0,
  cleanHtml: true
})

// 批量采集表单
const batchForm = reactive({
  urls: '',
  additionalInfo: '',
  crawlType: 0,
  commitPublish: true
})

// Cron编辑
const showCronModal = ref(false)
const editCron = ref('0 0 2 * * ?')

// 模拟最近记录
const recentRecords = ref([
  { id: '1005', type: '快速录入', status: '成功', time: '2分钟前' },
  { id: '1004', type: '批量采集', status: '进行中', time: '5分钟前' },
  { id: '1003', type: '定时任务', status: '成功', time: '1小时前' },
  { id: '1002', type: '快速录入', status: '失败', time: '2小时前' },
  { id: '1001', type: '批量采集', status: '成功', time: '3小时前' }
])

// 计算属性：下次执行预览
const nextExecutions = computed(() => {
  // 模拟预览数据
  return [
    '2026-03-15 02:00:00',
    '2026-03-16 02:00:00',
    '2026-03-17 02:00:00',
    '2026-03-18 02:00:00'
  ]
})

const previewExecutions = computed(() => {
  // 根据editCron计算预览
  return nextExecutions.value
})

// 获取状态类型
const getStatusType = (status) => {
  const map = {
    '成功': 'success',
    '进行中': 'processing',
    '失败': 'error'
  }
  return map[status] || 'default'
}

// 方法
const handleExecuteNow = () => {
  message.success('已触发立即执行任务')
}

const handlePause = () => {
  timerStatus.value = '已暂停'
  message.success('定时任务已暂停')
}

const handleResume = () => {
  timerStatus.value = '运行中'
  message.success('定时任务已恢复')
}

const handleQuickSubmit = () => {
  if (!quickForm.content.trim()) {
    message.warning('请输入新闻内容')
    return
  }
  message.success('已提交处理')
  quickForm.content = ''
}

const handleBatchSubmit = () => {
  if (!batchForm.urls.trim()) {
    message.warning('请输入URL列表')
    return
  }
  message.success('已发布采集任务')
  batchForm.urls = ''
}

const handleUpdateCron = () => {
  currentCron.value = editCron.value
  showCronModal.value = false
  message.success('定时策略已更新')
}
</script>

<style scoped>
.console-page {
  max-width: 1200px;
}

.page-title {
  font-size: 24px;
  font-weight: 500;
  margin-bottom: 24px;
  color: #262626;
}

.status-card {
  margin-bottom: 24px;
}

.status-content {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.status-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-label {
  color: #8c8c8c;
  font-size: 14px;
}

.status-value {
  font-size: 16px;
  font-weight: 500;
  color: #262626;
}

.status-actions {
  display: flex;
  gap: 12px;
}

.collection-card {
  margin-bottom: 24px;
}

.tab-content {
  padding: 16px 0;
}

.form-item {
  margin-bottom: 16px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  color: #262626;
  font-weight: 500;
}

.form-row {
  display: flex;
  gap: 24px;
}

.form-row .form-item {
  flex: 1;
}

.form-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.form-hint {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 4px;
  margin-bottom: 0;
}

.cron-display {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.cron-item {
  margin-bottom: 12px;
}

.cron-item:last-child {
  margin-bottom: 0;
}

.cron-label {
  display: block;
  color: #8c8c8c;
  font-size: 12px;
  margin-bottom: 4px;
}

.cron-value {
  font-family: monospace;
  font-size: 16px;
  color: #52c41a;
  font-weight: 500;
}

.cron-preview {
  list-style: none;
  padding: 0;
  margin: 0;
}

.cron-preview li {
  padding: 4px 0;
  color: #262626;
}

.recent-card {
  margin-bottom: 24px;
}

.record-item {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.record-id {
  font-family: monospace;
  color: #1890ff;
  min-width: 60px;
}

.record-type {
  color: #262626;
  min-width: 80px;
}

.record-time {
  color: #8c8c8c;
  margin-left: auto;
}

.cron-modal-content {
  padding: 8px 0;
}

.cron-preview-section {
  margin-top: 16px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 4px;
}

.cron-preview-section label {
  display: block;
  margin-bottom: 8px;
  color: #262626;
  font-weight: 500;
}

.cron-preview-section ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.cron-preview-section li {
  padding: 4px 0;
  color: #595959;
}
</style>
