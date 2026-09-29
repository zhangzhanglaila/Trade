#!/usr/bin/env node
/**
 * 问答页渲染验收脚本（前端展示逻辑）。
 *
 * 覆盖两件此前出过问题的事：
 *
 *  A. 「来源展示」——后端一次问答会取回比「进入模型上下文」更多的候选
 *     （ai.vector.candidate-topk，默认 20），用来如实呈现检索规模。
 *     但候选一多，前端若照单全铺，一次问答就能拉出二十行表格把对话区撑爆。
 *     约定：**默认只列前 5 条，其余收进下拉面板**。
 *
 *  B. 「Markdown 渲染」——模型返回的 answer 是 Markdown 原文，
 *     此前直接 `{{ m.text }}` 输出，页面上的 `**加粗**`、`- 列表` 原样露出。
 *     约定：统一走 renderMd()，且必须**关掉原始 HTML**（模型是弱可控输入源）。
 *
 * 本脚本不依赖浏览器：它把 QAPage.vue 的 <script setup> **原样抽出来**
 * （不是抄一份实现，避免脚本和代码各说各话），只把 import 换成桩；
 * 其中 markdown-it 用**真实的**那个包（经 createRequire 从前端工程解析），
 * 这样"Markdown 到底渲染成什么样"是被真的跑出来的，不是打桩假装通过。
 * DOMPurify 在 Node 下没有 DOM，无法真跑，用恒等桩并在输出里注明。
 *
 * 用法（在服务器上执行）：
 *     node tools/qa_source_display_check.mjs
 *     node tools/qa_source_display_check.mjs --base http://127.0.0.1:8080
 *
 * 注意：/ai/chat 请求体字段名是 text（不是 question）。
 */
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const FRONTEND = path.join(HERE, '..', 'src', 'frontend-vue')
const VUE = path.join(FRONTEND, 'src', 'views', 'QAPage.vue')
const DEFAULT_ROWS_EXPECTED = 5

const argv = process.argv.slice(2)
const baseIdx = argv.indexOf('--base')
const BASE = (baseIdx >= 0 ? argv[baseIdx + 1] : 'http://127.0.0.1:8080').replace(/\/+$/, '')

// 真实的 markdown-it：从 tools/ 解析不到，必须借前端工程的 package.json 起算
const requireFromFrontend = createRequire(path.join(FRONTEND, 'package.json'))
const MarkdownItReal = requireFromFrontend('markdown-it')

/** 从真实 .vue 里抽出展示逻辑。 */
function loadHelpers() {
  const src = fs.readFileSync(VUE, 'utf8')
  const m = src.match(/<script setup>([\s\S]*?)<\/script>/)
  if (!m) throw new Error('QAPage.vue 里没找到 <script setup> 块')
  // 只摘掉 import，其余代码逐字保留
  const code = m[1].replace(/^\s*import[\s\S]*?from\s+['"][^'"]+['"]\s*$/gm, '')

  const stubs = `
    function ref(v) { return { value: v } }
    function reactive(o) { return o }
    function computed(f) { return { value: f() } }
    function nextTick(f) { return Promise.resolve().then(f) }
    const message = { error() {}, success() {} }
    const dayjs = (x) => ({ format: () => String(x) })
    function chat() { return Promise.resolve({}) }
    const MarkdownIt = __MarkdownItReal
    const window = { __stub: true }
    // DOMPurify 在 Node 里没有 DOM，无法真跑 —— 用恒等桩。
    // 有 sanitize 属性即可通过代码里的"形状探测"分支。
    const DOMPurifyDefault = { sanitize: (h) => h, __isStub: true }
  `

  const sandbox = {
    __MarkdownItReal: MarkdownItReal,
    // 真 setTimeout 传进去，这样 holdThinking 的最短停留是可以被真的量出来的
    setTimeout: (fn, ms) => setTimeout(fn, ms)
  }
  vm.createContext(sandbox)
  vm.runInContext(`${stubs}\n${code}\n
    ;globalThis.__probe = {
      primaryCount, primarySources, restSources, restHeader, sourceNote,
      sourceList, DEFAULT_SOURCE_ROWS, renderMd,
      THINKING_MIN_MS, holdThinking, ROUTE_LABELS,
    };
  `, sandbox, { filename: 'QAPage.script.js' })
  return sandbox.__probe
}

