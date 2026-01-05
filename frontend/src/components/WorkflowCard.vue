<script setup lang="ts">
import { ref } from 'vue'
import type { AlbumItem } from '../types/app'

const props = defineProps<{
  workflowJson: string
  parseInfo: string
  albumItems: AlbumItem[]
  imageParseInfo: string
  imageParseLoading: boolean
}>()
const emit = defineEmits<{
  (e: 'update:workflowJson', value: string): void
  (e: 'parse'): void
  (e: 'clear'): void
  (e: 'parse-image-file', value: File): void
  (e: 'parse-image-album', value: string): void
}>()

const selectedAlbum = ref('')

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

const onPngChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  emit('parse-image-file', file)
  target.value = ''
}

const onParseAlbum = () => {
  if (!selectedAlbum.value) return
  emit('parse-image-album', selectedAlbum.value)
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

    <div class="mt-4 border-t border-base-200 pt-3">
      <h4 class="text-sm font-semibold">从图片解析 Workflow</h4>
      <div class="mt-2 flex flex-wrap items-center gap-3">
        <input
          type="file"
          accept=".png,image/png"
          class="input input-bordered input-sm max-w-sm"
          aria-label="png-input"
          @change="onPngChange"
        />
        <span class="text-xs text-base-content/60">上传 PNG 并解析其中的 workflow/prompt</span>
      </div>
      <div class="mt-2 flex flex-wrap items-center gap-3">
        <select
          v-model="selectedAlbum"
          class="select select-bordered select-sm max-w-xs"
          :disabled="props.albumItems.length === 0"
        >
          <option value="">选择相册图片</option>
          <option v-for="item in props.albumItems" :key="item.name" :value="item.name">
            {{ item.name }}
          </option>
        </select>
        <button
          class="btn btn-outline btn-sm"
          :disabled="!selectedAlbum || props.imageParseLoading"
          @click="onParseAlbum"
        >
          解析相册图片
        </button>
      </div>
      <div class="mt-2 text-xs text-base-content/70">
        <span v-if="props.imageParseLoading">正在解析 PNG...</span>
        <span v-else>{{ props.imageParseInfo }}</span>
      </div>
    </div>
  </div>
</template>
