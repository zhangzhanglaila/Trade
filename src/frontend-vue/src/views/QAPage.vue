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
        请输入问题开始对话。新闻类问题将走 RAG；填写预测槽位时将优先走 PREDICT。
      </div>

      <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
        <div class="bubble">
          <div class="bubble-text">{{ m.text }}</div>

          <div v-if="m.role === 'assistant' && m.data" class="bubble-extra">
            <a-divider style="margin: 8px 0" />

            <div class="meta">
              <a-tag color="blue">{{ m.data.route }}</a-tag>
            </div>

            <!-- RAG 来源 -->
            <div v-if="m.data.route === 'RAG_NEWS'">
              <div class="section-title">来源（sources）</div>
              <a-table
                :columns="sourceColumns"
                :data-source="m.data.sources || []"
                :pagination="false"
                size="small"
                row-key="newsId"
              />
            </div>

            <!-- 预测结果 -->
            <div v-else-if="m.data.route === 'PREDICT'">
              <div class="section-title">预测结果</div>
              <a-descriptions size="small" bordered :column="1">
                <a-descriptions-item label="value">
                  {{ m.data.predictResult?.value ?? '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="unit">
                  {{ m.data.predictResult?.unit ?? '-' }}
                </a-descriptions-item>
              </a-descriptions>

              <a-collapse class="mt8">
                <a-collapse-panel key="raw" header="raw（Flask 原始返回）">
                  <pre class="raw-pre">{{ prettyJson(m.data.predictResult?.raw) }}</pre>
                </a-collapse-panel>
              </a-collapse>
            </div>
          </div>
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
import { chat } from '@/api/ai'

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
  { title: 'newsId', dataIndex: 'newsId', width: 90 },
  { title: 'title', dataIndex: 'title', ellipsis: true },
  { title: 'source', dataIndex: 'source', width: 120, ellipsis: true },
  {
    title: 'publishTime',
    dataIndex: 'publishTime',
    width: 170,
    customRender: ({ text }) => (text ? dayjs(text).format('YYYY-MM-DD HH:mm') : '-')
  },
  {
    title: 'score',
    dataIndex: 'score',
    width: 90,
    customRender: ({ text }) => (text == null ? '-' : Number(text).toFixed(4))
  }
]

function prettyJson(obj) {
  try {
    return JSON.stringify(obj ?? {}, null, 2)
  } catch {
    return String(obj)
  }
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

async function send() {
  const text = inputText.value.trim()
  if (!text) return

  pushUser(text)
  inputText.value = ''

  sending.value = true
  try {
    const data = await chat(buildPayload(text))
    pushAssistant(data?.answer || '（无回答）', data)
  } catch (e) {
    const msg = e?.message || '调用失败'
    message.error(msg)
    pushAssistant(`调用失败：${msg}`)
  } finally {
    sending.value = false
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

.bubble-extra {
  margin-top: 6px;
}

.section-title {
  font-weight: 500;
  margin-bottom: 8px;
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
