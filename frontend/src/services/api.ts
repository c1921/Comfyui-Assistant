import type { AlbumItem, ImageFile, WorkflowJson, WorkflowMap } from '../types/app'

export const convertWorkflow = async (baseHttp: string, workflowObj: WorkflowJson) => {
  const resp = await fetch(`${baseHttp}/workflow/convert`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ workflow: workflowObj }),
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`HTTP ${resp.status} ${text}`.trim())
  }
  return (await resp.json()) as WorkflowMap
}

export const postPrompt = async (baseHttp: string, prompt: WorkflowMap, clientId: string) => {
  const resp = await fetch(`${baseHttp}/prompt`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, client_id: clientId }),
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`POST /api/prompt 失败：HTTP ${resp.status} ${text}`)
  }
  return await resp.json()
}

export const interrupt = async (baseHttp: string) => {
  const resp = await fetch(`${baseHttp}/interrupt`, { method: 'POST' })
  if (!resp.ok) throw new Error(`POST /api/interrupt 失败：HTTP ${resp.status}`)
}

export const getHistory = async (baseHttp: string, promptId: string) => {
  const resp = await fetch(`${baseHttp}/history/${encodeURIComponent(promptId)}`)
  if (!resp.ok) {
    const fallback = await fetch(`${baseHttp}/history`)
    if (!fallback.ok) {
      throw new Error(`GET /api/history 失败：HTTP ${fallback.status}`)
    }
    return await fallback.json()
  }
  return await resp.json()
}

export const getAlbum = async (baseHttp: string, order: 'asc' | 'desc') => {
  const resp = await fetch(`${baseHttp}/album/list?order=${encodeURIComponent(order)}`)
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`HTTP ${resp.status} ${text}`.trim())
  }
  return (await resp.json()) as AlbumItem[]
}

export const deleteAlbumItem = async (baseHttp: string, name: string) => {
  const resp = await fetch(`${baseHttp}/album/file/${encodeURIComponent(name)}`, {
    method: 'DELETE',
  })
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`HTTP ${resp.status} ${text}`.trim())
  }
}

export const getConfig = async (baseHttp: string) => {
  const resp = await fetch(`${baseHttp}/config`)
  if (!resp.ok) return null
  return await resp.json()
}

export const getComfyHealth = async (baseHttp: string) => {
  const resp = await fetch(`${baseHttp}/comfy/health`)
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`)
  }
  return await resp.json()
}

export const buildViewUrl = (baseHttp: string, file: ImageFile) => {
  const filename = encodeURIComponent(file.filename)
  const subfolder = encodeURIComponent(file.subfolder || '')
  const type = encodeURIComponent(file.type || 'output')
  return `${baseHttp}/view?filename=${filename}&subfolder=${subfolder}&type=${type}`
}
