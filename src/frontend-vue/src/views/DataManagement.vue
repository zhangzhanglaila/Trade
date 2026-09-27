
<!-- src/views/DataManagement.vue -->
<template>
  <main class="data-wrap">
    <header class="header">
      <h2>贸易数据管理</h2>
      <a-space>
         <!-- 新增 -->
        <a-button type="primary" @click="openTrain">重新训练</a-button>
        <a-button type="primary" @click="downloadTemplate">下载模板</a-button>
        <a-upload accept=".xlsx,.xls" :show-upload-list="false" :before-upload="beforeUpload">
          <a-button type="primary">导入 Excel</a-button>
        </a-upload>
        <a-button type="primary" @click="exportExcel">导出 Excel</a-button>
        <a-button type="primary" @click="showAdd">新增数据</a-button>
      </a-space>
    </header>

    <!-- 搜索条：年月改为单个输入 -->
    <a-row class="search-bar" :gutter="16">
      <a-col :span="4">
        <a-input
          v-model:value="searchForm.dataYearMonth"
          placeholder="年月（202503）"
          maxlength="6"
          @pressEnter="fetch"
        />
      </a-col>
      <a-col :span="3">
        <a-input
          v-model:value="searchForm.tradePartnerCode"
          placeholder="贸易伙伴编码"
          @pressEnter="fetch"
        />
      </a-col>
      <a-col :span="3">
        <a-input
          v-model:value="searchForm.tradePartnerName"
          placeholder="贸易伙伴名称"
          @pressEnter="fetch"
        />
      </a-col>
      <a-col :span="3">
        <a-input
          v-model:value="searchForm.commodityCode"
          placeholder="商品编码"
          @pressEnter="fetch"
        />
      </a-col>
      <a-col :span="3">
        <a-select
          v-model:value="searchForm.importExportType"
          placeholder="进出口"
          allow-clear
          @change="fetch"
        >
          <a-select-option :value="0">进口</a-select-option>
          <a-select-option :value="1">出口</a-select-option>
        </a-select>
      </a-col>
      <a-col :span="3">
        <a-input
          v-model:value="searchForm.country"
          placeholder="国家"
          @pressEnter="fetch"
        />
      </a-col>
      <a-col :span="3">
        <a-button type="primary" @click="fetch">查询</a-button>
      </a-col>
    </a-row>

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="list"
      :pagination="pagination"
      :loading="loading"
      row-key="id"
      size="small"
      class="table"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="showEdit(record)">编辑</a>
            <a-popconfirm title="确定删除？" @confirm="remove(record.id)">
              <a class="danger">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="visible"
      :title="isAdd ? '新增数据' : '编辑数据'"
      width="900px"
      @cancel="reset"
    >
      <a-form
        ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="submit"
      >
        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="数据年月" name="dataYearMonth">
              <a-date-picker
                v-model:value="form.dataYearMonth"
                format="YYYYMM"
                picker="month"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="贸易伙伴编码" name="tradePartnerCode">
              <a-input v-model:value="form.tradePartnerCode" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="贸易伙伴名称" name="tradePartnerName">
              <a-input v-model:value="form.tradePartnerName" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="注册地编码" name="registeredPlaceCode">
              <a-input v-model:value="form.registeredPlaceCode" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="注册地名称" name="registeredPlaceName">
              <a-input v-model:value="form.registeredPlaceName" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="商品编码" name="commodityCode">
              <a-input v-model:value="form.commodityCode" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="商品名称" name="commodityName">
              <a-input v-model:value="form.commodityName" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="贸易方式编码" name="tradeMethodCode">
              <a-input v-model:value="form.tradeMethodCode" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="贸易方式名称" name="tradeMethodName">
              <a-input v-model:value="form.tradeMethodName" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="第一数量" name="firstQuantity">
              <a-input-number
                v-model:value="form.firstQuantity"
                style="width: 100%"
                :precision="4"
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="计量单位" name="firstUnit">
              <a-input v-model:value="form.firstUnit" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="人民币金额" name="rmbAmount">
              <a-input v-model:value="form.rmbAmount" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="6">
            <a-form-item label="国家" name="country">
              <a-input v-model:value="form.country" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="进出口类型" name="importExportType">
              <a-select v-model:value="form.importExportType" style="width: 100%">
                <a-select-option :value="0">进口</a-select-option>
                <a-select-option :value="1">出口</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">保存</a-button>
            <a-button @click="reset">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>
    
  <a-modal v-model:open="trainVisible" title="模型重训练" :footer="null" width="520px" @cancel="closeTrain">
  <!-- 1. 上传区域 -->
  <a-row v-if="trainInfo.overallStatus==='PENDING'" class="mb16">
    <a-col :span="24">
      <a-upload accept=".xlsx,.xls" :show-upload-list="false" :before-upload="beforeUploadTrain">
        <a-button type="primary">选择数据并上传</a-button>
      </a-upload>
      <span class="ml8 tips">请先上传用于训练的 CSV 文件</span>
    </a-col>
  </a-row>

  <!-- 2. 文件状态 / 训练状态 一体化 -->
  <a-row>
    <a-col :span="8">整体状态：</a-col>
    <a-col :span="16">
      <a-tag :color="trainColor">
  {{ trainStatus[trainInfo.overallStatus] ?? trainInfo.overallStatus }}
