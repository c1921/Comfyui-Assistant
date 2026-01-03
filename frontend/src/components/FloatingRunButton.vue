<script setup lang="ts">
const props = defineProps<{
  isRunning: boolean
}>()

const emit = defineEmits<{
  (e: 'run'): void
}>()
</script>

<template>
  <button
    class="btn btn-circle btn-primary fixed bottom-20 right-4 z-20 shadow-lg"
    type="button"
    @click="emit('run')"
    aria-label="运行"
  >
    <span
      v-if="props.isRunning"
      class="icon-[svg-spinners--90-ring] size-4.5 shrink-0"
    ></span>
    <span
      v-else
      class="icon-[tabler--player-play] size-4.5 shrink-0"
    ></span>
  </button>
</template>
