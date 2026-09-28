<template>
  <a-card class="container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <a-button type="link" @click="goBack" class="back-btn">
          <LeftOutlined /> 返回列表
        </a-button>
        <div class="title-section">
          <h2 class="project-title">{{ ontologyName }}</h2>
          <a-tag :color="versionStatus === 1 ? 'success' : 'default'">
            {{ versionStatus === 1 ? '当前版本' : '历史版本' }}
          </a-tag>
        </div>
        <div class="project-meta">
          <span class="meta-item">
            <UserOutlined /> 创建人：{{ creatorName }}
          </span>
          <span class="meta-item">
            <TagOutlined /> 版本：{{ version }}
          </span>
          <span class="meta-item">
            <ClockCircleOutlined /> 更新时间：{{ updateTime }}
          </span>
        </div>
      </div>
      <div class="header-right">
        <a-space>
          <a-button type="primary" @click="handleImportToGraph">
            <CloudUploadOutlined /> 入库
          </a-button>
          <a-button @click="handleExport">
            <DownloadOutlined /> 导出
          </a-button>
          <a-button type="dashed" danger @click="handleDelete">
            <DeleteOutlined /> 删除
          </a-button>
        </a-space>
      </div>
    </div>

    <!-- 统计卡片 -->
    <a-row :gutter="16" class="stat-row">
      <a-col :span="6">
        <a-card class="stat-card" :bordered="true">
          <div class="stat-icon blue">
            <DatabaseOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statData.classCount }}</div>
            <div class="stat-label">类数量</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card" :bordered="true">
          <div class="stat-icon green">
            <AppstoreOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statData.individualCount }}</div>
            <div class="stat-label">实例数量</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card" :bordered="true">
          <div class="stat-icon orange">
            <LinkOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statData.propertyCount }}</div>
            <div class="stat-label">属性数量</div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card class="stat-card" :bordered="true">
          <div class="stat-icon purple">
            <NodeIndexOutlined />
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ statData.tripleCount }}</div>
            <div class="stat-label">三元组数量</div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- Tab 内容区 -->
    <a-tabs v-model:activeKey="activeKey" type="card" class="content-tabs">
      <!-- 本体可视化：放在第一个 tab 且默认选中。
           原先它排在最后、且只在切到该 tab 时才发请求（watch(activeKey)），
           于是「必须先点一下才看得到图」，点完还要再等接口 18s。
           现在改为：进页面就在后台预加载 + 默认展示本 tab。 -->
      <a-tab-pane key="visual" tab="本体可视化">
        <div class="tab-content visual-content">
          <div class="visual-toolbar">
            <a-space>
              <a-select v-model:value="visualLayout" style="width: 150px" @change="handleLayoutChange">
                <a-select-option value="force">力导向布局</a-select-option>
                <a-select-option value="circular">环形布局</a-select-option>
                <a-select-option value="hierarchical">层次布局</a-select-option>
              </a-select>
              <a-button @click="handleRefreshVisual" :loading="visualLoading">
                <ReloadOutlined /> 刷新
              </a-button>
              <a-button @click="handleExportVisual" :disabled="!visualChartInstance">
                <DownloadOutlined /> 导出图片
              </a-button>
            </a-space>
          </div>
          <div class="visual-stats" v-if="visualStats.nodeCount > 0">
            <a-space size="large">
              <span>节点: <strong>{{ visualStats.nodeCount }}</strong></span>
              <span>边: <strong>{{ visualStats.edgeCount }}</strong></span>
              <span>类: <strong>{{ visualStats.classCount }}</strong></span>
              <span>实例: <strong>{{ visualStats.individualCount }}</strong></span>
              <span v-if="visualStats.totalTriples">
                全图三元组: <strong>{{ visualStats.totalTriples }}</strong>
              </span>
              <span v-if="visualStats.truncated" class="visual-truncated">（图为抽样展示，非全量）</span>
            </a-space>
          </div>
          <a-spin :spinning="visualLoading" tip="正在加载图谱数据，首次约需十几秒…">
            <div class="visual-container" ref="visualChart" style="height: 600px;"></div>
          </a-spin>
          <div v-if="!visualLoading && visualStats.nodeCount === 0" class="visual-empty">
            该本体暂无可视化数据，请先在本体管理中「入库」RDF 数据。
          </div>
        </div>
      </a-tab-pane>

      <!-- 类管理 -->
      <a-tab-pane key="class" tab="类管理">
        <div class="tab-content">
          <div class="toolbar">
            <a-space>
              <a-button type="primary" @click="handleAddClass">
                <PlusOutlined /> 新增类
              </a-button>
              <a-input-search
                v-model:value="classSearchText"
                placeholder="搜索类名"
                style="width: 250px"
                @search="handleSearchClass"
              />
            </a-space>
          </div>
          <a-table
            :columns="classColumns"
            :data-source="classData"
            :loading="classLoading"
            :pagination="classPagination"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleEditClass(record)">编辑</a-button>
                  <a-button type="link" size="small" @click="handleViewIndividuals(record)">查看实例</a-button>
                  <a-popconfirm title="确定删除此类吗？" @confirm="handleDeleteClass(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
              <template v-else-if="column.key === 'name'">
                <span>{{ decodeUri(record.name) }}</span>
              </template>
              <template v-else-if="column.key === 'parentName'">
                <span>{{ decodeUri(record.parentName) || '--' }}</span>
              </template>
              <template v-else-if="column.key === 'description'">
                <a-tooltip :title="record.description">
                  <span class="ellipsis-text">{{ record.description || '--' }}</span>
                </a-tooltip>
              </template>
            </template>
          </a-table>
        </div>
      </a-tab-pane>

      <!-- 实例管理 -->
      <a-tab-pane key="individual" tab="实例管理">
        <div class="tab-content">
          <div class="toolbar">
            <a-space>
              <a-button type="primary" @click="handleAddIndividual">
                <PlusOutlined /> 新增实例
              </a-button>
              <a-select
                v-model:value="individualClassFilter"
                placeholder="选择所属类"
                style="width: 180px"
                allowClear
              >
                <a-select-option v-for="cls in classOptions" :key="cls.value" :value="cls.value">
                  {{ decodeUri(cls.label) }}
                </a-select-option>
              </a-select>
              <a-input-search
                v-model:value="individualSearchText"
                placeholder="搜索实例名"
                style="width: 250px"
                @search="handleSearchIndividual"
              />
            </a-space>
          </div>
          <a-table
            :columns="individualColumns"
            :data-source="individualData"
            :loading="individualLoading"
            :pagination="individualPagination"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleEditIndividual(record)">编辑</a-button>
                  <a-button type="link" size="small" @click="handleViewRelations(record)">查看关系</a-button>
                  <a-popconfirm title="确定删除此实例吗？" @confirm="handleDeleteIndividual(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
              <template v-else-if="column.key === 'description'">
                <span>{{ record.description || '--' }}</span>
              </template>
              <template v-else-if="column.key === 'className'">
                <span>{{ decodeUri(record.className) || '--' }}</span>
              </template>
              <template v-else-if="column.key === 'name'">
                <span>{{ decodeUri(record.name) }}</span>
              </template>
              <template v-else-if="column.key === 'createTime'">
                <span>{{ record.createTime || '--' }}</span>
              </template>
            </template>
            <template #empty>
              <a-empty description="暂无实例数据" />
            </template>
          </a-table>
        </div>
      </a-tab-pane>

      <!-- 属性管理 -->
      <a-tab-pane key="property" tab="属性管理">
        <div class="tab-content">
          <div class="toolbar">
            <a-space>
              <a-button type="primary" @click="handleAddProperty">
                <PlusOutlined /> 新增属性
              </a-button>
              <a-select
                v-model:value="propertyTypeFilter"
                placeholder="属性类型"
                style="width: 150px"
                allowClear
              >
                <a-select-option value="object">对象属性</a-select-option>
                <a-select-option value="datatype">数据属性</a-select-option>
                <a-select-option value="annotation">注释属性</a-select-option>
              </a-select>
              <a-input-search
                v-model:value="propertySearchText"
                placeholder="搜索属性名"
                style="width: 250px"
                @search="handleSearchProperty"
              />
            </a-space>
          </div>
          <a-table
            :columns="propertyColumns"
            :data-source="propertyData"
            :loading="propertyLoading"
            :pagination="propertyPagination"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'name'">
                <span>{{ decodeUri(record.name) }}</span>
              </template>
              <template v-else-if="column.key === 'domain'">
                <span>{{ decodeUri(record.domain) || '--' }}</span>
              </template>
              <template v-else-if="column.key === 'range'">
                <span>{{ decodeUri(record.range) || '--' }}</span>
              </template>
              <template v-else-if="column.key === 'type'">
                <a-tag :color="getPropertyTypeColor(record.type)">
                  {{ getPropertyTypeText(record.type) }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleEditProperty(record)">编辑</a-button>
                  <a-popconfirm title="确定删除此属性吗？" @confirm="handleDeleteProperty(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-tab-pane>

    </a-tabs>

    <!-- 新增/编辑类弹窗 -->
    <a-modal
      v-model:open="classModalVisible"
      :title="isEditClass ? '编辑类' : '新增类'"
      @ok="handleSaveClass"
      @cancel="classModalVisible = false"
      width="600px"
    >
      <a-form :model="classForm" :rules="classRules" ref="classFormRef" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="类名" name="name">
          <a-input v-model:value="classForm.name" placeholder="请输入类名" />
        </a-form-item>
        <a-form-item label="父类">
          <a-tree-select
            v-model:value="classForm.parentId"
            :tree-data="classTreeData"
            placeholder="选择父类（可选）"
            allow-clear
            tree-default-expand-all
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="classForm.description" :rows="3" placeholder="请输入类描述" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增/编辑实例弹窗 -->
    <a-modal
      v-model:open="individualModalVisible"
      :title="isEditIndividual ? '编辑实例' : '新增实例'"
      @ok="handleSaveIndividual"
      @cancel="individualModalVisible = false"
      width="700px"
    >
      <a-form :model="individualForm" :rules="individualRules" ref="individualFormRef" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="实例名" name="name">
          <a-input v-model:value="individualForm.name" placeholder="请输入实例名" />
        </a-form-item>
        <a-form-item label="所属类" name="classId">
          <a-select
            v-model:value="individualForm.classId"
            placeholder="选择所属类"
            allow-clear
            @change="onClassChange"
          >
            <a-select-option v-for="cls in classOptions" :key="cls.value" :value="cls.value">
              {{ cls.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="individualForm.description" :rows="2" placeholder="请输入实例描述" />
        </a-form-item>
        
        <!-- 对象属性设置 -->
        <a-divider orientation="left">对象属性关系</a-divider>
        <div v-for="(prop, index) in individualForm.objectProperties" :key="index" class="property-row">
          <a-row :gutter="8">
            <a-col :span="10">
              <a-select
                v-model:value="prop.propertyUri"
                placeholder="选择关系属性"
                style="width: 100%"
              >
                <a-select-option v-for="p in availableObjectProperties" :key="p.uri" :value="p.uri">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-col>
            <a-col :span="10">
              <a-select
                v-model:value="prop.targetUri"
                placeholder="选择目标实例"
                style="width: 100%"
                show-search
                :filter-option="filterIndividualOption"
              >
                <a-select-option v-for="ind in availableTargetIndividuals" :key="ind.uri" :value="ind.uri">
                  {{ ind.name }}
                </a-select-option>
              </a-select>
            </a-col>
            <a-col :span="4">
              <a-button type="link" danger @click="removeObjectProperty(index)">
                <DeleteOutlined />
              </a-button>
            </a-col>
          </a-row>
        </div>
        <a-button type="dashed" block @click="addObjectProperty" style="margin-top: 8px">
          <PlusOutlined /> 添加对象属性
        </a-button>
        
        <!-- 数据属性设置 -->
        <a-divider orientation="left">数据属性</a-divider>
        <div v-for="(prop, index) in individualForm.datatypeProperties" :key="index" class="property-row">
          <a-row :gutter="8">
            <a-col :span="8">
              <a-select
                v-model:value="prop.propertyUri"
                placeholder="选择属性"
                style="width: 100%"
              >
                <a-select-option v-for="p in availableDatatypeProperties" :key="p.uri" :value="p.uri">
                  {{ p.name }}
                </a-select-option>
              </a-select>
            </a-col>
            <a-col :span="12">
              <a-input v-model:value="prop.value" placeholder="输入属性值" />
            </a-col>
            <a-col :span="4">
              <a-button type="link" danger @click="removeDatatypeProperty(index)">
                <DeleteOutlined />
              </a-button>
            </a-col>
          </a-row>
        </div>
        <a-button type="dashed" block @click="addDatatypeProperty" style="margin-top: 8px">
          <PlusOutlined /> 添加数据属性
        </a-button>
      </a-form>
    </a-modal>

    <!-- 入库弹窗 -->
    <a-modal
      v-model:open="importModalVisible"
      title="本体入库"
      @ok="confirmImport"
      @cancel="importModalVisible = false"
      width="500px"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="当前版本">
          <span>{{ version }}</span>
        </a-form-item>
        <a-form-item label="新版本号">
          <a-input v-model:value="newVersion" placeholder="请输入新版本号，如 1.1" />
        </a-form-item>
        <a-form-item label="版本说明">
          <a-textarea v-model:value="versionRemark" :rows="3" placeholder="请输入版本说明（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 导出弹窗 -->
    <a-modal
      v-model:open="exportModalVisible"
      title="导出本体"
      @ok="confirmExport"
      @cancel="exportModalVisible = false"
      width="400px"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="导出格式">
          <a-select v-model:value="exportFormat" placeholder="选择格式">
            <a-select-option value="OWL">OWL (.owl)</a-select-option>
            <a-select-option value="RDF">RDF/XML (.rdf)</a-select-option>
            <a-select-option value="Turtle">Turtle (.ttl)</a-select-option>
            <a-select-option value="N-Triples">N-Triples (.nt)</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增/编辑属性弹窗 -->
    <a-modal
      v-model:open="propertyModalVisible"
      :title="isEditProperty ? '编辑属性' : '新增属性'"
      @ok="handleSaveProperty"
      @cancel="propertyModalVisible = false"
      width="600px"
    >
      <a-form :model="propertyForm" :rules="propertyRules" ref="propertyFormRef" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="属性名" name="name">
          <a-input v-model:value="propertyForm.name" placeholder="请输入属性名，如 hasPart" />
        </a-form-item>
        <a-form-item label="属性类型" name="type">
          <a-select v-model:value="propertyForm.type" placeholder="选择属性类型">
            <a-select-option value="object">对象属性（指向其他实例）</a-select-option>
            <a-select-option value="datatype">数据属性（文字/数字）</a-select-option>
            <a-select-option value="annotation">注释属性（说明信息）</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="定义域">
          <a-select
            v-model:value="propertyForm.domain"
            placeholder="选择该属性适用的类（可选）"
            allow-clear
          >
            <a-select-option v-for="cls in classOptions" :key="cls.value" :value="cls.value">
              {{ cls.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="值域">
          <a-select
            v-if="propertyForm.type === 'object'"
            v-model:value="propertyForm.range"
            placeholder="选择值域类（目标实例的类）"
            allow-clear
          >
            <a-select-option v-for="cls in classOptions" :key="cls.value" :value="cls.value">
              {{ cls.label }}
            </a-select-option>
          </a-select>
          <a-select
            v-else-if="propertyForm.type === 'datatype'"
            v-model:value="propertyForm.range"
            placeholder="选择数据类型"
            allow-clear
          >
            <a-select-option value="xsd:string">字符串 (string)</a-select-option>
            <a-select-option value="xsd:int">整数 (int)</a-select-option>
            <a-select-option value="xsd:float">浮点数 (float)</a-select-option>
            <a-select-option value="xsd:boolean">布尔值 (boolean)</a-select-option>
            <a-select-option value="xsd:date">日期 (date)</a-select-option>
          </a-select>
          <a-input v-else v-model:value="propertyForm.range" placeholder="输入值域（可选）" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="propertyForm.description" :rows="3" placeholder="请输入属性描述" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 查看关系弹窗 -->
    <a-modal
      v-model:open="relationModalVisible"
      :title="`实例关系 - ${currentIndividual?.name || ''}`"
      @cancel="relationModalVisible = false"
      width="800px"
      :footer="null"
    >
      <a-spin :spinning="relationLoading">
        <div class="relation-container">
          <!-- 关系统计 -->
          <a-row :gutter="16" class="relation-stats">
            <a-col :span="8">
              <a-statistic title="对象属性关系" :value="objectRelations.length" />
            </a-col>
            <a-col :span="8">
              <a-statistic title="数据属性" :value="datatypeRelations.length" />
            </a-col>
            <a-col :span="8">
              <a-statistic title="反向关系" :value="inverseRelations.length" />
            </a-col>
          </a-row>

          <!-- 关系详情 -->
          <a-tabs v-model:activeKey="relationActiveKey" type="card">
            <!-- 对象属性关系 -->
            <a-tab-pane key="object" :tab="`对象属性 (${objectRelations.length})`">
              <a-table
                :columns="relationColumns"
                :data-source="objectRelations"
                :pagination="{ pageSize: 5 }"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'target'">
                    <a-tag color="blue">{{ record.targetName }}</a-tag>
                  </template>
                </template>
                <template #empty>
                  <a-empty description="暂无对象属性关系" />
                </template>
              </a-table>
            </a-tab-pane>

            <!-- 数据属性 -->
            <a-tab-pane key="datatype" :tab="`数据属性 (${datatypeRelations.length})`">
              <a-table
                :columns="datatypeColumns"
                :data-source="datatypeRelations"
                :pagination="{ pageSize: 5 }"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'value'">
                    <span class="datatype-value">{{ record.value }}</span>
                  </template>
                </template>
                <template #empty>
                  <a-empty description="暂无数据属性" />
                </template>
              </a-table>
            </a-tab-pane>

            <!-- 反向关系 -->
            <a-tab-pane key="inverse" :tab="`反向关系 (${inverseRelations.length})`">
              <a-table
                :columns="inverseColumns"
                :data-source="inverseRelations"
                :pagination="{ pageSize: 5 }"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'source'">
                    <a-tag color="green">{{ record.sourceName }}</a-tag>
                  </template>
                </template>
                <template #empty>
                  <a-empty description="暂无反向关系" />
                </template>
              </a-table>
            </a-tab-pane>
          </a-tabs>
        </div>
      </a-spin>
    </a-modal>
  </a-card>
</template>

<script setup>
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  LeftOutlined,
  UserOutlined,
  TagOutlined,
  ClockCircleOutlined,
  CloudUploadOutlined,
  DownloadOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  DatabaseOutlined,
  AppstoreOutlined,
  LinkOutlined,
  NodeIndexOutlined
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { 
  getOntologyById, 
  checkOntologyName, 
  getOntologyVersion, 
  importOntologyToHouse, 
  exportOntologyFile,
  getOntologyStats,
  getOntologyClasses,
  getOntologyIndividuals,
  getOntologyProperties,
  createOntologyClass,
  updateOntologyClass,
  deleteOntologyClass,
  createOntologyIndividual,
  updateOntologyIndividual,
  deleteOntologyIndividual,
  getIndividualRelations,
  createOntologyProperty,
  updateOntologyProperty,
  deleteOntologyProperty,
  getOntologyVisualization
} from '@/api/ontology'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

// 基础信息
const ontologyId = ref('')
const ontologyName = ref('')
const creatorName = ref('')
const version = ref('')
const updateTime = ref('')
const versionStatus = ref(1)
const namespaceUri = ref('')

// 当前Tab
const activeKey = ref('visual')

// 统计数据
const statData = reactive({
  classCount: 0,
  individualCount: 0,
  propertyCount: 0,
  tripleCount: 0
})

// URI 解码工具函数
function decodeUri(str) {
  if (!str) return ''
  try {
    // 解码 %XX 格式的 URI 编码
    return decodeURIComponent(str)
  } catch (e) {
    // 解码失败返回原字符串
    return str
  }
}

// 类管理
const classSearchText = ref('')
const classLoading = ref(false)
const classData = ref([])
const classPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})
const classColumns = [
  { title: '类名', dataIndex: 'name', key: 'name', width: 200 },
  { title: '父类', dataIndex: 'parentName', key: 'parentName', width: 180 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '实例数', dataIndex: 'individualCount', key: 'individualCount', width: 100, align: 'center' },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
]

// 实例管理
const individualSearchText = ref('')
const individualClassFilter = ref(undefined)
const individualLoading = ref(false)
const individualData = ref([])
const classOptions = ref([])
const individualPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})
const individualColumns = [
  { title: '实例名', dataIndex: 'name', key: 'name', width: 200 },
  { title: '所属类', dataIndex: 'className', key: 'className', width: 180 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
]

// 实例编辑弹窗
const individualModalVisible = ref(false)
const isEditIndividual = ref(false)
const individualFormRef = ref(null)
const individualForm = reactive({
  id: null,
  name: '',
  classId: undefined,
  description: '',
  objectProperties: [],  // 对象属性列表 {propertyUri, targetUri}
  datatypeProperties: [] // 数据属性列表 {propertyUri, value}
})
const individualRules = {
  name: [{ required: true, message: '请输入实例名', trigger: 'blur' }],
  classId: [{ required: true, message: '请选择所属类', trigger: 'change' }]
}

// 可用的属性列表（根据所选类过滤）
const availableObjectProperties = ref([])
const availableDatatypeProperties = ref([])
const availableTargetIndividuals = ref([])

// 属性管理
const propertySearchText = ref('')
const propertyTypeFilter = ref(undefined)
const propertyLoading = ref(false)
const propertyData = ref([])
const propertyPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})
const propertyColumns = [
  { title: '属性名', dataIndex: 'name', key: 'name', width: 200 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 120 },
  { title: '定义域', dataIndex: 'domain', key: 'domain', width: 180 },
  { title: '值域', dataIndex: 'range', key: 'range', width: 180 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 150, fixed: 'right' }
]

