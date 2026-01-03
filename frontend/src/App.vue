<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import ConnectionCard from './components/ConnectionCard.vue'
import WorkflowCard from './components/WorkflowCard.vue'
import ParamsCard from './components/ParamsCard.vue'
import LogCard from './components/LogCard.vue'
import OutputCard from './components/OutputCard.vue'

interface WorkflowNode {
  class_type?: string
  _meta?: { title?: string }
  inputs?: Record<string, unknown>
}

type WorkflowMap = Record<string, WorkflowNode>

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

const posPrompt = ref('1girl')
const negPrompt = ref('')

const posNode = ref('')
const negNode = ref('')

const seed = ref(0)
const steps = ref(8)
const cfg = ref(1)
const width = ref(1024)
const height = ref(1024)
const batch = ref(1)

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

const findNodesByClass = (wf: WorkflowMap, classType: string) => {
  const out: { id: string; node: WorkflowNode }[] = []
  for (const [id, node] of Object.entries(wf || {})) {
    if (node && node.class_type === classType) out.push({ id, node })
  }
  return out
}

const findSizeCandidates = (wf: WorkflowMap) => {
  const candidates: { id: string; node: WorkflowNode }[] = []
  const classSet = new Set([
    'EmptyLatentImage',
    'EmptyLatentImage (WAS)',
    'LatentUpscale',
    'ImageResize',
    'SDXLAspectRatio',
  ])
  for (const [id, node] of Object.entries(wf || {})) {
    if (!node) continue
    if (classSet.has(node.class_type || '')) candidates.push({ id, node })
    const ins = node.inputs || {}
    if (typeof ins.width === 'number' && typeof ins.height === 'number') {
      candidates.push({ id, node })
    }
  }
  const seen = new Set<string>()
  return candidates.filter((x) => (seen.has(x.id) ? false : (seen.add(x.id), true)))
}

const getLinkedNodeId = (value: unknown) => {
  if (Array.isArray(value) && typeof value[0] === 'string') return value[0]
  if (typeof value === 'string') return value
  return ''
}

const findPromptNodesFromSampler = (wf: WorkflowMap, samplerId?: string) => {
  if (!samplerId) return { posId: '', negId: '' }
  const node = wf[samplerId]
  const ins = node?.inputs || {}
  const posId = getLinkedNodeId(ins.positive)
  const negId = getLinkedNodeId(ins.negative)
  return { posId, negId }
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

  const posId = posNode.value
  const negId = negNode.value
  const ksId = findNodesByClass(wf, 'KSampler')[0]?.id || ''
  const szId = findSizeCandidates(wf)[0]?.id || ''

  if (posId && wf[posId]?.inputs) wf[posId].inputs.text = posPrompt.value ?? ''
  if (negId && wf[negId]?.inputs) wf[negId].inputs.text = negPrompt.value ?? ''

  const seedValue = Number(seed.value)
  const stepsValue = Number(steps.value)
  const cfgValue = Number(cfg.value)

  if (ksId && wf[ksId]?.inputs) {
    if (!Number.isNaN(seedValue)) wf[ksId].inputs.seed = seedValue
    if (!Number.isNaN(stepsValue)) wf[ksId].inputs.steps = stepsValue
    if (!Number.isNaN(cfgValue)) wf[ksId].inputs.cfg = cfgValue
  }

  const w = Number(width.value)
  const h = Number(height.value)
  const b = Number(batch.value)

  if (szId && wf[szId]?.inputs) {
    if (!Number.isNaN(w) && 'width' in wf[szId].inputs) wf[szId].inputs.width = w
    if (!Number.isNaN(h) && 'height' in wf[szId].inputs) wf[szId].inputs.height = h
    if (!Number.isNaN(b) && 'batch_size' in wf[szId].inputs) wf[szId].inputs.batch_size = b
    if (!Number.isNaN(b) && 'batch' in wf[szId].inputs) wf[szId].inputs.batch = b
  }

  return wf
}

const onParse = () => {
  const raw = workflowJson.value.trim()
  const obj = safeJsonParse(raw)
  if (!obj || typeof obj !== 'object') {
    alert('JSON 解析失败：请确认粘贴的是完整 workflow JSON')
    return
  }
  workflow.value = obj as WorkflowMap

  const clips = findNodesByClass(obj, 'CLIPTextEncode')
  const ksamplers = findNodesByClass(obj, 'KSampler')
  const samplerId = ksamplers[0]?.id || ''
  const { posId, negId } = findPromptNodesFromSampler(obj, samplerId)
  if (posId) posNode.value = posId
  if (negId) negNode.value = negId
  if (!posNode.value && clips[0]) posNode.value = clips[0].id
  if (!negNode.value && clips[1]) negNode.value = clips[1].id

  parseInfo.value =
    `解析成功：CLIPTextEncode=${clips.length}，KSampler=${ksamplers.length}。` +
    '（如选项为空，说明你的图里对应节点类型不同，需要我按你的 workflow 调整识别规则。）'

  log('Workflow 已加载并解析节点。')
}

const onClear = () => {
  workflowJson.value = ''
  parseInfo.value = ''
  workflow.value = null
  images.value = []
  shownFiles.clear()
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

onBeforeUnmount(() => {
  if (ws.value) ws.value.close()
})
</script>

<template>
  <div>
    <h2>ComfyUI 后端代理客户端（/api + /ws）</h2>
    <div class="small">
      使用流程：①确认后端地址 → ②粘贴 Workflow JSON → ③点“解析节点”并选择节点 → ④运行 → ⑤自动显示结果图
    </div>

    <ConnectionCard
      v-model:pc-ip="pcIp"
      v-model:pc-port="pcPort"
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

    <ParamsCard
      v-model:pos-prompt="posPrompt"
      v-model:neg-prompt="negPrompt"
      v-model:seed="seed"
      v-model:steps="steps"
      v-model:cfg="cfg"
      v-model:width="width"
      v-model:height="height"
      v-model:batch="batch"
      @run="onRun"
      @stop="onStop"
    />

    <LogCard :log-text="logText" />
    <OutputCard :images="images" />
  </div>
</template>
