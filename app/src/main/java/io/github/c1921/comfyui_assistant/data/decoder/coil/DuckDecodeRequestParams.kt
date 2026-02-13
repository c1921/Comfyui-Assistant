package io.github.c1921.comfyui_assistant.data.decoder.coil

import coil.request.ImageRequest
import coil.request.Options
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object DuckDecodeRequestParams {
    private const val PARAM_AUTO_DECODE_ENABLED = "duck_auto_decode_enabled"
    private const val PARAM_DECODE_PASSWORD = "duck_decode_password"

    fun isAutoDecodeEnabled(options: Options): Boolean {
        return options.parameters.value<Boolean>(PARAM_AUTO_DECODE_ENABLED) == true
    }

    fun decodePassword(options: Options): String {
        return options.parameters.value<String>(PARAM_DECODE_PASSWORD).orEmpty()
    }

    fun ImageRequest.Builder.enableDuckAutoDecode(password: String): ImageRequest.Builder {
        return this
            .setParameter(PARAM_AUTO_DECODE_ENABLED, true, "1")
            .setParameter(
                PARAM_DECODE_PASSWORD,
                password,
                "sha256:${sha256Hex(password)}",
            )
    }

    private fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val v = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[v ushr 4])
            out.append(HEX_DIGITS[v and 0x0F])
        }
        return out.toString()
    }

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
