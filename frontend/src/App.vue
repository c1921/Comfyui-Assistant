<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ConnectionCard from './components/ConnectionCard.vue'
import WorkflowCard from './components/WorkflowCard.vue'
import ParamsCard from './components/ParamsCard.vue'
import LogCard from './components/LogCard.vue'
import OutputCard from './components/OutputCard.vue'

onMounted(() => {
  setTimeout(() => window.HSStaticMethods.autoInit(), 100)
});

interface WorkflowNode {
  class_type?: string
  _meta?: { title?: string }
  inputs?: Record<string, unknown>
}

type WorkflowMap = Record<string, WorkflowNode>

type EditableValue = string | number | boolean

type ParamField = {
  id: string
  nodeId: string
  nodeLabel: string
  inputKey: string
  value: EditableValue
  inputType: 'string' | 'number' | 'boolean'
}

type ImageFile = {
  filename: string
  subfolder?: string
  type?: string
}

type ImageItem = {
  src: string
  alt: string
}

function makeUUID(): string {
  const cryptoObj = globalThis.crypto
  if (cryptoObj?.randomUUID && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID()
  }
  if (cryptoObj?.getRandomValues && typeof cryptoObj.getRandomValues === 'function') {
    const buf = new Uint8Array(16)
    cryptoObj.getRandomValues(buf)
    const b6 = buf[6] ?? 0
    const b8 = buf[8] ?? 0
    buf[6] = (b6 & 0x0f) | 0x40
    buf[8] = (b8 & 0x3f) | 0x80
    const hex = [...buf].map((b) => b.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  return `cid-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const clientId = ref(makeUUID())
const ws = ref<WebSocket | null>(null)
const workflow = ref<WorkflowMap | null>(null)
const lastPromptId = ref<string | null>(null)
const shownFiles = new Set<string>()

const pcIp = ref('')
const pcPort = ref('8000')
const wsState = ref('WS: 未连接')

const workflowJson = ref('')
const parseInfo = ref('')
const lastParsedRaw = ref('')
let parseTimer: ReturnType<typeof setTimeout> | null = null

const paramFields = ref<ParamField[]>([])

const logLines = ref<string[]>([])
const logText = computed(() => logLines.value.join('\n'))

const images = ref<ImageItem[]>([])

const backendOrigin = () => {
  const host = pcIp.value.trim()
  const port = pcPort.value.trim()
  if (!host) return window.location.origin
  if (port) return `http://${host}:${port}`
  return `http://${host}`
}

const toWsOrigin = (origin: string) =>
  origin.replace(/^http(s?):\/\//, (_, s) => (s ? 'wss://' : 'ws://'))

const baseHttp = () => `${backendOrigin()}/api`
const baseWs = () => toWsOrigin(backendOrigin())
const backendLabel = computed(() => backendOrigin())

const log = (msg: string) => {
  const ts = new Date().toLocaleTimeString()
  logLines.value = [`[${ts}] ${msg}`, ...logLines.value]
}

const safeJsonParse = (raw: string) => {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const isEditableValue = (value: unknown): value is EditableValue =>
  typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'

const buildEditableParams = (wf: WorkflowMap) => {
  const fields: ParamField[] = []
  for (const [nodeId, node] of Object.entries(wf || {})) {
    if (!node?.inputs) continue
    const nodeLabel = node._meta?.title || node.class_type || nodeId
    for (const [inputKey, value] of Object.entries(node.inputs)) {
      if (!isEditableValue(value)) continue
      const inputType =
        typeof value === 'string' ? 'string' : typeof value === 'number' ? 'number' : 'boolean'
      fields.push({
        id: `${nodeId}.${inputKey}`,
        nodeId,
        nodeLabel,
        inputKey,
        value,
        inputType,
      })
    }
  }
  return fields
}

const ensureWsConnected = () => {
  if (ws.value && ws.value.readyState === WebSocket.OPEN) return true
  return connectWs()
}

const connectWs = () => {
  const url = `${baseWs()}/ws?clientId=${encodeURIComponent(clientId.value)}`
  try {
    const socket = new WebSocket(url)
    ws.value = socket

    wsState.value = 'WS: 连接中...'
    socket.onopen = () => {
      wsState.value = 'WS: 已连接'
      log(`WebSocket 已连接，clientId=${clientId.value}`)
    }
    socket.onclose = () => {
      wsState.value = 'WS: 已断开'
      log('WebSocket 已断开')
    }
    socket.onerror = () => {
      wsState.value = 'WS: 错误'
      log('WebSocket 出错（可能是后端地址/防火墙/CORS/混合内容）')
    }

    socket.onmessage = async (ev) => {
      let msg: any
      try {
        msg = JSON.parse(ev.data)
      } catch {
        return
      }
      console.log('WS RAW MESSAGE:', msg)

      if (msg.type === 'status') {
        const q = msg.data?.status?.exec_info?.queue_remaining
        if (typeof q === 'number') log(`队列剩余：${q}`)
        return
      }

      if (msg.type === 'progress') {
        const v = msg.data?.value
        const m = msg.data?.max
        if (typeof v === 'number' && typeof m === 'number') log(`进度：${v}/${m}`)
        return
      }

      if (msg.type === 'executed' || msg.type === 'execution_success') {
        const pid = msg.data?.prompt_id || msg.prompt_id || lastPromptId.value
        log(`执行完成（prompt_id=${pid || 'unknown'}），尝试拉取历史输出...`)
        await tryFetchHistoryAndShow(pid)
        return
      }

      if (msg.type === 'executing') {
        const node = msg.data?.node
        const pid = msg.data?.prompt_id || lastPromptId.value
        if (node === null) {
          log(`执行结束信号（prompt_id=${pid || 'unknown'}），尝试拉取历史输出...`)
          await tryFetchHistoryAndShow(pid)
        } else {
          log(`正在执行节点：${node}`)
        }
        return
      }

      if (msg.data?.images?.length) {
        for (const img of msg.data.images) await showImageFile(img)
      }
    }

    return true
  } catch (err) {
    log(`WebSocket 连接失败：${(err as Error)?.message || String(err)}`)
    return false
  }
}

const disconnectWs = () => {
  if (ws.value) ws.value.close()
  ws.value = null
  wsState.value = 'WS: 未连接'
}

const postPrompt = async (workflowObj: WorkflowMap) => {
  const url = `${baseHttp()}/prompt`
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: workflowObj, client_id: clientId.value }),
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`POST /api/prompt 失败：HTTP ${resp.status} ${text}`)
  }
  return await resp.json()
}

