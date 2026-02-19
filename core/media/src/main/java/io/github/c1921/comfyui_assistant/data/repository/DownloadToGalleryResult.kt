package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeOutcome
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind

data class DownloadToGalleryResult(
    val fileName: String,
    val savedKind: OutputMediaKind,
    val decodeOutcome: DuckDecodeOutcome?,
)
