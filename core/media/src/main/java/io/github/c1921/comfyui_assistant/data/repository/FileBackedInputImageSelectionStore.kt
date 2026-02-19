package io.github.c1921.comfyui_assistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileBackedInputImageSelectionStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InputImageSelectionStore {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun loadSelections(): PersistedInputImageSelections = withContext(ioDispatcher) {
        PersistedInputImageSelections(
            imageMode = loadSelection(GenerationMode.IMAGE),
            videoMode = loadSelection(GenerationMode.VIDEO),
        )
    }

    override suspend fun persistSelection(
        mode: GenerationMode,
        sourceUri: Uri,
        displayName: String,
    ): Result<PersistedInputImageSelection> = withContext(ioDispatcher) {
        runCatching {
            val resolver = appContext.contentResolver
            val dir = File(appContext.filesDir, INPUT_IMAGE_DIR).apply {
                if (!exists() && !mkdirs()) {
                    throw IllegalStateException("Unable to create input image directory.")
                }
            }

            deleteSelectionFiles(mode, dir)

            val extension = resolveExtension(
                mimeType = resolver.getType(sourceUri),
                sourceUri = sourceUri,
            )
            val targetFile = File(dir, "${modeFileBaseName(mode)}.$extension")
            resolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to read selected image.")

            val normalizedDisplayName = displayName.trim().ifBlank { targetFile.name }
            saveSelection(mode, targetFile.absolutePath, normalizedDisplayName)
            PersistedInputImageSelection(
                uri = Uri.fromFile(targetFile),
                displayName = normalizedDisplayName,
            )
        }
    }

    override suspend fun clearSelection(mode: GenerationMode) = withContext(ioDispatcher) {
        deleteSelectionFiles(mode, File(appContext.filesDir, INPUT_IMAGE_DIR))
        clearSelectionMetadata(mode)
    }

    private fun loadSelection(mode: GenerationMode): PersistedInputImageSelection? {
        val path = prefs.getString(pathKey(mode), "").orEmpty()
        if (path.isBlank()) {
            return null
        }

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            clearSelectionMetadata(mode)
            return null
        }

        val displayName = prefs.getString(displayNameKey(mode), "").orEmpty().ifBlank { file.name }
        return PersistedInputImageSelection(
            uri = Uri.fromFile(file),
            displayName = displayName,
        )
    }

    private fun saveSelection(
        mode: GenerationMode,
        absolutePath: String,
        displayName: String,
    ) {
        prefs.edit()
            .putString(pathKey(mode), absolutePath)
            .putString(displayNameKey(mode), displayName)
            .apply()
    }

    private fun clearSelectionMetadata(mode: GenerationMode) {
        prefs.edit()
            .remove(pathKey(mode))
            .remove(displayNameKey(mode))
            .apply()
    }

    private fun deleteSelectionFiles(
        mode: GenerationMode,
        dir: File,
    ) {
        val savedPath = prefs.getString(pathKey(mode), "").orEmpty()
        if (savedPath.isNotBlank()) {
            File(savedPath).delete()
        }

        if (!dir.exists() || !dir.isDirectory) {
            return
        }

        val fileNamePrefix = "${modeFileBaseName(mode)}."
        dir.listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith(fileNamePrefix) || file.name == modeFileBaseName(mode)) {
                file.delete()
            }
        }
    }

    private fun resolveExtension(
        mimeType: String?,
        sourceUri: Uri,
    ): String {
        val normalizedMimeType = mimeType
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        val extensionFromMimeType = when (normalizedMimeType) {
            "image/jpeg" -> "jpg"
            "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> ""
        }
        if (extensionFromMimeType.isNotBlank()) {
            return extensionFromMimeType
        }

        val extensionFromUri = sourceUri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (extensionFromUri.isNotBlank()) {
            return extensionFromUri
        }
        return "jpg"
    }

    private fun modeFileBaseName(mode: GenerationMode): String {
        return when (mode) {
            GenerationMode.IMAGE -> IMAGE_MODE_FILE_BASENAME
            GenerationMode.VIDEO -> VIDEO_MODE_FILE_BASENAME
        }
    }

    private fun pathKey(mode: GenerationMode): String {
        return when (mode) {
            GenerationMode.IMAGE -> KEY_IMAGE_MODE_PATH
            GenerationMode.VIDEO -> KEY_VIDEO_MODE_PATH
        }
    }

    private fun displayNameKey(mode: GenerationMode): String {
        return when (mode) {
            GenerationMode.IMAGE -> KEY_IMAGE_MODE_DISPLAY_NAME
            GenerationMode.VIDEO -> KEY_VIDEO_MODE_DISPLAY_NAME
        }
    }

    private companion object {
        const val PREFS_NAME = "input_image_selection_store"
        const val INPUT_IMAGE_DIR = "persisted_input_images"
        const val IMAGE_MODE_FILE_BASENAME = "image_mode_input"
        const val VIDEO_MODE_FILE_BASENAME = "video_mode_input"

        const val KEY_IMAGE_MODE_PATH = "image_mode_path"
        const val KEY_IMAGE_MODE_DISPLAY_NAME = "image_mode_display_name"
        const val KEY_VIDEO_MODE_PATH = "video_mode_path"
        const val KEY_VIDEO_MODE_DISPLAY_NAME = "video_mode_display_name"
    }
}
