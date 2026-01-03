<script setup lang="ts">
import { computed } from 'vue'

type ParamField = {
  id: string
  nodeId: string
  nodeLabel: string
  inputKey: string
  value: string | number | boolean
  inputType: 'string' | 'number' | 'boolean'
}

const props = defineProps<{
  fields: ParamField[]
  autoRandomSeed: boolean
}>()

const emit = defineEmits<{
  (e: 'update:fields', value: ParamField[]): void
  (e: 'update:autoRandomSeed', value: boolean): void
  (e: 'run'): void
  (e: 'stop'): void
}>()

const groups = computed(() => {
  const map = new Map<string, { key: string; label: string; items: ParamField[] }>()
  for (const field of props.fields) {
    const key = `${field.nodeLabel} (${field.nodeId})`
    const existing = map.get(key)
    if (existing) {
      existing.items.push(field)
    } else {
      map.set(key, { key, label: key, items: [field] })
    }
  }
  return [...map.values()]
})

const updateFieldValue = (id: string, value: string | number | boolean) => {
  const next = props.fields.map((field) => (field.id === id ? { ...field, value } : field))
  emit('update:fields', next)
}

const asTextarea = (value: unknown) => {
  if (typeof value !== 'string') return false
  return value.length > 80 || value.includes('\n')
}

const isSeedField = (inputKey: string) => inputKey.trim().toLowerCase() === 'seed'
</script>

<template>
  <div class="rounded-xl border border-base-300 bg-base-100 p-4 shadow-sm">
    <h3 class="mb-2 text-base font-semibold">2) 参数设置（会写回 workflow）</h3>

    <div v-if="props.fields.length === 0" class="text-sm text-base-content/70">
      未发现可编辑字段。提示：只有 string/number/boolean 会被展示，连线引用会被跳过。
    </div>
    <div v-else>
      <div v-for="group in groups" :key="group.key" class="mt-3">
        <div class="mb-1.5 text-xs text-base-content/60">节点：{{ group.label }}</div>
        <div v-for="field in group.items" :key="field.id" class="mb-2">
          <label
            v-if="field.inputType === 'boolean'"
            class="inline-flex items-center gap-2 text-sm text-base-content/80"
          >
            <span>{{ field.inputKey }}：</span>
            <input
              type="checkbox"
              class="checkbox checkbox-sm"
              :checked="Boolean(field.value)"
              @change="
                updateFieldValue(field.id, ($event.target as HTMLInputElement).checked)
              "
            />
          </label>
          <div v-else class="flex flex-wrap items-center gap-2 text-sm text-base-content/80">
            <span>{{ field.inputKey }}：</span>
            <input
              v-if="field.inputType === 'number'"
              type="number"
              class="input input-bordered input-sm w-32 sm:w-44"
              :value="field.value"
              @input="
                updateFieldValue(field.id, Number(($event.target as HTMLInputElement).value))
              "
            />
            <textarea
              v-else-if="asTextarea(field.value)"
              class="textarea textarea-bordered textarea-sm w-full max-w-130"
              :value="String(field.value)"
              rows="3"
              @input="updateFieldValue(field.id, ($event.target as HTMLTextAreaElement).value)"
            ></textarea>
            <input
              v-else
              class="input input-bordered input-sm w-full max-w-130"
              :value="String(field.value)"
              @input="updateFieldValue(field.id, ($event.target as HTMLInputElement).value)"
            />
            <label
              v-if="isSeedField(field.inputKey)"
              class="ml-1 inline-flex items-center gap-1 text-xs text-base-content/70"
            >
              <span>随机</span>
              <input
                type="checkbox"
                class="toggle toggle-xs"
                :checked="props.autoRandomSeed"
                @change="
                  emit('update:autoRandomSeed', ($event.target as HTMLInputElement).checked)
                "
              />
            </label>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-3 flex flex-wrap gap-3">
      <button class="btn btn-primary btn-sm" @click="emit('run')">运行</button>
      <button class="btn btn-outline btn-sm" @click="emit('stop')">中止（Interrupt）</button>
    </div>
  </div>
</template>
