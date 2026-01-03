<script setup lang="ts">
const props = defineProps<{ backendOrigin: string; wsState: string }>()
const emit = defineEmits<{
  (e: 'connect'): void
  (e: 'disconnect'): void
}>()
</script>

<template>
  <div class="card">
    <div class="row">
      <span>
        后端地址（配置文件）：<strong>{{ props.backendOrigin }}</strong>
      </span>
      <span class="pill">{{ props.wsState }}</span>
      <button @click="emit('connect')">连接 WS</button>
      <button @click="emit('disconnect')">断开 WS</button>
    </div>
    <div class="small" style="margin-top: 6px">
      提示：先点“连接 WS”，再点“运行”更稳（也可直接运行，代码会自动尝试连接）。
    </div>
  </div>
</template>
