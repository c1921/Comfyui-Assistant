package io.github.c1921.comfyui_assistant.data.decoder

import java.util.Locale

sealed interface DuckDecodeOutcome {
    data class Decoded(
        val imageBytes: ByteArray,
        val extension: String,
    ) : DuckDecodeOutcome

    data class Fallback(
        val reason: DuckDecodeFailureReason,
    ) : DuckDecodeOutcome
}

sealed interface DuckDecodeFailureReason {
    val shouldNotifyUser: Boolean
    val message: String

    data object NotCarrierImage : DuckDecodeFailureReason {
        override val shouldNotifyUser: Boolean = false
        override val message: String = "Image is not a supported SS_tools encrypted carrier."
    }

    data object PasswordRequired : DuckDecodeFailureReason {
        override val shouldNotifyUser: Boolean = true
        override val message: String = "Encrypted payload requires a decode password."
    }

    data object WrongPassword : DuckDecodeFailureReason {
        override val shouldNotifyUser: Boolean = true
        override val message: String = "Decode password is incorrect. Saved original file instead."
    }

    data class NonImagePayload(
        val extension: String,
    ) : DuckDecodeFailureReason {
        override val shouldNotifyUser: Boolean = true
        override val message: String =
            "Decoded payload type '$extension' is not an image. Saved original image instead."
    }

    data object CorruptedPayload : DuckDecodeFailureReason {
        override val shouldNotifyUser: Boolean = true
        override val message: String = "Encrypted payload is corrupted. Saved original image instead."
    }
}

fun DuckDecodeOutcome.fallbackOrNull(): DuckDecodeFailureReason? {
    return (this as? DuckDecodeOutcome.Fallback)?.reason
}

internal fun normalizeExtension(extension: String): String {
    return extension.trim()
        .removePrefix(".")
        .lowercase(Locale.ROOT)
}
