<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ConnectionCard from './components/ConnectionCard.vue'
import WorkflowCard from './components/WorkflowCard.vue'
import ParamsCard from './components/ParamsCard.vue'
import ProgressCard from './components/ProgressCard.vue'
import LogCard from './components/LogCard.vue'
import OutputCard from './components/OutputCard.vue'
import AlbumCard from './components/AlbumCard.vue'
import MainTabs from './components/MainTabs.vue'
import FloatingRunButton from './components/FloatingRunButton.vue'
import { useAlbum } from './composables/useAlbum'
import { useConnection } from './composables/useConnection'
import { usePersist } from './composables/usePersist'
import { useRunner } from './composables/useRunner'
import { useWorkflow } from './composables/useWorkflow'
import type { EditableValue } from './types/app'

onMounted(() => {
  setTimeout(() => window.HSStaticMethods.autoInit(), 100)
})

const persistedParamValues = ref<Record<string, EditableValue>>({})

const connection = useConnection()
const album = useAlbum({
  baseHttp: () => connection.baseHttp.value,
  backendOrigin: () => connection.backendOrigin.value,
})
const runner = useRunner({
  baseHttp: () => connection.baseHttp.value,
  baseWs: () => connection.baseWs.value,
  fetchAlbum: album.fetchAlbum,
})
const workflow = useWorkflow({
  baseHttp: () => connection.baseHttp.value,
  log: runner.log,
  persistedParamValues,
})
const persist = usePersist({
  workflowJson: workflow.workflowJson,
  paramFields: workflow.paramFields,
  simpleMode: workflow.simpleMode,
  simplePrompt: workflow.simplePrompt,
  simpleWidth: workflow.simpleWidth,
  simpleHeight: workflow.simpleHeight,
  autoRandomSeed: workflow.autoRandomSeed,
  albumSortOrder: album.albumSortOrder,
  persistedParamValues,
})

const { backendLabel, comfyState } = connection
const { albumImages, albumError, albumSortOrder } = album
const {
  wsState,
  logText,
  images,
  progressValue,
  progressMax,
  progressNode,
  progressQueueRemaining,
  progressStatus,
  isRunning,
} = runner
const {
  workflowJson,
  parseInfo,
  paramFields,
  simpleMode,
  simplePrompt,
  simpleWidth,
  simpleHeight,
  sizePresets,
  autoRandomSeed,
} = workflow
const { onParse } = workflow
const { fetchAlbum, deleteAlbumItem } = album
const { connectWs, disconnectWs } = runner

runner.log(`clientId=${runner.clientId.value}`)

const onClear = () => {
  workflow.clearWorkflowState()
  runner.resetRunnerState()
  runner.log('已清空。')
  persist.clearPersistedState()
}

const onRun = async () => {
  try {
    runner.prepareForRun()
    workflow.randomizeSeedFields()

    if (!workflow.workflow.value) {
      alert('请先粘贴并解析 workflow JSON')
      return
    }

    runner.ensureWsConnected()

    if (workflow.simpleMode.value) workflow.applySimpleOverrides()
    const wf = workflow.buildWorkflowWithParams()
    runner.log('正在提交任务到 /api/prompt ...')
    const pid = await runner.sendPrompt(wf)

    runner.log(`提交成功：prompt_id=${pid || 'unknown'}；clientId=${runner.clientId.value}`)
    runner.log('等待 WebSocket 进度/完成消息...（如果没有消息，将在完成后手动拉 history）')

    if (pid) {
      setTimeout(() => runner.tryFetchHistoryAndShow(pid), 2000)
      setTimeout(() => runner.tryFetchHistoryAndShow(pid), 5000)
      setTimeout(() => runner.tryFetchHistoryAndShow(pid), 9000)
      setTimeout(() => runner.tryFetchHistoryAndShow(pid), 15000)
    }
  } catch (err) {
    runner.log(`运行失败：${(err as Error)?.message || String(err)}`)
    alert('运行失败，查看日志区获取详情。常见原因：后端代理/CORS/混合内容/防火墙。')
    runner.isRunning.value = false
    runner.isOutputDone.value = false
  }
}

const onStop = async () => {
  try {
    await runner.sendInterrupt()
    runner.log('已发送 Interrupt。')
    runner.isRunning.value = false
    runner.isOutputDone.value = false
  } catch (err) {
    runner.log(`Interrupt 失败：${(err as Error)?.message || String(err)}`)
  }
}

onMounted(async () => {
  persist.loadPersistedState()
  await connection.fetchBackendConfig()
  await album.fetchAlbum()
  runner.connectWs()
  await connection.refreshComfyHealth()
  connection.startHealthTimer()
})
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <div class="flex-1">
      <ProgressCard
        :value="progressValue"
        :max="progressMax"
        :node="progressNode"
        :queue-remaining="progressQueueRemaining"
        :status-text="progressStatus"
      />
      <div id="tabs-main-workflow" role="tabpanel" aria-labelledby="tabs-main-workflow-item">
        <div class="space-y-4">
          <WorkflowCard
            v-model:workflow-json="workflowJson"
            :parse-info="parseInfo"
            @parse="onParse"
            @clear="onClear"
          />

          <ParamsCard
            v-model:fields="paramFields"
            :auto-random-seed="autoRandomSeed"
            :simple-mode="simpleMode"
            :simple-prompt="simplePrompt"
            :simple-width="simpleWidth"
            :simple-height="simpleHeight"
            :size-presets="sizePresets"
            @update:auto-random-seed="autoRandomSeed = $event"
            @update:simple-mode="simpleMode = $event"
            @update:simple-prompt="simplePrompt = $event"
            @update:simple-width="simpleWidth = $event"
            @update:simple-height="simpleHeight = $event"
            @run="onRun"
            @stop="onStop"
          />

          <LogCard :log-text="logText" />
        </div>
      </div>

      <div
        id="tabs-main-images"
        class="hidden"
        role="tabpanel"
        aria-labelledby="tabs-main-images-item"
      >
        <div class="space-y-4">
          <OutputCard :images="images" />
          <AlbumCard
            :items="albumImages"
            :error-message="albumError"
            :sort-order="albumSortOrder"
            @update:sort-order="albumSortOrder = $event"
            @refresh="fetchAlbum"
            @delete="deleteAlbumItem"
          />
        </div>
      </div>

      <div
        id="tabs-main-settings"
        class="hidden"
        role="tabpanel"
        aria-labelledby="tabs-main-settings-item"
      >
        <div class="space-y-4">
          <ConnectionCard
            :backend-origin="backendLabel"
            :ws-state="wsState"
            :comfy-state="comfyState"
            @connect="connectWs"
            @disconnect="disconnectWs"
          />
        </div>
      </div>
    </div>
    <FloatingRunButton :is-running="isRunning" @run="onRun" />
    <div class="sticky bottom-0 bg-base-100/95 backdrop-blur supports-backdrop-filter:bg-base-100/80">
      <MainTabs />
    </div>
  </div>
</template>
