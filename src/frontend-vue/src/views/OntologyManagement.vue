<template>
  <a-card class="container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <a-button type="primary" @click="handleAdd">新增本体</a-button>

      <a-button
        :disabled="!selectedRows.length"
        danger
        @click="handleBatchDel"
        style="margin-left: 8px"
      >
        批量删除（{{ selectedRows.length }}）
      </a-button>

      <a-button type="default" style="margin-left: 8px" @click="handleExportAll">
        <template #icon><DownloadOutlined /></template>
        导出Excel
      </a-button>

      <a-button type="default" style="margin-left: 8px" @click="openBatchImportDialog">
        <template #icon><UploadOutlined /></template>
        批量导入
      </a-button>

      <a-button type="dashed" style="margin-left: 8px" @click="handleTpl">
        <template #icon><FileTextOutlined /></template>
        导入模板
      </a-button>

      <!-- 搜索 -->
      <div class="filters">
        <a-input
          v-model:value="searchName"
          placeholder="搜索项目"
          allowClear
          @pressEnter="handleSearch"
        />
        <a-input
          v-model:value="searchCreator"
          placeholder="搜索创建人"
          allowClear
          @pressEnter="handleSearch"
        />
        <a-button type="primary" @click="handleSearch">
          <template #icon><SearchOutlined /></template>
          搜索
        </a-button>
      </div>
    </div>

    <!-- 已选择提示 -->
    <div class="selected-tip" v-if="selectedRows.length > 0">
      <InfoCircleOutlined style="color: #1890ff; margin-right: 5px;" />
      <span>已选择 <span style="color: #1890ff; font-weight: bold;">{{ selectedRows.length }}</span> 条数据</span>
    </div>

    <!-- 表格 -->
    <a-table
      rowKey="ontologyId"
      :columns="columns"
      :data-source="tableData"
      :pagination="pagination"
      :loading="loading"
      :row-selection="rowSelection"
      bordered
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'ontologyName'">
          <a @click="handleViewDetail(record)" class="project-link">{{ record.ontologyName }}</a>
        </template>
        <template v-if="column.dataIndex === 'namespaceUri'">
          {{ record.namespaceUri || '--' }}
        </template>
        <template v-if="column.dataIndex === 'createTime'">
          {{ fmtDate(record.createTime) }}
        </template>
        <template v-if="column.dataIndex === 'updateTime'">
          {{ fmtDate(record.updateTime) }}
        </template>
        <template v-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
          <a-button type="link" size="small" @click="handleExportOntology(record)">导出</a-button>
          <a-button type="link" size="small" style="color: #52c41a;" @click="handleImportToGraph(record)">入库</a-button>
          <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
        </template>
      </template>

      <template #empty>
        <a-empty description="暂无数据" />
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="isEdit ? '编辑本体' : '新增本体'"
      width="600px"
      @ok="handleSave"
      @cancel="closeDialog"
      :confirmLoading="saveLoading"
    >
      <a-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="本体名称" name="ontologyName">
          <a-input v-model:value="form.ontologyName" placeholder="请输入本体名称" />
        </a-form-item>
        <a-form-item label="创建人" name="creatorName">
          <a-input v-model:value="form.creatorName" placeholder="请输入创建人" />
        </a-form-item>
        <a-form-item label="版本号" name="version">
          <a-input v-model:value="form.version" placeholder="请输入版本号，例如：v1.0" />
        </a-form-item>
        <a-form-item label="命名空间URI" name="namespaceUri">
          <a-input v-model:value="form.namespaceUri" placeholder="请输入命名空间URI" />
        </a-form-item>
        <a-form-item label="文件格式" name="fileFormat" v-if="!isEdit">
          <a-select v-model:value="form.fileFormat" placeholder="请选择文件格式">
            <a-select-option value="OWL">OWL</a-select-option>
            <a-select-option value="RDF">RDF/XML</a-select-option>
            <a-select-option value="Turtle">Turtle</a-select-option>
            <a-select-option value="N-Triples">N-Triples</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="本体附件" v-if="!isEdit">
          <a-upload
            :before-upload="beforeUpload"
            :file-list="fileList"
            @remove="handleRemove"
            accept=".owl,.rdf,.ttl,.nt,.csv"
            :max-count="1"
          >
            <a-button>
              <UploadOutlined />
              选择文件
            </a-button>
            <template #tip>
              <div style="margin-top: 8px; color: #999;">
                支持 OWL、RDF/XML、Turtle、N-Triples、CSV 格式（CSV 会自动转换为 RDF）
              </div>
            </template>
          </a-upload>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 批量导入弹窗 -->
    <a-modal
      v-model:open="batchImportDialogVisible"
      title="批量导入本体"
      width="700px"
      @ok="confirmBatchImport"
      @cancel="closeBatchImportDialog"
      :confirmLoading="batchImportLoading"
      :ok-text="importStep === 1 ? '下一步：验证格式' : '开始导入'"
    >
      <!-- 步骤 1: 文件上传和验证 -->
      <div v-if="importStep === 1">
        <a-alert
          message="批量导入说明"
          description="支持 RDF 文件直接导入，或 CSV 文件自动转换为 RDF。RDF 文件会进行格式预检。"
          type="info"
          show-icon
          style="margin-bottom: 16px;"
        />
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }" style="margin-bottom: 16px;">
          <a-form-item label="统一创建人" required>
            <a-input v-model:value="batchImportCreator" placeholder="请输入创建人（所有项目共用）" />
          </a-form-item>
          <a-form-item label="项目名称前缀">
            <a-input v-model:value="batchImportPrefix" placeholder="例如：kazakhstan-2025（可选）" />
          </a-form-item>
        </a-form>
        <input 
          ref="batchUploadInput" 
          type="file" 
          accept=".rdf,.owl,.ttl,.nt,.csv" 
          multiple
          style="display:none" 
          @change="readBatchFiles" 
        />
        <div
          class="upload-card"
          @click="batchUploadInput.click()"
          @drop.prevent="handleBatchDrop"
          @dragover.prevent
        >
          <CloudUploadOutlined style="font-size:40px;color:#999" />
          <div class="tip">
            <span v-if="!batchFileList.length">点击或拖拽 RDF/CSV 文件到此处</span>
            <span v-else class="file-name">已选择 {{ batchFileList.length }} 个文件</span>
          </div>
          <div class="sub-tip">支持 .rdf / .owl / .ttl / .nt / .csv 格式</div>
        </div>
      </div>

      <!-- 步骤 2: 格式验证结果和CSV模式选择 -->
      <div v-if="importStep === 2">
        <a-alert
          message="文件验证完成"
          :description="validationSummary"
          :type="validationAllPassed ? 'success' : 'warning'"
          show-icon
          style="margin-bottom: 16px;"
        />
        
        <!-- 文件验证列表 -->
        <div style="max-height: 300px; overflow-y: auto; margin-bottom: 16px;">
          <a-collapse>
            <a-collapse-panel v-for="(result, index) in validationResults" :key="index">
              <template #header>
                <span :style="{ color: result.valid ? '#52c41a' : '#ff4d4f' }">
                  {{ result.fileName }}
                  {{ result.valid ? ' ✓' : ' ✗' }}
                </span>
              </template>
              
              <!-- RDF 文件验证结果 -->
              <div v-if="result.fileType === 'rdf'">
                <p v-if="result.valid">
                  <CheckCircleOutlined style="color: #52c41a" /> 
                  格式正确 ({{ result.format }})，包含 {{ result.tripleCount }} 个三元组
                </p>
                <div v-else>
                  <p style="color: #ff4d4f;">
                    <CloseCircleOutlined /> {{ result.errorMessage }}
                  </p>
                  <a-alert
                    v-if="result.suggestion"
                    :message="result.suggestion"
                    type="warning"
                    :show-icon="false"
                    style="margin-top: 8px;"
                  />
                  <!-- CSV 转换选项 -->
                  <div v-if="result.canConvertToCsv" style="margin-top: 12px;">
                    <a-radio-group v-model:value="result.handleMode">
                      <a-radio value="skip">跳过此文件</a-radio>
                      <a-radio value="csv">转为 CSV 模式上传</a-radio>
                    </a-radio-group>
                    <div v-if="result.handleMode === 'csv'" style="margin-top: 8px;">
                      <a-select v-model:value="result.csvMode" style="width: 200px;">
                        <a-select-option value="original">使用原版本结构</a-select-option>
                        <a-select-option value="custom">自定义配置（暂未支持）</a-select-option>
                      </a-select>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- CSV 文件 -->
              <div v-if="result.fileType === 'csv'">
                <p><FileExcelOutlined style="color: #52c41a" /> CSV 文件，将自动转换为 RDF</p>
                <a-form-item label="转换模式" style="margin-top: 8px;">
                  <a-select v-model:value="result.csvMode" style="width: 200px;">
                    <a-select-option value="original">原版本（中亚贸易数据结构）</a-select-option>
                    <a-select-option value="custom">自定义配置（暂未支持）</a-select-option>
                  </a-select>
                </a-form-item>
              </div>
            </a-collapse-panel>
          </a-collapse>
        </div>
        
        <a-button @click="importStep = 1">返回重新选择</a-button>
      </div>
    </a-modal>

    <!-- 入库弹窗 -->
    <a-modal
      v-model:open="inDialogVisible"
      title="本体入库"
      width="600px"
      @ok="closeInDialog"
      @cancel="closeInDialog"
      :footer="null"
    >
      <!-- 已存在图数据库 -->
      <div v-if="exists">
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
          <a-form-item label="图名称">
            <a-input v-model:value="importForm.namedGraph" disabled />
          </a-form-item>
          <a-form-item label="当前版本">
            <a-input v-model:value="importForm.version" disabled />
          </a-form-item>
          <a-form-item label="更新版本">
            <a-input-group compact>
              <a-input v-model:value="newVersion" placeholder="请输入新版本号" style="width: calc(100% - 100px);" />
              <a-button type="primary" @click="updateOntologyVersion" :loading="versionLoading">更新版本</a-button>
            </a-input-group>
          </a-form-item>
          <a-form-item label="历史版本" :wrapper-col="{ span: 24 }">
            <a-timeline style="margin-top: 10px;">
              <a-timeline-item v-for="item in importForm.versionHistory" :key="item.ontologyId">
                <div style="background: #f5f5f5; padding: 10px; border-radius: 4px;">
                  <p style="margin: 0; font-weight: bold;">版本：{{ item.version }}</p>
                  <p style="margin: 5px 0; color: #666;">创建时间: {{ fmtDate(item.createTime) }}</p>
                  <a-button size="small" type="primary" @click="rollbackOntologyVersion(item)" :loading="rollbackLoading">
                    回滚到此版本
                  </a-button>
                </div>
              </a-timeline-item>
            </a-timeline>
          </a-form-item>
        </a-form>
      </div>
      <!-- 不存在，首次入库 -->
      <div v-else>
        <a-alert
          message="检测到不存在同名图"
          description="请输入版本号后进行入库操作"
          type="info"
          show-icon
          style="margin-bottom: 20px;"
        />
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
          <a-form-item label="版本号">
            <a-input-group compact>
              <a-input v-model:value="newVersion" placeholder="请输入初始版本号，例如：v1.0" style="width: calc(100% - 100px);" />
              <a-button type="primary" @click="addOntologyToHouse" :loading="versionLoading">新增入库</a-button>
            </a-input-group>
          </a-form-item>
        </a-form>
      </div>
    </a-modal>

    <!-- 导出本体文件弹窗 -->
    <a-modal
      v-model:open="exportDialogVisible"
      title="导出本体文件"
      width="400px"
      @ok="confirmExportOntology"
      @cancel="exportDialogVisible = false"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="导出格式">
          <a-select v-model:value="exportFormat" placeholder="请选择导出格式">
            <a-select-option value="OWL">OWL (.owl)</a-select-option>
            <a-select-option value="RDF">RDF/XML (.rdf)</a-select-option>
            <a-select-option value="Turtle">Turtle (.ttl)</a-select-option>
            <a-select-option value="N-Triples">N-Triples (.nt)</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import {
  DownloadOutlined,
  UploadOutlined,
  FileTextOutlined,
  SearchOutlined,
  CloudUploadOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileExcelOutlined
} from '@ant-design/icons-vue'
import {
  addOntology,
  updateOntology,
  delOntology,
  batchDelOntology,
  getPageOntology,
  searchByProjectName,
  searchByCreator,
  importOntology,
  validateRdfFormat,
  // 新增API
  createOntologyWithFile,
  checkOntologyName,
  getOntologyVersion,
  importOntologyToHouse,
  rollbackOntology,
  exportOntologyFile
} from '@/api/ontology'
import dayjs from 'dayjs'
import * as XLSX from 'xlsx'
import { message, Modal } from 'ant-design-vue'