// 可视化
const visualLayout = ref('force')
const visualChart = ref(null)
const visualChartInstance = ref(null)
const visualLoading = ref(false)
const visualData = ref({ nodes: [], edges: [] })
const visualStats = ref({
  nodeCount: 0,
  edgeCount: 0,
  classCount: 0,
  individualCount: 0,
  totalTriples: 0,
  truncated: false
})

// 关系查看
const relationModalVisible = ref(false)
const relationLoading = ref(false)
const relationActiveKey = ref('object')
const currentIndividual = ref(null)
const objectRelations = ref([])
const datatypeRelations = ref([])
const inverseRelations = ref([])

const relationColumns = [
  { title: '属性名', dataIndex: 'propertyName', key: 'propertyName', width: 200 },
  { title: '目标实例', dataIndex: 'targetName', key: 'target', width: 200 },
  { title: '目标URI', dataIndex: 'targetUri', key: 'targetUri', ellipsis: true }
]

const datatypeColumns = [
  { title: '属性名', dataIndex: 'propertyName', key: 'propertyName', width: 200 },
  { title: '属性值', dataIndex: 'value', key: 'value', ellipsis: true },
  { title: '数据类型', dataIndex: 'datatype', key: 'datatype', width: 120 }
]

const inverseColumns = [
  { title: '源实例', dataIndex: 'sourceName', key: 'source', width: 200 },
  { title: '关系属性', dataIndex: 'propertyName', key: 'propertyName', width: 200 },
  { title: '源URI', dataIndex: 'sourceUri', key: 'sourceUri', ellipsis: true }
]

