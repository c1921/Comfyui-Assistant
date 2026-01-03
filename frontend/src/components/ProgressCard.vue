<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: number | null
  max: number | null
  node: string | null
  queueRemaining: number | null
  statusText: string
}>()

const percent = computed(() => {
  if (typeof props.value !== 'number' || typeof props.max !== 'number') return null
  if (props.max <= 0) return null
  const ratio = Math.min(1, Math.max(0, props.value / props.max))
  return Math.round(ratio * 100)
})
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <h3 class="mb-2 text-base font-semibold">3) 运行进度</h3>
    <div class="mb-2 flex flex-wrap gap-3 text-xs text-base-content/70">
      <span>状态：{{ props.statusText || '等待进度...' }}</span>
      <span v-if="props.queueRemaining !== null">队列剩余：{{ props.queueRemaining }}</span>
      <span v-if="props.node">当前节点：{{ props.node }}</span>
    </div>
    <progress
      class="progress progress-primary w-full"
      :value="props.value ?? undefined"
      :max="props.max ?? undefined"
    ></progress>
    <div v-if="percent !== null" class="mt-1 text-xs text-base-content/60">
      {{ percent }}%（{{ props.value }}/{{ props.max }}）
    </div>
  </div>
</template>
