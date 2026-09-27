
<template>
  <main class="news-wrap">
   <header class="header">
  <h2>新闻语料管理</h2>

  <!-- 右侧按钮组：模板 → 导入 → 导出 → 新增 -->
  <a-space>
    <a-button type="primary" @click="downloadTemplate">下载模板</a-button>

    <a-button :loading="indexing" @click="buildVectorIndex">一键构建向量索引</a-button>

    <a-upload
      accept=".xlsx,.xls"
      :show-upload-list="false"
      :before-upload="beforeUpload"
    >
      <a-button type="primary">导入 Excel</a-button>
    </a-upload>

    <a-button type="primary" @click="exportExcel">导出 Excel</a-button>

    <a-button type="primary" @click="showAdd">新增新闻</a-button>
  </a-space>
</header>

    <!-- 搜索条 -->
   <!-- 1. 把这段放到 <template> 里任意位置（通常放在表格上方） -->
<a-card size="small" style="margin-bottom: 16px">
  <a-form layout="inline" :model="searchForm">
    <!-- 标题关键词 -->
    <a-form-item label="标题关键词">
      <a-input
        v-model:value="searchForm.keyword"
        placeholder="请输入标题关键词"
        allow-clear
        style="width: 220px"
        @pressEnter="fetch"     
      />
    </a-form-item>

    <!-- 国家 -->
    <a-form-item label="国家">
      <a-input
        v-model:value="searchForm.country"
        placeholder="国家"
        allow-clear
        style="width: 140px"
      />
    </a-form-item>

    <!-- 年份 -->
    <a-form-item label="年份">
      <a-input-number
        v-model:value="searchForm.year"
        placeholder="年份"
        :min="1900"
        :max="dayjs().year()"
        style="width: 120px"
      />
    </a-form-item>

    <!-- 按钮 -->
    <a-form-item>
      <a-button type="primary" @click="fetch">搜索</a-button>
      <a-button style="margin-left: 8px" @click="resetSearch">重置</a-button>
    </a-form-item>
  </a-form>
</a-card>
    <!-- 表格 -->
 <a-table
  :columns="columns"
  :data-source="list"
  :pagination="pagination"
  :loading="loading"
  row-key="id"
  size="middle"
  class="table"
  @change="handleTableChange"
>
  <template #bodyCell="{ column, record }">
  <!-- 1. 内容列：缩略展示 -->
  <template v-if="column.key === 'newsContent'">
    <a-tooltip :title="record.newsContent">
      <span>
        {{ record.newsContent.slice(0, 15) }}
        <template v-if="record.newsContent.length > 15">...</template>
      </span>
    </a-tooltip>
  </template>

  <!-- 2. 操作列：保持原样 -->
 <template v-if="column.key === 'action'">
  <a-space size="small">
    <!-- 查看 -->
    <a-tooltip title="查看">
      <a-button type="text" size="small" @click="showContent(record.newsContent)">
        <EyeOutlined />
      </a-button>
    </a-tooltip>

    <!-- 编辑 -->
    <a-tooltip title="编辑">
      <a-button type="text" size="small" @click="showEdit(record)">
        <EditOutlined />
      </a-button>
    </a-tooltip>

    <!-- 删除 -->
    <a-popconfirm title="确定删除？" @confirm="remove(record.id)">
      <a-tooltip title="删除">
        <a-button type="text" danger size="small">
          <DeleteOutlined />
        </a-button>
      </a-tooltip>
    </a-popconfirm>
  </a-space>
</template>
</template>
</a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="visible"
      :title="isAdd ? '新增新闻' : '编辑新闻'"
      width="700px"
      @cancel="reset"
    >
      <a-form
        ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="submit"
      >
        <a-form-item label="新闻标题" name="newsTitle">
          <a-input v-model:value="form.newsTitle" placeholder="请输入标题" />
        </a-form-item>

        <a-form-item label="新闻内容" name="newsContent">
          <a-textarea
            v-model:value="form.newsContent"
            :rows="5"
            placeholder="请输入正文"
          />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="出口国家" name="country">
              <a-input
                v-model:value="form.country"
                placeholder="请输入或选择国家"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="出口年份" name="year">
              <a-input-number
                v-model:value="form.year"
                placeholder="年份"
                :min="2015"
                :max="2030"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="新闻来源" name="newsSource">
              <a-input v-model:value="form.newsSource" placeholder="如：新华社" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发布时间" name="publishTime">
              <a-date-picker
                v-model:value="form.publishTime"
                show-time
                style="width: 100%"
              />
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

    <!-- 内容预览抽屉 -->
    <a-drawer
      v-model:open="drawerVisible"
      title="新闻内容"
      placement="right"
      width="560"
    >
      <div v-html="currentContent" class="content-drawer" />
    </a-drawer>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { ragIndexFull } from '@/api/ai'
