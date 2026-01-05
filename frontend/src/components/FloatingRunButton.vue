<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{
  isRunning: boolean
}>()

const emit = defineEmits<{
  (e: 'run'): void
}>()

const btnRef = ref<HTMLButtonElement | null>(null)
const pos = ref({ x: 0, y: 0 })
const posReady = ref(false)
const isDragging = ref(false)
const wasDrag = ref(false)
let dragOffsetX = 0
let dragOffsetY = 0
let suppressClick = false

const storageKey = 'floating-run-btn-pos'

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const getBounds = () => {
  const btn = btnRef.value
  const width = btn?.offsetWidth ?? 56
  const height = btn?.offsetHeight ?? 56
  const maxX = window.innerWidth - width - 8
  const maxY = window.innerHeight - height - 8
  return { width, height, maxX, maxY }
}

const setPosition = (x: number, y: number) => {
  const { maxX, maxY } = getBounds()
  pos.value = {
    x: clamp(x, 8, maxX),
    y: clamp(y, 8, maxY),
  }
  localStorage.setItem(storageKey, JSON.stringify(pos.value))
}

const initPosition = () => {
  const saved = localStorage.getItem(storageKey)
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      if (typeof parsed?.x === 'number' && typeof parsed?.y === 'number') {
        setPosition(parsed.x, parsed.y)
        posReady.value = true
        return
      }
    } catch {
      // Ignore invalid storage payload
    }
  }
  const { width, height } = getBounds()
  const x = window.innerWidth - width - 16
  const y = window.innerHeight - height - 80
  pos.value = { x, y }
  posReady.value = true
}

const onPointerMove = (event: PointerEvent) => {
  if (!isDragging.value) return
  const nextX = event.clientX - dragOffsetX
  const nextY = event.clientY - dragOffsetY
  if (Math.abs(nextX - pos.value.x) > 3 || Math.abs(nextY - pos.value.y) > 3) {
    wasDrag.value = true
  }
  setPosition(nextX, nextY)
}

const endDrag = () => {
  if (!isDragging.value) return
  isDragging.value = false
  if (wasDrag.value) {
    suppressClick = true
    setTimeout(() => {
      suppressClick = false
    }, 0)
  }
}

const onPointerDown = (event: PointerEvent) => {
  if (event.button !== 0) return
  const btn = btnRef.value
  if (!btn) return
  isDragging.value = true
  wasDrag.value = false
  dragOffsetX = event.clientX - pos.value.x
  dragOffsetY = event.clientY - pos.value.y
  btn.setPointerCapture(event.pointerId)
}

const onClick = () => {
  if (suppressClick) {
    suppressClick = false
    return
  }
  emit('run')
}

const onResize = () => {
  if (!posReady.value) return
  setPosition(pos.value.x, pos.value.y)
}

onMounted(() => {
  initPosition()
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('pointercancel', endDrag)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('pointercancel', endDrag)
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <button
    ref="btnRef"
    class="btn btn-circle btn-primary fixed z-20 select-none touch-none"
    type="button"
    :style="posReady ? { left: `${pos.x}px`, top: `${pos.y}px` } : undefined"
    @click="onClick"
    @pointerdown="onPointerDown"
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
