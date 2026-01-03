<script setup lang="ts">
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
  (e: 'update:sortOrder', value: 'asc' | 'desc'): void
}>()

const onSortChange = (ev: Event) => {
  const value = (ev.target as HTMLSelectElement).value === 'asc' ? 'asc' : 'desc'
  emit('update:sortOrder', value)
  emit('refresh')
}
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <h3 class="text-base font-semibold">6) 相册</h3>
      <div class="flex items-center gap-2">
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
      <img
        v-for="item in props.items"
        :key="item.url"
        :src="item.url"
        :alt="item.name"
        class="h-auto w-full rounded-lg border border-base-200 shadow-sm"
        loading="lazy"
      />
    </div>
  </div>
</template>