import {
  searchNews,
  addNews,
  updateNews,
  deleteNews,
  getNewsById
} from '@/api/newsCorpus'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { EyeOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'

/* 搜索表单 - 初始全空 */
const searchForm = reactive({
  newsTitle: '',
  country: '',
  keyword: '',     // 标题关键词
  year: null
})
/* 重置搜索条件 */
function resetSearch() {
  Object.assign(searchForm, {
    newsTitle: '',
    keyword: '',
    country: '',
    year: null
  })
  pagination.current = 1
  fetch()
}

/* 表格列（含 id、操作） */
const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '标题', dataIndex: 'newsTitle', width: 160, ellipsis: true },
  { title: '内容', dataIndex: 'newsContent', width: 400, key: 'newsContent' },
  { title: '国家', dataIndex: 'country', width: 110 },
  { title: '年份', dataIndex: 'year', width: 80 },
  { title: '来源', dataIndex: 'newsSource', width: 120 },
  { title: '发布时间', dataIndex: 'publishTime', width: 160 },
  { title: '操作', key: 'action', width: 120 }
]

/* 手写：需要导出的列（不含 id，不含操作） */
const excelCols = [
  { title: '标题', dataIndex: 'newsTitle' },
  { title: '内容',  dataIndex: 'newsContent' },
  { title: '国家',  dataIndex: 'country' },
  { title: '年份',  dataIndex: 'year' },
  { title: '来源',  dataIndex: 'newsSource' },
  { title: '发布时间', dataIndex: 'publishTime' }
]

/* 表格数据与分页 */
const list = ref([])
const loading = ref(false)
const indexing = ref(false)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50']
})

/* 弹窗与表单 */
const visible = ref(false)
const isAdd = ref(true)
const formRef = ref()
const form = reactive({
  id: null,
  newsTitle: '',
  newsContent: '',
  country: '',
  year: dayjs().year(),
  newsSource: '',
  publishTime: dayjs()
})
const rules = {
  newsTitle: [{ required: true, message: '请输入标题' }],
  newsContent: [{ required: true, message: '请输入内容' }],
  country: [{ required: true, message: '请输入出口国家' }],
  year: [{ required: true, message: '请选择年份' }],
  newsSource: [{ required: true, message: '请输入来源' }],
  publishTime: [{ required: true, message: '请选择发布时间' }]
}

/* 内容预览抽屉 */
const drawerVisible = ref(false)
const currentContent = ref('')

function showContent(html) {
  currentContent.value = html
  drawerVisible.value = true
}

async function buildVectorIndex() {
  indexing.value = true
  try {
    const res = await ragIndexFull()
    message.success(typeof res === 'string' ? res : '向量索引构建完成')
  } catch (e) {
    message.error('构建失败：' + (e?.message || '未知错误'))
  } finally {
    indexing.value = false
  }
}

