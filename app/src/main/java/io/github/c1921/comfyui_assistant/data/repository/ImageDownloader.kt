package io.github.c1921.comfyui_assistant.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

class ImageDownloader(
    private val httpClient: OkHttpClient,
) {
    suspend fun downloadToGallery(
        context: Context,
        fileUrl: String,
        fileType: String,
        taskId: String,
        index: Int,
    ): Result<String> {
        return runCatching {
            val request = Request.Builder()
                .url(fileUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed, HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Download failed, empty body.")

                val extension = resolveExtension(fileType, fileUrl)
                val mimeType = resolveMimeType(extension)
                val fileName = buildFileName(taskId, index, extension)

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RunningHubAssistant")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Unable to create gallery entry.")

                resolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Unable to open output stream.")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                fileName
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
