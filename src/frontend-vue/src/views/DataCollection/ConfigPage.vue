<template>
  <div class="config-page">
    <h2 class="page-title">采集配置</h2>

    <!-- 数据源配置 -->
    <a-card class="config-card" title="数据源管理">
      <template #extra>
        <a-button type="primary" @click="showAddDatasourceModal = true">
          <plus-outlined /> 新增数据源
        </a-button>
      </template>

      <a-tabs v-model:activeKey="datasourceActiveKey">
        <a-tab-pane key="custom" tab="自定义数据源">
          <a-table
            :columns="datasourceColumns"
            :data-source="customDatasources"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-switch
                  :checked="record.status === 1"
                  @change="(checked) => handleToggleDatasource(record, checked)"
                />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="editDatasource(record)">编辑</a-button>
                <a-button type="link" size="small" danger @click="deleteDatasource(record)">删除</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="easyspider" tab="EasySpider">
          <a-table
            :columns="easyspiderColumns"
            :data-source="easyspiderDatasources"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-switch
                  :checked="record.status === 1"
                  @change="(checked) => handleToggleDatasource(record, checked)"
                />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="editDatasource(record)">编辑</a-button>
                <a-button type="link" size="small" danger @click="deleteDatasource(record)">删除</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="gdelt" tab="GDELT">
          <div class="gdelt-config">
            <div class="gdelt-item">
              <span class="gdelt-label">GDELT数据源</span>
              <a-switch v-model:checked="gdeltEnabled" @change="handleGdeltChange" />
            </div>
            <p class="gdelt-desc">启用后，系统将定时从GDELT全球事件数据库采集新闻数据</p>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 爬虫配置 -->
    <a-card class="config-card" title="爬虫管理">
      <template #extra>
        <a-button type="primary" @click="showAddCrawlerModal = true">
          <plus-outlined /> 新增爬虫
        </a-button>
      </template>

      <a-tabs v-model:activeKey="crawlerActiveKey">
        <a-tab-pane key="custom" tab="自定义爬虫">
          <a-table
            :columns="crawlerColumns"
            :data-source="customCrawlers"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-switch
                  :checked="record.status === 1"
                  @change="(checked) => handleToggleCrawler(record, checked)"
                />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="editCrawler(record)">编辑</a-button>
                <a-button type="link" size="small" danger @click="deleteCrawler(record)">删除</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="easyspider" tab="EasySpider">
          <a-table
            :columns="easyspiderCrawlerColumns"
            :data-source="easyspiderCrawlers"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-switch
                  :checked="record.status === 1"
                  @change="(checked) => handleToggleCrawler(record, checked)"
                />
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="editCrawler(record)">编辑</a-button>
                <a-button type="link" size="small" danger @click="deleteCrawler(record)">删除</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 系统设置 -->
    <a-card class="config-card" title="系统设置">
      <div class="settings-list">
        <div class="setting-item">
          <div class="setting-info">
            <div class="setting-title">自动重试失败任务</div>
            <div class="setting-desc">任务失败时自动重试，最多重试3次</div>
          </div>
          <a-switch v-model:checked="settings.autoRetry" />
        </div>
        <div class="setting-item">
          <div class="setting-info">
            <div class="setting-title">采集结果通知</div>
            <div class="setting-desc">任务完成时发送系统通知</div>
          </div>
          <a-switch v-model:checked="settings.notification" />
        </div>
        <div class="setting-item">
          <div class="setting-info">
            <div class="setting-title">默认清洗HTML</div>
            <div class="setting-desc">采集时自动清洗HTML标签</div>
          </div>
          <a-switch v-model:checked="settings.defaultCleanHtml" />
        </div>
        <div class="setting-item">
          <div class="setting-info">
            <div class="setting-title">批量任务大小</div>
            <div class="setting-desc">每个批量任务包含的最大URL数量</div>
          </div>
          <a-input-number v-model:value="settings.batchSize" :min="1" :max="100" />
        </div>
      </div>
    </a-card>

    <!-- 新增数据源弹窗 -->
    <a-modal
      v-model:open="showAddDatasourceModal"
      title="新增数据源"
      @ok="handleAddDatasource"
      okText="保存"
      cancelText="取消"
    >
      <a-form :model="datasourceForm" layout="vertical">
        <a-form-item label="数据源名称" required>
          <a-input v-model:value="datasourceForm.name" placeholder="请输入名称" />
        </a-form-item>
        <a-form-item label="请求地址" required>
          <a-input v-model:value="datasourceForm.reqUrl" placeholder="http://example.com/api" />
        </a-form-item>
        <a-form-item label="请求方式">
          <a-select v-model:value="datasourceForm.crawlMethod">
            <a-select-option value="GET">GET</a-select-option>
            <a-select-option value="POST">POST</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="参数配置">
          <a-textarea
            v-model:value="datasourceForm.params"
            :rows="3"
            placeholder="JSON格式参数"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增爬虫弹窗 -->
    <a-modal
      v-model:open="showAddCrawlerModal"
      title="新增爬虫"
      @ok="handleAddCrawler"
      okText="保存"
      cancelText="取消"
    >
      <a-form :model="crawlerForm" layout="vertical">
        <a-form-item label="爬虫名称" required>
          <a-input v-model:value="crawlerForm.name" placeholder="请输入名称" />
        </a-form-item>
        <a-form-item label="回调地址" required>
          <a-input v-model:value="crawlerForm.reqUrl" placeholder="http://example.com/callback" />
        </a-form-item>
        <a-form-item label="参数配置">
          <a-textarea
            v-model:value="crawlerForm.params"
            :rows="3"
            placeholder="JSON格式参数"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'

