<template>
  <main class="dashboard-wrap">
    <!-- 当不显示大屏时显示仪表板内容 -->
    <div v-if="!showReportModal">
      <header class="header">
        <div class="header-left">
          <h2>中哈贸易数据看板</h2>
          <span class="sub">实时更新 · 最后刷新：{{ now }}</span>
        </div>
        <div class="header-actions">
          <a-button @click="goToAjReportPage">
            <template #icon>
              <AppstoreOutlined />
            </template>
            AJ-Report 入口
          </a-button>
          <a-button type="primary" @click="showReportModal = true">
            <template #icon>
              <EyeOutlined />
            </template>
            可视化大屏
          </a-button>
        </div>
      </header>

      <!-- KPI 卡片 -->
      <a-row :gutter="16" class="kpi-row">
        <a-col :span="6" v-for="k in kpiList" :key="k.title">
          <div class="kpi-card">
            <div class="value">{{ k.value }}</div>
            <div class="unit">{{ k.unit }}</div>
            <div class="title">{{ k.title }}</div>
          </div>
        </a-col>
      </a-row>

      <!-- 图表区 -->
      <a-row :gutter="16" class="chart-row">
        <!-- 1. 地区分布 -->
        <a-col :span="8">
          <div class="chart-box">
            <div class="chart-title">地区贸易分布</div>
            <div id="pie-region" class="chart"></div>
          </div>
        </a-col>

        <!-- 2. 模块数据占比 -->
        <a-col :span="8">
          <div class="chart-box">
            <div class="chart-title">语料管理-不同年份语料条数</div>
            <div id="ring-module" class="chart"></div>
          </div>
        </a-col>

        <!-- 3. 贸易方式（近6个月柱状图） -->
        <a-col :span="8">
          <div class="chart-box">
            <div class="chart-title">数据管理-贸易方式统计</div>
            <div id="bar-trade" class="chart"></div>
          </div>
        </a-col>
      </a-row>

      <!-- 底部：近12个月进出口量&金额折线图 -->
      <div class="chart-box">
        <div class="chart-title">近12个月进出口量 & 金额</div>
        <div id="line-io" class="chart"></div>
      </div>
    </div>

    <!-- AJ-Report大屏全屏容器 -->
    <div v-if="showReportModal" class="fullscreen-report">
      <div class="fullscreen-header">
        <div class="screen-switcher">
          <a-button-group>
            <a-button 
              :type="currentReportUrl === reportUrls.screen1 ? 'primary' : 'default'"
              @click="currentReportUrl = reportUrls.screen1">
              大屏1
            </a-button>
            <a-button 
              :type="currentReportUrl === reportUrls.screen2 ? 'primary' : 'default'"
              @click="currentReportUrl = reportUrls.screen2">
              大屏2
            </a-button>
          </a-button-group>
        </div>
        <a-button type="primary" @click="showReportModal = false">
          <template #icon>
            <CloseOutlined />
          </template>
          关闭大屏
        </a-button>
      </div>
      <div class="fullscreen-iframe-container">
        <iframe 
          :src="currentReportUrl" 
          frameborder="0" 
          width="100%" 
          height="100%"
          :title="currentReportUrl === reportUrls.screen1 ? '大屏1' : '大屏2'"
          allowfullscreen>
        </iframe>
      </div>
    </div>
  </main>
</template>