// 弹窗控制
const importModalVisible = ref(false)
const exportModalVisible = ref(false)
const classModalVisible = ref(false)
const isEditClass = ref(false)
const propertyModalVisible = ref(false)
const isEditProperty = ref(false)
const newVersion = ref('')
const versionRemark = ref('')
const exportFormat = ref('OWL')

// 类表单
const classFormRef = ref(null)
const classForm = reactive({
  id: null,
  name: '',
  parentId: undefined,
  description: ''
})
const classRules = {
  name: [{ required: true, message: '请输入类名', trigger: 'blur' }]
}
const classTreeData = ref([])

// 属性表单
const propertyFormRef = ref(null)
const propertyForm = reactive({
  id: null,
  name: '',
  type: 'object',
  domain: undefined,
  range: undefined,
  description: ''
})
const propertyRules = {
  name: [{ required: true, message: '请输入属性名', trigger: 'blur' }],
  type: [{ required: true, message: '请选择属性类型', trigger: 'change' }]
}

onMounted(() => {
  ontologyId.value = route.params.id
  loadOntologyDetail()
  loadClassData()
  loadIndividualData()
  loadPropertyData()
  loadStats()
  // 进页面就在后台把可视化数据拉下来（接口约 18s，等用户切 tab 时已经就绪）
  loadVisualizationData()
})

