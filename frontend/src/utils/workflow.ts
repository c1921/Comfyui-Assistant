import type { EditableValue, ParamField, WorkflowJson, WorkflowMap } from '../types/app'

export const safeJsonParse = (raw: string) => {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const looksLikeApiPrompt = (value: unknown): value is WorkflowMap => {
  if (!value || typeof value !== 'object') return false
  for (const item of Object.values(value as Record<string, unknown>)) {
    if (item && typeof item === 'object' && 'class_type' in item && 'inputs' in item) {
      return true
    }
    break
  }
  return false
}

export const looksLikeWorkflowJson = (value: unknown): value is WorkflowJson => {
  if (!value || typeof value !== 'object') return false
  const obj = value as WorkflowJson
  return Array.isArray(obj.nodes) && Array.isArray(obj.links)
}

const isEditableValue = (value: unknown): value is EditableValue =>
  typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'

export const buildEditableParams = (wf: WorkflowMap) => {
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

export const buildParamValueMap = (fields: ParamField[]) => {
  const map: Record<string, EditableValue> = {}
  for (const field of fields) {
    map[field.id] = field.value
  }
  return map
}

export const applyParamValueMap = (fields: ParamField[], map: Record<string, EditableValue>) =>
  fields.map((field) => {
    if (!Object.prototype.hasOwnProperty.call(map, field.id)) return field
    const mapped = map[field.id]
    if (mapped === undefined) return field
    return { ...field, value: mapped }
  })

export const updateFieldsByKey = (
  fields: ParamField[],
  key: string,
  value: EditableValue,
  preferredNodes: string[] = [],
) => {
  let updated = false
  const next = fields.map((field) => {
    if (field.inputKey !== key) return field
    if (preferredNodes.length && !preferredNodes.includes(field.nodeLabel)) return field
    updated = true
    return { ...field, value }
  })
  if (updated) return next
  return fields.map((field) => (field.inputKey === key ? { ...field, value } : field))
}

export const cloneWorkflow = (value: WorkflowMap) =>
  JSON.parse(JSON.stringify(value)) as WorkflowMap
