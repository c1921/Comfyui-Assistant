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
  simpleMode: boolean
  simplePrompt: string
  simpleWidth: number
  simpleHeight: number
  sizePresets: { label: string; width: number; height: number }[]
}>()

const emit = defineEmits<{
  (e: 'update:fields', value: ParamField[]): void
  (e: 'update:autoRandomSeed', value: boolean): void
  (e: 'update:simpleMode', value: boolean): void
  (e: 'update:simplePrompt', value: string): void
  (e: 'update:simpleWidth', value: number): void
  (e: 'update:simpleHeight', value: number): void
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

const coerceNumber = (raw: string, fallback: number) => {
  const next = Number(raw)
  return Number.isFinite(next) ? next : fallback
}

const getFieldNumber = (field: ParamField) => {
  const next = Number(field.value)
  return Number.isFinite(next) ? next : 0
}

const getStepForField = (field: ParamField) =>
  field.inputKey.trim().toLowerCase() === 'denoise' ? 0.1 : 1

const roundStep = (value: number, step: number) => {
  if (step === 1) return value
  return Math.round(value * 10) / 10
}

const onNumberDecrement = (event: Event, field: ParamField) => {
  event.preventDefault()
  if ('stopImmediatePropagation' in event) {
    ;(event as Event).stopImmediatePropagation()
  }
  const step = getStepForField(field)
  updateFieldValue(field.id, roundStep(getFieldNumber(field) - step, step))
}

const onNumberIncrement = (event: Event, field: ParamField) => {
  event.preventDefault()
  if ('stopImmediatePropagation' in event) {
    ;(event as Event).stopImmediatePropagation()
  }
  const step = getStepForField(field)
  updateFieldValue(field.id, roundStep(getFieldNumber(field) + step, step))
}

const asTextarea = (value: unknown) => {
  if (typeof value !== 'string') return false
  return value.length > 80 || value.includes('\n')
}

const isSeedField = (inputKey: string) => inputKey.trim().toLowerCase() === 'seed'

const onPresetChange = (ev: Event) => {
  const value = (ev.target as HTMLSelectElement).value
  const preset = props.sizePresets.find((item) => item.label === value)
  if (!preset) return
  emit('update:simpleWidth', preset.width)
  emit('update:simpleHeight', preset.height)
}
</script>

<template>
  <div class="rounded-xl bg-base-100 p-4">
    <div class="mb-2 flex items-center justify-between gap-3">
      <h3 class="text-base font-semibold">参数设置</h3>
      <label class="inline-flex items-center gap-2 text-xs text-base-content/70">
        <span>简易模式</span>
        <input
          type="checkbox"
          class="toggle toggle-xs"
          :checked="props.simpleMode"
          @change="emit('update:simpleMode', ($event.target as HTMLInputElement).checked)"
        />
      </label>
    </div>

    <div v-if="props.simpleMode" class="space-y-3">
      <div>
        <div class="mb-1 text-xs text-base-content/60">提示词</div>
        <textarea
          class="textarea textarea-bordered textarea-sm w-full"
          rows="3"
          :value="props.simplePrompt"
          @input="emit('update:simplePrompt', ($event.target as HTMLTextAreaElement).value)"
        ></textarea>
      </div>
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-2 text-xs text-base-content/60">
          <span>预设</span>
          <select class="select select-xs select-bordered" @change="onPresetChange">
            <option value="">自定义</option>
            <option v-for="preset in props.sizePresets" :key="preset.label" :value="preset.label">
              {{ preset.label }} ({{ preset.width }}×{{ preset.height }})
            </option>
          </select>
        </div>
        <div class="flex items-center gap-2 text-xs text-base-content/60">
          <span>宽</span>
          <input
            type="number"
            class="input input-bordered input-xs w-24"
            :value="props.simpleWidth"
            @input="emit('update:simpleWidth', Number(($event.target as HTMLInputElement).value))"
          />
        </div>
        <div class="flex items-center gap-2 text-xs text-base-content/60">
          <span>高</span>
          <input
            type="number"
            class="input input-bordered input-xs w-24"
            :value="props.simpleHeight"
            @input="emit('update:simpleHeight', Number(($event.target as HTMLInputElement).value))"
          />
        </div>
      </div>
      <div class="text-xs text-base-content/60">
        简易模式会自动写入提示词与尺寸（如果节点存在对应输入）。
      </div>
    </div>

    <div v-else-if="props.fields.length === 0" class="text-sm text-base-content/70">
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
            <div
              v-if="field.inputType === 'number'"
              class="max-w-sm"
              data-input-number
            >
              <div class="input px-0">
                <span class="border-base-content/25 border-e ps-0">
                  <button
                    type="button"
                    class="flex size-9.5 items-center justify-center"
                    aria-label="Decrement button"
                    data-input-number-decrement
                    @click="onNumberDecrement($event, field)"
                  >
                    <span class="icon-[tabler--minus] size-3.5 shrink-0"></span>
                  </button>
                </span>
                <input
                  class="px-3"
                  type="text"
                  :value="String(field.value)"
                  :id="`number-input-${field.id}`"
                  aria-label="Number input"
                  data-input-number-input
                  @input="
                    updateFieldValue(
                      field.id,
                      coerceNumber(($event.target as HTMLInputElement).value, Number(field.value)),
                    )
                  "
                  @change="
                    updateFieldValue(
                      field.id,
                      coerceNumber(($event.target as HTMLInputElement).value, Number(field.value)),
                    )
                  "
                />
                <span class="border-base-content/25 border-s pe-0">
                  <button
                    type="button"
                    class="flex size-9.5 items-center justify-center"
                    aria-label="Increment button"
                    data-input-number-increment
                    @click="onNumberIncrement($event, field)"
                  >
                    <span class="icon-[tabler--plus] size-3.5 shrink-0"></span>
                  </button>
                </span>
              </div>
            </div>
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