// 监听筛选条件变化
watch(individualClassFilter, () => {
  loadIndividualData()
})

watch(propertyTypeFilter, () => {
  loadPropertyData()
})

// 加载本体详情
async function loadOntologyDetail() {
  if (!ontologyId.value) {
    message.error('本体ID不能为空')
    return
  }
  try {
    const res = await getOntologyById(ontologyId.value)
    if (res?.data) {
      const data = res.data
      ontologyName.value = data.projectName || data.ontologyName || '未命名本体'
      creatorName.value = data.creator || data.creatorName || '-'
      version.value = data.versionNumber || data.version || '-'
      versionStatus.value = data.versionStatus || 1
      namespaceUri.value = data.namespaceUri || ''
      updateTime.value = data.modifyTime || data.updateTime || '-'
    }
  } catch (e) {
    message.error(`加载本体详情失败：${e.message}`)
  }
}

// 加载统计数据
async function loadStats() {
  try {
    const res = await getOntologyStats(ontologyId.value)
    if (res?.data) {
      statData.classCount = res.data.classCount || 0
      statData.individualCount = res.data.individualCount || 0
      statData.propertyCount = res.data.propertyCount || 0
      statData.tripleCount = res.data.tripleCount || 0
    }
  } catch (e) {
    console.error('加载统计数据失败:', e)
    // 使用默认值
    statData.classCount = 0
    statData.individualCount = 0
    statData.propertyCount = 0
    statData.tripleCount = 0
  }
}

