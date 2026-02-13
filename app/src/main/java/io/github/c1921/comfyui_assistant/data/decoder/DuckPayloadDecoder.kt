package io.github.c1921.comfyui_assistant.data.decoder

import android.graphics.BitmapFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class DuckPayloadDecoder {
    fun decodeIfCarrierImage(
        imageBytes: ByteArray,
        password: String,
    ): DuckDecodeOutcome {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return DuckDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)

        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) {
                DuckDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            } else {
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val rgb = ByteArray(pixels.size * RGB_CHANNELS)
                var out = 0
                for (pixel in pixels) {
                    rgb[out++] = ((pixel shr 16) and 0xFF).toByte()
                    rgb[out++] = ((pixel shr 8) and 0xFF).toByte()
                    rgb[out++] = (pixel and 0xFF).toByte()
                }
                decodeFromRgb(rgb, width, height, password)
            }
        } catch (_: Exception) {
            DuckDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
        } finally {
            bitmap.recycle()
        }
    }

    internal fun decodeFromRgb(
        rgb: ByteArray,
        width: Int,
        height: Int,
        password: String,
    ): DuckDecodeOutcome {
        if (width <= 0 || height <= 0) {
            return DuckDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
        }
        val expectedSize = width * height * RGB_CHANNELS
        if (rgb.size < expectedSize) {
            return DuckDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
        }

        val errors = mutableListOf<DuckDecodeFailureReason>()
        for (k in BIT_WIDTH_ATTEMPTS) {
            try {
                val header = extractPayloadWithK(rgb, width, height, k)
                val payload = parseHeader(header, password)
                val normalizedExt = normalizeExtension(payload.extension)
                if (!IMAGE_EXTENSIONS.contains(normalizedExt)) {
                    throw DuckDecodeException(
                        DuckDecodeFailureReason.NonImagePayload(
                            extension = normalizedExt.ifBlank { "unknown" },
                        )
                    )
                }
                if (!matchesImageSignature(normalizedExt, payload.data)) {
                    throw DuckDecodeException(DuckDecodeFailureReason.CorruptedPayload)
                }
                return DuckDecodeOutcome.Decoded(
                    imageBytes = payload.data,
                    extension = normalizedExt,
                )
            } catch (error: DuckDecodeException) {
                errors += error.reason
            } catch (_: Exception) {
                errors += DuckDecodeFailureReason.CorruptedPayload
            }
        }

        return DuckDecodeOutcome.Fallback(selectFallbackReason(errors))
    }

    private fun extractPayloadWithK(
        rgb: ByteArray,
        width: Int,
        height: Int,
        k: Int,
    ): ByteArray {
        val skipW = (width * WATERMARK_SKIP_W_RATIO).toInt()
        val skipH = (height * WATERMARK_SKIP_H_RATIO).toInt()
        val usablePixels = width.toLong() * height.toLong() - skipW.toLong() * skipH.toLong()
        val availableBits = usablePixels * RGB_CHANNELS.toLong() * k.toLong()
        if (availableBits < LENGTH_PREFIX_BITS) {
            throw DuckDecodeException(DuckDecodeFailureReason.NotCarrierImage)
        }

        val lengthBits = readBits(rgb, width, height, k, LENGTH_PREFIX_BITS, skipW, skipH)
        val headerLength = parseUnsignedInt(lengthBits)
        val totalBits = LENGTH_PREFIX_BITS.toLong() + headerLength * 8L
        if (headerLength <= 0L || totalBits > availableBits || totalBits > Int.MAX_VALUE) {
            throw DuckDecodeException(DuckDecodeFailureReason.NotCarrierImage)
        }

        val allBits = readBits(
            rgb = rgb,
            width = width,
            height = height,
            k = k,
            requiredBits = totalBits.toInt(),
            skipW = skipW,
            skipH = skipH,
        )
        val payloadBits = (headerLength * 8L).toInt()
        return bitsToBytes(allBits, LENGTH_PREFIX_BITS, payloadBits)
    }

    private fun readBits(
        rgb: ByteArray,
        width: Int,
        height: Int,
        k: Int,
        requiredBits: Int,
        skipW: Int,
        skipH: Int,
    ): IntArray {
        val out = IntArray(requiredBits)
        var count = 0
        val lowMask = (1 shl k) - 1

        rowLoop@ for (row in 0 until height) {
            for (col in 0 until width) {
                if (row < skipH && col < skipW) {
                    continue
                }
                val pixelStart = (row * width + col) * RGB_CHANNELS
                for (ch in 0 until RGB_CHANNELS) {
                    val value = rgb[pixelStart + ch].toInt() and 0xFF
                    val lowBits = value and lowMask
                    for (bit in k - 1 downTo 0) {
                        out[count++] = (lowBits shr bit) and 0x01
                        if (count >= requiredBits) {
                            break@rowLoop
                        }
                    }
                }
            }
        }

        if (count < requiredBits) {
            throw DuckDecodeException(DuckDecodeFailureReason.NotCarrierImage)
        }
        return out
    }

    private fun parseUnsignedInt(bits: IntArray): Long {
        var value = 0L
        for (bit in bits) {
            value = (value shl 1) or bit.toLong()
        }
        return value
    }

    private fun bitsToBytes(
        bits: IntArray,
        start: Int,
        bitCount: Int,
    ): ByteArray {
        val out = ByteArray(bitCount / 8)
        var bitIndex = start
        for (i in out.indices) {
            var value = 0
            repeat(8) {
                value = (value shl 1) or bits[bitIndex++]
            }
            out[i] = value.toByte()
        }
        return out
    }

    private fun parseHeader(
        header: ByteArray,
        password: String,
    ): ParsedPayload {
        var idx = 0
        if (header.isEmpty()) {
            throw DuckDecodeException(DuckDecodeFailureReason.CorruptedPayload)
        }
        val hasPassword = (header[idx].toInt() and 0xFF) == 1
        idx += 1

        var storedPwdHash = EMPTY_BYTES
        var salt = EMPTY_BYTES
        if (hasPassword) {
            if (header.size < idx + PASSWORD_HASH_BYTES + SALT_BYTES) {
                throw DuckDecodeException(DuckDecodeFailureReason.CorruptedPayload)
            }
            storedPwdHash = header.copyOfRange(idx, idx + PASSWORD_HASH_BYTES)
            idx += PASSWORD_HASH_BYTES
            salt = header.copyOfRange(idx, idx + SALT_BYTES)
            idx += SALT_BYTES
        }

        if (header.size < idx + 1) {
            throw DuckDecodeException(DuckDecodeFailureReason.CorruptedPayload)
        }
        val extLen = header[idx].toInt() and 0xFF
        idx += 1

        if (header.size < idx + extLen + DATA_LEN_BYTES) {
            throw DuckDecodeException(DuckDecodeFailureReason.CorruptedPayload)
        }

        val extension = header.copyOfRange(idx, idx + extLen).toString(StandardCharsets.UTF_8)
        idx += extLen

        val dataLen = readIntBigEndian(header, idx)
        idx += DATA_LEN_BYTES
        if (dataLen < 0 || header.size - idx != dataLen) {
            throw DuckDecodeException(DuckDecodeFailureReason.CorruptedPayload)
        }

        val payload = header.copyOfRange(idx, header.size)
        if (!hasPassword) {
            return ParsedPayload(payload, extension)
        }

        if (password.isEmpty()) {
            throw DuckDecodeException(DuckDecodeFailureReason.PasswordRequired)
        }

        val checkHash = sha256((password + salt.toLowerHex()).toByteArray(StandardCharsets.UTF_8))
        if (!checkHash.contentEquals(storedPwdHash)) {
            throw DuckDecodeException(DuckDecodeFailureReason.WrongPassword)
        }

        val keyStream = generateKeyStream(password, salt, payload.size)
        val plain = ByteArray(payload.size)
        for (i in payload.indices) {
            plain[i] = (payload[i].toInt() xor keyStream[i].toInt()).toByte()
        }
        return ParsedPayload(plain, extension)
    }

    private fun readIntBigEndian(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun generateKeyStream(
        password: String,
        salt: ByteArray,
        length: Int,
    ): ByteArray {
        val keyMaterial = (password + salt.toLowerHex()).toByteArray(StandardCharsets.UTF_8)
        val out = ByteArray(length)
        var produced = 0
        var counter = 0

        while (produced < length) {
            val digestInput = keyMaterial + counter.toString().toByteArray(StandardCharsets.UTF_8)
            val digest = sha256(digestInput)
            val copyLen = minOf(digest.size, length - produced)
            System.arraycopy(digest, 0, out, produced, copyLen)
            produced += copyLen
            counter += 1
        }
        return out
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun matchesImageSignature(
        extension: String,
        payload: ByteArray,
    ): Boolean {
        return when (extension) {
            "png" -> payload.size >= 8 &&
                payload[0] == 0x89.toByte() &&
                payload[1] == 0x50.toByte() &&
                payload[2] == 0x4E.toByte() &&
                payload[3] == 0x47.toByte() &&
                payload[4] == 0x0D.toByte() &&
                payload[5] == 0x0A.toByte() &&
                payload[6] == 0x1A.toByte() &&
                payload[7] == 0x0A.toByte()

            "jpg", "jpeg" -> payload.size >= 2 &&
                payload[0] == 0xFF.toByte() &&
                payload[1] == 0xD8.toByte()

            "webp" -> payload.size >= 12 &&
                payload[0] == 'R'.code.toByte() &&
                payload[1] == 'I'.code.toByte() &&
                payload[2] == 'F'.code.toByte() &&
                payload[3] == 'F'.code.toByte() &&
                payload[8] == 'W'.code.toByte() &&
                payload[9] == 'E'.code.toByte() &&
                payload[10] == 'B'.code.toByte() &&
                payload[11] == 'P'.code.toByte()

            "gif" -> payload.size >= 6 &&
                payload[0] == 'G'.code.toByte() &&
                payload[1] == 'I'.code.toByte() &&
                payload[2] == 'F'.code.toByte() &&
                payload[3] == '8'.code.toByte() &&
                (payload[4] == '7'.code.toByte() || payload[4] == '9'.code.toByte()) &&
                payload[5] == 'a'.code.toByte()

            "bmp" -> payload.size >= 2 &&
                payload[0] == 'B'.code.toByte() &&
                payload[1] == 'M'.code.toByte()

            else -> false
        }
    }

    private fun selectFallbackReason(errors: List<DuckDecodeFailureReason>): DuckDecodeFailureReason {
        if (errors.any { it is DuckDecodeFailureReason.WrongPassword }) {
            return DuckDecodeFailureReason.WrongPassword
        }
        if (errors.any { it is DuckDecodeFailureReason.PasswordRequired }) {
            return DuckDecodeFailureReason.PasswordRequired
        }
        errors.firstOrNull { it is DuckDecodeFailureReason.NonImagePayload }?.let { return it }
        if (errors.any { it is DuckDecodeFailureReason.CorruptedPayload }) {
            return DuckDecodeFailureReason.CorruptedPayload
        }
        return DuckDecodeFailureReason.NotCarrierImage
    }

    private fun ByteArray.toLowerHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4])
            out.append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    private data class ParsedPayload(
        val data: ByteArray,
        val extension: String,
    )

    private class DuckDecodeException(
        val reason: DuckDecodeFailureReason,
    ) : RuntimeException()

    private companion object {
        val BIT_WIDTH_ATTEMPTS = intArrayOf(2, 6, 8)
        const val RGB_CHANNELS = 3
        const val WATERMARK_SKIP_W_RATIO = 0.40
        const val WATERMARK_SKIP_H_RATIO = 0.08
        const val LENGTH_PREFIX_BITS = 32
        const val PASSWORD_HASH_BYTES = 32
        const val SALT_BYTES = 16
        const val DATA_LEN_BYTES = 4
        val EMPTY_BYTES = ByteArray(0)
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        val HEX_DIGITS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
        )
    }
}
