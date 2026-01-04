<script setup lang="ts">
const props = defineProps<{ backendOrigin: string; wsState: string; comfyState: string }>()
const emit = defineEmits<{
  (e: 'connect'): void
  (e: 'disconnect'): void
}>()
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <div class="flex flex-wrap items-center gap-3">
      <span class="text-sm text-base-content/80">
        后端地址（配置文件）：<strong class="font-semibold text-base-content">{{ props.backendOrigin }}</strong>
      </span>
      <span class="badge badge-outline text-xs">{{ props.wsState }}</span>
      <span class="badge badge-outline text-xs">{{ props.comfyState }}</span>
      <button class="btn btn-sm btn-primary" @click="emit('connect')">连接 WS</button>
      <button class="btn btn-sm btn-outline" @click="emit('disconnect')">断开 WS</button>
    </div>
    <div class="mt-1.5 text-xs text-base-content/70">
      提示：先点“连接 WS”，再点“运行”更稳（也可直接运行，代码会自动尝试连接）。
    </div>
  </div>
</template>
