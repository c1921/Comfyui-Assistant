<script setup lang="ts">
const props = defineProps<{ logText: string }>()
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <h3 class="mb-2 text-base font-semibold">4) 日志</h3>
    <div class="textarea textarea-bordered text-xs w-full min-h-40 max-h-64 overflow-auto whitespace-pre-wrap font-mono">
      {{ props.logText || '等待操作...' }}
    </div>
  </div>
</template>