</a-tag>
    </a-col>
  </a-row>
  <a-row class="mt12">
    <a-col :span="8">当前步骤：</a-col>
    <a-col :span="16">
      {{ trainInfo.currentStep }} / {{ trainInfo.totalSteps }}
    </a-col>
  </a-row>
  <a-row class="mt12">
    <a-col :span="8">总进度：</a-col>
    <a-col :span="16">
      <a-progress
        :percent="Number(trainInfo.progress || 0)"
        :status="trainPercent === 100 ? 'success' : 'active'"
      />
    </a-col>
  </a-row>

  <!-- 3. 步骤明细 -->
  <a-table
    :columns="stepColumns"
    :data-source="trainInfo.scriptResults"
    row-key="scriptName"
    size="small"
    :pagination="false"
    class="mt16"
  />

  <!-- 4. 操作按钮 -->
  <div class="mt16" style="text-align: right">
    <a-space>
      <!-- 开始训练：仅文件就绪后可用 -->
      <a-button
        type="primary"
        :disabled="trainInfo.overallStatus !== 'FILE_OK'"
        @click="startTrainingAndPoll"
      >
        开始训练
      </a-button>
      <a-button @click="closeTrain">关闭</a-button>
    </a-space>
  </div>

  <!-- 5. 错误信息 -->
  <div v-if="trainInfo.errorMessage" class="mt16 error">
    {{ trainInfo.errorMessage }}
  </div>
</a-modal>

  </main>
</template>


<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { h } from 'vue'

/* ---------- API ---------- */
import {
  pageLogistics,
  addLogistics,
  updateLogistics,
  delLogistics
} from '@/api/logistics'

import {
  uploadTrainingFiles,
  startTraining,
  getTrainingStatus
} from '@/api/training'




/* ---------- 训练状态 ---------- */
const trainVisible = ref(false)
const trainInfo = ref({
  overallStatus: 'PENDING',
  currentStep: 0,
  totalSteps: 6,
  progress: 0,
  scriptResults: [],
  errorMessage: null
})

const trainStatus = ref({
  PENDING:  '待上传',   // 初始
  FILE_OK:  '文件已就绪',
  RUNNING:  '正在训练',
  SUCCESS:  '训练完成',
  FAILED:   '训练失败'
})
// 在组件作用域内声明 timer
let timer = null

const stepColumns = [
  { 
    title: '脚本', 
    dataIndex: 'scriptName', 
    width: 260,
    customRender: ({ text }) => text || '-'
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 120,
    customRender: ({ text }) => {
      const color = text === 'SUCCESS' ? 'green' : text === 'FAILED' ? 'red' : 'orange'
      return h('a-tag', { color }, text || 'PENDING')
    }
  },
  { 
    title: '耗时(s)', 
    dataIndex: 'duration', 
    width: 100,
    customRender: ({ text }) => text ? Number(text).toFixed(2) : '-'
  }
]
const trainPercent = computed(() => {
  return Number(trainInfo.value.progress || 0)
})

const trainColor = computed(() => {
  const s = trainInfo.value.overallStatus
  return s === 'SUCCESS' ? 'green' : s === 'FAILED' || s === 'CANCELLED' ? 'red' : 'blue'
})

/* ---------- 训练弹窗 ---------- */
function openTrain() {
  trainVisible.value = true
  resetTrainInfo()
  // 不再自动开始，等待用户上传
}

