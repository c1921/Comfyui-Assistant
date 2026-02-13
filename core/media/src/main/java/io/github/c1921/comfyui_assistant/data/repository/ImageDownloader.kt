package io.github.c1921.comfyui_assistant.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeOutcome
import io.github.c1921.comfyui_assistant.data.decoder.DuckPayloadDecoder
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

class ImageDownloader(
    context: Context,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val payloadDecoder: DuckPayloadDecoder = DuckPayloadDecoder(),
) : MediaSaver {
    private val appContext = context.applicationContext

    override suspend fun saveToGallery(
        output: GeneratedOutput,
        taskId: String,
        index: Int,
        decodePassword: String,
    ): Result<DownloadToGalleryResult> = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder()
                .url(output.fileUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed, HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Download failed, empty body.")
                val downloadedBytes = body.bytes()
                val decodeOutcome = payloadDecoder.decodeIfCarrierImage(
                    imageBytes = downloadedBytes,
                    password = decodePassword,
                )

                val (bytesToSave, extension) = when (decodeOutcome) {
                    is DuckDecodeOutcome.Decoded -> decodeOutcome.imageBytes to decodeOutcome.extension
                    is DuckDecodeOutcome.Fallback ->
                        downloadedBytes to resolveExtension(output.fileType, output.fileUrl)
                }
                val mimeType = resolveMimeType(extension)
                val fileName = buildFileName(taskId, index, extension)

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RunningHubAssistant")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = appContext.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Unable to create gallery entry.")

                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytesToSave)
                    } ?: throw IllegalStateException("Unable to open output stream.")

                    val doneValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, doneValues, null, null)
                } catch (error: Exception) {
                    resolver.delete(uri, null, null)
                    throw error
                }
                DownloadToGalleryResult(
                    fileName = fileName,
                    decodeOutcome = decodeOutcome,
                )
            }
        }
    }

    private fun buildFileName(taskId: String, index: Int, extension: String): String {
        val timestamp = System.currentTimeMillis()
        return "rh_${taskId}_${index}_$timestamp.$extension"
    }

    private fun resolveExtension(fileType: String, fileUrl: String): String {
        val normalizedType = fileType.trim().lowercase(Locale.ROOT)
        if (normalizedType.matches(Regex("^[a-z0-9]{2,5}$"))) {
            return normalizedType
        }
        val fromUrl = fileUrl.substringAfterLast('.', "")
            .substringBefore('?')
            .trim()
            .lowercase(Locale.ROOT)
        return if (fromUrl.matches(Regex("^[a-z0-9]{2,5}$"))) fromUrl else "jpg"
    }

    private fun resolveMimeType(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "jpeg", "jpg" -> "image/jpeg"
            else -> "image/jpeg"
        }
    }
}
