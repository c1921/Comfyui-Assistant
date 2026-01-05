export interface WorkflowNode {
  class_type?: string
  _meta?: { title?: string }
  inputs?: Record<string, unknown>
}

export type WorkflowMap = Record<string, WorkflowNode>

export type WorkflowJson = {
  nodes: unknown[]
  links: unknown[]
}

export type EditableValue = string | number | boolean

export type ParamField = {
  id: string
  nodeId: string
  nodeLabel: string
  inputKey: string
  value: EditableValue
  inputType: 'string' | 'number' | 'boolean'
}

export type ImageFile = {
  filename: string
  subfolder?: string
  type?: string
}

export type ImageItem = {
  src: string
  alt: string
}

export type AlbumItem = {
  name: string
  url: string
}

export type PersistedState = {
  workflowJson: string
  paramValues: Record<string, EditableValue>
  simpleMode: boolean
  simplePrompt: string
  simpleWidth: number
  simpleHeight: number
  autoRandomSeed: boolean
  albumSortOrder: 'asc' | 'desc'
}

export type SizePreset = {
  label: string
  width: number
  height: number
}