async function beforeUploadTrain(file) {
  try {
    console.log('开始上传训练文件:', file.name)

    const fd = new FormData()
    fd.append('mergedInput', file)   // 后端 @RequestParam("mergedInput")

    const res = await uploadTrainingFiles(fd)   // axios 已被拦截器解开
    console.log('上传响应:', res)

    // ✅ 上传接口只关心 files 字段
    if (res.success === true && res.files?.length) {
      trainInfo.value.overallStatus = 'FILE_OK'
      message.success(res.message || '数据文件上传成功！可以开始训练了')
      console.log('文件保存路径:', res.files[0].savedPath)
      return false          // 阻止 a-upload 默认上传行为
    }

    // 后端明确返回失败
    message.error(res.message || '上传失败')
    return false
  } catch (err) {
    console.error('上传训练文件失败:', err)
    message.error(`上传失败：${err.response?.data?.message || err.message}`)
    return false
  }
}


async function startTrainingAndPoll() {
  trainInfo.value.overallStatus = 'RUNNING'
  try {
    const  res  = await startTraining()
    console.log('训练接口真正返回：', res)
    const taskId = res.taskId
    message.success('训练任务已启动')

    timer = setInterval(async () => {
      await checkTrainingStatus(taskId)
    }, 1500)
  } catch (e) {
     clearInterval(timer)
    timer = null
    trainInfo.value.overallStatus = 'FAILED'
    trainInfo.value.errorMessage = e.response?.data?.message || e.message
    message.error('启动训练失败：' + trainInfo.value.errorMessage)
  }
}

async function checkTrainingStatus(taskId) {
  try {
    const  data  = await getTrainingStatus(taskId)
    trainInfo.value = { ...trainInfo.value, ...data }

    if (!['RUNNING', 'PENDING', 'FILE_OK'].includes(data.overallStatus)) {
      clearInterval(timer)
      timer = null
      if (data.overallStatus === 'SUCCESS') {
        message.success('训练完成！')
      } else {
        message.error('训练失败：' + (data.errorMessage || '未知原因'))
      }
    }
  } catch (e) {
    clearInterval(timer)
    timer = null
    trainInfo.value.overallStatus = 'FAILED'
    trainInfo.value.errorMessage = e.message
  }
}

/* 关闭弹窗 */
function closeTrain() {
  trainVisible.value = false
  if (timer) { clearInterval(timer); timer = null }
}

/* 重置训练信息 */
function resetTrainInfo() {
  trainInfo.value = {
    overallStatus: 'PENDING',
    currentStep: 0,
    totalSteps: 6,
    progress: 0,
    scriptResults: [],
    errorMessage: null
  }
}

/* 组件卸载清理 */
onUnmounted(() => {
  if (timer) { clearInterval(timer); timer = null }
})

/* ---------- 导入 Excel 或 CSV（仅入库，不再触发训练） ---------- */
async function beforeUpload(file) {
  try {
    const fileName = file.name.toLowerCase()
    const isCsv  = fileName.endsWith('.csv')
    const isExcel = fileName.endsWith('.xlsx') || fileName.endsWith('.xls')

    if (!isCsv && !isExcel) {
      message.warning('请上传 Excel 或 CSV 文件')
      return false
    }

    let json
    /* 1. 统一转成二维数组 */
    if (isCsv) {
      const text = await file.text()
      json = text.split(/\r?\n/).filter(r => r.trim()).map(row => row.split(',').map(h => String(h).trim()))
    } else {
      json = XLSX.utils.sheet_to_json(
        XLSX.read(await file.arrayBuffer(), { type: 'array', defval: '' }),
        { header: 1 }
      )
    }

    if (json.length < 2) {
      message.warning('文件无有效数据')
      return false
    }

    /* 2. 表头校验与字段映射（与你原逻辑相同） */
    const headers = json[0]
    const keys = columns
      .filter(c => c.dataIndex && !['action', 'id'].includes(c.dataIndex))
      .map(c => c.title)

    if (!keys.every(k => headers.includes(k))) {
      message.error('请使用下载的模板')
      return false
    }

    /* 3. 构造入库数据 */
    const addList = []
    for (let i = 1; i < json.length; i++) {
      const row = json[i]
      const item = {}
      keys.forEach(k => {
        const key = columns.find(c => c.title === k).dataIndex
        if (key !== 'id') item[key] = row[headers.indexOf(k)] ?? ''
      })
      item.dataYearMonth = dayjs(item.dataYearMonth, 'YYYY-MM', true).isValid()
        ? dayjs(item.dataYearMonth, 'YYYY-MM').format('YYYYMM')
        : dayjs().format('YYYYMM')
      addList.push(item)
    }

    /* 4. 批量入库 & 刷新 */
    await Promise.all(addList.map(r => addLogistics(r)))
    message.success(`成功导入 ${addList.length} 条数据`)
    fetch()

  } catch (error) {
    message.error('导入失败：' + error.message)
  }
  return false
}


