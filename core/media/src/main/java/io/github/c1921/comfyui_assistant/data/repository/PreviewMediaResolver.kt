package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind

data class PreviewMediaResolution(
    val kind: OutputMediaKind,
    val playbackUrl: String,
    val isDecodedFromDuck: Boolean,
)

interface PreviewMediaResolver {
    suspend fun resolve(
        output: GeneratedOutput,
        decodePassword: String,
    ): PreviewMediaResolution
}