// 加载类数据
async function loadClassData() {
  classLoading.value = true
  try {
    const keyword = classSearchText.value?.trim() || undefined
    const res = await getOntologyClasses(ontologyId.value, keyword)
    // 字段映射：后端 uri -> 前端 id
    classData.value = (res?.data || []).map(item => ({
      id: item.id || item.uri,
      name: item.name,
      uri: item.uri,
      parentId: item.parentId,
      parentName: item.parentName || 'Thing',
      description: item.description,
      individualCount: item.individualCount || 0
    }))
    classPagination.total = classData.value.length
    
    // 更新类选项（用于实例筛选）
    classOptions.value = classData.value.map(cls => ({
      value: cls.id,
      label: cls.name
    }))
    
    // 更新树形数据（用于父类选择）
    // 简单处理：平铺结构，实际应该构建树形结构
    classTreeData.value = [
      {
        title: 'Thing (根类)',
        value: 'http://www.w3.org/2002/07/owl#Thing',
        key: 'http://www.w3.org/2002/07/owl#Thing',
        children: classData.value.map(cls => ({
          title: cls.name,
          value: cls.id,
          key: cls.id
        }))
      }
    ]
  } catch (e) {
    message.error(`加载类数据失败：${e.message}`)
    classData.value = []
    classPagination.total = 0
    classOptions.value = []
    classTreeData.value = []
  } finally {
    classLoading.value = false
  }
}

// 保存实例防抖锁
let isSavingIndividual = false

// 加载实例数据（带防抖锁）
let isLoadingIndividual = false
async function loadIndividualData() {
  if (isLoadingIndividual) return // 防止重复请求
  isLoadingIndividual = true
  individualLoading.value = true
  
  try {
    const classUri = individualClassFilter.value || undefined
    const keyword = individualSearchText.value?.trim() || undefined
    console.log('加载实例数据, ontologyId:', ontologyId.value, 'classUri:', classUri, 'keyword:', keyword)
    const res = await getOntologyIndividuals(ontologyId.value, classUri, keyword)
    console.log('实例数据响应:', res)
    // 字段映射 - 确保ID唯一
    const dataMap = new Map()
    ;(res?.data || []).forEach(item => {
      const id = item.id || item.uri
      dataMap.set(id, {
        id: id,
        name: item.name,
        uri: item.uri,
        classId: item.classId,
        className: item.className,
        description: item.description,
        createTime: item.createTime,
        properties: item.properties
      })
    })
    individualData.value = Array.from(dataMap.values())
    console.log('映射后实例数据:', individualData.value)
    individualPagination.total = individualData.value.length
  } catch (e) {
    console.error('加载实例数据失败:', e)
    message.error(`加载实例数据失败：${e.message}`)
    individualData.value = []
    individualPagination.total = 0
  } finally {
    individualLoading.value = false
    isLoadingIndividual = false
  }
}

// 加载属性数据
async function loadPropertyData() {
  propertyLoading.value = true
  try {
    const type = propertyTypeFilter.value || undefined
    const keyword = propertySearchText.value?.trim() || undefined
    console.log('加载属性数据, ontologyId:', ontologyId.value, 'type:', type, 'keyword:', keyword)
    const res = await getOntologyProperties(ontologyId.value, type, keyword)
    console.log('属性数据响应:', res)
    // 字段映射
    propertyData.value = (res?.data || []).map(item => ({
      id: item.id || item.uri,
      name: item.name,
      uri: item.uri,
      type: item.type,
      domain: item.domain,
      range: item.range,
      description: item.description
    }))
    console.log('映射后属性数据:', propertyData.value)
    propertyPagination.total = propertyData.value.length
  } catch (e) {
    console.error('加载属性数据失败:', e)
    message.error(`加载属性数据失败：${e.message}`)
    propertyData.value = []
    propertyPagination.total = 0
  } finally {
    propertyLoading.value = false
  }
}

// 操作函数
function goBack() {
  router.push({ name: 'OntologyManagement' })
}

function handleImportToGraph() {
  newVersion.value = ''
  versionRemark.value = ''
  importModalVisible.value = true
}

async function confirmImport() {
  if (!newVersion.value.trim()) {
    message.warning('请输入新版本号')
    return
  }
  try {
    const data = {
      sourceOntologyId: parseInt(ontologyId.value),
      newVersion: newVersion.value.trim(),
      remark: versionRemark.value
    }
    const res = await importOntologyToHouse(data)
    if (res.code === 200) {
      message.success('入库成功')
      importModalVisible.value = false
      loadOntologyDetail()
    } else {
      message.error(res.message || '入库失败')
    }
  } catch (e) {
    message.error(`入库失败：${e.message}`)
  }
}

function handleExport() {
  exportFormat.value = 'OWL'
  exportModalVisible.value = true
}

async function confirmExport() {
  try {
    const loading = message.loading('正在导出...', 0)
    const res = await exportOntologyFile(parseInt(ontologyId.value), exportFormat.value)
    loading()
    
    const blobData = res.data || res
    const blob = new Blob([blobData], { type: 'application/octet-stream' })
    
    const extMap = {
      'OWL': '.owl',
      'RDF': '.rdf',
      'Turtle': '.ttl',
      'N-Triples': '.nt'
    }
    const ext = extMap[exportFormat.value] || '.owl'
    const fileName = ontologyName.value + '_' + version.value + ext
    
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(downloadUrl)
    
    message.success('导出成功')
    exportModalVisible.value = false
  } catch (e) {
    message.error(`导出失败：${e.message}`)
  }
}

