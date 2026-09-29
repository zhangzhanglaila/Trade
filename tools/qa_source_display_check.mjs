#!/usr/bin/env node
/**
 * 问答「来源展示」验收脚本（前端渲染逻辑）。
 *
 * 背景：后端一次问答会取回比「进入模型上下文」更多的候选
 *      （ai.vector.candidate-topk，默认 20），用来如实呈现检索规模。
 *      但候选一多，前端若照单全铺，一次问答就能拉出二十行表格，
 *      把对话区撑得极长。约定：**默认只列前 5 条，其余收进下拉面板**。
 *
 * 本脚本不依赖浏览器：它把 QAPage.vue 的 <script setup> **原样抽出来**
 * （不是抄一份实现，避免脚本和代码各说各话），只把 import 换成桩，
 * 再喂线上 /ai/chat 的真实响应，断言上面那条约定成立。
 *
 * 用法（在服务器上执行）：
 *     node tools/qa_source_display_check.mjs
 *     node tools/qa_source_display_check.mjs --base http://127.0.0.1:8080
 *
 * 注意：请求体字段名是 text（不是 question）。
 */
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const VUE = path.join(HERE, '..', 'src', 'frontend-vue', 'src', 'views', 'QAPage.vue')
const DEFAULT_ROWS_EXPECTED = 5

const argv = process.argv.slice(2)
const baseIdx = argv.indexOf('--base')
const BASE = (baseIdx >= 0 ? argv[baseIdx + 1] : 'http://127.0.0.1:8080').replace(/\/+$/, '')

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
  `

  const sandbox = {}
  vm.createContext(sandbox)
  vm.runInContext(stubs + '\n' + code + `
    ;globalThis.__probe = { primaryCount, primarySources, restSources, restHeader, sourceNote,
                            sourceList, DEFAULT_SOURCE_ROWS };
  `, sandbox, { filename: 'QAPage.script.js' })
  return sandbox.__probe
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

let bad = 0
const check = (cond, label) => {
  console.log((cond ? 'OK   ' : 'FAIL ') + label)
  if (!cond) bad++
}

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

console.log('='.repeat(74))
console.log('线上响应: corpusSize=%s contextDocs=%s sources=%s',
  data.corpusSize, data.contextDocs, all.length)
console.log('-'.repeat(74))
console.log('DEFAULT_SOURCE_ROWS =', P.DEFAULT_SOURCE_ROWS)
console.log('默认表格渲染行数     =', primary.length)
console.log('下拉面板渲染行数     =', rest.length)
console.log('下拉面板标题         =', P.restHeader(msg))
console.log('说明行               =', P.sourceNote(msg))
console.log('='.repeat(74))

check(all.length > DEFAULT_ROWS_EXPECTED, `后端取回 ${all.length} 条候选（>5，说明候选与上下文确实解耦）`)
check(primary.length === DEFAULT_ROWS_EXPECTED, `默认只渲染 ${DEFAULT_ROWS_EXPECTED} 行`)
check(rest.length === all.length - DEFAULT_ROWS_EXPECTED, `其余 ${rest.length} 条进下拉面板`)
check(primary.length + rest.length === all.length, '两段加起来不丢不重')
check(primary.every((s) => s.usedInContext === true), '默认表里的正是「已交给模型」的那几条')
check(rest.every((s) => s.usedInContext === false), '下拉面板里都是「仅相关」的候选')
check(P.restHeader(msg).includes('点击展开'), '下拉面板标题提示可展开')

console.log('-'.repeat(74))
console.log(bad === 0 ? '全部通过' : `存在 ${bad} 项失败`)
process.exit(bad ? 1 : 0)
