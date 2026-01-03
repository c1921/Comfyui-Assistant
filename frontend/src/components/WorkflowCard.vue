<script setup lang="ts">
const props = defineProps<{ workflowJson: string; parseInfo: string }>()
const emit = defineEmits<{
  (e: 'update:workflowJson', value: string): void
  (e: 'parse'): void
  (e: 'clear'): void
}>()

const onFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = () => {
    const text = typeof reader.result === 'string' ? reader.result : ''
    emit('update:workflowJson', text)
  }
  reader.onerror = () => {
    alert('读取 JSON 文件失败，请重试。')
  }
  reader.readAsText(file)
  target.value = ''
}
</script>

<template>
  <div class="card">
    <h3 style="margin: 0 0 8px 0">1) 粘贴或上传你的完整 Workflow JSON</h3>
    <textarea
      :value="props.workflowJson"
      placeholder="把你从 ComfyUI 导出的 workflow JSON 粘贴到这里..."
      @input="emit('update:workflowJson', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <div class="row" style="margin-top: 8px; gap: 8px; flex-wrap: wrap">
      <label class="btn">
        上传 JSON
        <input
          type="file"
          accept=".json,application/json"
          style="display: none"
          @change="onFileChange"
        />
      </label>
      <button @click="emit('parse')">解析节点</button>
      <button @click="emit('clear')">清空</button>
    </div>
    <div class="small" style="margin-top: 8px">{{ props.parseInfo }}</div>
  </div>
</template>
