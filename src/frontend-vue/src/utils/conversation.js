/**
 * 会话持久化工具（localStorage）。
 *
 * 设计目标（对应需求）：
 *   1. 刷新后消息仍在 —— 每条消息实时落盘到 localStorage；
 *   2. 刷新后进入「新会话」，但能通过左侧列表找回历史对话；
 *   3. 切到其他功能页再点回来，仍停在「当前会话」（activeId 持久化）。
 *
 * 多用户隔离：以 userId 作为存储 key 前缀，不同账号互不可见。
 * 存储键：
 *   wb.conv.<userId>.list      —— 会话索引数组（id/title/updateTime，不含消息体，轻量）
 *   wb.conv.<userId>.data.<id> —— 单个会话的完整消息
 *   wb.conv.<userId>.active    —— 当前活跃会话 id
 */

const PREFIX = 'wb.conv'

function uid() {
  try {
    const u = localStorage.getItem('userInfo')
    if (u) {
      const o = JSON.parse(u)
      return String(o.id ?? o.userId ?? 'anonymous')
    }
  } catch (e) { /* ignore */ }
  return 'anonymous'
}

function listKey() {
  return `${PREFIX}.${uid()}.list`
}

function dataKey(id) {
  return `${PREFIX}.${uid()}.data.${id}`
}

function activeKey() {
  return `${PREFIX}.${uid()}.active`
}

function readJSON(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch (e) {
    return fallback
  }
}

function writeJSON(key, val) {
  try {
    localStorage.setItem(key, JSON.stringify(val))
  } catch (e) {
    // localStorage 写满（QuotaExceeded）时静默失败，避免影响主流程
    console.warn('[conversation] 写入失败', key, e)
  }
}

/** 生成一个 12 位、含时间信息的会话 id，便于按时间排序。 */
function genId() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}

/** 从消息里提炼一个简短的会话标题（取首条用户消息，截断）。 */
export function deriveTitle(messages) {
  const firstUser = (messages || []).find((m) => m.role === 'user')
  if (!firstUser) return '新会话'
  const t = String(firstUser.text || '').replace(/\s+/g, ' ').trim()
  return t.length > 24 ? t.slice(0, 24) + '…' : (t || '新会话')
}

/** 读取会话索引（按更新时间倒序）。 */
export function listConversations() {
  return readJSON(listKey(), [])
}

/** 读取单个会话的完整消息。 */
export function getConversation(id) {
  const meta = listConversations().find((c) => c.id === id)
  const messages = readJSON(dataKey(id), [])
  return { id, title: meta?.title, messages }
}

/** 当前活跃会话 id。 */
export function getActiveId() {
  return localStorage.getItem(activeKey()) || null
}

export function setActiveId(id) {
  if (id) localStorage.setItem(activeKey(), id)
  else localStorage.removeItem(activeKey())
}

/**
 * 创建会话（不落盘，直到有消息时才会保存）。
 * 返回新会话 id，并把它设为当前活跃。
 */
export function createConversation() {
  const id = genId()
  setActiveId(id)
  return id
}

/**
 * 保存会话：写消息体 + 更新索引。若会话没有消息则跳过（不留空会话）。
 */
export function saveConversation(id, messages) {
  if (!id) return
  const msgs = messages || []
  if (msgs.length === 0) return

  writeJSON(dataKey(id), msgs)

  const list = listConversations()
  const idx = list.findIndex((c) => c.id === id)
  const title = deriveTitle(msgs)
  const now = Date.now()
  const meta = { id, title, updateTime: now }
  if (idx >= 0) {
    list[idx] = { ...list[idx], ...meta }
  } else {
    list.push(meta)
  }
  // 按更新时间倒序
  list.sort((a, b) => (b.updateTime || 0) - (a.updateTime || 0))
  writeJSON(listKey(), list)
}

/** 删除会话及其消息。若删除的是当前活跃会话，活跃 id 一并清空。 */
export function deleteConversation(id) {
  localStorage.removeItem(dataKey(id))
  const list = listConversations().filter((c) => c.id !== id)
  writeJSON(listKey(), list)
  if (getActiveId() === id) setActiveId(null)
}

/** 重命名会话标题（后续可扩展，暂留）。 */
export function renameConversation(id, title) {
  const list = listConversations()
  const idx = list.findIndex((c) => c.id === id)
  if (idx >= 0) {
    list[idx].title = title
    writeJSON(listKey(), list)
  }
}
