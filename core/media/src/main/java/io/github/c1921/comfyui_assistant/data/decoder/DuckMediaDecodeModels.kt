package io.github.c1921.comfyui_assistant.data.decoder

sealed interface DuckMediaDecodeOutcome {
    data class DecodedImage(
        val imageBytes: ByteArray,
        val extension: String,
    ) : DuckMediaDecodeOutcome

    data class DecodedVideo(
        val videoBytes: ByteArray,
        val extension: String,
    ) : DuckMediaDecodeOutcome

    data class Fallback(
        val reason: DuckDecodeFailureReason,
    ) : DuckMediaDecodeOutcome
}
