package io.github.c1921.comfyui_assistant.data.local

import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

enum class ConfigSyncTrigger {
    SETTINGS_SAVE,
    AFTER_ARCHIVE,
}

sealed interface ConfigSyncResult {
    data class Skipped(
        val reason: String,
    ) : ConfigSyncResult

    data class Pushed(
        val remotePath: String,
    ) : ConfigSyncResult

    data class Pulled(
        val config: WorkflowConfig,
    ) : ConfigSyncResult

    data class Failed(
        val message: String,
    ) : ConfigSyncResult
}

interface WebDavSyncRepository {
    suspend fun syncConfig(trigger: ConfigSyncTrigger): ConfigSyncResult
}

object NoOpWebDavSyncRepository : WebDavSyncRepository {
    override suspend fun syncConfig(trigger: ConfigSyncTrigger): ConfigSyncResult {
        return ConfigSyncResult.Skipped(reason = "WebDAV sync is disabled.")
    }
}