/* 搜索表单 */
const searchForm = reactive({
  monthRange: [],
  tradePartnerCode: '',
  tradePartnerName: '',
  commodityCode: '',
  importExportType: undefined,
  country: ''
})

/* 表格列 */
const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '年月', dataIndex: 'dataYearMonth', width: 100 },
  { title: '伙伴编码', dataIndex: 'tradePartnerCode', width: 120 },
  { title: '伙伴名称', dataIndex: 'tradePartnerName', width: 140 },
  { title: '注册地编码', dataIndex: 'registeredPlaceCode', width: 120 },
  { title: '注册地名称', dataIndex: 'registeredPlaceName', width: 140 },
  { title: '商品编码', dataIndex: 'commodityCode', width: 120 },
  { title: '商品名称', dataIndex: 'commodityName', width: 140 },
  { title: '贸易方式编码', dataIndex: 'tradeMethodCode', width: 130 },
  { title: '贸易方式名称', dataIndex: 'tradeMethodName', width: 140 },
  { title: '第一数量', dataIndex: 'firstQuantity', width: 100 },
  { title: '计量单位', dataIndex: 'firstUnit', width: 90 },
  { title: '人民币金额', dataIndex: 'rmbAmount', width: 120 },
  { title: '国家', dataIndex: 'country', width: 100 },
  { title: '进出口', dataIndex: 'importExportType', width: 90 },
  { title: '操作', key: 'action', width: 120 }
]

/* 表格数据与分页 */
const list = ref([])
const loading = ref(false)
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100']
})

/* 弹窗与表单 */
const visible = ref(false)
const isAdd = ref(true)
const formRef = ref()
const form = reactive({
  id: null,
  dataYearMonth: '', 
  tradePartnerCode: '',
  tradePartnerName: '',
  registeredPlaceCode: '',
  registeredPlaceName: '',
  commodityCode: '',
  commodityName: '',
  tradeMethodCode: '',
  tradeMethodName: '',
  firstQuantity: 0,
  firstUnit: '千克',
  rmbAmount: '',
  country: '',
  importExportType: 1
})
const rules = {
  dataYearMonth: [{ required: true, message: '请选择数据年月' }],
  tradePartnerCode: [{ required: true, message: '请输入贸易伙伴编码' }],
  tradePartnerName: [{ required: true, message: '请输入贸易伙伴名称' }],
  registeredPlaceCode: [{ required: true, message: '请输入注册地编码' }],
  registeredPlaceName: [{ required: true, message: '请输入注册地名称' }],
  commodityCode: [{ required: true, message: '请输入商品编码' }],
  commodityName: [{ required: true, message: '请输入商品名称' }],
  tradeMethodCode: [{ required: true, message: '请输入贸易方式编码' }],
  tradeMethodName: [{ required: true, message: '请输入贸易方式名称' }],
  firstQuantity: [{ required: true, message: '请输入第一数量' }],
  firstUnit: [{ required: true, message: '请输入计量单位' }],
  rmbAmount: [{ required: true, message: '请输入人民币金额' }],
  country: [{ required: true, message: '请输入国家' }],
  importExportType: [{ required: true, message: '请选择进出口类型' }]
}

async function fetch() {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      country: searchForm.country || undefined,
      commodityName: searchForm.commodityName || undefined,
      tradePartnerCode: searchForm.tradePartnerCode || undefined,
      tradePartnerName: searchForm.tradePartnerName || undefined,
      commodityCode: searchForm.commodityCode || undefined,
      importExportType: searchForm.importExportType ?? undefined,
      dataYearMonth: searchForm.dataYearMonth || undefined
    }
    const { data } = await pageLogistics(params)
    list.value = data.records
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

