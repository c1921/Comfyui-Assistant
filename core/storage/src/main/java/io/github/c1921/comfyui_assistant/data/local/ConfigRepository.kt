package io.github.c1921.comfyui_assistant.data.local

import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

interface ConfigRepository {
    suspend fun loadConfig(): WorkflowConfig

    suspend fun saveConfig(config: WorkflowConfig)

    suspend fun clearApiKey()
}
