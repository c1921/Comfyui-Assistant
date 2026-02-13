package io.github.c1921.comfyui_assistant.feature.generate

import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

data class GenerateUiState(
    val isLoadingConfig: Boolean = true,
    val config: WorkflowConfig = WorkflowConfig(),
    val prompt: String = "",
    val negative: String = "",
    val generationState: GenerationState = GenerationState.Idle,
)