function handleDelete() {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除本体 "${ontologyName.value}" 吗？此操作不可恢复。`,
    okText: '删除',
    okType: 'danger',
    onOk: () => {
      message.success('删除成功')
      goBack()
    }
  })
}

// 类管理函数
function handleAddClass() {
  isEditClass.value = false
  classForm.id = null
  classForm.name = ''
  classForm.parentId = undefined
  classForm.description = ''
  classModalVisible.value = true
}

function handleEditClass(record) {
  isEditClass.value = true
  classForm.id = record.id
  classForm.name = record.name
  classForm.parentId = record.parentId
  classForm.description = record.description
  classModalVisible.value = true
}

async function handleSaveClass() {
  try {
    await classFormRef.value.validate()
    
    const classInfo = {
      name: classForm.name,
      parentId: classForm.parentId,
      description: classForm.description
    }
    
    if (isEditClass.value) {
      // 更新类
      await updateOntologyClass(ontologyId.value, classForm.id, classInfo)
      message.success('编辑成功')
    } else {
      // 创建类
      await createOntologyClass(ontologyId.value, classInfo)
      message.success('新增成功')
    }
    
    classModalVisible.value = false
    loadClassData()
    loadStats() // 刷新统计数据
  } catch (e) {
    message.error(`保存失败：${e.message}`)
  }
}

async function handleDeleteClass(record) {
  try {
    await deleteOntologyClass(ontologyId.value, record.id)
    message.success(`删除类 "${record.name}" 成功`)
    loadClassData()
    loadStats() // 刷新统计数据
  } catch (e) {
    message.error(`删除失败：${e.message}`)
  }
}

function handleViewIndividuals(record) {
  activeKey.value = 'individual'
  individualClassFilter.value = record.id
}

function handleSearchClass() {
  loadClassData()
}

// 实例管理函数
function handleAddIndividual() {
  isEditIndividual.value = false
  individualForm.id = null
  individualForm.name = ''
  individualForm.classId = undefined
  individualForm.description = ''
  individualForm.objectProperties = []
  individualForm.datatypeProperties = []
  availableObjectProperties.value = []
  availableDatatypeProperties.value = []
  availableTargetIndividuals.value = []
  individualModalVisible.value = true
}

async function handleEditIndividual(record) {
  isEditIndividual.value = true
  individualForm.id = record.id
  individualForm.name = record.name
  individualForm.classId = record.classId
  individualForm.description = record.description
  individualForm.objectProperties = []
  individualForm.datatypeProperties = []
  
  // 先加载可用属性列表
  await onClassChange(record.classId)
  
  // 加载该实例现有的关系数据到表单
  if (record.uri) {
    try {
      const res = await getIndividualRelations(ontologyId.value, record.uri)
      if (res?.data) {
        // 填充对象属性到表单
        individualForm.objectProperties = res.data.objectProperties?.map(p => ({
          propertyUri: p.propertyUri,
          targetUri: p.targetUri
        })) || []
        
        // 填充数据属性到表单
        individualForm.datatypeProperties = res.data.datatypeProperties?.map(p => ({
          propertyUri: p.propertyUri,
          value: p.value
        })) || []
      }
    } catch (e) {
      console.error('加载实例关系失败:', e)
    }
  }
  
  individualModalVisible.value = true
}

// 当选择类变化时，加载该类的属性定义
async function onClassChange(classId) {
  if (!classId) {
    availableObjectProperties.value = []
    availableDatatypeProperties.value = []
    return
  }
  
  // 从属性数据中筛选适用于该类的属性
  // 简化处理：显示所有对象属性和数据属性
  availableObjectProperties.value = propertyData.value
    .filter(p => p.type === 'object')
    .map(p => ({ uri: p.uri, name: p.name }))
  
  availableDatatypeProperties.value = propertyData.value
    .filter(p => p.type === 'datatype')
    .map(p => ({ uri: p.uri, name: p.name }))
  
  // 加载可作为目标的其他实例
  availableTargetIndividuals.value = individualData.value
    .filter(ind => ind.id !== individualForm.id) // 排除自己
    .map(ind => ({ uri: ind.uri, name: ind.name }))
}

function addObjectProperty() {
  individualForm.objectProperties.push({
    propertyUri: undefined,
    targetUri: undefined
  })
}

function removeObjectProperty(index) {
  individualForm.objectProperties.splice(index, 1)
}

function addDatatypeProperty() {
  individualForm.datatypeProperties.push({
    propertyUri: undefined,
    value: ''
  })
}

function removeDatatypeProperty(index) {
  individualForm.datatypeProperties.splice(index, 1)
}

function filterIndividualOption(input, option) {
  return option.children.toLowerCase().includes(input.toLowerCase())
}

async function handleSaveIndividual() {
  if (isSavingIndividual) return
  isSavingIndividual = true
  
  try {
    await individualFormRef.value.validate()
    
    // 构建属性映射
    const properties = {}
    
    // 添加对象属性（目标实例URI作为值）
    individualForm.objectProperties.forEach(prop => {
      if (prop.propertyUri && prop.targetUri) {
        properties[prop.propertyUri] = prop.targetUri
      }
    })
    
    // 添加数据属性
    individualForm.datatypeProperties.forEach(prop => {
      if (prop.propertyUri && prop.value) {
        properties[prop.propertyUri] = prop.value
      }
    })
    
    const individualInfo = {
      name: individualForm.name,
      classId: individualForm.classId,
      description: individualForm.description,
      properties: properties
    }
    
    if (isEditIndividual.value) {
      // 更新实例
      await updateOntologyIndividual(ontologyId.value, individualForm.id, individualInfo)
      message.success('编辑成功')
    } else {
      // 创建实例
      await createOntologyIndividual(ontologyId.value, individualInfo)
      message.success('新增成功')
    }
    
    individualModalVisible.value = false
    loadIndividualData()
    loadStats() // 刷新统计数据
  } catch (e) {
    message.error(`保存失败：${e.message}`)
  } finally {
    isSavingIndividual = false
  }
}

async function handleDeleteIndividual(record) {
  try {
    await deleteOntologyIndividual(ontologyId.value, record.id)
    message.success(`删除实例 "${record.name}" 成功`)
    loadIndividualData()
    loadStats() // 刷新统计数据
  } catch (e) {
    message.error(`删除失败：${e.message}`)
  }
}

async function handleViewRelations(record) {
  currentIndividual.value = record
  relationModalVisible.value = true
  relationLoading.value = true
  relationActiveKey.value = 'object'
  
  // 清空旧数据
  objectRelations.value = []
  datatypeRelations.value = []
  inverseRelations.value = []
  
  try {
    // 从后端获取最新关系数据
    await loadIndividualRelations(record)
  } catch (e) {
    message.error(`加载关系数据失败：${e.message}`)
  } finally {
    relationLoading.value = false
  }
}

async function loadIndividualRelations(individual) {
  // 调用后端API获取真实数据
  const res = await getIndividualRelations(ontologyId.value, individual.uri)
  if (res?.data) {
    objectRelations.value = res.data.objectProperties || []
    datatypeRelations.value = res.data.datatypeProperties || []
    inverseRelations.value = res.data.inverseRelations || []
  } else {
    objectRelations.value = []
    datatypeRelations.value = []
    inverseRelations.value = []
  }
}

function handleSearchIndividual() {
  loadIndividualData()
}

// 属性管理函数
function handleAddProperty() {
  isEditProperty.value = false
  propertyForm.id = null
  propertyForm.name = ''
  propertyForm.type = 'object'
  propertyForm.domain = undefined
  propertyForm.range = undefined
  propertyForm.description = ''
  propertyModalVisible.value = true
}

function handleEditProperty(record) {
  isEditProperty.value = true
  propertyForm.id = record.id
  propertyForm.name = record.name
  propertyForm.type = record.type || 'object'
  propertyForm.domain = record.domain
  propertyForm.range = record.range
  propertyForm.description = record.description
  propertyModalVisible.value = true
}

async function handleSaveProperty() {
  try {
    await propertyFormRef.value.validate()
    
    const propertyInfo = {
      name: propertyForm.name,
      type: propertyForm.type,
      domain: propertyForm.domain,
      range: propertyForm.range,
      description: propertyForm.description
    }
    
    if (isEditProperty.value) {
      // 更新属性
      await updateOntologyProperty(ontologyId.value, propertyForm.id, propertyInfo)
      message.success('编辑属性成功')
    } else {
      // 创建属性
      await createOntologyProperty(ontologyId.value, propertyInfo)
      message.success('新增属性成功')
    }
    
    propertyModalVisible.value = false
    loadPropertyData()
  } catch (e) {
    message.error(`保存失败：${e.message}`)
  }
}

async function handleDeleteProperty(record) {
  try {
    await deleteOntologyProperty(ontologyId.value, record.id)
    message.success(`删除属性 "${record.name}" 成功`)
    loadPropertyData()
  } catch (e) {
    message.error(`删除失败：${e.message}`)
  }
}

function handleSearchProperty() {
  loadPropertyData()
}

function getPropertyTypeColor(type) {
  const colorMap = {
    'object': 'blue',
    'datatype': 'green',
    'annotation': 'orange'
  }
  return colorMap[type] || 'default'
}

function getPropertyTypeText(type) {
  const textMap = {
    'object': '对象属性',
    'datatype': '数据属性',
    'annotation': '注释属性'
  }
  return textMap[type] || type
}

// 可视化函数
async function handleRefreshVisual() {
  await loadVisualizationData(true)
}

/**
 * 拉取并渲染本体可视化数据。
 *
 * @param {boolean} forceRender 为 true 时忽略当前 tab 直接渲染（点「刷新」按钮用）
 *
 * 关键点：数据加载与「渲染」解耦。接口要跑全图扫描，首次约十几秒，所以进页面
 * 就在后台先把它拉下来；而 ECharts 渲染必须等容器可见（隐藏 tab 的 clientWidth
 * 为 0，init 会失败），因此只有本 tab 处于激活状态时才 renderChart()。
 */
async function loadVisualizationData(forceRender = false) {
  if (!ontologyId.value || visualLoading.value) return

  visualLoading.value = true
  try {
    const res = await getOntologyVisualization(ontologyId.value)
    if (res?.data) {
      // 数据验证和清理
      let nodes = res.data.nodes || []
      let edges = res.data.edges || []
      
      // 过滤无效节点
      nodes = nodes.filter(n => n && n.id && typeof n.id === 'string')
      
      // 去重节点（按 id）
      const nodeMap = new Map()
      nodes.forEach(n => {
        if (!nodeMap.has(n.id)) {
          nodeMap.set(n.id, n)
        }
      })
      nodes = Array.from(nodeMap.values())
      
      // 获取有效的节点 ID 集合
      const validNodeIds = new Set(nodes.map(n => n.id))
      
      // 过滤无效边（确保 source 和 target 都存在且有效）
      edges = edges.filter(e => {
        if (!e || !e.source || !e.target) return false
        // 确保 source 和 target 都是字符串
        const source = String(e.source)
        const target = String(e.target)
        return validNodeIds.has(source) && validNodeIds.has(target)
      })
      
      // 去重边（按 source+target+label）
      const edgeMap = new Map()
      edges.forEach(e => {
        const key = `${e.source}|${e.target}|${e.label || ''}`
        if (!edgeMap.has(key)) {
          edgeMap.set(key, e)
        }
      })
      edges = Array.from(edgeMap.values())
      
      visualData.value = { nodes, edges }
      
      // 统计（totalTriples / truncated 来自后端 statistics，图是抽样时如实告知）
      const st = res.data.statistics || {}
      visualStats.value = {
        nodeCount: nodes.length,
        edgeCount: edges.length,
        classCount: nodes.filter(n => n.type === 'class').length,
        individualCount: nodes.filter(n => n.type === 'individual').length,
        totalTriples: st.totalTriples || 0,
        truncated: !!st.truncated
      }
      
      // 渲染图表：只在「本 tab 可见」或显式要求渲染时进行
      if (nodes.length > 0 && (forceRender || activeKey.value === 'visual')) {
        await nextTick()
        renderChart()
      } else if (nodes.length === 0) {
        message.warning('没有有效的可视化数据')
      }
    } else {
      message.warning('可视化数据为空')
    }
  } catch (e) {
    console.error('加载可视化数据失败:', e)
    message.error(`加载可视化数据失败：${e.message}`)
  } finally {
    visualLoading.value = false
  }
}

/** renderChart 因容器不可见而重试的次数（每次 100ms，最多 ~5s） */
let renderRetry = 0

function renderChart() {
  if (!visualChart.value) {
    message.error('图表容器未找到')
    return
  }

  // 检查容器尺寸。隐藏的 tab 里 clientWidth/Height 为 0，
  // 此时 echarts.init 会得到 0×0 画布，必须等容器可见再渲染。
  const width = visualChart.value.clientWidth
  const height = visualChart.value.clientHeight

  if (width === 0 || height === 0) {
    if (renderRetry < 50) {
      renderRetry += 1
      setTimeout(renderChart, 100)
    }
    return
  }
  renderRetry = 0

  // 如果已有实例，先销毁
  if (visualChartInstance.value) {
    visualChartInstance.value.dispose()
  }

  // 初始化 ECharts
  visualChartInstance.value = echarts.init(visualChart.value)
  
  let nodes = visualData.value.nodes || []
  let edges = visualData.value.edges || []
  
  // 数据量太大时进行采样优化（只显示前50个）
  if (nodes.length > 50) {
    console.warn(`节点数量过多(${nodes.length})，只显示前 50 个`)
    message.warning(`节点数量过多(${nodes.length})，只显示前 50 个`)
    
    // 保留所有类节点，再补充实例节点到50个
    const classNodes = nodes.filter(n => n.type === 'class')
    const individualNodes = nodes.filter(n => n.type !== 'class')
    
    // 优先保留类节点，然后补充实例节点到50个
    const keepCount = 50 - classNodes.length
    const sampledIndividuals = individualNodes.slice(0, Math.max(keepCount, 10))
    nodes = [...classNodes, ...sampledIndividuals]
    
    // 只保留相关边
    const nodeIds = new Set(nodes.map(n => n.id))
    edges = edges.filter(e => nodeIds.has(e.source) && nodeIds.has(e.target))
    
    console.log('采样后:', nodes.length, 'nodes,', edges.length, 'edges')
  }
  
  // 转换数据格式（确保 ID 唯一且为字符串，并解码中文）
  const chartNodes = nodes.map((node, index) => ({
    id: String(node.id),
    name: decodeUri(node.label || node.name || String(node.id)),
    value: node.size || 50,
    symbolSize: node.size || 50,
    category: node.type === 'class' ? 0 : 1,
    type: node.type || 'individual',
    itemStyle: {
      color: node.color || (node.type === 'class' ? '#1890ff' : '#52c41a')
    }
  }))
  
  const chartEdges = edges.map(edge => ({
    source: String(edge.source),
    target: String(edge.target),
    value: edge.label || '',
    label: {
      show: true,
      formatter: edge.label || ''
    },
    lineStyle: {
      color: edge.color || '#999',
      width: 2
    }
  }))
  
  // 布局配置
  let layoutOption = {}
  if (visualLayout.value === 'force') {
    layoutOption = {
      layout: 'force',
      force: {
        repulsion: 300,
        edgeLength: 150,
        gravity: 0.1
      }
    }
  } else if (visualLayout.value === 'circular') {
    layoutOption = {
      layout: 'circular',
      circular: {
        rotateLabel: true
      }
    }
  } else if (visualLayout.value === 'hierarchical') {
    layoutOption = {
      layout: 'none'
    }
    // 层次布局需要计算位置
    const level0Nodes = chartNodes.filter(n => n.level === 0)
    const level1Nodes = chartNodes.filter(n => n.level === 1)
    const centerX = visualChart.value.clientWidth / 2
    const centerY = visualChart.value.clientHeight / 2
    
    level0Nodes.forEach((node, i) => {
      node.x = centerX + (i - level0Nodes.length / 2) * 200
      node.y = centerY - 150
    })
    level1Nodes.forEach((node, i) => {
      node.x = centerX + (i - level1Nodes.length / 2) * 150
      node.y = centerY + 150
    })
  }
  
  const option = {
    title: {
      text: ontologyName.value,
      subtext: `版本: ${version.value}`,
      left: 'center'
    },
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<strong>${params.name}</strong><br/>类型: ${params.data.type === 'class' ? '类' : '实例'}`
        } else {
          return `<strong>${params.data.label}</strong><br/>${params.data.source} → ${params.data.target}`
        }
      }
    },
    legend: {
      data: ['类', '实例'],
      left: 'left'
    },
    series: [{
      type: 'graph',
      roam: true,
      draggable: true,
      label: {
        show: true,
        position: 'bottom',
        formatter: '{b}'
      },
      edgeSymbol: ['circle', 'arrow'],
      edgeSymbolSize: [4, 10],
      data: chartNodes,
      links: chartEdges,
      categories: [
        { name: '类', itemStyle: { color: '#1890ff' } },
        { name: '实例', itemStyle: { color: '#52c41a' } }
      ],
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 4
        }
      },
      ...layoutOption
    }]
  }
  
  console.log('设置 ECharts 配置...', option)
  try {
    visualChartInstance.value.setOption(option)
    console.log('图表渲染完成')
  } catch (e) {
    console.error('图表渲染失败:', e)
    message.error(`图表渲染失败: ${e.message}`)
  }
  
  // 点击事件
  visualChartInstance.value.on('click', (params) => {
    if (params.dataType === 'node') {
      console.log('点击节点:', params.data)
      message.info(`选中: ${params.name} (${params.data.type})`)
    }
  })
}

