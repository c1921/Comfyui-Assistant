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
    val sizeNodeId: String = "",
    val imageInputNodeId: String = "",
    val videoWorkflowId: String = "",
    val videoPromptNodeId: String = "",
    val videoPromptFieldName: String = "",
    val videoImageInputNodeId: String = "",
    val videoLengthNodeId: String = "",
    val decodePassword: String = "",
    val webDavEnabled: Boolean = false,
    val webDavServerUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavSyncPassphrase: String = "",
) {
    fun toWorkflowConfig(): WorkflowConfig {
        return WorkflowConfig(
            apiKey = apiKey,
            workflowId = workflowId,
            promptNodeId = promptNodeId,
            promptFieldName = promptFieldName,
            negativeNodeId = negativeNodeId,
            negativeFieldName = negativeFieldName,
            sizeNodeId = sizeNodeId,
            imageInputNodeId = imageInputNodeId,
            videoWorkflowId = videoWorkflowId,
            videoPromptNodeId = videoPromptNodeId,
            videoPromptFieldName = videoPromptFieldName,
            videoImageInputNodeId = videoImageInputNodeId,
            videoLengthNodeId = videoLengthNodeId,
            decodePassword = decodePassword,
            webDavEnabled = webDavEnabled,
            webDavServerUrl = webDavServerUrl,
            webDavUsername = webDavUsername,
            webDavPassword = webDavPassword,
            webDavSyncPassphrase = webDavSyncPassphrase,
        )
    }
}