const interrupt = async () => {
  const url = `${baseHttp()}/interrupt`
  const resp = await fetch(url, { method: 'POST' })
  if (!resp.ok) throw new Error(`POST /api/interrupt 失败：HTTP ${resp.status}`)
}

const getHistory = async (promptId: string) => {
  const try1 = `${baseHttp()}/history/${encodeURIComponent(promptId)}`
  let resp = await fetch(try1)
  if (resp.ok) return await resp.json()

  const try2 = `${baseHttp()}/history`
  resp = await fetch(try2)
  if (!resp.ok) throw new Error(`GET /api/history 失败：HTTP ${resp.status}`)
  const all = await resp.json()
  return all?.[promptId] ? { [promptId]: all[promptId] } : all
}

const showImageFile = async (file: ImageFile) => {
  if (!file?.filename) return
  const key = `${file.type || 'output'}|${file.subfolder || ''}|${file.filename}`
  if (shownFiles.has(key)) return
  shownFiles.add(key)

  const url = `${baseHttp()}/view?filename=${encodeURIComponent(file.filename)}&subfolder=${encodeURIComponent(file.subfolder || '')}&type=${encodeURIComponent(file.type || 'output')}`
  images.value = [{ src: url, alt: file.filename }, ...images.value]
  log(`已展示图片：${file.filename}`)
}

const tryFetchHistoryAndShow = async (promptId?: string | null) => {
  if (!promptId) return

  let hist: any
  try {
    hist = await getHistory(promptId)
  } catch (err) {
    log(`拉取 history 失败：${(err as Error)?.message || String(err)}`)
    return
  }

  const entry = hist && hist[promptId] ? hist[promptId] : hist

  const files: ImageFile[] = []
  const seen = new Set<string>()

  const walk = (x: any) => {
    if (!x) return
    if (Array.isArray(x)) {
      x.forEach(walk)
      return
    }
    if (typeof x !== 'object') return

    if (typeof x.filename === 'string') {
      const file = {
        filename: x.filename,
        subfolder: x.subfolder || '',
        type: x.type || 'output',
      }
      const key = `${file.type}|${file.subfolder}|${file.filename}`
      if (!seen.has(key)) {
        seen.add(key)
        files.push(file)
      }
    }

    Object.values(x).forEach(walk)
  }

  walk(entry)

  if (files.length === 0) {
    log('history 中未扫描到 filename 信息。已把原始 history 打到控制台，便于定位结构。')
    console.log('RAW_HISTORY_ENTRY:', entry)
    return
  }

  log(`从 history 扫描到 ${files.length} 个文件，开始展示...`)
  for (const f of files) {
    await showImageFile(f)
  }
}

const cloneWorkflow = (value: WorkflowMap) => JSON.parse(JSON.stringify(value)) as WorkflowMap