/** 模板是源码级断言用（v-if / v-html 这类结构，跑不起来，只能读源码）。 */
function loadTemplate() {
  const src = fs.readFileSync(VUE, 'utf8')
  const m = src.match(/<template>([\s\S]*?)<\/template>/)
  if (!m) throw new Error('QAPage.vue 里没找到 <template> 块')
  return m[1]
}

async function ask(text, timeoutMs = 180000) {
  const ctl = new AbortController()
  const timer = setTimeout(() => ctl.abort(), timeoutMs)
  try {
    const r = await fetch(BASE + '/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
      signal: ctl.signal
    })
    return await r.json()
  } finally {
    clearTimeout(timer)
  }
}

const P = loadHelpers()
const TPL = loadTemplate()

let bad = 0
const check = (cond, label) => {
  console.log((cond ? 'OK   ' : 'FAIL ') + label)
  if (!cond) bad++
}

// ===========================================================================
// A. 来源展示
// ===========================================================================
console.log('#' + '='.repeat(72))
console.log('# A. 来源展示（默认 5 条 + 其余收进下拉）')
console.log('#' + '='.repeat(72))

// 这个问法稳定命中 RAG_NEWS，且候选条数足够多，正好压到「太长」这个点
const resp = await ask('最近有哪些关于哈萨克斯坦的新闻')
const data = resp.data || {}

if (data.route !== 'RAG_NEWS') {
  console.log('FAIL  期望 RAG_NEWS，实际 ' + data.route)
  process.exit(1)
}

const msg = { id: 'probe', role: 'assistant', text: data.answer || '', data }
const all = P.sourceList(msg)
const primary = P.primarySources(msg)
const rest = P.restSources(msg)

console.log('线上响应: corpusSize=%s contextDocs=%s sources=%s',
  data.corpusSize, data.contextDocs, all.length)
console.log('-'.repeat(74))
console.log('DEFAULT_SOURCE_ROWS =', P.DEFAULT_SOURCE_ROWS)
console.log('默认表格渲染行数     =', primary.length)
console.log('下拉面板渲染行数     =', rest.length)
console.log('下拉面板标题         =', P.restHeader(msg))
console.log('说明行               =', P.sourceNote(msg))
console.log('-'.repeat(74))

check(all.length > DEFAULT_ROWS_EXPECTED, `后端取回 ${all.length} 条候选（>5，说明候选与上下文确实解耦）`)
check(primary.length === DEFAULT_ROWS_EXPECTED, `默认只渲染 ${DEFAULT_ROWS_EXPECTED} 行`)
check(rest.length === all.length - DEFAULT_ROWS_EXPECTED, `其余 ${rest.length} 条进下拉面板`)
check(primary.length + rest.length === all.length, '两段加起来不丢不重')
check(primary.every((s) => s.usedInContext === true), '默认表里的正是「已交给模型」的那几条')
check(rest.every((s) => s.usedInContext === false), '下拉面板里都是「仅相关」的候选')
check(P.restHeader(msg).includes('点击展开'), '下拉面板标题提示可展开')

// ===========================================================================
// B. Markdown 渲染
// ===========================================================================
console.log()
console.log('#' + '='.repeat(72))
console.log('# B. Markdown 渲染（markdown-it 真实解析 + DOMPurify 恒等桩）')
console.log('#' + '='.repeat(72))

const SAMPLE = [
  '**3. 1992年上半年的进口结构（历史数据）：**',
  '- 食品占66.7%',
  '- 日用消费品占13%',
  '',
  '### 小标题',
  '',
  '| 年份 | 占比 |',
  '|---|---|',
  '| 2020 | 11% |',
  '',
  '详见 [来源](https://example.com/a) 与 `code`。'
].join('\n')

const html = P.renderMd(SAMPLE)
console.log('渲染样例输出：')
console.log(html.trim())
console.log('-'.repeat(74))

check(!html.includes('**'), '输出里不再有裸露的 ** 标记（原问题的直接复现点）')
check(html.includes('<strong>3. 1992年上半年的进口结构（历史数据）：</strong>'),
  '首行 **…** 被解析成 <strong>')
