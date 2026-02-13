package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.domain.GeneratedOutput

interface MediaSaver {
    suspend fun saveToGallery(
        output: GeneratedOutput,
        taskId: String,
        index: Int,
        decodePassword: String,
    ): Result<DownloadToGalleryResult>
}
