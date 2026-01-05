import { ref, watch } from 'vue'
import type { EditableValue, ParamField, WorkflowMap } from '../types/app'
import { convertWorkflow } from '../services/api'
import {
  applyParamValueMap,
  buildEditableParams,
  cloneWorkflow,
  looksLikeApiPrompt,
  looksLikeWorkflowJson,
  safeJsonParse,
  updateFieldsByKey,
} from '../utils/workflow'

type WorkflowOptions = {
  baseHttp: () => string
  log: (msg: string) => void
  persistedParamValues: { value: Record<string, EditableValue> }
}

export const useWorkflow = ({ baseHttp, log, persistedParamValues }: WorkflowOptions) => {
  const workflow = ref<WorkflowMap | null>(null)
  const workflowJson = ref('')
  const parseInfo = ref('')
  const lastParsedRaw = ref('')
  let parseTimer: ReturnType<typeof setTimeout> | null = null
  let autoParseEnabled = true

  const paramFields = ref<ParamField[]>([])
  const simpleMode = ref(false)
  const simplePrompt = ref('')
  const simpleWidth = ref(512)
  const simpleHeight = ref(512)
  const autoRandomSeed = ref(true)

  const sizePresets = [
    { label: '512x512', width: 512, height: 512 },
    { label: '768x512', width: 768, height: 512 },
    { label: '512x768', width: 512, height: 768 },
    { label: '1024x1024', width: 1024, height: 1024 },
    { label: '1024x768', width: 1024, height: 768 },
    { label: '768x1024', width: 768, height: 1024 },
  ]

  const syncSimpleFromFields = (fields: ParamField[]) => {
    const promptField = fields.find((f) => f.inputKey === 'text' && typeof f.value === 'string')
    if (promptField) simplePrompt.value = String(promptField.value)

    const widthField = fields.find((f) => f.inputKey === 'width' && typeof f.value === 'number')
    const heightField = fields.find((f) => f.inputKey === 'height' && typeof f.value === 'number')
    if (widthField) simpleWidth.value = Number(widthField.value)
    if (heightField) simpleHeight.value = Number(heightField.value)
  }

  const applySimpleOverrides = () => {
    const preferredPromptNodes = ['CLIPTextEncode', 'CLIPTextEncode (1)', 'CLIPTextEncode (2)']
    let next = updateFieldsByKey(paramFields.value, 'text', simplePrompt.value, preferredPromptNodes)
    next = updateFieldsByKey(next, 'width', simpleWidth.value, [
      'EmptyLatentImage',
      'EmptySD3LatentImage',
    ])
    next = updateFieldsByKey(next, 'height', simpleHeight.value, [
      'EmptyLatentImage',
      'EmptySD3LatentImage',
    ])
    paramFields.value = next
  }

  const randomizeSeedFields = () => {
    if (!autoRandomSeed.value) return
    let updated = false
    const next = paramFields.value.map((field) => {
      if (field.inputKey.trim().toLowerCase() !== 'seed') return field
      const nextSeed = Math.floor(Math.random() * 2 ** 32)
      updated = true
      if (field.inputType === 'string') {
        return { ...field, value: String(nextSeed) }
      }
      return { ...field, value: nextSeed }
    })
    if (updated) paramFields.value = next
  }

  const parseWorkflow = async (raw: string, notify: boolean, applyPersisted = true) => {
    const obj = safeJsonParse(raw)
    if (!obj || typeof obj !== 'object') {
      if (notify) alert('JSON 解析失败：请确认粘贴的是完整 workflow JSON')
      return false
    }
    if (looksLikeWorkflowJson(obj) && !looksLikeApiPrompt(obj)) {
      parseInfo.value = '检测到编辑器工作流，正在转换为 API 格式...'
      try {
        const converted = await convertWorkflow(baseHttp(), obj)
        workflow.value = converted
        const fields = buildEditableParams(workflow.value)
        paramFields.value = applyPersisted ? applyParamValueMap(fields, persistedParamValues.value) : fields
        syncSimpleFromFields(paramFields.value)
      } catch (err) {
        parseInfo.value = '转换失败：请检查后端日志或工作流内容'
        log(`工作流转换失败：${(err as Error)?.message || String(err)}`)
        return false
      }
    } else if (looksLikeApiPrompt(obj)) {
      workflow.value = obj as WorkflowMap
      const fields = buildEditableParams(workflow.value)
      paramFields.value = applyPersisted ? applyParamValueMap(fields, persistedParamValues.value) : fields
      syncSimpleFromFields(paramFields.value)
    } else {
      if (notify) alert('JSON 结构不符合 workflow 或 API prompt')
      return false
    }

    parseInfo.value = `解析成功：发现 ${paramFields.value.length} 个可编辑字段。`
    lastParsedRaw.value = raw

    log('Workflow 已加载并解析节点。')
    return true
  }

  const onParse = async () => {
    const raw = workflowJson.value.trim()
    if (!(await parseWorkflow(raw, true, false))) return
  }

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

  const clearWorkflowState = () => {
    workflowJson.value = ''
    parseInfo.value = ''
    workflow.value = null
    paramFields.value = []
    lastParsedRaw.value = ''
  }

  watch(workflowJson, (next) => {
    if (!autoParseEnabled) return
    const raw = next.trim()
    if (!raw) return
    if (raw === lastParsedRaw.value) return
    if (parseTimer) clearTimeout(parseTimer)
    parseTimer = setTimeout(() => {
      if (raw !== workflowJson.value.trim()) return
      void parseWorkflow(raw, false, false)
    }, 300)
  })

  watch(simpleMode, (enabled) => {
    if (enabled) syncSimpleFromFields(paramFields.value)
  })

  return {
    workflow,
    workflowJson,
    parseInfo,
    paramFields,
    simpleMode,
    simplePrompt,
    simpleWidth,
    simpleHeight,
    autoRandomSeed,
    sizePresets,
    parseWorkflow,
    onParse,
    buildWorkflowWithParams,
    applySimpleOverrides,
    randomizeSeedFields,
    clearWorkflowState,
    setAutoParseEnabled: (enabled: boolean) => {
      autoParseEnabled = enabled
    },
  }
}