// 数据源Tab
const datasourceActiveKey = ref('custom')
const crawlerActiveKey = ref('custom')

// 数据源表格列
const datasourceColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '请求地址', dataIndex: 'reqUrl', key: 'reqUrl', ellipsis: true },
  { title: '请求方式', dataIndex: 'crawlMethod', key: 'crawlMethod', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 },
]

const easyspiderColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId' },
  { title: '请求方式', dataIndex: 'crawlMethod', key: 'crawlMethod', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 },
]

// 爬虫表格列
const crawlerColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '回调地址', dataIndex: 'reqUrl', key: 'reqUrl', ellipsis: true },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 },
]

const easyspiderCrawlerColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId' },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 },
]

// 模拟数据
const customDatasources = ref([
  {
    id: '1',
    name: 'Custom-API-1',
    reqUrl: 'http://localhost:8080/api/news',
    crawlMethod: 'GET',
    params: '{"page": 1}',
    status: 1,
    createTime: '2026-03-01 10:00:00',
  },
  {
    id: '2',
    name: 'Custom-API-2',
    reqUrl: 'http://localhost:8080/api/data',
    crawlMethod: 'POST',
    params: '{"type": "news"}',
    status: 0,
    createTime: '2026-03-05 14:30:00',
  },
])

const easyspiderDatasources = ref([
  {
    id: '3',
    name: 'EasySpider-News',
    taskId: 'task-123',
    crawlMethod: 'GET',
    status: 1,
    createTime: '2026-03-10 09:00:00',
  },
])

const customCrawlers = ref([
  {
    id: '1',
    name: 'Custom-Crawler-1',
    reqUrl: 'http://localhost:8080/callback',
    status: 1,
    createTime: '2026-03-02 11:00:00',
  },
])

const easyspiderCrawlers = ref([
  {
    id: '2',
    name: 'EasySpider-Crawler-1',
    taskId: 'task-456',
    status: 1,
    createTime: '2026-03-08 16:00:00',
  },
])

// GDELT
const gdeltEnabled = ref(false)

// 系统设置
const settings = reactive({
  autoRetry: true,
  notification: true,
  defaultCleanHtml: true,
  batchSize: 10,
})

// 弹窗控制
const showAddDatasourceModal = ref(false)
const showAddCrawlerModal = ref(false)

// 表单数据
const datasourceForm = reactive({
  name: '',
  reqUrl: '',
  crawlMethod: 'GET',
  params: '',
})

const crawlerForm = reactive({
  name: '',
  reqUrl: '',
  params: '',
})

// 方法
const handleToggleDatasource = (record, checked) => {
  record.status = checked ? 1 : 0
  message.success(`${record.name} 已${checked ? '启用' : '禁用'}`)
}

const handleToggleCrawler = (record, checked) => {
  record.status = checked ? 1 : 0
  message.success(`${record.name} 已${checked ? '启用' : '禁用'}`)
}

const handleGdeltChange = (checked) => {
  message.success(`GDELT 已${checked ? '启用' : '禁用'}`)
}

const editDatasource = (record) => {
  message.info(`编辑数据源: ${record.name}`)
}

const deleteDatasource = (record) => {
  message.success(`已删除数据源: ${record.name}`)
}

const editCrawler = (record) => {
  message.info(`编辑爬虫: ${record.name}`)
}

const deleteCrawler = (record) => {
  message.success(`已删除爬虫: ${record.name}`)
}

const handleAddDatasource = () => {
  showAddDatasourceModal.value = false
  message.success('数据源已添加')
  datasourceForm.name = ''
  datasourceForm.reqUrl = ''
  datasourceForm.params = ''
}

const handleAddCrawler = () => {
  showAddCrawlerModal.value = false
  message.success('爬虫已添加')
  crawlerForm.name = ''
  crawlerForm.reqUrl = ''
  crawlerForm.params = ''
}
</script>

<style scoped>
.config-page {
  max-width: 1200px;
}

.page-title {
  font-size: 24px;
  font-weight: 500;
  margin-bottom: 24px;
  color: #262626;
}

.config-card {
  margin-bottom: 24px;
}

.gdelt-config {
  padding: 24px;
}

.gdelt-item {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.gdelt-label {
  font-weight: 500;
  color: #262626;
}

.gdelt-desc {
  color: #8c8c8c;
  margin: 0;
  padding-left: 60px;
}

.settings-list {
  padding: 8px 0;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-info {
  flex: 1;
}

.setting-title {
  font-weight: 500;
  color: #262626;
  margin-bottom: 4px;
}

.setting-desc {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