const router = useRouter()

/* ---------------- 数据 ---------------- */
const searchName = ref('')
const searchCreator = ref('')
const selectedRows = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const uploadInput = ref(null)
const excelFileList = ref([])
const fileList = ref([])
const isEdit = ref(false)
const saveLoading = ref(false)
const importLoading = ref(false)

// 表单数据
const form = reactive({
  ontologyId: null,
  ontologyName: '',
  creatorName: '',
  version: '',
  namespaceUri: '',
  fileFormat: 'OWL',
  file: null
})

// 批量导入相关
const batchImportDialogVisible = ref(false)
const batchImportLoading = ref(false)
const batchUploadInput = ref(null)
const batchFileList = ref([])
const batchImportCreator = ref('')
const batchImportPrefix = ref('')

// 批量导入步骤控制
const importStep = ref(1) // 1: 选择文件, 2: 验证结果
const validationResults = ref([])
const validationAllPassed = ref(false)
const validationSummary = ref('')

// 保留原导入相关（兼容）
const importProjectName = ref('')
const importCreator = ref('')
const importFile = ref(null)

const formRef = ref(null)
const formRules = {
  ontologyName: [{ required: true, message: '请输入本体名称', trigger: 'blur' }],
  creatorName: [{ required: true, message: '请输入创建人', trigger: 'blur' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  namespaceUri: [{ required: true, message: '请输入命名空间URI', trigger: 'blur' }],
  fileFormat: [{ required: true, message: '请选择文件格式', trigger: 'change' }]
}

// 入库相关
const inDialogVisible = ref(false)
const exists = ref(false)
const newVersion = ref('')
const versionLoading = ref(false)
const rollbackLoading = ref(false)
const currentOntology = ref(null)
const importForm = reactive({
  ontologyInfo: {},
  namedGraph: '',
  version: '',
  versionHistory: []
})

// 导出本体文件相关
const exportDialogVisible = ref(false)
const exportFormat = ref('OWL')
const currentExportRow = ref(null)

let validImportData = []

/* ---------------- 表格列定义 ---------------- */
const columns = [
  { title: '序号', dataIndex: 'index', width: 60, align: 'center', customRender: ({ index }) => index + 1 },
  { title: '分类本体名称', dataIndex: 'ontologyName', width: 150 },
  { title: '创建人', dataIndex: 'creatorName', width: 100 },
  { title: '版本号', dataIndex: 'version', width: 80, align: 'center' },
  { title: '命名空间URI', dataIndex: 'namespaceUri', width: 200 },
  { title: '创建时间', dataIndex: 'createTime', width: 150 },
  { title: '修改时间', dataIndex: 'updateTime', width: 150 },
  { title: '操作', dataIndex: 'action', width: 220, fixed: 'right' }
]

/* ---------------- 生命周期 ---------------- */
onMounted(() => loadData())
watch([currentPage, pageSize], () => loadData())

/* ---------------- 业务方法 ---------------- */
async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      projectName: searchName.value.trim() || undefined,
      creator: searchCreator.value.trim() || undefined
    }
    
    let resp
    if (searchName.value.trim()) {
      resp = await searchByProjectName(params)
    } else if (searchCreator.value.trim()) {
      resp = await searchByCreator(params)
    } else {
      resp = await getPageOntology(params)
    }
    
    const d = resp?.data || {}
    // 字段映射：后端字段 -> 前端字段
    tableData.value = (d.records || []).map(item => ({
      ontologyId: item.id,
      ontologyName: item.projectName,
      creatorName: item.creator,
      version: item.versionNumber,
      namespaceUri: item.namespaceUri,
      createTime: item.createTime,
      updateTime: item.modifyTime,
      versionStatus: item.versionStatus,
      parentId: item.parentId
    }))
    total.value = d.total || 0
  } catch (e) {
    message.error(`加载失败：${e.message}`)
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRows.value.map(r => r.ontologyId),
  onChange: (keys, rows) => (selectedRows.value = rows)
}))

