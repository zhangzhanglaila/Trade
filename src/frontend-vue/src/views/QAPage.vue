<template>
  <div class="qa-card">
    <div class="qa-header">
      <h2 class="qa-title">智能问答</h2>
      <a-space>
        <a-button @click="clearChat">清空对话</a-button>
      </a-space>
    </div>

    <a-collapse v-model:activeKey="collapseActive" class="qa-advanced">
      <a-collapse-panel key="advanced" header="高级参数（可选）">
        <a-form layout="inline" :model="ragForm" style="margin-bottom: 8px">
          <a-form-item label="国家(country)">
            <a-input v-model:value="ragForm.country" allow-clear style="width: 160px" />
          </a-form-item>
          <a-form-item label="年份(year)">
            <a-input-number v-model:value="ragForm.year" :min="1900" :max="2100" style="width: 120px" />
          </a-form-item>
        </a-form>

        <a-divider style="margin: 8px 0" />

        <a-form layout="inline" :model="predictForm">
          <a-form-item label="tradeType">
            <a-select v-model:value="predictForm.tradeType" allow-clear style="width: 120px">
              <a-select-option value="in">in</a-select-option>
              <a-select-option value="out">out</a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="target">
            <a-select v-model:value="predictForm.target" allow-clear style="width: 140px">
              <a-select-option value="price">price</a-select-option>
              <a-select-option value="quantity">quantity</a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="year">
            <a-input-number v-model:value="predictForm.year" :min="1900" :max="2100" style="width: 120px" />
          </a-form-item>

          <a-form-item label="month">
            <a-input-number v-model:value="predictForm.month" :min="1" :max="12" style="width: 100px" />
          </a-form-item>

          <a-form-item label="tradePartnerName">
            <a-input v-model:value="predictForm.tradePartnerName" allow-clear style="width: 200px" />
          </a-form-item>

          <a-form-item label="productName">
            <a-input v-model:value="predictForm.productName" allow-clear style="width: 200px" />
          </a-form-item>

          <a-form-item label="tradeMode">
            <a-input v-model:value="predictForm.tradeMode" allow-clear style="width: 180px" />
          </a-form-item>

          <a-form-item label="registerName">
            <a-input v-model:value="predictForm.registerName" allow-clear style="width: 180px" />
          </a-form-item>

          <a-form-item>
            <a-button @click="clearAdvanced">清空参数</a-button>
          </a-form-item>
        </a-form>
      </a-collapse-panel>
    </a-collapse>

    <div class="qa-body" ref="chatBodyRef">
      <div v-if="messages.length === 0" class="qa-empty">
        请输入问题开始对话。查历史数据（「2025年1月哈萨克斯坦的出口数量」）、
        预测未来（「预测…下个月进口单价」）、新闻问答（「最近有哪些关于哈萨克斯坦的新闻」）
        都会自动路由，不需要手动选。
      </div>

      <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
        <div class="bubble">
          <!--
            模型返回的是 Markdown 原文。此前直接 {{ }} 输出，页面上的
            `**加粗**`、`- 列表` 会原样露出来，既不美观也削弱可读性。
            这里统一走 renderMd()：markdown-it 解析 + DOMPurify 清洗。
          -->
          <div class="bubble-text md-body" v-html="renderMd(m.text)"></div>

          <div v-if="m.role === 'assistant' && m.data" class="bubble-extra">
            <a-divider style="margin: 8px 0" />

            <div class="meta">
              <a-tag :color="routeColor(m.data.route)">{{ routeLabel(m.data.route) }}</a-tag>
            </div>

            <!-- 历史数据查询（DATA_QUERY）：展示按月明细 -->
            <div v-if="m.data.route === 'DATA_QUERY' && (m.data.dataQuery?.rows || []).length">
              <div class="section-title">历史数据（按月倒序）</div>
              <a-table
                :columns="dataColumns"
                :data-source="m.data.dataQuery.rows"
                :pagination="false"
                size="small"
                row-key="ym"
              />
            </div>

            <!-- RAG 来源 -->
            <div v-else-if="m.data.route === 'RAG_NEWS'">
              <div class="section-title">来源（按相关度从高到低）</div>
              <div v-if="sourceNote(m)" class="section-note">{{ sourceNote(m) }}</div>
              <a-table
                :columns="sourceColumns"
                :data-source="primarySources(m)"
                :pagination="false"
                size="small"
                row-key="newsId"
              />
              <!--
                其余候选默认收在这个下拉面板里。
                一次问答最多能取回 20 条候选（ai.vector.candidate-topk），
                全铺出来会把对话区撑得很长，所以固定只展开前 5 条。
              -->
              <a-collapse v-if="restSources(m).length" ghost class="more-collapse">
                <a-collapse-panel key="rest" :header="restHeader(m)">
                  <a-table
                    :columns="sourceColumns"
                    :data-source="restSources(m)"
                    :pagination="false"
                    size="small"
                    row-key="newsId"
                  />
                </a-collapse-panel>
              </a-collapse>
            </div>

            <!--
              预测结果：只有真的算出来了才渲染这张表。
              此前无值时也渲染，页面出现 value=- / unit=- 的空表格，
              用户会以为「页面坏了」——实际上只是槽位没凑齐。
            -->
            <div v-else-if="m.data.route === 'PREDICT' && m.data.predictResult">
              <div class="section-title">预测结果</div>
              <a-descriptions size="small" bordered :column="1">
                <a-descriptions-item label="value">
                  {{ m.data.predictResult.value ?? '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="unit">
                  {{ m.data.predictResult.unit ?? '-' }}
                </a-descriptions-item>
              </a-descriptions>

              <a-collapse class="mt8">
                <a-collapse-panel key="raw" header="raw（Flask 原始返回）">
                  <pre class="raw-pre">{{ prettyJson(m.data.predictResult.raw) }}</pre>
                </a-collapse-panel>
              </a-collapse>
            </div>
          </div>
        </div>
      </div>

      <!--
        等待作答的占位气泡。
        后端 /ai/chat 是**单次 POST、非流式**，一次 RAG 问答端到端要 1~2 秒，
        预测类还可能更久。此前这段时间界面上毫无变化，用户会以为没点动。
      -->
      <div v-if="sending" class="msg assistant">
        <div class="bubble bubble-thinking">
          <a-spin size="small" />
          <span class="thinking-text">正在深度思考</span>
          <span class="thinking-dots"><i></i><i></i><i></i></span>
        </div>
      </div>
    </div>

    <div class="qa-input">
      <a-textarea
        v-model:value="inputText"
        placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
        :auto-size="{ minRows: 2, maxRows: 4 }"
        @keydown="onKeydown"
      />
      <div class="qa-actions">
        <a-button type="primary" :loading="sending" :disabled="!inputText.trim()" @click="send">
          发送
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import MarkdownIt from 'markdown-it'
import DOMPurifyDefault from 'dompurify'
import { chat } from '@/api/ai'

// ---------------------------------------------------------------------------
// Markdown 渲染
//
// 模型（DeepSeek）返回的 answer 是 Markdown 原文，直接插进模板只会显示成
// 一堆 `**` 和 `-`。这里用 markdown-it 解析，再用 DOMPurify 洗一遍。
//
// 两道防护缺一不可：
//   1. markdown-it 关掉 html 选项 —— 模型输出里的原始 HTML 标签一律当**文本**，
//      这从源头消灭了绝大部分注入面（模型是可控性很弱的输入源）；
//   2. DOMPurify 兜底 —— 即便 1 被绕过（未来改配置、链接协议等），仍会过滤。
// ---------------------------------------------------------------------------
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false
})

