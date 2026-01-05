import { computed, onBeforeUnmount, ref } from 'vue'
import type { ImageFile, ImageItem, WorkflowMap } from '../types/app'
import { buildViewUrl, getHistory, interrupt, postPrompt } from '../services/api'

type RunnerOptions = {
  baseHttp: () => string
  baseWs: () => string
  fetchAlbum: () => Promise<void>
}

const makeUUID = (): string => {
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

export const useRunner = ({ baseHttp, baseWs, fetchAlbum }: RunnerOptions) => {
  const clientId = ref(makeUUID())
  const ws = ref<WebSocket | null>(null)
  const wsState = ref('WS: 未连接')
  const lastPromptId = ref<string | null>(null)
  const shownFiles = new Set<string>()

  const logLines = ref<string[]>([])
  const logText = computed(() => logLines.value.join('\n'))
  const images = ref<ImageItem[]>([])

  const progressValue = ref<number | null>(null)
  const progressMax = ref<number | null>(null)
  const progressNode = ref<string | null>(null)
  const progressQueueRemaining = ref<number | null>(null)
  const progressStatus = ref('等待进度...')
  const isRunning = ref(false)
  const isOutputDone = ref(false)

  const log = (msg: string) => {
    const ts = new Date().toLocaleTimeString()
    logLines.value = [`[${ts}] ${msg}`, ...logLines.value]
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
          if (typeof q === 'number') {
            progressQueueRemaining.value = q
            log(`队列剩余：${q}`)
            if (isOutputDone.value && q === 0) isRunning.value = false
          }
          return
        }

        if (msg.type === 'progress') {
          const v = msg.data?.value
          const m = msg.data?.max
          if (typeof v === 'number' && typeof m === 'number') {
            progressValue.value = v
            progressMax.value = m
            progressStatus.value = `运行中：${v}/${m}`
            log(`进度：${v}/${m}`)
          }
          return
        }

        if (msg.type === 'executed' || msg.type === 'execution_success') {
          const pid = msg.data?.prompt_id || msg.prompt_id || lastPromptId.value
          progressStatus.value = '执行完成'
          isOutputDone.value = true
          log(`执行完成（prompt_id=${pid || 'unknown'}），尝试拉取历史输出...`)
          await tryFetchHistoryAndShow(pid)
          return
        }

        if (msg.type === 'executing') {
          const node = msg.data?.node
          const pid = msg.data?.prompt_id || lastPromptId.value
          if (node === null) {
            progressNode.value = null
            progressStatus.value = '执行结束'
            isOutputDone.value = true
            log(`执行结束信号（prompt_id=${pid || 'unknown'}），尝试拉取历史输出...`)
            await tryFetchHistoryAndShow(pid)
          } else {
            progressNode.value = String(node)
            progressStatus.value = `正在执行节点：${node}`
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

  const showImageFile = async (file: ImageFile) => {
    if (!file?.filename) return
    const key = `${file.type || 'output'}|${file.subfolder || ''}|${file.filename}`
    if (shownFiles.has(key)) return
    shownFiles.add(key)

    const url = buildViewUrl(baseHttp(), file)
    images.value = [{ src: url, alt: file.filename }, ...images.value]
    log(`已展示图片：${file.filename}`)
  }

  const tryFetchHistoryAndShow = async (promptId?: string | null) => {
    if (!promptId) return

    let hist: any
    try {
      hist = await getHistory(baseHttp(), promptId)
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
    await fetchAlbum()
  }

  const prepareForRun = () => {
    isRunning.value = true
    isOutputDone.value = false
    images.value = []
    shownFiles.clear()
    progressValue.value = null
    progressMax.value = null
    progressNode.value = null
    progressQueueRemaining.value = null
    progressStatus.value = '已提交，等待进度...'
  }

  const resetRunnerState = () => {
    images.value = []
    shownFiles.clear()
    progressValue.value = null
    progressMax.value = null
    progressNode.value = null
    progressQueueRemaining.value = null
    progressStatus.value = '等待进度...'
    isRunning.value = false
    isOutputDone.value = false
  }

  const sendPrompt = async (wf: WorkflowMap) => {
    const res = await postPrompt(baseHttp(), wf, clientId.value)
    const pid = res?.prompt_id
    lastPromptId.value = pid || null
    return pid as string | null
  }

  const sendInterrupt = async () => {
    await interrupt(baseHttp())
  }

  onBeforeUnmount(() => {
    if (ws.value) ws.value.close()
  })

  return {
    clientId,
    wsState,
    logLines,
    logText,
    log,
    images,
    progressValue,
    progressMax,
    progressNode,
    progressQueueRemaining,
    progressStatus,
    isRunning,
    isOutputDone,
    ensureWsConnected,
    connectWs,
    disconnectWs,
    prepareForRun,
    resetRunnerState,
    sendPrompt,
    sendInterrupt,
    tryFetchHistoryAndShow,
  }
}
