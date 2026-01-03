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
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <h3 class="mb-2 text-base font-semibold">粘贴或上传 Workflow JSON</h3>
    <textarea
      :value="props.workflowJson"
      class="textarea textarea-bordered textarea-sm w-full min-h-40"
      placeholder="把你从 ComfyUI 导出的 workflow JSON 粘贴到这里..."
      @input="emit('update:workflowJson', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <div class="mt-2 flex flex-wrap items-center gap-3">
      <input
        type="file"
        accept=".json,application/json"
        class="input input-bordered input-sm max-w-sm"
        aria-label="file-input"
        @change="onFileChange"
      />
      <button class="btn btn-primary btn-sm" @click="emit('parse')">解析节点</button>
      <button class="btn btn-outline btn-sm" @click="emit('clear')">清空</button>
    </div>
    <div class="mt-2 text-xs text-base-content/70">{{ props.parseInfo }}</div>
  </div>
</template>