// 外链一律新窗口打开，并附 rel 防止 window.opener 被反向控制
const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/**
 * dompurify 3.x 的 ESM 默认导出**已经是可直接使用的实例**
 * （内部 `createDOMPurify()` 时用全局 window 构造，浏览器下开箱即用）。
 * 这里做一次形状探测：若拿到的不是实例而是工厂，就自己传 window 构造 ——
 * 免得将来升级到「必须自己传 window」的版本时，静态引用不报错却运行期静默失效。
 */
const DOMPurify =
  typeof DOMPurifyDefault?.sanitize === 'function' ? DOMPurifyDefault : DOMPurifyDefault(window)

function escapeHtml(s) {
  return String(s).replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
  )
}

/**
 * 把 Markdown 原文渲染成可直接 v-html 的 HTML。
 * 解析或清洗环节一旦抛错，退化成转义后的纯文本 —— 宁可少渲染，不可白屏。
 */
function renderMd(text) {
  const raw = String(text ?? '')
  if (!raw) return ''
  try {
    return DOMPurify.sanitize(md.render(raw))
  } catch (e) {
    return escapeHtml(raw)
  }
}

const collapseActive = ref([])

const ragForm = reactive({
  country: '',
  year: null
})

const predictForm = reactive({
  tradeType: undefined,
  target: undefined,
  year: null,
  month: null,
  tradePartnerName: '',
  productName: '',
  tradeMode: '',
  registerName: ''
})

