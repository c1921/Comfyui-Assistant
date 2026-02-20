package io.github.c1921.comfyui_assistant.data.local

enum class ConfigSyncAction {
    PUSH_LOCAL_TO_REMOTE,
    PULL_REMOTE_TO_LOCAL,
    SKIP_ALREADY_SYNCED,
}

class ConfigSyncDecisionEngine {
    fun decide(
        localUpdatedAtEpochMs: Long,
        remoteUpdatedAtEpochMs: Long?,
    ): ConfigSyncAction {
        if (remoteUpdatedAtEpochMs == null) {
            return ConfigSyncAction.PUSH_LOCAL_TO_REMOTE
        }
        return when {
            remoteUpdatedAtEpochMs > localUpdatedAtEpochMs -> ConfigSyncAction.PULL_REMOTE_TO_LOCAL
            remoteUpdatedAtEpochMs < localUpdatedAtEpochMs -> ConfigSyncAction.PUSH_LOCAL_TO_REMOTE
            else -> ConfigSyncAction.SKIP_ALREADY_SYNCED
        }
    }
}

