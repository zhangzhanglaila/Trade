<template>
  <div class="monitor-page">
    <h2 class="page-title">任务监控</h2>

    <!-- 筛选栏 -->
    <a-card class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <label>状态</label>
          <a-select v-model:value="filters.status" style="width: 120px" allowClear placeholder="全部">
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="0">就绪</a-select-option>
            <a-select-option value="1">已发布</a-select-option>
            <a-select-option value="2">已接收</a-select-option>
            <a-select-option value="3">已完成</a-select-option>
            <a-select-option value="4">失败</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <label>采集类型</label>
          <a-select v-model:value="filters.type" style="width: 150px" allowClear placeholder="全部">
            <a-select-option value="">全部</a-select-option>
            <a-select-option :value="0">HTML直接请求</a-select-option>
            <a-select-option :value="1">Selenium</a-select-option>
            <a-select-option :value="3">EasySpider</a-select-option>
            <a-select-option :value="4">自定义抓取</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <label>时间范围</label>
          <a-select v-model:value="filters.timeRange" style="width: 120px">
            <a-select-option value="today">今天</a-select-option>
            <a-select-option value="week">近7天</a-select-option>
            <a-select-option value="month">近30天</a-select-option>
          </a-select>
        </div>
        <div class="filter-item search-item">
          <label>搜索</label>
          <a-input-search
            v-model:value="filters.keyword"
            placeholder="任务ID或URL关键词"
            style="width: 240px"
            @search="handleSearch"
          />
        </div>
        <div class="filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </a-card>

    <!-- 任务列表 -->
    <a-card class="list-card">
      <a-table
        :columns="columns"
        :data-source="taskList"
        :pagination="pagination"
        :loading="loading"
        row-key="id"
        @change="handleTableChange"
      >
        <!-- 状态列 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'crawlType'">
            {{ getCrawlTypeText(record.crawlType) }}
          </template>
          <template v-else-if="column.key === 'urls'">
            <div class="url-list">
              <div v-for="(url, idx) in record.urls.slice(0, 2)" :key="idx" class="url-item">
                <a-typography-text ellipsis style="max-width: 300px">{{ url }}</a-typography-text>
              </div>
              <div v-if="record.urls.length > 2" class="url-more">
                +{{ record.urls.length - 2 }} 更多
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-btns">
              <a-button type="link" size="small" @click="showDetail(record)">详情</a-button>
              <a-button 
                type="link" 
                size="small" 
                danger 
                v-if="record.status === 4"
                @click="handleRetry(record)"
              >
                重试
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      title="任务详情"
      width="600"
      :footer="null"
    >
      <div class="detail-content" v-if="currentTask">
        <div class="detail-section">
          <h4>基本信息</h4>
          <div class="detail-item">
            <span class="detail-label">任务ID</span>
            <span class="detail-value">{{ currentTask.id }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">采集类型</span>
            <span class="detail-value">{{ getCrawlTypeText(currentTask.crawlType) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">状态</span>
            <a-tag :color="getStatusColor(currentTask.status)">
              {{ getStatusText(currentTask.status) }}
            </a-tag>
          </div>
          <div class="detail-item">
            <span class="detail-label">创建时间</span>
            <span class="detail-value">{{ currentTask.createTime }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">更新时间</span>
            <span class="detail-value">{{ currentTask.updateTime }}</span>
          </div>
        </div>

        <div class="detail-section">
          <h4>URL列表</h4>
          <div class="detail-urls">
            <div v-for="(url, idx) in currentTask.urls" :key="idx" class="detail-url-item">
              <a-typography-text copyable>{{ url }}</a-typography-text>
            </div>
          </div>
        </div>

        <div class="detail-section" v-if="currentTask.message">
          <h4>处理消息</h4>
          <div class="detail-message">{{ currentTask.message }}</div>
        </div>

        <div class="detail-section" v-if="currentTask.additionalInfo">
          <h4>额外信息</h4>
          <pre class="detail-json">{{ JSON.stringify(currentTask.additionalInfo, null, 2) }}</pre>
        </div>

        <div class="detail-actions">
          <a-button type="primary" @click="viewInCorpus(currentTask)">查看语料</a-button>
          <a-button @click="viewInGraph(currentTask)">查看图谱</a-button>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'

// 筛选条件
const filters = reactive({
  status: undefined,
  type: undefined,
  timeRange: 'week',
  keyword: ''
})

// 表格列定义
const columns = [
  {
    title: '任务ID',
    dataIndex: 'id',
    key: 'id',
    width: 120,
  },
  {
    title: '采集类型',
    dataIndex: 'crawlType',
    key: 'crawlType',
    width: 130,
  },
  {
    title: 'URL列表',
    key: 'urls',
    ellipsis: true,
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    key: 'action',
    width: 120,
  },
]

// 状态映射
const statusMap = {
  0: { text: '就绪', color: 'default' },
  1: { text: '已发布', color: 'processing' },
  2: { text: '已接收', color: 'processing' },
  3: { text: '已完成', color: 'success' },
  4: { text: '失败', color: 'error' },
}

// 采集类型映射
const crawlTypeMap = {
  0: 'HTML直接请求',
  1: 'Selenium',
  2: 'Adaptor',
  3: 'EasySpider',
  4: '自定义抓取',
}

// 获取状态文本和颜色
const getStatusText = (status) => statusMap[status]?.text || '未知'
const getStatusColor = (status) => statusMap[status]?.color || 'default'
const getCrawlTypeText = (type) => crawlTypeMap[type] || '未知'

// 模拟任务列表数据
const taskList = ref([
  {
    id: '1005',
    crawlType: 0,
    urls: ['https://example.com/news/1005'],
    status: 3,
    createTime: '2026-03-14 14:30:00',
    updateTime: '2026-03-14 14:32:15',
    message: '处理成功',
    additionalInfo: { source: 'manual' }
  },
  {
    id: '1004',
    crawlType: 4,
    urls: ['https://example.com/news/1004', 'https://example.com/news/1004-2'],
    status: 2,
    createTime: '2026-03-14 14:25:00',
    updateTime: '2026-03-14 14:25:30',
    message: '',
    additionalInfo: { crawler_id: '102' }
  },
  {
    id: '1003',
    crawlType: 3,
    urls: ['https://example.com/news/1003'],
    status: 4,
    createTime: '2026-03-14 14:20:00',
    updateTime: '2026-03-14 14:21:00',
    message: '连接超时',
    additionalInfo: {}
  },
  {
    id: '1002',
    crawlType: 1,
    urls: ['https://example.com/news/1002'],
    status: 3,
    createTime: '2026-03-14 14:15:00',
    updateTime: '2026-03-14 14:18:30',
    message: '处理成功',
    additionalInfo: {}
  },
  {
    id: '1001',
    crawlType: 0,
    urls: ['https://example.com/news/1001', 'https://example.com/news/1001-2', 'https://example.com/news/1001-3'],
    status: 3,
    createTime: '2026-03-14 14:10:00',
    updateTime: '2026-03-14 14:12:45',
    message: '处理成功',
    additionalInfo: { batch: true }
  },
])

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 128,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total) => `共 ${total} 条`,
})

// 加载状态
const loading = ref(false)

// 详情抽屉
const detailVisible = ref(false)
const currentTask = ref(null)

// 方法
const handleSearch = () => {
  loading.value = true
  // 模拟查询
  setTimeout(() => {
    loading.value = false
    message.success('查询完成')
  }, 500)
}

const handleReset = () => {
  filters.status = undefined
  filters.type = undefined
  filters.timeRange = 'week'
  filters.keyword = ''
  handleSearch()
}

const handleTableChange = (pag) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  handleSearch()
}