<script setup>
/* ==================  仅替换这段 script setup  ================== */
import { ref, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { EyeOutlined, CloseOutlined, AppstoreOutlined } from '@ant-design/icons-vue'
import {
  getCurrentComprehensive,
  getCountryRatio,
  getMethodRatio,
  getLast12Months,
  getYearlyCorpus
} from '@/api/TradeDashboard'
import { getAjReportConfig } from '@/api/ajReport'

/* ---------- 1 基础 ---------- */
const now = ref(dayjs().format('YYYY-MM-DD HH:mm'))
const showReportModal = ref(false)
const router = useRouter()

const reportUrls = {
  screen1: '',
  screen2: ''
}
const currentReportUrl = ref('')

async function loadAjReportUrls() {
  try {
    const config = await getAjReportConfig()
    reportUrls.screen1 = config?.screen1Url || config?.targetUrl || ''
    reportUrls.screen2 = config?.screen2Url || config?.targetUrl || ''
    currentReportUrl.value = reportUrls.screen1 || reportUrls.screen2
  } catch (error) {
    console.error('加载 AJ-Report 配置失败:', error)
  }
}

function goToAjReportPage() {
  router.push('/aj-report')
}

/* ---------- 2 响应式数据 ---------- */
const kpiList        = ref([])          // KPI 卡片
const countryRatio   = ref([])          // 地区分布饼图
const methodRatio    = ref([])          // 贸易方式横向柱图
const ioData         = ref({})          // 近 12 个月折线
const corpusByYear   = ref([])          // 语料年份柱图

/* ---------- 3 图表实例 ---------- */
let pieChart, barChart, lineIoChart, ringChart

/* ---------- 4 统一拉数据 ---------- */
async function loadData() {
  const currentYear = dayjs().year()
  const currentMonth = dayjs().month() + 1

  try {
    // 1. 综合数据 -> KPI
    const compRes = await getCurrentComprehensive()
    console.log('综合数据响应:', compRes)
    
    // 处理响应数据（可能是直接对象或包装在 data 中）
    const comp = compRes?.data || compRes
    
    kpiList.value = [
      { title: '语料条目', value: (comp?.corpusEntryCount || 0).toLocaleString(), unit: '条' },
      { title: '数据条目', value: (comp?.dataEntryCount || 0).toLocaleString(), unit: '条' },
      { title: '问答访问', value: (comp?.queryVisitCount || 0).toLocaleString(), unit: '次' },
      { title: '贸易国家', value: comp?.tradeCountryCount || 0, unit: '个' }
    ]
    console.log('KPI数据:', kpiList.value)

    // 2. 国家占比 -> 饼图
    const countryRes = await getCountryRatio(currentYear, currentMonth)
    countryRatio.value = countryRes?.data || countryRes || []

    // 3. 贸易方式 -> 横向柱图
    const methodRes = await getMethodRatio(currentYear, currentMonth)
    methodRatio.value = methodRes?.data || methodRes || []

    // 4. 近 12 个月走势 -> 折线
    const last12Res = await getLast12Months()
    const tmp = last12Res?.data || last12Res || []
    ioData.value = {
      month:       tmp.map(i => `${i.year}-${String(i.month).padStart(2, '0')}`),
      importQty:   tmp.map(i => i.importQuantity),
      exportQty:   tmp.map(i => i.exportQuantity),
      importAmt:   tmp.map(i => i.importAmount),
      exportAmt:   tmp.map(i => i.exportAmount)
    }

    // 5. 历年语料 -> 柱图
    const corpusRes = await getYearlyCorpus()
    const corpus = corpusRes?.data || corpusRes || []
    corpusByYear.value = corpus.sort((a, b) => a.year - b.year)
    
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

/* ---------- 5 绘图函数 ---------- */
function initPie() {
  pieChart = echarts.init(document.getElementById('pie-region'))
  // 后端返回 {country_name, total_amount}
  const data = countryRatio.value.map(r => ({ 
    name: r.country_name || r.country, 
    value: r.total_amount || r.amount 
  }))
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: ['#1890ff', '#36cbcb', '#facc14', '#2fc25b', '#f04864'],
    series: [{
      type: 'pie',
      radius: '60%',
      data: data
    }]
  })
}
function initRing() {
  ringChart = echarts.init(document.getElementById('ring-module'))
  const data = corpusByYear.value
  // 后端返回 {year, entryCount}
  ringChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 70, right: 20, top: 28, bottom: 40 },
    xAxis: { type: 'category', data: data.map(d => d.year + '年') },
    yAxis: { type: 'value', name: '条数', nameTextStyle: { fontSize: 12, align: 'right' }, nameGap: 15 },
    series: [{
      name: '语料条数',
      type: 'bar',
      data: data.map(d => d.entryCount || d.count),
      barWidth: '50%',
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', formatter: '{c}' }
    }]
  })
}
function drawBarTrade() {
  barChart = echarts.init(document.getElementById('bar-trade'))
  const data = methodRatio.value
  // 后端返回 {trade_method, total_amount}
  barChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 80, right: 40, top: 20, bottom: 20 },
    xAxis: { type: 'value', name: '数量(条)' },
    yAxis: {
      type: 'category',
      data: data.map(d => d.trade_method || d.method),
      axisLine: { show: false },
      axisTick: { show: false }
    },
    color: ['#1890ff', '#36cbcb', '#facc14', '#f04864'],
    series: [{
      name: '贸易方式',
      type: 'bar',
      data: data.map(d => d.total_amount || d.value),
      barWidth: 20,
      label: { show: true, position: 'right', formatter: '{c}' },
      itemStyle: { borderRadius: [0, 4, 4, 0] }
    }]
  })
}
function drawLineIo() {
  const { month, importQty, exportQty, importAmt, exportAmt } = ioData.value
  lineIoChart = echarts.init(document.getElementById('line-io'))
  lineIoChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: ['进口量', '出口量', '进口额', '出口额'] },
    grid: { left: 60, right: 60, top: 40, bottom: 80 },
    xAxis: {
      type: 'category',
      data: month,
      axisLabel: { rotate: 45, fontSize: 12, interval: 0 }
    },
    yAxis: [
      { type: 'value', name: '量(万吨)', position: 'left', nameTextStyle: { fontSize: 12 } },
      { type: 'value', name: '额(万美元)', position: 'right', nameTextStyle: { fontSize: 12 } }
    ],
    series: [
      { name: '进口量', type: 'line', smooth: true, data: importQty, itemStyle: { color: '#1890ff' }, lineStyle: { width: 3 } },
      { name: '出口量', type: 'line', smooth: true, data: exportQty, itemStyle: { color: '#36cbcb' }, lineStyle: { width: 3 } },
      { name: '进口额', type: 'line', smooth: true, yAxisIndex: 1, data: importAmt, itemStyle: { color: '#f04864' }, lineStyle: { width: 2, type: 'dashed' } },
      { name: '出口额', type: 'line', smooth: true, yAxisIndex: 1, data: exportAmt, itemStyle: { color: '#facc14' }, lineStyle: { width: 2, type: 'dashed' } }
    ]
  })
}

