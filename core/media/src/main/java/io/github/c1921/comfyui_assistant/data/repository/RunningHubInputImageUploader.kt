package io.github.c1921.comfyui_assistant.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.c1921.comfyui_assistant.data.network.RunningHubApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale

class RunningHubInputImageUploader(
    context: Context,
    private val apiService: RunningHubApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InputImageUploader {
    private val appContext = context.applicationContext

    override suspend fun uploadInputImage(
        apiKey: String,
        imageUri: Uri,
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val resolver = appContext.contentResolver
            val mimeType = resolver.getType(imageUri)
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
                .ifBlank { "image/jpeg" }
            if (!mimeType.startsWith("image/")) {
                throw IllegalArgumentException("Only image files are supported.")
            }
            val bytes = resolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected image.")

            val fileName = resolveFileName(imageUri, mimeType)
            val filePart = MultipartBody.Part.createFormData(
                name = "file",
                filename = fileName,
                body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
            )

            val response = apiService.uploadMediaBinary(
                authorization = authHeader(apiKey),
                file = filePart,
            )

            if (response.code != 0) {
                val message = response.message?.takeIf { it.isNotBlank() } ?: "Upload failed."
                throw IllegalStateException(message)
            }
            val uploadedFileName = response.data?.fileName?.trim().orEmpty()
            if (uploadedFileName.isBlank()) {
                throw IllegalStateException("Upload succeeded but fileName is missing.")
            }
            uploadedFileName
        }.recoverCatching { error ->
            throw IllegalStateException(mapUploadException(error))
        }
    }

    private fun resolveFileName(
        imageUri: Uri,
        mimeType: String,
    ): String {
        val resolver = appContext.contentResolver
        resolver.query(imageUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(0)?.trim().orEmpty()
                    if (displayName.isNotBlank()) return displayName
                }
            }

        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> "jpg"
        }
        return "upload_${System.currentTimeMillis()}.$extension"
    }

    private fun authHeader(apiKey: String): String = "Bearer ${apiKey.trim()}"

    private fun mapUploadException(error: Throwable): String {
        return when (error) {
            is SocketTimeoutException -> "Upload failed: request timed out."
            is IOException -> "Upload failed: network unavailable."
            is IllegalStateException,
            is IllegalArgumentException -> error.message.orEmpty()
            else -> "Upload failed: ${error.message.orEmpty()}"
        }
    }
}