const messages = ref([])
const inputText = ref('')
const sending = ref(false)

const chatBodyRef = ref(null)

const sourceColumns = [
  { title: 'ID', dataIndex: 'newsId', width: 80 },
  {
    title: '相关度',
    dataIndex: 'score',
    width: 90,
    customRender: ({ text }) => (text == null ? '-' : Number(text).toFixed(4))
  },
  { title: '标题', dataIndex: 'title', ellipsis: true },
  {
    title: '发布时间',
    dataIndex: 'publishTime',
    width: 160,
    customRender: ({ text }) => (text ? dayjs(text).format('YYYY-MM-DD HH:mm') : '-')
  },
  {
    title: '是否进模型',
    dataIndex: 'usedInContext',
    width: 110,
    customRender: ({ text }) => (text ? '已引用' : '仅相关')
  }
]

// 历史数据查询结果表：一行一个月
const dataColumns = [
  { title: '月份', dataIndex: 'label', width: 100 },
  { title: '数量', dataIndex: 'quantity', width: 150, customRender: ({ text }) => fmtNum(text) },
  { title: '金额(人民币)', dataIndex: 'rmb', width: 170, customRender: ({ text }) => fmtNum(text) },
  { title: '单价', dataIndex: 'price', width: 130, customRender: ({ text }) => fmtNum(text) },
  { title: '单位', dataIndex: 'unit', width: 90 }
]

const ROUTE_LABELS = {
  PREDICT: '贸易预测',
  DATA_QUERY: '历史数据查询',
  RAG_NEWS: '新闻问答',
  SCOPE: '数据范围',
  CHITCHAT: '助手说明'
}

const ROUTE_COLORS = {
  PREDICT: 'blue',
  DATA_QUERY: 'green',
  RAG_NEWS: 'purple',
  SCOPE: 'cyan',
  CHITCHAT: 'default'
}

function routeLabel(route) {
  return ROUTE_LABELS[route] || route || '-'
}

function routeColor(route) {
  return ROUTE_COLORS[route] || 'default'
}