const buildWorkflowWithParams = () => {
  if (!workflow.value) throw new Error('请先解析并加载 workflow')
  const wf = cloneWorkflow(workflow.value)
  for (const field of paramFields.value) {
    const node = wf[field.nodeId]
    if (!node?.inputs) continue
    if (field.inputType === 'number') {
      const next = Number(field.value)
      if (!Number.isNaN(next)) node.inputs[field.inputKey] = next
    } else if (field.inputType === 'boolean') {
      node.inputs[field.inputKey] = Boolean(field.value)
    } else {
      node.inputs[field.inputKey] = String(field.value)
    }
  }
  return wf
}

const parseWorkflow = (raw: string, notify: boolean) => {
  const obj = safeJsonParse(raw)
  if (!obj || typeof obj !== 'object') {
    if (notify) alert('JSON 解析失败：请确认粘贴的是完整 workflow JSON')
    return false
  }
  workflow.value = obj as WorkflowMap
  paramFields.value = buildEditableParams(workflow.value)

  parseInfo.value = `解析成功：发现 ${paramFields.value.length} 个可编辑字段。`
  lastParsedRaw.value = raw

  log('Workflow 已加载并解析节点。')
  return true
}

const onParse = () => {
  const raw = workflowJson.value.trim()
  if (!parseWorkflow(raw, true)) return
}

const onClear = () => {
  workflowJson.value = ''
  parseInfo.value = ''
  workflow.value = null
  paramFields.value = []
  images.value = []
  shownFiles.clear()
  lastParsedRaw.value = ''
  log('已清空。')
}

const onRun = async () => {
  try {
    images.value = []
    shownFiles.clear()

    if (!workflow.value) {
      alert('请先粘贴并解析 workflow JSON')
      return
    }

    ensureWsConnected()

    const wf = buildWorkflowWithParams()
    log('正在提交任务到 /api/prompt ...')
    const res = await postPrompt(wf)

    const pid = res?.prompt_id
    lastPromptId.value = pid || null

    log(`提交成功：prompt_id=${pid || 'unknown'}；clientId=${clientId.value}`)
    log('等待 WebSocket 进度/完成消息...（如果没有消息，将在完成后手动拉 history）')

    if (pid) {
      setTimeout(() => tryFetchHistoryAndShow(pid), 2000)
      setTimeout(() => tryFetchHistoryAndShow(pid), 5000)
      setTimeout(() => tryFetchHistoryAndShow(pid), 9000)
      setTimeout(() => tryFetchHistoryAndShow(pid), 15000)
    }
  } catch (err) {
    log(`运行失败：${(err as Error)?.message || String(err)}`)
    alert('运行失败，查看日志区获取详情。常见原因：后端代理/CORS/混合内容/防火墙。')
  }
}

const onStop = async () => {
  try {
    await interrupt()
    log('已发送 Interrupt。')
  } catch (err) {
    log(`Interrupt 失败：${(err as Error)?.message || String(err)}`)
  }
}

log(`clientId=${clientId.value}`)

const fetchBackendConfig = async () => {
  try {
    const resp = await fetch(`${baseHttp()}/config`)
    if (!resp.ok) return
    const data = await resp.json()
    if (typeof data?.pcIp === 'string') pcIp.value = data.pcIp
    if (typeof data?.pcPort === 'string') pcPort.value = data.pcPort
  } catch {
    // Ignore config load failures
  }
}

onMounted(async () => {
  await fetchBackendConfig()
  connectWs()
})

watch(workflowJson, (next) => {
  const raw = next.trim()
  if (!raw) return
  if (raw === lastParsedRaw.value) return
  if (parseTimer) clearTimeout(parseTimer)
  parseTimer = setTimeout(() => {
    if (raw !== workflowJson.value.trim()) return
    parseWorkflow(raw, false)
  }, 300)
})

onBeforeUnmount(() => {
  if (ws.value) ws.value.close()
})
</script>

<template>
  <div class="space-y-4">
    <h2 class="text-xl font-semibold">ComfyUI 后端代理客户端（/api + /ws）</h2>
    <div class="text-sm text-base-content/70">
      使用流程：①读取配置文件的后端地址 → ②粘贴 Workflow JSON → ③点“解析节点”并选择节点 → ④运行 → ⑤自动显示结果图
    </div>

    <ConnectionCard
      :backend-origin="backendLabel"
      :ws-state="wsState"
      @connect="connectWs"
      @disconnect="disconnectWs"
    />

    <WorkflowCard
      v-model:workflow-json="workflowJson"
      :parse-info="parseInfo"
      @parse="onParse"
      @clear="onClear"
    />

    <ParamsCard v-model:fields="paramFields" @run="onRun" @stop="onStop" />

    <LogCard :log-text="logText" />
    <OutputCard :images="images" />
  </div>
</template>
