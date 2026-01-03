<script setup lang="ts">
const props = defineProps<{ pcIp: string; pcPort: string; wsState: string }>()
const emit = defineEmits<{
  (e: 'update:pcIp', value: string): void
  (e: 'update:pcPort', value: string): void
  (e: 'connect'): void
  (e: 'disconnect'): void
}>()
</script>

<template>
  <div class="card">
    <div class="row">
      <label>
        后端 IP：
        <input
          :value="props.pcIp"
          placeholder="为空则用当前站点"
          style="width: 180px"
          @input="emit('update:pcIp', ($event.target as HTMLInputElement).value)"
        />
      </label>
      <label>
        后端端口：
        <input
          :value="props.pcPort"
          placeholder="可留空"
          style="width: 90px"
          @input="emit('update:pcPort', ($event.target as HTMLInputElement).value)"
        />
      </label>
      <span class="pill">{{ props.wsState }}</span>
      <button @click="emit('connect')">连接 WS</button>
      <button @click="emit('disconnect')">断开 WS</button>
    </div>
    <div class="small" style="margin-top: 6px">
      提示：先点“连接 WS”，再点“运行”更稳（也可直接运行，代码会自动尝试连接）。
    </div>
  </div>
</template>
