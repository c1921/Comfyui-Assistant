<script setup lang="ts">
interface ImageItem {
  src: string
  alt: string
}

const props = defineProps<{ images: ImageItem[] }>()
</script>

<template>
  <div class="card">
    <h3 style="margin: 0 0 8px 0">4) 输出图片</h3>
    <div id="images">
      <img v-for="item in props.images" :key="item.src" :src="item.src" :alt="item.alt" />
    </div>
  </div>
</template>
