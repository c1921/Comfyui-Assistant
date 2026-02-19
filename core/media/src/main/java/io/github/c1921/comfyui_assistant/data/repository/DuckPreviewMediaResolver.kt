package io.github.c1921.comfyui_assistant.data.repository

import android.content.Context
import android.net.Uri
import io.github.c1921.comfyui_assistant.data.decoder.DuckMediaDecodeOutcome
import io.github.c1921.comfyui_assistant.data.decoder.DuckPayloadDecoder
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class DuckPreviewMediaResolver(
    context: Context,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val payloadDecoder: DuckPayloadDecoder = DuckPayloadDecoder(),
) : PreviewMediaResolver {
    private val appContext = context.applicationContext
    private val cacheDirectory: File = File(appContext.cacheDir, CACHE_DIRECTORY_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }
    private val memoryCache = mutableMapOf<String, PreviewMediaResolution>()

    override suspend fun resolve(
        output: GeneratedOutput,
        decodePassword: String,
    ): PreviewMediaResolution = withContext(ioDispatcher) {
        val detectedKind = output.detectMediaKind()
        if (detectedKind == OutputMediaKind.VIDEO) {
            return@withContext PreviewMediaResolution(
                kind = OutputMediaKind.VIDEO,
                playbackUrl = output.fileUrl,
                isDecodedFromDuck = false,
            )
        }

        val cacheKey = buildCacheKey(output.fileUrl, decodePassword)
        synchronized(memoryCache) {
            memoryCache[cacheKey]?.let { return@withContext it }
        }

        val cacheFile = File(cacheDirectory, buildCacheFileName(cacheKey))
        if (cacheFile.exists() && cacheFile.length() > 0) {
            val cached = PreviewMediaResolution(
                kind = OutputMediaKind.VIDEO,
                playbackUrl = Uri.fromFile(cacheFile).toString(),
                isDecodedFromDuck = true,
            )
            synchronized(memoryCache) {
                memoryCache[cacheKey] = cached
            }
            return@withContext cached
        }

        val fallbackKind = if (detectedKind == OutputMediaKind.UNKNOWN) OutputMediaKind.IMAGE else detectedKind
        val resolved = runCatching {
            val downloadedBytes = download(output.fileUrl)
            payloadDecoder.decodeMediaIfCarrierImage(
                imageBytes = downloadedBytes,
                password = decodePassword,
            )
        }.fold(
            onSuccess = { decodeOutcome ->
                when (decodeOutcome) {
                    is DuckMediaDecodeOutcome.DecodedVideo -> {
                        writeCacheFile(cacheFile, decodeOutcome.videoBytes)
                        PreviewMediaResolution(
                            kind = OutputMediaKind.VIDEO,
                            playbackUrl = Uri.fromFile(cacheFile).toString(),
                            isDecodedFromDuck = true,
                        )
                    }

                    else -> PreviewMediaResolution(
                        kind = fallbackKind,
                        playbackUrl = output.fileUrl,
                        isDecodedFromDuck = false,
                    )
                }
            },
            onFailure = {
                PreviewMediaResolution(
                    kind = fallbackKind,
                    playbackUrl = output.fileUrl,
                    isDecodedFromDuck = false,
                )
            },
        )
        synchronized(memoryCache) {
            memoryCache[cacheKey] = resolved
        }
        return@withContext resolved
    }

    private fun download(fileUrl: String): ByteArray {
        val request = Request.Builder()
            .url(fileUrl)
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed, HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Download failed, empty body.")
            body.bytes()
        }
    }

    private fun writeCacheFile(
        destination: File,
        bytes: ByteArray,
    ) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, "${destination.name}.tmp")
        temp.outputStream().use { output ->
            output.write(bytes)
        }
        if (destination.exists()) {
            destination.delete()
        }
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
    }

    private fun buildCacheKey(
        fileUrl: String,
        decodePassword: String,
    ): String {
        val passwordHash = sha256Hex(decodePassword)
        val key = "$fileUrl#$passwordHash"
        return sha256Hex(key)
    }

    private fun buildCacheFileName(
        cacheKey: String,
    ): String {
        return "$cacheKey.mp4"
    }

    private fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val intValue = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[intValue ushr 4])
            out.append(HEX_DIGITS[intValue and 0x0F])
        }
        return out.toString()
    }

    private companion object {
        const val CACHE_DIRECTORY_NAME = "duck_preview"
        val HEX_DIGITS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
        )
    }
}
