<script setup lang="ts">
interface ImageItem {
  src: string
  alt: string
}

const props = defineProps<{ images: ImageItem[] }>()
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <h3 class="mb-2 text-base font-semibold">5) 输出图片</h3>
    <div id="images" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <img
        v-for="item in props.images"
        :key="item.src"
        :src="item.src"
        :alt="item.alt"
        class="h-auto w-full rounded-lg border border-base-200 shadow-sm"
      />
    </div>
  </div>
</template>