check(html.includes('<ul>') && html.includes('<li>食品占66.7%</li>'), '- 开头被解析成 <ul>/<li>')
check(html.includes('<h3>小标题</h3>'), '### 被解析成 <h3>')
check(html.includes('<table>') && html.includes('<th>年份</th>'), 'Markdown 表格被解析成 <table>/<th>')
check(html.includes('<code>code</code>'), '行内 `code` 被解析成 <code>')

const linkHtml = P.renderMd('[来源](https://example.com/a)')
check(linkHtml.includes('target="_blank"'), '外链带 target="_blank"')
check(linkHtml.includes('rel="noopener noreferrer"'), '外链带 rel="noopener noreferrer"（防 window.opener 反控）')

// 模型是弱可控输入源：原始 HTML 必须被当文本，绝不能进 DOM
const xss = P.renderMd('<script>alert(1)</script>\n\n<img src=x onerror=alert(2)>')
console.log('原始 HTML 输入 -> ' + xss.trim())
check(!xss.includes('<script>'), '<script> 被转义，未原样进入 DOM')
// 注意判据是「有没有真的 <img 标签」。转义后的文本里仍然会出现 onerror= 这串字符，
// 但那只是可读文本，拿子串去判会误报（本脚本初版就踩了这个坑）。
check(!xss.includes('<img'), '<img onerror=…> 未被当作标签渲染')
check(xss.includes('&lt;script&gt;'), '转义后是可读文本（不是被静默丢弃）')

// 空值/异常输入不能白屏
check(P.renderMd('') === '', '空输入返回空串')
check(P.renderMd(null) === '', 'null 输入返回空串')
check(typeof P.renderMd('普通文本') === 'string', '纯文本输入有返回值')

// 线上真实回答：原始 markdown 里带 ** 是正常的，要看**渲染后**是否还残留
const liveRaw = data.answer || ''
const liveHtml = P.renderMd(liveRaw)
console.log('线上回答长度 = %s，原始 ** 对数 = %s，渲染后残留 ** = %s',
  liveRaw.length,
  (liveRaw.match(/\*\*[^*\n]+\*\*/g) || []).length,
  liveHtml.includes('**') ? '有' : '无')
console.log('渲染后片段 -> ' + liveHtml.slice(0, 160).replace(/\n/g, ' '))
check(liveHtml.length > 0, '线上回答能渲染出 HTML')
check(!liveHtml.includes('**'), '渲染后不再残留 **（原缺陷的直接复现点）')

// ===========================================================================
// C. 「正在深度思考」占位
// ===========================================================================
console.log()
console.log('#' + '='.repeat(72))
console.log('# C. 等待作答占位（模板源码级断言）')
console.log('#' + '='.repeat(72))

check(TPL.includes('正在深度思考'), '模板里有「正在深度思考」文案')
check(/v-if="sending"[\s\S]{0,300}正在深度思考/.test(TPL), '该占位由 sending 状态驱动')
check(TPL.includes('v-html="renderMd(m.text)"'), '正文改用 v-html 走 renderMd()')
check(!/\{\{\s*m\.text\s*\}\}/.test(TPL), '已移除直接插值的 {{ m.text }}（原缺陷点）')

// 光有占位还不够 —— 闲聊类问题命中规则、不调模型，后端 7ms 就返回，
// 占位会一闪而过，用户主观感受仍是「没有这个提示」。所以要量最短停留。
console.log('-'.repeat(74))
const t0 = Date.now()
await P.holdThinking(t0)
const waited = Date.now() - t0
console.log('THINKING_MIN_MS = %s，接口立即返回时实际停留 %s ms', P.THINKING_MIN_MS, waited)
check(P.THINKING_MIN_MS >= 300, '最短停留时长足够被人眼感知（≥300ms）')
check(waited >= P.THINKING_MIN_MS, '秒回的请求会被补足到最短停留时长（否则占位看不见）')

const t1 = Date.now()
await P.holdThinking(t1 - 5000)
const extra = Date.now() - t1
console.log('已耗时 5000ms 的请求被额外延迟 %s ms', extra)
check(extra < 50, '已经等够久的请求不再补延迟（不会拖慢正常问答）')

check(P.ROUTE_LABELS.SCOPE === '数据范围', 'SCOPE 路由有中文标签，不会把内部路由名直接显示给用户')

console.log('-'.repeat(74))
console.log(bad === 0 ? '全部通过' : `存在 ${bad} 项失败`)
process.exit(bad ? 1 : 0)
