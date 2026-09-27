<template>
  <div class="crawler-news-page">
    <h2 class="page-title">新闻采集</h2>

    <a-card class="action-card" title="1. 选择数据源并抓取列表">
      <a-space wrap>
        <a-select v-model:value="source" style="width: 220px" placeholder="请选择数据源">
          <a-select-option v-for="item in sources" :key="item" :value="item">
            {{ item }}
          </a-select-option>
        </a-select>
        <a-button type="primary" :loading="listLoading" @click="handleFetchList">抓取列表</a-button>
        <a-button :disabled="!listTaskId" @click="pollListTask">刷新列表任务</a-button>
        <span v-if="listTaskId" class="task-hint">任务：{{ listTaskId }}</span>
      </a-space>
    </a-card>

    <a-card class="table-card" title="2. 列表结果">
      <a-table
        :columns="listColumns"
        :data-source="listData"
        :pagination="false"
        row-key="url"
        :row-selection="rowSelection"
        :loading="listLoading"
      />
      <div class="table-actions">
        <a-space wrap>
          <span>已选 {{ selectedRows.length }} 条</span>
          <a-button type="primary" :loading="contentLoading" :disabled="!selectedRows.length" @click="handleFetchContent">
            抓取正文
          </a-button>
          <a-button :disabled="!contentTaskId" @click="pollContentTask">刷新正文任务</a-button>
          <span v-if="contentTaskId" class="task-hint">任务：{{ contentTaskId }}</span>
        </a-space>
      </div>
    </a-card>

    <a-card class="table-card" title="3. 正文结果预览">
      <a-table
        :columns="contentColumns"
        :data-source="contentList"
        :pagination="{ pageSize: 5, showSizeChanger: false }"
        row-key="key"
        :loading="contentLoading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'content'">
            <a-tooltip :title="record.contentText">
              <span>{{ record.contentText?.slice(0, 80) }}<template v-if="record.contentText?.length > 80">...</template></span>
            </a-tooltip>
          </template>
        </template>
      </a-table>
      <div class="table-actions">
        <a-space>
          <a-button type="primary" :loading="importLoading" :disabled="!contentTaskId || !contentList.length" @click="handleImport">
            导入新闻库
          </a-button>
          <span v-if="importSummary">已导入 {{ importSummary.importedCount }} 条，跳过 {{ importSummary.skippedCount }} 条</span>
        </a-space>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  fetchCrawlerContent,
  fetchCrawlerList,
  getCrawlerSources,
  getCrawlerTask,
  importCrawlerResults
} from '@/api/crawler'

const sources = ref([])
const source = ref(undefined)
const listTaskId = ref('')
const contentTaskId = ref('')
const listData = ref([])
const contentMap = ref({})
const selectedRows = ref([])
const listLoading = ref(false)
const contentLoading = ref(false)
const importLoading = ref(false)
const importSummary = ref(null)

const listColumns = [
  { title: '标题', dataIndex: 'title', ellipsis: true },
  { title: '日期', dataIndex: 'date', width: 180 },
  { title: '摘要', dataIndex: 'desc', ellipsis: true },
  { title: 'URL', dataIndex: 'url', ellipsis: true }
]

const contentColumns = [
  { title: '标题', dataIndex: 'title', ellipsis: true },
  { title: '日期', dataIndex: 'date', width: 180 },
  { title: '正文', key: 'content' }
]

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRows.value.map(item => item.url),
  onChange: (_keys, rows) => {
    selectedRows.value = rows
  }
}))

const contentList = computed(() => Object.entries(contentMap.value).map(([key, item]) => ({
  key,
  ...item,
  contentText: item.content_text || item.content || ''
})))

async function loadSources() {
  const res = await getCrawlerSources()
  sources.value = res.data || []
  if (!source.value && sources.value.length) {
    source.value = sources.value[0]
  }
}

async function handleFetchList() {
  if (!source.value) {
    message.warning('请先选择数据源')
    return
  }
  listLoading.value = true
  importSummary.value = null
  selectedRows.value = []
  contentMap.value = {}
  contentTaskId.value = ''
  try {
    const res = await fetchCrawlerList({ source: source.value })
    listTaskId.value = res.data.taskId
    message.success('列表抓取任务已创建')
    await pollListTask()
  } finally {
    listLoading.value = false
  }
}

async function pollListTask() {
  if (!listTaskId.value) return
  listLoading.value = true
  try {
    const res = await getCrawlerTask(listTaskId.value)
    const task = res.data
    if (task.status === 'SUCCESS') {
      listData.value = task.listResult || []
      message.success('列表抓取完成')
    } else if (task.status === 'FAILED') {
      message.error(task.error || '列表抓取失败')
    } else {
      message.info(`当前状态：${task.status}`)
    }
  } finally {
    listLoading.value = false
  }
}

async function handleFetchContent() {
  if (!source.value) {
    message.warning('请先选择数据源')
    return
  }
  if (!selectedRows.value.length) {
    message.warning('请先勾选要抓取的新闻')
    return
  }
  contentLoading.value = true
  importSummary.value = null
  try {
    const res = await fetchCrawlerContent({
      source: source.value,
      urls: selectedRows.value.map(item => item.url)
    })
    contentTaskId.value = res.data.taskId
    message.success('正文抓取任务已创建')
    await pollContentTask()
  } finally {
    contentLoading.value = false
  }
}

async function pollContentTask() {
  if (!contentTaskId.value) return
  contentLoading.value = true
  try {
    const res = await getCrawlerTask(contentTaskId.value)
    const task = res.data
    if (task.status === 'SUCCESS') {
      contentMap.value = task.contentResult || {}
      message.success('正文抓取完成')
    } else if (task.status === 'FAILED') {
      message.error(task.error || '正文抓取失败')
    } else {
      message.info(`当前状态：${task.status}`)
    }
  } finally {
    contentLoading.value = false
  }
}

async function handleImport() {
  if (!contentTaskId.value) {
    message.warning('请先完成正文抓取')
    return
  }
  importLoading.value = true
  try {
    const res = await importCrawlerResults({ taskId: contentTaskId.value })
    importSummary.value = res.data
    message.success(`导入完成：${res.data.importedCount} 条`)
  } finally {
    importLoading.value = false
  }
}

onMounted(() => {
  loadSources().catch(err => {
    message.error(err?.message || '加载数据源失败')
  })
})
</script>

<style scoped>
.crawler-news-page {
  max-width: 1320px;
}

.page-title {
  font-size: 24px;
  font-weight: 500;
  margin-bottom: 24px;
  color: #262626;
}

.action-card,
.table-card {
  margin-bottom: 20px;
}

.table-actions {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-hint {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
