package io.github.c1921.comfyui_assistant.feature.generate

import android.net.Uri
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

data class GenerateUiState(
    val isLoadingConfig: Boolean = true,
    val config: WorkflowConfig = WorkflowConfig(),
    val prompt: String = "",
    val negative: String = "",
    val selectedMode: GenerationMode = GenerationMode.IMAGE,
    val selectedImagePreset: ImageAspectPreset = ImageAspectPreset.RATIO_1_1,
    val videoLengthFramesText: String = "80",
    val selectedInputImageUri: Uri? = null,
    val selectedInputImageDisplayName: String = "",
    val isUploadingInputImage: Boolean = false,
    val generationState: GenerationState = GenerationState.Idle,
    val lastArchivedTaskId: String = "",
)
