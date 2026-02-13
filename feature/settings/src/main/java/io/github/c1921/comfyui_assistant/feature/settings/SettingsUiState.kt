package io.github.c1921.comfyui_assistant.feature.settings

import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

data class SettingsUiState(
    val isLoadingConfig: Boolean = true,
    val apiKey: String = "",
    val workflowId: String = "",
    val promptNodeId: String = "",
    val promptFieldName: String = "",
    val negativeNodeId: String = "",
    val negativeFieldName: String = "",
    val decodePassword: String = "",
) {
    fun toWorkflowConfig(): WorkflowConfig {
        return WorkflowConfig(
            apiKey = apiKey,
            workflowId = workflowId,
            promptNodeId = promptNodeId,
            promptFieldName = promptFieldName,
            negativeNodeId = negativeNodeId,
            negativeFieldName = negativeFieldName,
            decodePassword = decodePassword,
        )
    }
}
