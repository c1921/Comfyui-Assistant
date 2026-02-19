package io.github.c1921.comfyui_assistant.data.decoder

import java.io.ByteArrayOutputStream
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckPayloadDecoderTest {
    private val decoder = DuckPayloadDecoder()

    @Test
    fun `decodeFromRgb decodes k2 payload without password`() {
        val rgb = buildCarrierRgb(
            rawPayload = SAMPLE_PNG_BYTES,
            extension = "png",
            password = "",
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "")
        val decoded = outcome as DuckDecodeOutcome.Decoded

        assertEquals("png", decoded.extension)
        assertArrayEquals(SAMPLE_PNG_BYTES, decoded.imageBytes)
    }

    @Test
    fun `decodeFromRgb decodes k6 payload without password`() {
        val rgb = buildCarrierRgb(
            rawPayload = SAMPLE_PNG_BYTES,
            extension = "png",
            password = "",
            k = 6,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "")
        val decoded = outcome as DuckDecodeOutcome.Decoded

        assertEquals("png", decoded.extension)
        assertArrayEquals(SAMPLE_PNG_BYTES, decoded.imageBytes)
    }

    @Test
    fun `decodeFromRgb decodes k8 payload without password`() {
        val rgb = buildCarrierRgb(
            rawPayload = SAMPLE_PNG_BYTES,
            extension = "png",
            password = "",
            k = 8,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "")
        val decoded = outcome as DuckDecodeOutcome.Decoded

        assertEquals("png", decoded.extension)
        assertArrayEquals(SAMPLE_PNG_BYTES, decoded.imageBytes)
    }

    @Test
    fun `decodeFromRgb returns wrong password when password is invalid`() {
        val rgb = buildCarrierRgb(
            rawPayload = SAMPLE_PNG_BYTES,
            extension = "png",
            password = "secret123",
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "invalid")
        val fallback = outcome as DuckDecodeOutcome.Fallback
        assertTrue(fallback.reason is DuckDecodeFailureReason.WrongPassword)
    }

    @Test
    fun `decodeFromRgb returns not carrier for random image data`() {
        val rgb = ByteArray(80 * 80 * 3) { index -> ((index * 37 + 11) and 0xFF).toByte() }

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "")
        val fallback = outcome as DuckDecodeOutcome.Fallback
        assertTrue(fallback.reason is DuckDecodeFailureReason.NotCarrierImage)
    }

    @Test
    fun `decodeFromRgb returns non image payload for txt payload`() {
        val rgb = buildCarrierRgb(
            rawPayload = "hello".toByteArray(StandardCharsets.UTF_8),
            extension = "txt",
            password = "",
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "")
        val fallback = outcome as DuckDecodeOutcome.Fallback
        val reason = fallback.reason as DuckDecodeFailureReason.NonImagePayload
        assertEquals("txt", reason.extension)
    }

    @Test
    fun `decodeFromRgb returns corrupted payload for malformed header`() {
        val malformedHeader = buildMalformedHeader()
        val rgb = embedHeader(
            fileHeader = malformedHeader,
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeFromRgb(rgb, 80, 80, "")
        val fallback = outcome as DuckDecodeOutcome.Fallback
        assertTrue(fallback.reason is DuckDecodeFailureReason.CorruptedPayload)
    }

    @Test
    fun `decodeMediaFromRgb decodes mp4 binpng payload without password`() {
        val sampleVideoBytes = byteArrayOf(
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(),
            0x10.toByte(), 0x20.toByte(), 0x30.toByte(),
            0x40.toByte(),
        )
        val binaryPngBytes = bytesToBinaryPngBytes(sampleVideoBytes)
        val rgb = buildCarrierRgb(
            rawPayload = binaryPngBytes,
            extension = "mp4.binpng",
            password = "",
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeMediaFromRgb(rgb, 80, 80, "")
        val decoded = outcome as DuckMediaDecodeOutcome.DecodedVideo

        assertEquals("mp4", decoded.extension)
        assertArrayEquals(sampleVideoBytes, decoded.videoBytes)
    }

    @Test
    fun `decodeMediaFromRgb returns wrong password for encrypted mp4 binpng payload`() {
        val sampleVideoBytes = byteArrayOf(
            0x01.toByte(), 0x02.toByte(), 0x03.toByte(),
            0x10.toByte(), 0x20.toByte(), 0x30.toByte(),
            0x40.toByte(),
        )
        val binaryPngBytes = bytesToBinaryPngBytes(sampleVideoBytes)
        val rgb = buildCarrierRgb(
            rawPayload = binaryPngBytes,
            extension = "mp4.binpng",
            password = "secret123",
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeMediaFromRgb(rgb, 80, 80, "invalid")
        val fallback = outcome as DuckMediaDecodeOutcome.Fallback

        assertTrue(fallback.reason is DuckDecodeFailureReason.WrongPassword)
    }

    @Test
    fun `decodeMediaFromRgb returns corrupted payload when mp4 binpng content is invalid`() {
        val rgb = buildCarrierRgb(
            rawPayload = "not_a_png".toByteArray(StandardCharsets.UTF_8),
            extension = "mp4.binpng",
            password = "",
            k = 2,
            width = 80,
            height = 80,
        )

        val outcome = decoder.decodeMediaFromRgb(rgb, 80, 80, "")
        val fallback = outcome as DuckMediaDecodeOutcome.Fallback

        assertTrue(fallback.reason is DuckDecodeFailureReason.CorruptedPayload)
    }

    private fun buildCarrierRgb(
        rawPayload: ByteArray,
        extension: String,
        password: String,
        k: Int,
        width: Int,
        height: Int,
    ): ByteArray {
        val fileHeader = buildFileHeader(rawPayload, extension, password)
        return embedHeader(fileHeader, k, width, height)
    }

    private fun buildFileHeader(
        rawPayload: ByteArray,
        extension: String,
        password: String,
    ): ByteArray {
        val extBytes = extension.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()

        if (password.isEmpty()) {
            out.write(0)
            out.write(extBytes.size)
            out.write(extBytes)
            out.write(intToBytes(rawPayload.size))
            out.write(rawPayload)
            return out.toByteArray()
        }

        val salt = ByteArray(16) { index -> (index + 1).toByte() }
        val keyStream = generateKeyStream(password, salt, rawPayload.size)
        val cipher = ByteArray(rawPayload.size)
        for (i in rawPayload.indices) {
            cipher[i] = (rawPayload[i].toInt() xor keyStream[i].toInt()).toByte()
        }
        val passwordHash = sha256((password + salt.toLowerHex()).toByteArray(StandardCharsets.UTF_8))

        out.write(1)
        out.write(passwordHash)
        out.write(salt)
        out.write(extBytes.size)
        out.write(extBytes)
        out.write(intToBytes(cipher.size))
        out.write(cipher)
        return out.toByteArray()
    }

    private fun buildMalformedHeader(): ByteArray {
        val extBytes = "png".toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(extBytes.size)
        out.write(extBytes)
        out.write(intToBytes(SAMPLE_PNG_BYTES.size + 10))
        out.write(SAMPLE_PNG_BYTES)
        return out.toByteArray()
    }

    private fun embedHeader(
        fileHeader: ByteArray,
        k: Int,
        width: Int,
        height: Int,
    ): ByteArray {
        val payloadWithLength = intToBytes(fileHeader.size) + fileHeader
        val bits = bytesToBits(payloadWithLength)
        val rgb = ByteArray(width * height * 3) { index -> ((index * 17 + 5) and 0xFF).toByte() }
        val skipW = (width * 0.40).toInt()
        val skipH = (height * 0.08).toInt()
        val mask = (1 shl k) - 1

        var bitIndex = 0
        outer@ for (row in 0 until height) {
            for (col in 0 until width) {
                if (row < skipH && col < skipW) {
                    continue
                }
                val pixelStart = (row * width + col) * 3
                for (ch in 0 until 3) {
                    var value = 0
                    repeat(k) {
                        value = (value shl 1) or if (bitIndex < bits.size) bits[bitIndex++] else 0
                    }
                    val src = rgb[pixelStart + ch].toInt() and 0xFF
                    rgb[pixelStart + ch] = ((src and (0xFF xor mask)) or value).toByte()
                    if (bitIndex >= bits.size) {
                        break@outer
                    }
                }
            }
        }

        if (bitIndex < bits.size) {
            throw IllegalStateException("Test payload exceeds carrier capacity.")
        }
        return rgb
    }

    private fun bytesToBits(bytes: ByteArray): IntArray {
        val bits = IntArray(bytes.size * 8)
        var index = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            for (bit in 7 downTo 0) {
                bits[index++] = (value shr bit) and 1
            }
        }
        return bits
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        )
    }

    private fun bytesToBinaryPngBytes(
        data: ByteArray,
        width: Int = 8,
    ): ByteArray {
        val safeWidth = width.coerceAtLeast(1)
        val pixels = (data.size + 2) / 3
        val height = maxOf(1, (pixels + safeWidth - 1) / safeWidth)
        val totalBytes = safeWidth * height * 3
        val padded = data + ByteArray(totalBytes - data.size)

        val image = BufferedImage(safeWidth, height, BufferedImage.TYPE_INT_RGB)
        var cursor = 0
        for (row in 0 until height) {
            for (col in 0 until safeWidth) {
                val red = padded[cursor++].toInt() and 0xFF
                val green = padded[cursor++].toInt() and 0xFF
                val blue = padded[cursor++].toInt() and 0xFF
                val rgb = (red shl 16) or (green shl 8) or blue
                image.setRGB(col, row, rgb)
            }
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
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
            val digest = sha256(keyMaterial + counter.toString().toByteArray(StandardCharsets.UTF_8))
            val copyLen = minOf(digest.size, length - produced)
            System.arraycopy(digest, 0, out, produced, copyLen)
            produced += copyLen
            counter += 1
        }
        return out
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

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private companion object {
        val SAMPLE_PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
        val HEX_DIGITS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
        )
    }
}
