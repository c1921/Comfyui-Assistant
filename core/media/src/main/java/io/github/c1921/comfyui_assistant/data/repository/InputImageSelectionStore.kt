package io.github.c1921.comfyui_assistant.data.repository

import android.net.Uri
import io.github.c1921.comfyui_assistant.domain.GenerationMode

data class PersistedInputImageSelection(
    val uri: Uri,
    val displayName: String,
)

data class PersistedInputImageSelections(
    val imageMode: PersistedInputImageSelection?,
    val videoMode: PersistedInputImageSelection?,
)

interface InputImageSelectionStore {
    suspend fun loadSelections(): PersistedInputImageSelections

    suspend fun persistSelection(
        mode: GenerationMode,
        sourceUri: Uri,
        displayName: String,
    ): Result<PersistedInputImageSelection>

    suspend fun clearSelection(mode: GenerationMode)
}
