package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.flow.Flow

interface GenerationRepository {
    fun generateAndPoll(
        config: WorkflowConfig,
        input: GenerationInput,
    ): Flow<GenerationState>
}
