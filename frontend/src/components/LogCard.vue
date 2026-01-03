<script setup lang="ts">
const props = defineProps<{ logText: string }>()
</script>

<template>
  <div class="card">
    <h3 style="margin: 0 0 8px 0">3) 日志 / 进度</h3>
    <div class="log">{{ props.logText || '等待操作...' }}</div>
  </div>
</template>