/* ---------- 下载模板 ---------- */
function downloadTemplate() {
  // 1. 按表格列生成表头（去掉 ID、操作列）
  const header = columns
    .filter(c => c.dataIndex && !['id', 'action'].includes(c.key))
    .map(c => c.title)

  // 2. 建工作簿
  const ws = XLSX.utils.aoa_to_sheet([header])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '模板')

  // 3. 下载
  const fileName = `贸易数据模板_${dayjs().format('YYYYMMDD')}.xlsx`
  XLSX.writeFile(wb, fileName)
}

/* ---------- 导出 Excel ---------- */
async function exportExcel() {
  try {
    loading.value = true
    // 1. 拉全量数据（如只想导当前页，把 pageSize 保留即可）
    const params = {
      pageNum: 1,
      pageSize: -1,                 // 后端支持 -1 表示不分页
      country: searchForm.country || undefined,
      commodityName: searchForm.commodityName || undefined,
      tradePartnerCode: searchForm.tradePartnerCode || undefined,
      tradePartnerName: searchForm.tradePartnerName || undefined,
      commodityCode: searchForm.commodityCode || undefined,
      importExportType: searchForm.importExportType ?? undefined,
      dataYearMonth: searchForm.dataYearMonth || undefined
    }
    const { data } = await pageLogistics(params)

    // 2. 组装工作簿
    const header = columns
      .filter(c => c.dataIndex && c.title !== 'ID')   // 去掉 ID 列
      .map(c => c.title)
    const body = data.records.map(r =>
      columns
        .filter(c => c.dataIndex && c.title !== 'ID')
        .map(c => r[c.dataIndex] ?? '')
    )
    const ws = XLSX.utils.aoa_to_sheet([header, ...body])
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '贸易数据')

    // 3. 下载
    const fileName = `贸易数据_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
    XLSX.writeFile(wb, fileName)
    message.success('导出成功')
  } finally {
    loading.value = false
  }
}
/* 表格翻页/条数变化 */
function handleTableChange(pager) {
  pagination.current = pager.current
  pagination.pageSize = pager.pageSize
  fetch()
}

/* 新增 */
function showAdd() {
  isAdd.value = true
  reset()
  visible.value = true
}

/* 编辑 */
function showEdit(record) {
  isAdd.value = false
  Object.assign(form, { ...record ,dataYearMonth: dayjs(record.dataYearMonth + '', 'YYYYMM'), })
  visible.value = true
}

/* 删除 */
async function remove(id) {
  await delLogistics(id)
  message.success('删除成功')
  fetch()
}

/* 提交（新增/修改） */
function submit() {
  formRef.value.validate().then(async () => {
    const payload = { ...form,dataYearMonth: form.dataYearMonth.format('YYYYMM') }
    // 新增时删除可能存在的id字段
    if (isAdd.value) {
      delete payload.id
    }
    const api = isAdd.value ? addLogistics : updateLogistics
    await api(payload)
    message.success(isAdd.value ? '新增成功' : '修改成功')
    reset()
    fetch()
  })
}

/* 重置表单并关闭弹窗 */
function reset() {
  formRef.value?.resetFields()
  Object.assign(form, {
    id: null,
    dataYearMonth: dayjs(),
    tradePartnerCode: '',
    tradePartnerName: '',
    registeredPlaceCode: '',
    registeredPlaceName: '',
    commodityCode: '',
    commodityName: '',
    tradeMethodCode: '',
    tradeMethodName: '',
    firstQuantity: 0,
    firstUnit: '千克',
    rmbAmount: '',
    country: '',
    importExportType: 1
  })
  visible.value = false
}

onMounted(() => {
  console.log('【初始 dataYearMonth 类型】', typeof form.dataYearMonth, form.dataYearMonth)
  fetch()
})
</script>


<style scoped>
.data-wrap {
  margin-left: 10px;
  padding: 24px 32px;
  background: #f5f7fa;
  min-height: 100vh;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-bar {
  margin-bottom: 16px;
}
.table {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.danger {
  color: #ff4d4f;
}
.mt12 { margin-top: 12px; }
.mt16 { margin-top: 16px; }

.tips { color: #999; font-size: 12px; }
.error { color: #ff4d4f; background: #fff1f0; padding: 8px; border-radius: 4px; }
</style>