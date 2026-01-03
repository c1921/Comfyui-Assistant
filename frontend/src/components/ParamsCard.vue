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

const props = defineProps<{ fields: ParamField[] }>()

const emit = defineEmits<{
  (e: 'update:fields', value: ParamField[]): void
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
</script>

<template>
  <div class="card">
    <h3 style="margin: 0 0 8px 0">2) 参数设置（会写回 workflow）</h3>

    <div v-if="props.fields.length === 0" class="small">
      未发现可编辑字段。提示：只有 string/number/boolean 会被展示，连线引用会被跳过。
    </div>
    <div v-else>
      <div v-for="group in groups" :key="group.key" style="margin-top: 10px">
        <div class="small" style="margin-bottom: 6px">节点：{{ group.label }}</div>
        <div
          v-for="field in group.items"
          :key="field.id"
          class="row"
          style="margin-bottom: 6px"
        >
          <label>
            {{ field.inputKey }}：
            <input
              v-if="field.inputType === 'number'"
              type="number"
              :value="field.value"
              style="width: 180px"
              @input="
                updateFieldValue(field.id, Number(($event.target as HTMLInputElement).value))
              "
            />
            <input
              v-else-if="field.inputType === 'boolean'"
              type="checkbox"
              :checked="Boolean(field.value)"
              @change="
                updateFieldValue(field.id, ($event.target as HTMLInputElement).checked)
              "
            />
            <textarea
              v-else-if="asTextarea(field.value)"
              :value="String(field.value)"
              style="width: 520px"
              rows="3"
              @input="updateFieldValue(field.id, ($event.target as HTMLTextAreaElement).value)"
            ></textarea>
            <input
              v-else
              :value="String(field.value)"
              style="width: 520px"
              @input="updateFieldValue(field.id, ($event.target as HTMLInputElement).value)"
            />
          </label>
        </div>
      </div>
    </div>

    <div class="row" style="margin-top: 12px">
      <button @click="emit('run')">运行</button>
      <button @click="emit('stop')">中止（Interrupt）</button>
    </div>
  </div>
</template>
