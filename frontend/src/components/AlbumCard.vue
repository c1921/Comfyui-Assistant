<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface AlbumItem {
  name: string
  url: string
}

const props = defineProps<{
  items: AlbumItem[]
  errorMessage: string
  sortOrder: 'asc' | 'desc'
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'delete', item: AlbumItem): void
  (e: 'update:sortOrder', value: 'asc' | 'desc'): void
}>()

type FolderOption = {
  value: string
  label: string
}

const selectedFolder = ref('')

const onSortChange = (ev: Event) => {
  const value = (ev.target as HTMLSelectElement).value === 'asc' ? 'asc' : 'desc'
  emit('update:sortOrder', value)
  emit('refresh')
}
const onDeleteClick = (item: AlbumItem) => emit('delete', item)

const getFolderKey = (name: string) => {
  const normalized = name.replace(/\\/g, '/')
  const idx = normalized.lastIndexOf('/')
  return idx === -1 ? '' : normalized.slice(0, idx)
}

const folderOptions = computed<FolderOption[]>(() => {
  const counts = new Map<string, number>()
  for (const item of props.items) {
    const key = getFolderKey(item.name)
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return Array.from(counts.entries())
    .map(([value, count]) => ({
      value,
      label: `${value || '根目录'}（${count}）`,
    }))
    .sort((a, b) => {
      if (a.value === '' && b.value !== '') return -1
      if (a.value !== '' && b.value === '') return 1
      return a.value.localeCompare(b.value)
    })
})

watch(
  folderOptions,
  (options) => {
    if (!options.length) {
      selectedFolder.value = ''
      return
    }
    if (!options.some((opt) => opt.value === selectedFolder.value)) {
      const first = options[0]
      if (first) selectedFolder.value = first.value
    }
  },
  { immediate: true },
)

const filteredItems = computed(() =>
  props.items.filter((item) => getFolderKey(item.name) === selectedFolder.value),
)
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <h3 class="text-base font-semibold">相册</h3>
      <div class="flex items-center gap-2">
        <select
          class="select select-xs select-bordered"
          :value="selectedFolder"
          @change="selectedFolder = ($event.target as HTMLSelectElement).value"
        >
          <option v-for="option in folderOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <select class="select select-xs select-bordered" :value="props.sortOrder" @change="onSortChange">
          <option value="desc">倒序</option>
          <option value="asc">正序</option>
        </select>
        <button class="btn btn-xs btn-outline" @click="emit('refresh')">刷新</button>
      </div>
    </div>
    <div v-if="props.errorMessage" class="mb-2 text-xs text-error">
      {{ props.errorMessage }}
    </div>
    <div v-if="props.items.length === 0" class="text-sm text-base-content/60">
      暂无图片（请检查配置文件 albumPath 是否正确）。
    </div>
    <div v-else class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="item in filteredItems"
        :key="item.url"
        class="group overflow-hidden rounded-lg border border-base-200 shadow-sm"
      >
        <div class="relative">
          <img
            :src="item.url"
            :alt="item.name"
            class="h-auto w-full"
            loading="lazy"
          />
          <button
            class="btn btn-xs btn-error absolute right-2 top-2 hidden shadow-sm opacity-0 transition-opacity sm:inline-flex sm:group-hover:opacity-100"
            type="button"
            @click="onDeleteClick(item)"
            aria-label="删除图片"
          >
            删除
          </button>
        </div>
        <div class="flex items-center justify-between gap-2 p-2 sm:hidden">
          <span class="min-w-0 flex-1 truncate text-xs text-base-content/70">
            {{ item.name }}
          </span>
          <button
            class="btn btn-xs btn-error"
            type="button"
            @click="onDeleteClick(item)"
            aria-label="删除图片"
          >
            删除
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
