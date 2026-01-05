import { watch } from 'vue'
import type { EditableValue, PersistedState, ParamField } from '../types/app'
import { buildParamValueMap, safeJsonParse } from '../utils/workflow'

const STORAGE_KEY = 'comfyui-assistant-ui-state-v1'

type PersistOptions = {
  workflowJson: { value: string }
  paramFields: { value: ParamField[] }
  simpleMode: { value: boolean }
  simplePrompt: { value: string }
  simpleWidth: { value: number }
  simpleHeight: { value: number }
  autoRandomSeed: { value: boolean }
  albumSortOrder: { value: 'asc' | 'desc' }
  persistedParamValues: { value: Record<string, EditableValue> }
}

export const usePersist = (options: PersistOptions) => {
  const {
    workflowJson,
    paramFields,
    simpleMode,
    simplePrompt,
    simpleWidth,
    simpleHeight,
    autoRandomSeed,
    albumSortOrder,
    persistedParamValues,
  } = options

  let persistTimer: ReturnType<typeof setTimeout> | null = null

  const loadPersistedState = () => {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const parsed = safeJsonParse(raw)
    if (!parsed || typeof parsed !== 'object') return
    const data = parsed as Partial<PersistedState>
    if (typeof data.workflowJson === 'string') workflowJson.value = data.workflowJson
    if (data.paramValues && typeof data.paramValues === 'object') {
      persistedParamValues.value = data.paramValues as Record<string, EditableValue>
    }
    if (typeof data.simpleMode === 'boolean') simpleMode.value = data.simpleMode
    if (typeof data.simplePrompt === 'string') simplePrompt.value = data.simplePrompt
    if (typeof data.simpleWidth === 'number') simpleWidth.value = data.simpleWidth
    if (typeof data.simpleHeight === 'number') simpleHeight.value = data.simpleHeight
    if (typeof data.autoRandomSeed === 'boolean') autoRandomSeed.value = data.autoRandomSeed
    if (data.albumSortOrder === 'asc' || data.albumSortOrder === 'desc') {
      albumSortOrder.value = data.albumSortOrder
    }
  }

  const schedulePersist = () => {
    if (persistTimer) clearTimeout(persistTimer)
    persistTimer = setTimeout(() => {
      const paramValues = buildParamValueMap(paramFields.value)
      persistedParamValues.value = paramValues
      const payload: PersistedState = {
        workflowJson: workflowJson.value,
        paramValues,
        simpleMode: simpleMode.value,
        simplePrompt: simplePrompt.value,
        simpleWidth: simpleWidth.value,
        simpleHeight: simpleHeight.value,
        autoRandomSeed: autoRandomSeed.value,
        albumSortOrder: albumSortOrder.value,
      }
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
      } catch {
        // Ignore storage errors (quota, private mode)
      }
    }, 400)
  }

  const clearPersistedState = () => {
    localStorage.removeItem(STORAGE_KEY)
  }

  watch(
    [workflowJson, paramFields, simpleMode, simplePrompt, simpleWidth, simpleHeight, autoRandomSeed, albumSortOrder],
    () => {
      schedulePersist()
    },
    { deep: true },
  )

  return {
    loadPersistedState,
    clearPersistedState,
  }
}