const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: t => `共 ${t} 条`,
  onChange: (page, size) => {
    currentPage.value = page
    pageSize.value = size
  }
}))

/* ---------------- 新增/编辑 ---------------- */
function handleAdd() {
  isEdit.value = false
  resetForm()
  fileList.value = []
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    ontologyId: null,
    ontologyName: '',
    creatorName: '',
    version: '',
    namespaceUri: '',
    fileFormat: 'OWL',
    file: null
  })
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

function closeDialog() {
  dialogVisible.value = false
  resetForm()
  fileList.value = []
}

// 文件上传相关
function beforeUpload(file) {
  const validTypes = ['.owl', '.rdf', '.ttl', '.nt']
  const ext = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
  if (!validTypes.includes(ext)) {
    message.error('请上传 OWL、RDF、Turtle 或 N-Triples 格式的文件')
    return false
  }
  form.file = file
  fileList.value = [file]
  return false // 阻止自动上传
}

function handleRemove() {
  form.file = null
  fileList.value = []
}

async function handleSave() {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  
  saveLoading.value = true
  
  try {
    if (isEdit.value) {
      // 编辑模式
      await updateOntology(form)
      message.success('编辑成功')
    } else {
      // 新增模式，支持文件上传
      const formData = new FormData()
      formData.append('ontologyName', form.ontologyName)
      formData.append('creatorName', form.creatorName)
      formData.append('version', form.version || '1.0')
      formData.append('namespaceUri', form.namespaceUri)
      formData.append('fileFormat', form.fileFormat)
      if (form.file) {
        formData.append('file', form.file)
      }
      
      await createOntologyWithFile(formData)
      message.success('新增成功')
    }
    
    dialogVisible.value = false
    loadData()
  } catch (e) {
    message.error(`保存失败：${e.message}`)
  } finally {
    saveLoading.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function handleDelete(row) {
  Modal.confirm({
    title: '确认删除该本体？',
    content: '删除后无法恢复，是否继续？',
    onOk: async () => {
      try {
        await delOntology(row.ontologyId)
        message.success('删除成功')
        loadData()
      } catch (e) {
        message.error(`删除失败：${e.message}`)
      }
    }
  })
}

async function handleBatchDel() {
  if (!selectedRows.value.length) {
    message.warning('请先选择要删除的数据')
    return
  }
  Modal.confirm({
    title: `确认删除选中的 ${selectedRows.value.length} 条数据？`,
    content: '删除后无法恢复，是否继续？',
    onOk: async () => {
      try {
        const ids = selectedRows.value.map(r => r.ontologyId)
        await batchDelOntology(ids)
        message.success('批量删除成功')
        selectedRows.value = []
        loadData()
      } catch (e) {
        message.error(`批量删除失败：${e.message}`)
      }
    }
  })
}

/* ---------------- 入库功能 ---------------- */
async function handleImportToGraph(row) {
  currentOntology.value = row
  try {
    // 检查本体名称是否已存在
    const res = await checkOntologyName(row.ontologyName)
    // 空值保护
    const resultData = res?.data || {}
    exists.value = resultData.exists || false
    
    if (exists.value) {
      // 已存在，获取版本历史
      importForm.namedGraph = (resultData.namedGraphs || [])[0] || ''
      const versionRes = await getOntologyVersion(row.ontologyName)
      // 字段映射：后端 id -> 前端 ontologyId
      importForm.versionHistory = (versionRes?.data || []).map(v => ({
        ontologyId: v?.id,
        version: v?.version,
        creator: v?.creator,
        status: v?.status,
        createTime: v?.createTime
      }))
      importForm.version = importForm.versionHistory[0]?.version || ''
    }
    
    newVersion.value = ''
    inDialogVisible.value = true
  } catch (e) {
    message.error(`获取入库信息失败：${e.message}`)
  }
}

// 更新版本（入库）
async function updateOntologyVersion() {
  if (!newVersion.value.trim()) {
    message.warning('请输入新版本号')
    return
  }
  
  versionLoading.value = true
  try {
    const data = {
      sourceOntologyId: currentOntology.value.ontologyId,
      newVersion: newVersion.value.trim()
    }
    
    const res = await importOntologyToHouse(data)
    if (res.code === 200) {
      message.success('入库更新成功')
      // 刷新版本历史
      const versionRes = await getOntologyVersion(currentOntology.value.ontologyName)
      importForm.versionHistory = (versionRes.data || []).map(v => ({
        ontologyId: v.id,
        version: v.version,
        creator: v.creator,
        status: v.status,
        createTime: v.createTime
      }))
      importForm.version = importForm.versionHistory[0]?.version || ''
      newVersion.value = ''
      loadData()
    } else {
      message.error(res.message || '入库更新失败')
    }
  } catch (e) {
    message.error(`入库更新失败：${e.message}`)
  } finally {
    versionLoading.value = false
  }
}

// 新增入库（首次入库）
async function addOntologyToHouse() {
  if (!newVersion.value.trim()) {
    message.warning('请输入版本号')
    return
  }
  
  versionLoading.value = true
  try {
    const data = {
      sourceOntologyId: currentOntology.value.ontologyId,
      newVersion: newVersion.value.trim()
    }
    
    const res = await importOntologyToHouse(data)
    if (res.code === 200) {
      message.success('入库成功')
      // 刷新状态
      const checkRes = await checkOntologyName(currentOntology.value.ontologyName)
      exists.value = checkRes.data.exists
      if (exists.value) {
        importForm.namedGraph = checkRes.data.namedGraphs[0]
        const versionRes = await getOntologyVersion(currentOntology.value.ontologyName)
        importForm.versionHistory = (versionRes.data || []).map(v => ({
          ontologyId: v.id,
          version: v.version,
          creator: v.creator,
          status: v.status,
          createTime: v.createTime
        }))
        importForm.version = importForm.versionHistory[0]?.version || ''
      }
      newVersion.value = ''
      loadData()
    } else {
      message.error(res.message || '入库失败')
    }
  } catch (e) {
    message.error(`入库失败：${e.message}`)
  } finally {
    versionLoading.value = false
  }
}

// 回滚版本
async function rollbackOntologyVersion(item) {
  rollbackLoading.value = true
  try {
    const data = {
      sourceOntologyId: item.ontologyId,
      newVersion: item.version
    }
    const res = await rollbackOntology(data)
    if (res.code === 200) {
      message.success('回滚成功')
      // 刷新版本历史
      const versionRes = await getOntologyVersion(currentOntology.value.ontologyName)
      importForm.versionHistory = (versionRes.data || []).map(v => ({
        ontologyId: v.id,
        version: v.version,
        creator: v.creator,
        status: v.status,
        createTime: v.createTime
      }))
      importForm.version = importForm.versionHistory[0]?.version || ''
      loadData()
    } else {
      message.error(res.message || '回滚失败')
    }
  } catch (e) {
    message.error(`回滚失败：${e.message}`)
  } finally {
    rollbackLoading.value = false
  }
}

function closeInDialog() {
  inDialogVisible.value = false
  importForm.ontologyInfo = {}
  importForm.namedGraph = ''
  importForm.version = ''
  importForm.versionHistory = []
  newVersion.value = ''
}

/* ---------------- 导出本体文件 ---------------- */
function handleExportOntology(row) {
  currentExportRow.value = row
  exportFormat.value = 'OWL'
  exportDialogVisible.value = true
}

async function confirmExportOntology() {
  if (!currentExportRow.value) return
  
  const loading = message.loading('正在导出...', 0)
  try {
    const ontologyId = currentExportRow.value.ontologyId
    const format = exportFormat.value
    
    const res = await exportOntologyFile(ontologyId, format)
    
    // axios blob响应，res.data是blob
    const blobData = res.data || res
    const blob = new Blob([blobData], { type: 'application/octet-stream' })
    
    // 确定文件后缀
    const extMap = {
      'OWL': '.owl',
      'RDF': '.rdf',
      'Turtle': '.ttl',
      'N-Triples': '.nt'
    }
    const ext = extMap[format] || '.owl'
    const fileName = currentExportRow.value.ontologyName + ext
    
    // 下载文件
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(downloadUrl)
    
    message.success('导出成功')
    exportDialogVisible.value = false
  } catch (e) {
    message.error(`导出失败：${e.message}`)
  } finally {
    loading()
  }
}

/* ---------------- 批量导入RDF ---------------- */
function openBatchImportDialog() {
  batchImportDialogVisible.value = true
  batchFileList.value = []
  batchImportCreator.value = ''
  batchImportPrefix.value = ''
  importStep.value = 1
  validationResults.value = []
  validationAllPassed.value = false
  validationSummary.value = ''
}

function closeBatchImportDialog() {
  batchImportDialogVisible.value = false
  batchFileList.value = []
  batchImportCreator.value = ''
  batchImportPrefix.value = ''
  importStep.value = 1
  validationResults.value = []
  validationAllPassed.value = false
}

function handleBatchDrop(e) {
  const files = Array.from(e.dataTransfer.files)
  if (files.length) {
    addBatchFiles(files)
  }
}

function readBatchFiles(e) {
  const files = Array.from(e.target.files || [])
  if (files.length) {
    addBatchFiles(files)
  }
}

function addBatchFiles(files) {
  const validTypes = ['.rdf', '.owl', '.ttl', '.nt', '.csv']
  const validFiles = files.filter(file => {
    const ext = file.name.slice(file.name.lastIndexOf('.')).toLowerCase()
    return validTypes.includes(ext)
  })
  
  if (validFiles.length !== files.length) {
    message.warning(`已过滤 ${files.length - validFiles.length} 个非RDF/CSV格式文件`)
  }
  
  if (validFiles.length === 0) {
    message.error('没有有效的RDF或CSV文件')
    return
  }
  
  // 去重（按文件名）
  const existingNames = new Set(batchFileList.value.map(f => f.name))
  const newFiles = validFiles.filter(f => !existingNames.has(f.name))
  
  batchFileList.value.push(...newFiles.map(f => ({ 
    uid: Date.now() + Math.random(), 
    name: f.name, 
    file: f 
  })))
  
  message.success(`已添加 ${newFiles.length} 个文件，共 ${batchFileList.value.length} 个`)
}

function removeBatchFile(index) {
  batchFileList.value.splice(index, 1)
}

// 验证文件格式
async function validateFiles() {
  if (!batchFileList.value.length) {
    message.warning('请至少选择一个文件')
    return false
  }
  
  const results = []
  let allPassed = true
  let validCount = 0
  let invalidCount = 0
  
  for (const item of batchFileList.value) {
    const fileExt = item.name.toLowerCase().split('.').pop()
    const isRdf = ['rdf', 'owl', 'ttl', 'nt'].includes(fileExt)
    const isCsv = fileExt === 'csv'
    
    if (isRdf) {
      // RDF 文件需要验证格式
      try {
        const res = await validateRdfFormat(item.file)
        if (res.code === 200) {
          const data = res.data
          results.push({
            fileName: item.name,
            file: item.file,
            fileType: 'rdf',
            valid: data.valid,
            format: data.format,
            tripleCount: data.tripleCount,
            errorMessage: data.errorMessage,
            suggestion: data.suggestion,
            canConvertToCsv: !data.valid, // 如果RDF无效，可以转CSV模式
            handleMode: data.valid ? 'import' : 'skip', // 默认处理方式
            csvMode: 'original'
          })
          if (data.valid) validCount++
          else {
            invalidCount++
            allPassed = false
          }
        } else {
          results.push({
            fileName: item.name,
            file: item.file,
            fileType: 'rdf',
            valid: false,
            errorMessage: res.message || '验证失败',
            canConvertToCsv: true,
            handleMode: 'skip',
            csvMode: 'original'
          })
          invalidCount++
          allPassed = false
        }
      } catch (e) {
        results.push({
          fileName: item.name,
          file: item.file,
          fileType: 'rdf',
          valid: false,
          errorMessage: '验证请求失败: ' + e.message,
          canConvertToCsv: true,
          handleMode: 'skip',
          csvMode: 'original'
        })
        invalidCount++
        allPassed = false
      }
    } else if (isCsv) {
      // CSV 文件直接标记为有效，但需要选择转换模式
      results.push({
        fileName: item.name,
        file: item.file,
        fileType: 'csv',
        valid: true,
        format: 'CSV',
        csvMode: 'original',
        handleMode: 'import'
      })
      validCount++
    }
  }
  
  validationResults.value = results
  validationAllPassed.value = allPassed
  validationSummary.value = `共 ${results.length} 个文件：${validCount} 个有效，${invalidCount} 个需要处理`
  
  return true
}

// 步骤控制：进入验证步骤
async function goToValidationStep() {
  if (!batchImportCreator.value.trim()) {
    message.warning('请输入创建人')
    return
  }
  if (!batchFileList.value.length) {
    message.warning('请至少选择一个文件')
    return
  }
  
  batchImportLoading.value = true
  const hide = message.loading('正在验证文件格式...', 0)
  
  try {
    await validateFiles()
    importStep.value = 2
  } finally {
    hide()
    batchImportLoading.value = false
  }
}

// 执行批量导入
async function confirmBatchImport() {
  // 如果在步骤1，先进入验证步骤
  if (importStep.value === 1) {
    await goToValidationStep()
    return
  }
  
  // 步骤2：执行导入
  batchImportLoading.value = true
  
  // 计算需要导入的文件数量
  const filesToImport = validationResults.value.filter(r => r.handleMode !== 'skip')
  const totalFiles = filesToImport.length
  
  if (totalFiles === 0) {
    message.warning('没有需要导入的文件')
    batchImportLoading.value = false
    return
  }
  
  let currentIndex = 0
  let success = 0
  let failed = 0
  const errors = []
  
  for (const result of filesToImport) {
    currentIndex++
    const progressMsg = message.loading(`正在导入 (${currentIndex}/${totalFiles}): ${result.fileName}...`, 0)
    
    try {
      // 生成项目名称
      const fileName = result.fileName.replace(/\.[^/.]+$/, '')
      const projectName = batchImportPrefix.value.trim() 
        ? `${batchImportPrefix.value.trim()}-${fileName}`
        : fileName
      
      console.log(`[${currentIndex}/${totalFiles}] 正在导入: ${result.fileName} -> 项目名: ${projectName}, 模式: ${result.csvMode || 'rdf'}`)
      
      const res = await importOntology(
        result.file,
        projectName,
        batchImportCreator.value.trim(),
        result.csvMode || 'original'
      )
      
      progressMsg()
      
      if (res.code === 200) {
        success++
        message.success(`${result.fileName} 导入成功`)
        console.log(`✓ 导入成功: ${result.fileName}`)
      } else {
        failed++
        const errorMsg = res.message || '未知错误'
        errors.push(`${result.fileName}: ${errorMsg}`)
        message.error(`${result.fileName} 导入失败: ${errorMsg}`)
        console.error(`✗ 导入失败: ${result.fileName} - ${errorMsg}`)
      }
    } catch (e) {
      progressMsg()
      failed++
      const errorMsg = e.response?.data?.message || e.message || '网络错误'
      errors.push(`${result.fileName}: ${errorMsg}`)
      message.error(`${result.fileName} 导入异常: ${errorMsg}`)
      console.error(`✗ 导入异常: ${result.fileName}`, e)
    }
  }
  
  batchImportLoading.value = false
  
  // 显示结果
  if (success > 0 && failed === 0) {
    message.success(`批量导入完成：成功 ${success} 个`)
    closeBatchImportDialog()
    loadData()
  } else if (success > 0 && failed > 0) {
    message.warning(`批量导入完成：成功 ${success} 个，失败 ${failed} 个`)
    Modal.warning({
      title: '部分导入失败',
      content: () => h('div', [
        h('p', `成功: ${success} 个, 失败: ${failed} 个`),
        h('ul', { style: 'max-height: 200px; overflow-y: auto; margin-top: 10px;' },
          errors.map(err => h('li', { style: 'color: #ff4d4f; margin: 4px 0;' }, err))
        )
      ]),
      width: 600
    })
    loadData()
  } else {
    message.error(`全部导入失败，共 ${failed} 个文件`)
    Modal.error({
      title: '导入失败',
      content: () => h('div', [
        h('p', '所有文件导入失败，错误详情：'),
        h('ul', { style: 'max-height: 300px; overflow-y: auto; margin-top: 10px;' },
          errors.map(err => h('li', { style: 'color: #ff4d4f; margin: 4px 0;' }, err))
        )
      ]),
      width: 600
    })
  }
}

/* ---------------- 兼容旧版单条导入（保留） ---------------- */
function openImportDialog() {
  // 跳转到新增本体
  handleAdd()
}

function closeImportDialog() {
  importDialogVisible.value = false
}

function handleDrop(e) {
  // 批量导入的拖拽
  handleBatchDrop(e)
}

function readFile(e) {
  // 由批量导入处理
}

async function confirmImport() {
  // 不再使用，已由批量导入替代
}

function handleTpl() {
  const tpl = [
    { 本体名称: '示例本体1', 创建人: '张三', 版本号: 'v1.0.0', 命名空间URI: 'http://example.com/ontology1' },
    { 本体名称: '示例本体2', 创建人: '李四', 版本号: 'v2.1.0', 命名空间URI: '' }
  ]
  const ws = XLSX.utils.json_to_sheet(tpl)
  ws['!cols'] = [{ wch: 20 }, { wch: 12 }, { wch: 10 }, { wch: 35 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '本体导入模板')
  XLSX.writeFile(wb, `本体导入模板_${dayjs().format('YYYYMMDD')}.xlsx`)
  message.success('模板下载成功')
}

async function handleExportAll() {
  const hide = message.loading('正在导出...', 0)
  try {
    const pageSize = 100
    let pageNum = 1
    const all = []
    while (true) {
      const params = {
        pageNum,
        pageSize,
        ontologyName: searchName.value.trim() || undefined,
        creatorName: searchCreator.value.trim() || undefined
      }
      const { data } = await getPageOntology(params)
      const records = data?.records || []
      all.push(...records)
      if (!records.length || records.length < pageSize) break
      pageNum++
    }
    if (!all.length) throw new Error('暂无数据可导出')
    const sheet = XLSX.utils.json_to_sheet(
      all.map(r => ({
        本体名称: r.ontologyName || '',
        创建人: r.creatorName || '',
        版本号: r.version || '',
        命名空间URI: r.namespaceUri || '',
        创建时间: r.createTime ? dayjs(r.createTime).format('YYYY-MM-DD HH:mm:ss') : '',
        修改时间: r.updateTime ? dayjs(r.updateTime).format('YYYY-MM-DD HH:mm:ss') : ''
      }))
    )
    sheet['!cols'] = [{ wch: 20 }, { wch: 12 }, { wch: 10 }, { wch: 35 }, { wch: 20 }, { wch: 20 }]
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, sheet, '本体数据')
    XLSX.writeFile(wb, `本体列表_${dayjs().format('YYYYMMDD')}.xlsx`)
    message.success(`导出成功，共 ${all.length} 条`)
  } catch (e) {
    message.error(`导出失败：${e.message}`)
  } finally {
    hide()
  }
}

/* ---------------- 跳转项目详情 ---------------- */
function handleViewDetail(record) {
  // 跳转到项目详情页，后续开发
  router.push({
    name: 'OntologyProject',
    params: { id: record.ontologyId }
  })
}

/* ---------------- 工具方法 ---------------- */
const fmtDate = cell => (cell ? dayjs(cell).format('YYYY-MM-DD HH:mm') : '--')
</script>

<style scoped>
.container {
  padding: 16px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  align-items: center;
}
.filters {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.selected-tip {
  margin-bottom: 16px;
  padding: 8px 12px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  display: flex;
  align-items: center;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.upload-card {
  border: 1px dashed #ccc;
  border-radius: 6px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
}
.upload-card:hover {
  border-color: #409eff;
}
.tip {
  margin: 10px 0;
  font-size: 14px;
}
.sub-tip {
  font-size: 12px;
  color: #999;
}
.template {
  margin-top: 10px;
  text-align: center;
}
.file-name {
  color: #1890ff;
  font-weight: bold;
}
.project-link {
  color: #1890ff;
  cursor: pointer;
}
.project-link:hover {
  text-decoration: underline;
}
</style>