const showDetail = (record) => {
  currentTask.value = record
  detailVisible.value = true
}

const handleRetry = (record) => {
  message.success(`已重新触发任务 #${record.id}`)
}

const viewInCorpus = (task) => {
  message.info(`查看任务 #${task.id} 的语料`)
}

const viewInGraph = (task) => {
  message.info(`查看任务 #${task.id} 的知识图谱`)
}
</script>

<style scoped>
.monitor-page {
  max-width: 1400px;
}

.page-title {
  font-size: 24px;
  font-weight: 500;
  margin-bottom: 24px;
  color: #262626;
}

.filter-card {
  margin-bottom: 24px;
}

.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item label {
  color: #595959;
  font-size: 12px;
}

.search-item {
  flex: 1;
  min-width: 200px;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.list-card {
  margin-bottom: 24px;
}

.url-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.url-item {
  color: #595959;
}

.url-more {
  color: #1890ff;
  font-size: 12px;
}

.action-btns {
  display: flex;
  gap: 8px;
}

.detail-content {
  padding: 8px 0;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-item {
  display: flex;
  margin-bottom: 12px;
}

.detail-label {
  width: 100px;
  color: #8c8c8c;
  flex-shrink: 0;
}

.detail-value {
  color: #262626;
  flex: 1;
}

.detail-urls {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
}

.detail-url-item {
  margin-bottom: 8px;
  padding: 8px;
  background: #fff;
  border-radius: 4px;
}

.detail-message {
  padding: 12px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  color: #cf1322;
}

.detail-json {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
}

.detail-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