function handleLayoutChange() {
  renderChart()
}

function handleExportVisual() {
  if (!visualChartInstance.value) {
    message.warning('请先加载可视化数据')
    return
  }
  const url = visualChartInstance.value.getDataURL({
    type: 'png',
    backgroundColor: '#fff'
  })
  const link = document.createElement('a')
  link.download = `${ontologyName.value}_可视化.png`
  link.href = url
  link.click()
}

// 监听 Tab 切换到可视化：
//   · 数据已在后台预加载完 → 立即渲染（无需再等接口）
//   · 还在加载中 → 什么都不做，等 loadVisualizationData 完成后自行渲染
watch(activeKey, async (newKey) => {
  if (newKey !== 'visual') return
  if (visualData.value.nodes.length > 0) {
    await nextTick()
    renderChart()
  } else if (!visualLoading.value) {
    loadVisualizationData(true)
  }
})
</script>

<style scoped>
.container {
  padding: 16px;
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.header-left {
  flex: 1;
}

.back-btn {
  padding-left: 0;
  margin-bottom: 8px;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.project-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.project-meta {
  display: flex;
  gap: 24px;
  color: #666;
  font-size: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-row {
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 8px;
  transition: all 0.3s;
}

.stat-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.09);
}

.stat-card :deep(.ant-card-body) {
  display: flex;
  align-items: center;
  padding: 16px;
  width: 100%;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
  flex-shrink: 0;
}

.stat-icon.blue {
  background: #e6f7ff;
  color: #1890ff;
}

.stat-icon.green {
  background: #f6ffed;
  color: #52c41a;
}

.stat-icon.orange {
  background: #fff7e6;
  color: #fa8c16;
}

.stat-icon.purple {
  background: #f9f0ff;
  color: #722ed1;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #262626;
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #8c8c8c;
}

.content-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.tab-content {
  padding: 20px;
  background: #fafafa;
  min-height: 400px;
}

.visual-content {
  padding: 0;
  background: transparent;
}

.toolbar {
  margin-bottom: 16px;
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.visual-toolbar {
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
}

.visual-container {
  min-height: 500px;
  background: #f5f5f5;
}

.visual-placeholder {
  padding: 100px 20px;
  text-align: center;
}

.ellipsis-text {
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-container {
  min-height: 300px;
}

.relation-stats {
  margin-bottom: 16px;
  padding: 16px;
  background: #f5f5f5;
  border-radius: 8px;
}

.datatype-value {
  color: #1890ff;
  font-weight: 500;
}

.property-row {
  margin-bottom: 8px;
}

.property-row :deep(.ant-select) {
  width: 100%;
}

.visual-stats {
  padding: 12px 20px;
  background: #f5f5f5;
  border-bottom: 1px solid #e8e8e8;
}

.visual-stats strong {
  color: #1890ff;
  font-size: 16px;
}

/* 图是抽样展示时给个提示，避免误以为是全量 */
.visual-truncated {
  color: #d46b08;
  font-size: 13px;
}

.visual-empty {
  padding: 40px 0;
  text-align: center;
  color: #999;
}
</style>
