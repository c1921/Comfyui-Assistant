<script setup lang="ts">
interface SelectOption {
  value: string
  label: string
}

const props = defineProps<{
  posPrompt: string
  negPrompt: string
  seed: number
  steps: number
  cfg: number
  width: number
  height: number
  batch: number
}>()

const emit = defineEmits<{
  (e: 'update:posPrompt', value: string): void
  (e: 'update:negPrompt', value: string): void
  (e: 'update:seed', value: number): void
  (e: 'update:steps', value: number): void
  (e: 'update:cfg', value: number): void
  (e: 'update:width', value: number): void
  (e: 'update:height', value: number): void
  (e: 'update:batch', value: number): void
  (e: 'run'): void
  (e: 'stop'): void
}>()
</script>

<template>
  <div class="card">
    <h3 style="margin: 0 0 8px 0">2) 参数设置（会写回 workflow）</h3>

    <div class="row">
      <label>
        正向 Prompt：
        <input
          :value="props.posPrompt"
          style="width: 520px"
          placeholder="例如：a cinematic portrait of..."
          @input="emit('update:posPrompt', ($event.target as HTMLInputElement).value)"
        />
      </label>
    </div>
    <div class="row" style="margin-top: 8px">
      <label>
        负向 Prompt：
        <input
          :value="props.negPrompt"
          style="width: 520px"
          placeholder="例如：lowres, blurry..."
          @input="emit('update:negPrompt', ($event.target as HTMLInputElement).value)"
        />
      </label>
    </div>

    <div class="row" style="margin-top: 10px">
      <label>
        seed：
        <input
          type="number"
          :value="props.seed"
          style="width: 130px"
          @input="emit('update:seed', Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <label>
        steps：
        <input
          type="number"
          :value="props.steps"
          style="width: 110px"
          @input="emit('update:steps', Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <label>
        cfg：
        <input
          type="number"
          step="0.5"
          :value="props.cfg"
          style="width: 90px"
          @input="emit('update:cfg', Number(($event.target as HTMLInputElement).value))"
        />
      </label>
    </div>

    <div class="row" style="margin-top: 10px">
      <label>
        width：
        <input
          type="number"
          :value="props.width"
          style="width: 110px"
          @input="emit('update:width', Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <label>
        height：
        <input
          type="number"
          :value="props.height"
          style="width: 110px"
          @input="emit('update:height', Number(($event.target as HTMLInputElement).value))"
        />
      </label>
      <label>
        batch：
        <input
          type="number"
          :value="props.batch"
          style="width: 90px"
          @input="emit('update:batch', Number(($event.target as HTMLInputElement).value))"
        />
      </label>
    </div>

    <div class="row" style="margin-top: 12px">
      <button @click="emit('run')">运行</button>
      <button @click="emit('stop')">中止（Interrupt）</button>
    </div>
  </div>
</template>
