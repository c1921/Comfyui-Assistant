package io.github.c1921.comfyui_assistant.ui

import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

enum class MainTab {
    Generate,
    Settings,
}

data class MainUiState(
    val selectedTab: MainTab = MainTab.Generate,
    val isLoadingConfig: Boolean = true,
    val apiKey: String = "",
    val workflowId: String = "",
    val promptNodeId: String = "",
    val promptFieldName: String = "",
    val negativeNodeId: String = "",
    val negativeFieldName: String = "",
    val decodePassword: String = "",
    val prompt: String = "",
    val negative: String = "",
    val generationState: GenerationState = GenerationState.Idle,
) {
    fun toWorkflowConfig(): WorkflowConfig = WorkflowConfig(
        apiKey = apiKey,
        workflowId = workflowId,
        promptNodeId = promptNodeId,
        promptFieldName = promptFieldName,
        negativeNodeId = negativeNodeId,
        negativeFieldName = negativeFieldName,
        decodePassword = decodePassword,
    )
}

object UiTestTags {
    const val GENERATE_BUTTON = "generate_button"
    const val SAVE_SETTINGS_BUTTON = "save_settings_button"
}