function fmtNum(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (!isFinite(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

function prettyJson(obj) {
  try {
    return JSON.stringify(obj ?? {}, null, 2)
  } catch {
    return String(obj)
  }
}

// ---------------------------------------------------------------------------
// RAG 来源展示
//
// 后端一次会取回比「进入模型上下文」更多的候选（ai.vector.candidate-topk，默认 20），
// 用来如实呈现检索规模。但候选条数一旦调大，前端如果照单全铺，一次问答就能拉出
// 二十行表格，把对话区撑爆。
//
// 所以这里定死：**默认只列前 5 条**，其余收进下拉面板按需展开。
// 默认条数刻意不跟随后端配置变化，避免后端一调参、页面长度就跟着变。
// ---------------------------------------------------------------------------
const DEFAULT_SOURCE_ROWS = 5

function sourceList(m) {
  return m.data?.sources || []
}

/** 默认表里的条数：固定 5，且不超过实际候选数。 */
function primaryCount(m) {
  return Math.min(DEFAULT_SOURCE_ROWS, sourceList(m).length)
}

/** 默认表（前 5 条）。 */
function primarySources(m) {
  return sourceList(m).slice(0, primaryCount(m))
}

/** 下拉面板里的其余候选。 */
function restSources(m) {
  return sourceList(m).slice(primaryCount(m))
}

function restHeader(m) {
  return `其余 ${restSources(m).length} 条候选（未进入本次回答，点击展开）`
}

function sourceNote(m) {
  const all = sourceList(m)
  if (!all.length) return ''
  const used = all.filter((s) => s.usedInContext).length || m.data?.contextDocs || 0
  const total = Number(m.data?.corpusSize)
  let s = ''
  if (total > 0) {
    s += `本次在全部 ${fmtNum(total)} 条新闻语料中逐一比对相关度，`
  }
  s += `共取回 ${all.length} 条候选，按相关度排序，下方默认列出前 ${primaryCount(m)} 条`
  s += `，其中相关度最高的 ${used} 条已作为新闻片段交给模型作答`
  const rest = all.length - used
  if (rest > 0) {
    s += `，其余 ${rest} 条只是同样相关、并未进入本次回答`
  }
  return s + '。'
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatBodyRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  })
}

function pushUser(text) {
  messages.value.push({
    id: `${Date.now()}-u-${Math.random().toString(16).slice(2)}`,
    role: 'user',
    text
  })
  scrollToBottom()
}

function pushAssistant(text, data) {
  messages.value.push({
    id: `${Date.now()}-a-${Math.random().toString(16).slice(2)}`,
    role: 'assistant',
    text,
    data
  })
  scrollToBottom()
}

function buildPayload(text) {
  const payload = {
    text
  }

  if (ragForm.country) payload.country = ragForm.country
  if (ragForm.year != null && ragForm.year !== '') payload.year = Number(ragForm.year)

  // 预测槽位：只在有值时带上（避免影响意图识别）
  const slots = {
    tradeType: predictForm.tradeType,
    target: predictForm.target,
    year: predictForm.year,
    month: predictForm.month,
    tradePartnerName: predictForm.tradePartnerName?.trim() || undefined,
    productName: predictForm.productName?.trim() || undefined,
    tradeMode: predictForm.tradeMode?.trim() || undefined,
    registerName: predictForm.registerName?.trim() || undefined
  }

  Object.keys(slots).forEach(k => {
    const v = slots[k]
    if (v === undefined || v === null || v === '') return
    payload[k] = typeof v === 'string' ? v : Number(v)
  })

  return payload
}

/**
 * 「正在深度思考」的最短停留时长。
 *
 * 不是为了拖慢接口，而是为了让占位**看得见**：闲聊类问题命中规则、不调模型，
 * 后端 7ms 就返回，占位气泡一闪而过，用户的主观感受就是「根本没这个提示」。
 * 真正耗时的问答（RAG 约 1.5s、预测更久）只会被补很少的一段甚至不补。
 */
const THINKING_MIN_MS = 450

function holdThinking(t0) {
  const rest = THINKING_MIN_MS - (Date.now() - t0)
  return rest > 0 ? new Promise((r) => setTimeout(r, rest)) : Promise.resolve()
}

async function send() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  pushUser(text)
  inputText.value = ''

  sending.value = true
  scrollToBottom()
  const t0 = Date.now()
  try {
    const data = await chat(buildPayload(text))
    await holdThinking(t0)
    pushAssistant(data?.answer || '（无回答）', data)
  } catch (e) {
    await holdThinking(t0)
    const msg = e?.message || '调用失败'
    message.error(msg)
    pushAssistant(`调用失败：${msg}`)
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function clearChat() {
  messages.value = []
}

function clearAdvanced() {
  ragForm.country = ''
  ragForm.year = null

  predictForm.tradeType = undefined
  predictForm.target = undefined
  predictForm.year = null
  predictForm.month = null
  predictForm.tradePartnerName = ''
  predictForm.productName = ''
  predictForm.tradeMode = ''
  predictForm.registerName = ''
}
</script>

<style scoped>
.qa-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  padding: 16px 24px;
}

.qa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.qa-title {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
}

.qa-advanced {
  margin-bottom: 12px;
}

.qa-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 12px;
  background: #fafafa;
}

