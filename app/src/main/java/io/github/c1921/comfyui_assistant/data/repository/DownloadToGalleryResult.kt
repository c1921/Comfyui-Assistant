package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeOutcome

data class DownloadToGalleryResult(
    val fileName: String,
    val decodeOutcome: DuckDecodeOutcome,
)