/* 获取列表 */
async function fetch() {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      titleKeyword: searchForm.keyword?.trim() || undefined,
      country: searchForm.country?.trim() || undefined,
      year: searchForm.year || undefined
    }
    
    console.log('请求参数:', params)
    const { data } = await searchNews(params)
    console.log('响应数据:', data)
    
    // 添加空值检查
    if (data && Array.isArray(data.records)) {
      list.value = data.records.map(it => ({
        ...it,
        publishTime: dayjs(it.publishTime).format('YYYY-MM-DD HH:mm')
      }))
      pagination.total = data.total || 0
    } else {
      console.warn('数据格式异常，records不是数组:', data)
      list.value = []
      pagination.total = 0
      message.warning('获取数据失败，返回格式异常')
    }
    
  } catch (error) {
    console.error('获取数据失败:', error)
    message.error('获取数据失败：' + (error.response?.data?.message || error.message))
    list.value = []
    pagination.total = 0
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
async function showEdit(record) {
  isAdd.value = false
  const { data } = await getNewsById(record.id)
  Object.assign(form, {
    ...data,
    publishTime: dayjs(data.publishTime)
  })
  visible.value = true
}

/* 删除 */
async function remove(id) {
  await deleteNews(id)
  message.success('删除成功')
  fetch()
}

/* 提交（新增/修改） */
async function submit() {
  await formRef.value.validate()
  const payload = {
    ...form,
    // 补秒 + T → ISO 8601，后端 Date 可直接解析
    publishTime: form.publishTime.format('YYYY-MM-DDTHH:mm:ss'),
    year: Number(form.year)
  }
  isAdd.value ? await addNews(payload) : await updateNews(payload)
  message.success(isAdd.value ? '新增成功' : '修改成功')
  reset()
  fetch()
}

/* 重置表单并关闭弹窗 */
function reset() {
  formRef.value?.resetFields()
  Object.assign(form, {
    id: null,
    newsTitle: '',
    newsContent: '',
    country: '',
    year: dayjs().year(),
    newsSource: '',
    publishTime: dayjs()
  })
  visible.value = false
}

/* 导出当前列表 */
function exportExcel() {
  const headers = excelCols.map(c => c.title)
  const keys   = excelCols.map(c => c.dataIndex)

  const data = list.value.map(r =>
    keys.map(k => {
      let v = r[k] ?? ''
      // 内容过长时截断
      if (k === 'newsContent' && v.length > 200) v = v.slice(0, 200) + '...'
      return v
    })
  )
  data.unshift(headers)

  const ws = XLSX.utils.aoa_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'NewsCorpus')
  const blob = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
  saveAs(new Blob([blob]), `新闻语料_${dayjs().format('YYYYMMDD')}.xlsx`)
}

/* 下载空模板（不含 id 与操作列） */
function downloadTemplate() {
  const headers = excelCols.map(c => c.title)
  const ws = XLSX.utils.aoa_to_sheet([headers])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Template')
  const blob = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
  saveAs(new Blob([blob]), '新闻语料模板.xlsx')
}

/* 导入（不含 id 与操作列，日期统一 ISO） */
function beforeUpload(file) {
  const reader = new FileReader()
  reader.onload = e => {
    try {
      const wb = XLSX.read(e.target.result, { type: 'binary' })
      const ws = wb.Sheets[wb.SheetNames[0]]
      const json = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })

      if (json.length < 2) return message.warning('Excel 无有效数据')

      const headers = json[0].map(h => String(h).trim())
      const keys = excelCols.map(c => c.title)
      const miss = keys.find(k => !headers.includes(k))
      if (miss) return message.error(`缺少列：${miss}`)

      const hIdx = {}
      keys.forEach(h => { hIdx[h] = headers.indexOf(h) })

      const addList = []
      for (let i = 1; i < json.length; i++) {
        const row = json[i]
        const item = {}
        keys.forEach(h => {
          const key = excelCols.find(c => c.title === h).dataIndex
          item[key] = row[hIdx[h]] ?? ''
        })

        /* 发布时间：任意格式→统一 ISO（yyyy-MM-ddTHH:mm:ss） */
        const rawPub = item.publishTime
        let p = null
        if (typeof rawPub === 'number') {
          p = dayjs(XLSX.SSF.parse_date_code(rawPub))
        } else if (rawPub instanceof Date) {
          p = dayjs(rawPub)
        } else {
          p = dayjs(rawPub, ['YYYY-MM-DD HH:mm', 'YYYY-MM-DDTHH:mm:ss', 'YYYY/MM/DD HH:mm'], true)
        }
        item.publishTime = p?.isValid() ? p.format('YYYY-MM-DDTHH:mm:ss') : dayjs().format('YYYY-MM-DDTHH:mm:ss')

        /* 数字字段防字符串 */
        item.year = Number(item.year) || dayjs().year()
        item.firstQuantity = Number(item.firstQuantity) || 0
        item.rmbAmount = Number(item.rmbAmount) || 0

        addList.push(item)
      }

      Promise.all(addList.map(row => addNews(row)))
        .then(() => {
          message.success(`成功导入 ${addList.length} 条`)
          fetch()
        })
        .catch(err => message.error('导入失败：' + (err.response?.data?.message || err.message)))
    } catch (e) {
      message.error('解析文件失败：' + e.message)
    }
  }
  reader.readAsBinaryString(file)
  return false
}

/* 初始加载 */
onMounted(() => {
  fetch()
})
</script>

<style scoped>
.news-wrap {
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
.content-drawer {
  white-space: pre-wrap;
  line-height: 1.8;
}
</style>