/* ---------- 6 初始化图表 ---------- */
async function initCharts() {
  try {
    await nextTick()
    // 销毁旧的图表实例
    pieChart?.dispose()
    ringChart?.dispose()
    barChart?.dispose()
    lineIoChart?.dispose()
    
    // 重新初始化图表
    initPie()
    initRing()
    drawBarTrade()
    drawLineIo()
  } catch (error) {
    console.error('初始化图表失败:', error)
  }
}

/* ---------- 7 生命周期 ---------- */
onMounted(async () => {
  try {
    await loadAjReportUrls()
    await loadData()
    await initCharts()
    window.addEventListener('resize', () => {
      pieChart?.resize()
      ringChart?.resize()
      barChart?.resize()
      lineIoChart?.resize()
    })
  } catch (error) {
    console.error('初始化数据看板失败:', error)
  }
})

/* ---------- 8 监听大屏状态变化 ---------- */
watch(showReportModal, async (newVal, oldVal) => {
  // 当从大屏模式切换回数据看板模式时
  if (oldVal === true && newVal === false) {
    try {
      // 重新加载数据
      await loadData()
      // 重新初始化图表
      await initCharts()
    } catch (error) {
      console.error('重新加载数据看板失败:', error)
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', () => {})
  pieChart?.dispose()
  ringChart?.dispose()
  barChart?.dispose()
  lineIoChart?.dispose()
})
</script>


<style scoped>
.dashboard-wrap{
  margin-left: 10px;
  padding: 24px 32px;
  background: #f5f7fa;
  min-height: 100vh;
}
.header{
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header-actions {
  display: flex;
  gap: 12px;
}
.header .sub{
  color: #666;
  font-size: 14px;
}
.kpi-row{
  margin-bottom: 20px;
}
.kpi-card{
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
}
.kpi-card .value{
  font-size: 28px;
  font-weight: 600;
  color: #1890ff;
}
.kpi-card .unit{
  font-size: 12px;
  color: #999;
  margin: 4px 0;
}
.kpi-card .title{
  font-size: 14px;
  color: #666;
}
.chart-row{
  margin-bottom: 20px;
}
.chart-box{
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
}
.chart-title{
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 12px;
  color: #333;
}
.chart{
  height: 280px;
}

/* 全屏大屏样式 */
.fullscreen-report {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: #fff;
  z-index: 9999;
  display: flex;
  flex-direction: column;
}

.fullscreen-header {
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.screen-switcher {
  display: flex;
  align-items: center;
}

.fullscreen-iframe-container {
  flex: 1;
  width: 100%;
  height: calc(100vh - 73px); /* 减去header高度 */
  background: #f5f7fa;
}

.fullscreen-iframe-container iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