.qa-empty {
  color: #999;
  padding: 8px;
}

.msg {
  display: flex;
  margin-bottom: 12px;
}

.msg.user {
  justify-content: flex-end;
}

.msg.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 80%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #e8e8e8;
  background: #fff;
}

.msg.user .bubble {
  background: #e6f7ff;
  border-color: #91d5ff;
}

.bubble-text {
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

/* ---------------- Markdown 正文排版 ----------------
   气泡里的 HTML 来自 v-html，是子孙节点，scoped 样式必须用 :deep() 穿透，
   否则一条规则都命中不了（这是 v-html + scoped 最常见的坑）。 */
.md-body {
  white-space: normal; /* 交给 markdown 的段落/换行规则，避免和 pre-wrap 打架 */
}

.md-body :deep(:first-child) {
  margin-top: 0;
}

.md-body :deep(:last-child) {
  margin-bottom: 0;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4),
.md-body :deep(h5),
.md-body :deep(h6) {
  margin: 12px 0 6px;
  font-weight: 600;
  line-height: 1.4;
}

.md-body :deep(h1) { font-size: 17px; }
.md-body :deep(h2) { font-size: 16px; }
.md-body :deep(h3) { font-size: 15px; }
.md-body :deep(h4),
.md-body :deep(h5),
.md-body :deep(h6) { font-size: 14px; }

.md-body :deep(p) {
  margin: 0 0 8px;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}

.md-body :deep(li) {
  margin: 2px 0;
}

.md-body :deep(li > p) {
  margin: 0;
}

.md-body :deep(strong) {
  font-weight: 600;
}

.md-body :deep(em) {
  font-style: italic;
}

.md-body :deep(code) {
  background: #f5f5f5;
  border-radius: 3px;
  padding: 1px 4px;
  font-size: 12.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.md-body :deep(pre) {
  margin: 0 0 8px;
  padding: 10px;
  border-radius: 6px;
  overflow: auto;
  background: #0b1020;
  color: #d6deeb;
}

.md-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.md-body :deep(blockquote) {
  margin: 0 0 8px;
  padding: 4px 10px;
  border-left: 3px solid #d9d9d9;
  background: #fafafa;
  color: #595959;
}

.md-body :deep(table) {
  width: 100%;
  margin: 0 0 8px;
  border-collapse: collapse;
  font-size: 13px;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #e8e8e8;
  padding: 4px 8px;
  text-align: left;
}

.md-body :deep(th) {
  background: #fafafa;
  font-weight: 600;
}

.md-body :deep(hr) {
  margin: 10px 0;
  border: none;
  border-top: 1px solid #f0f0f0;
}

.md-body :deep(a) {
  color: #1890ff;
}

.md-body :deep(img) {
  max-width: 100%;
}

/* ---------------- 等待作答气泡 ---------------- */
.bubble-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8c8c8c;
}

.thinking-text {
  font-size: 14px;
}

.thinking-dots {
  display: inline-flex;
  gap: 3px;
}

.thinking-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #bfbfbf;
  animation: thinkBlink 1.2s infinite ease-in-out;
}

.thinking-dots i:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots i:nth-child(3) { animation-delay: 0.4s; }

@keyframes thinkBlink {
  0%, 80%, 100% { opacity: 0.25; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-2px); }
}

.bubble-extra {
  margin-top: 6px;
}

.section-title {
  font-weight: 500;
  margin-bottom: 8px;
}

.section-note {
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
  margin-bottom: 8px;
}

.more-collapse {
  margin-top: 8px;
  background: transparent;
}

/* 折叠面板只作为「其余候选」的下拉入口，标题压缩成一行提示，不要太抢眼 */
.more-collapse :deep(.ant-collapse-header) {
  padding: 4px 0 !important;
  font-size: 12px;
  color: #1890ff;
}

.more-collapse :deep(.ant-collapse-content-box) {
  padding: 8px 0 0 !important;
}

.raw-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #0b1020;
  color: #d6deeb;
  padding: 10px;
  border-radius: 6px;
}

.qa-input {
  margin-top: 12px;
}

.qa-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.mt8 {
  margin-top: 8px;
}
</style>
