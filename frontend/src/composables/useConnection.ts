import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getComfyHealth, getConfig } from '../services/api'

const toWsOrigin = (origin: string) =>
  origin.replace(/^http(s?):\/\//, (_, s) => (s ? 'wss://' : 'ws://'))

export const useConnection = () => {
  const pcIp = ref('')
  const pcPort = ref('8000')
  const comfyState = ref('ComfyUI: 未检测')
  const backendOrigin = computed(() => {
    const host = pcIp.value.trim()
    const port = pcPort.value.trim()
    if (!host) return window.location.origin
    if (port) return `http://${host}:${port}`
    return `http://${host}`
  })
  const baseHttp = computed(() => `${backendOrigin.value}/api`)
  const baseWs = computed(() => toWsOrigin(backendOrigin.value))
  const backendLabel = computed(() => backendOrigin.value)
  let comfyTimer: ReturnType<typeof setInterval> | null = null

  const refreshComfyHealth = async () => {
    try {
      const data = await getComfyHealth(baseHttp.value)
      if (data?.status === 'ok') {
        comfyState.value = 'ComfyUI: 在线'
      } else {
        comfyState.value = 'ComfyUI: 未启动'
      }
    } catch {
      comfyState.value = 'ComfyUI: 连接失败'
    }
  }

  const fetchBackendConfig = async () => {
    try {
      const data = await getConfig(baseHttp.value)
      if (!data) return
      if (typeof data?.pcIp === 'string') pcIp.value = data.pcIp
      if (typeof data?.pcPort === 'string') pcPort.value = data.pcPort
    } catch {
      // Ignore config load failures
    }
  }

  const startHealthTimer = () => {
    if (comfyTimer) clearInterval(comfyTimer)
    comfyTimer = setInterval(refreshComfyHealth, 10000)
  }

  const stopHealthTimer = () => {
    if (comfyTimer) clearInterval(comfyTimer)
    comfyTimer = null
  }

  watch([pcIp, pcPort], () => {
    refreshComfyHealth()
  })

  onBeforeUnmount(() => {
    stopHealthTimer()
  })

  return {
    pcIp,
    pcPort,
    comfyState,
    backendOrigin,
    baseHttp,
    baseWs,
    backendLabel,
    refreshComfyHealth,
    fetchBackendConfig,
    startHealthTimer,
    stopHealthTimer,
  }
}
