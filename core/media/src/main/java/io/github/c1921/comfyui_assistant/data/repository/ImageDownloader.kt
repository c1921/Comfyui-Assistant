package io.github.c1921.comfyui_assistant.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeOutcome
import io.github.c1921.comfyui_assistant.data.decoder.DuckMediaDecodeOutcome
import io.github.c1921.comfyui_assistant.data.decoder.DuckPayloadDecoder
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
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
            val downloadedBytes = download(output.fileUrl)
            val resolvedExtension = resolveExtension(output.fileType, output.fileUrl)
            val savedKind = resolveSavedKind(output, resolvedExtension)
            when (savedKind) {
                OutputMediaKind.IMAGE -> saveImageOrDecodedMedia(
                    taskId = taskId,
                    index = index,
                    decodePassword = decodePassword,
                    downloadedBytes = downloadedBytes,
                    resolvedExtension = resolvedExtension,
                )

                OutputMediaKind.VIDEO -> saveVideo(
                    taskId = taskId,
                    index = index,
                    downloadedBytes = downloadedBytes,
                    resolvedExtension = resolvedExtension,
                )

                OutputMediaKind.UNKNOWN -> saveImageOrDecodedMedia(
                    taskId = taskId,
                    index = index,
                    decodePassword = decodePassword,
                    downloadedBytes = downloadedBytes,
                    resolvedExtension = resolvedExtension,
                )
            }
        }
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

    private fun saveImageOrDecodedMedia(
        taskId: String,
        index: Int,
        decodePassword: String,
        downloadedBytes: ByteArray,
        resolvedExtension: String,
    ): DownloadToGalleryResult {
        val decodeOutcome = payloadDecoder.decodeMediaIfCarrierImage(
            imageBytes = downloadedBytes,
            password = decodePassword,
        )
        return when (decodeOutcome) {
            is DuckMediaDecodeOutcome.DecodedImage -> {
                val extension = decodeOutcome.extension
                val mimeType = resolveImageMimeType(extension)
                val fileName = buildFileName(taskId, index, extension)
                writeToMediaStore(
                    bytes = decodeOutcome.imageBytes,
                    mimeType = mimeType,
                    fileName = fileName,
                    relativePath = "Pictures/RunningHubAssistant",
                    collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                )
                DownloadToGalleryResult(
                    fileName = fileName,
                    savedKind = OutputMediaKind.IMAGE,
                    decodeOutcome = DuckDecodeOutcome.Decoded(
                        imageBytes = decodeOutcome.imageBytes,
                        extension = extension,
                    ),
                )
            }

            is DuckMediaDecodeOutcome.DecodedVideo -> {
                saveVideo(
                    taskId = taskId,
                    index = index,
                    downloadedBytes = decodeOutcome.videoBytes,
                    resolvedExtension = decodeOutcome.extension,
                )
            }

            is DuckMediaDecodeOutcome.Fallback -> {
                val imageFallbackExtension = if (resolvedExtension in VIDEO_EXTENSIONS) "jpg" else resolvedExtension
                val mimeType = resolveImageMimeType(imageFallbackExtension)
                val fileName = buildFileName(taskId, index, imageFallbackExtension)
                writeToMediaStore(
                    bytes = downloadedBytes,
                    mimeType = mimeType,
                    fileName = fileName,
                    relativePath = "Pictures/RunningHubAssistant",
                    collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                )
                DownloadToGalleryResult(
                    fileName = fileName,
                    savedKind = OutputMediaKind.IMAGE,
                    decodeOutcome = DuckDecodeOutcome.Fallback(
                        reason = decodeOutcome.reason,
                    ),
                )
            }
        }
    }

    private fun saveVideo(
        taskId: String,
        index: Int,
        downloadedBytes: ByteArray,
        resolvedExtension: String,
    ): DownloadToGalleryResult {
        val extension = if (resolvedExtension in VIDEO_EXTENSIONS) resolvedExtension else "mp4"
        val mimeType = resolveVideoMimeType(extension)
        val fileName = buildFileName(taskId, index, extension)
        writeToMediaStore(
            bytes = downloadedBytes,
            mimeType = mimeType,
            fileName = fileName,
            relativePath = "Movies/RunningHubAssistant",
            collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
        return DownloadToGalleryResult(
            fileName = fileName,
            savedKind = OutputMediaKind.VIDEO,
            decodeOutcome = null,
        )
    }

    private fun writeToMediaStore(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        relativePath: String,
        collectionUri: Uri,
    ) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = appContext.contentResolver
        val uri = resolver.insert(collectionUri, values)
            ?: throw IllegalStateException("Unable to create gallery entry.")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            } ?: throw IllegalStateException("Unable to open output stream.")

            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, doneValues, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun resolveSavedKind(output: GeneratedOutput, resolvedExtension: String): OutputMediaKind {
        return when (val detected = output.detectMediaKind()) {
            OutputMediaKind.UNKNOWN -> {
                if (resolvedExtension in VIDEO_EXTENSIONS) OutputMediaKind.VIDEO else OutputMediaKind.IMAGE
            }

            else -> detected
        }
    }

    private fun buildFileName(taskId: String, index: Int, extension: String): String {
        val timestamp = System.currentTimeMillis()
        return "rh_${taskId}_${index}_$timestamp.$extension"
    }

    private fun resolveExtension(fileType: String, fileUrl: String): String {
        val normalizedType = fileType.trim().lowercase(Locale.ROOT)
        if (normalizedType.matches(EXTENSION_REGEX)) {
            return normalizedType
        }
        val fromUrl = fileUrl.substringAfterLast('.', "")
            .substringBefore('?')
            .trim()
            .lowercase(Locale.ROOT)
        return if (fromUrl.matches(EXTENSION_REGEX)) fromUrl else "jpg"
    }

    private fun resolveImageMimeType(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "jpeg", "jpg" -> "image/jpeg"
            else -> "image/jpeg"
        }
    }

    private fun resolveVideoMimeType(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "m4v" -> "video/x-m4v"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
    }

    private companion object {
        val EXTENSION_REGEX = Regex("^[a-z0-9]{2,5}$")
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "m4v", "mkv")
    }
}
