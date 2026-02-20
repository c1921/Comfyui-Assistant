package io.github.c1921.comfyui_assistant.data.repository

import android.content.Context
import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeFailureReason
import io.github.c1921.comfyui_assistant.data.decoder.DuckMediaDecodeOutcome
import io.github.c1921.comfyui_assistant.data.decoder.DuckPayloadDecoder
import io.github.c1921.comfyui_assistant.domain.AlbumDecodeOutcomeCode
import io.github.c1921.comfyui_assistant.domain.AlbumDeleteResult
import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumSaveFailureItem
import io.github.c1921.comfyui_assistant.domain.AlbumSaveResult
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.AlbumTaskSummary
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationRequestSnapshot
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

class FileBackedInternalAlbumRepository(
    context: Context,
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    payloadDecoder: DuckPayloadDecoder = DuckPayloadDecoder(),
    private val decodeMediaIfCarrierImage: (ByteArray, String) -> DuckMediaDecodeOutcome =
        payloadDecoder::decodeMediaIfCarrierImage,
) : InternalAlbumRepository {
    private val appContext = context.applicationContext
    private val albumRootDir = File(appContext.filesDir, ALBUM_ROOT_DIRECTORY)
    private val tasksDir = File(albumRootDir, TASKS_DIRECTORY)
    private val indexFile = File(albumRootDir, INDEX_FILE_NAME)

    private val fileMutex = Mutex()
    private val taskSummariesState = MutableStateFlow<List<AlbumTaskSummary>>(emptyList())
    private val mediaSummariesState = MutableStateFlow<List<AlbumMediaSummary>>(emptyList())

    init {
        runCatching {
            ensureBaseDirectories()
            publishIndexSnapshot(readIndexSnapshotWithMigrationLocked())
        }
    }

    override suspend fun archiveGeneration(
        requestSnapshot: GenerationRequestSnapshot,
        successState: GenerationState.Success,
        decodePassword: String,
    ): Result<AlbumSaveResult> = withContext(ioDispatcher) {
        runCatching {
            fileMutex.withLock {
                ensureBaseDirectories()
                loadTaskDetailLocked(successState.taskId)?.let { existing ->
                    publishIndexSnapshot(readIndexSnapshotWithMigrationLocked())
                    return@withLock AlbumSaveResult(
                        taskId = existing.taskId,
                        totalOutputs = existing.totalOutputs,
                        successCount = existing.savedCount,
                        failedCount = existing.failedCount,
                        failures = existing.failures,
                    )
                }

                val taskId = successState.taskId
                val taskDirectory = taskDirectoryFor(taskId).apply {
                    if (!exists() && !mkdirs()) {
                        throw IllegalStateException("Unable to create task directory for $taskId")
                    }
                }

                val mediaItems = mutableListOf<AlbumMediaItem>()
                val failures = mutableListOf<AlbumSaveFailureItem>()
                successState.results.forEachIndexed { idx, output ->
                    val index = idx + 1
                    runCatching {
                        archiveSingleOutput(
                            taskDirectoryName = taskDirectory.name,
                            taskDirectory = taskDirectory,
                            output = output,
                            index = index,
                            decodePassword = decodePassword,
                        )
                    }.onSuccess { mediaItems += it }
                        .onFailure { error ->
                            failures += AlbumSaveFailureItem(
                                index = index,
                                reason = error.message?.ifBlank { "unknown error" } ?: "unknown error",
                            )
                        }
                }

                val detail = AlbumTaskDetail(
                    schemaVersion = TASK_SCHEMA_VERSION,
                    taskId = taskId,
                    requestSentAtEpochMs = requestSnapshot.requestSentAtEpochMs,
                    savedAtEpochMs = System.currentTimeMillis(),
                    generationMode = requestSnapshot.generationMode,
                    workflowId = requestSnapshot.workflowId,
                    prompt = requestSnapshot.prompt,
                    negative = requestSnapshot.negative,
                    imagePreset = requestSnapshot.imagePreset,
                    videoLengthFrames = requestSnapshot.videoLengthFrames,
                    uploadedImageFileName = requestSnapshot.uploadedImageFileName,
                    nodeInfoList = requestSnapshot.nodeInfoList,
                    promptTipsNodeErrors = successState.promptTipsNodeErrors,
                    totalOutputs = successState.results.size,
                    savedCount = mediaItems.size,
                    failedCount = failures.size,
                    failures = failures,
                    mediaItems = mediaItems.sortedBy { it.index },
                )
                writeTaskDetailLocked(taskDirectory, detail)

                val current = readIndexSnapshotWithMigrationLocked()
                val updated = IndexSnapshot(
                    tasks = (current.tasks.filterNot { it.taskId == taskId } + detail.toSummary())
                        .sortedByDescending { it.savedAtEpochMs },
                    media = (current.media.filterNot { it.key.taskId == taskId } + detail.toMediaSummaries())
                        .sortedByDescending { it.createdAtEpochMs },
                )
                writeIndexLocked(updated)
                publishIndexSnapshot(updated)

                AlbumSaveResult(
                    taskId = detail.taskId,
                    totalOutputs = detail.totalOutputs,
                    successCount = detail.savedCount,
                    failedCount = detail.failedCount,
                    failures = detail.failures,
                )
            }
        }
    }

    override fun observeTaskSummaries(): Flow<List<AlbumTaskSummary>> = taskSummariesState.asStateFlow()

    override fun observeMediaSummaries(): Flow<List<AlbumMediaSummary>> = mediaSummariesState.asStateFlow()

    override suspend fun loadTaskDetail(taskId: String): Result<AlbumTaskDetail> = withContext(ioDispatcher) {
        runCatching {
            fileMutex.withLock {
                loadTaskDetailLocked(taskId)
                    ?: throw FileNotFoundException("Task $taskId not found in internal album.")
            }
        }
    }

    override suspend fun hasTask(taskId: String): Boolean = withContext(ioDispatcher) {
        fileMutex.withLock { loadTaskDetailLocked(taskId) != null }
    }

    override suspend fun findFirstImageKey(taskId: String): Result<AlbumMediaKey?> = withContext(ioDispatcher) {
        runCatching {
            fileMutex.withLock {
                val detail = loadTaskDetailLocked(taskId) ?: return@withLock null
                detail.mediaItems.firstOrNull { it.savedMediaKind == OutputMediaKind.IMAGE }
                    ?.let { AlbumMediaKey(taskId = detail.taskId, index = it.index) }
            }
        }
    }

    override suspend fun findFirstMediaKey(taskId: String): Result<AlbumMediaKey?> = withContext(ioDispatcher) {
        runCatching {
            fileMutex.withLock {
                val detail = loadTaskDetailLocked(taskId) ?: return@withLock null
                detail.mediaItems.firstOrNull()?.let {
                    AlbumMediaKey(taskId = detail.taskId, index = it.index)
                }
            }
        }
    }

    override suspend fun deleteMedia(keys: Set<AlbumMediaKey>): Result<AlbumDeleteResult> = withContext(ioDispatcher) {
        val normalizedKeys = keys.mapNotNull(::normalizeMediaKey).toSet()
        val requestedCount = keys.size
        runCatching {
            fileMutex.withLock {
                ensureBaseDirectories()
                deleteMediaLocked(
                    normalizedKeys = normalizedKeys,
                    requestedCount = requestedCount,
                )
            }
        }.onFailure {
            runCatching {
                fileMutex.withLock {
                    ensureBaseDirectories()
                    val rebuilt = rebuildIndexFromTaskFiles()
                    writeIndexLocked(rebuilt)
                    publishIndexSnapshot(rebuilt)
                }
            }
        }
    }

    private fun deleteMediaLocked(
        normalizedKeys: Set<AlbumMediaKey>,
        requestedCount: Int,
    ): AlbumDeleteResult {
        if (normalizedKeys.isEmpty()) {
            return AlbumDeleteResult(
                requestedCount = requestedCount,
                deletedCount = 0,
                missingCount = 0,
                affectedTaskCount = 0,
            )
        }

        var deletedCount = 0
        var missingCount = 0
        var affectedTaskCount = 0

        normalizedKeys.groupBy { it.taskId }.forEach { (taskId, taskKeys) ->
            val detail = loadTaskDetailLocked(taskId)
            if (detail == null) {
                missingCount += taskKeys.size
                return@forEach
            }

            val indexesToDelete = taskKeys.map { it.index }.toSet()
            val mediaByIndex = detail.mediaItems.associateBy { it.index }
            val mediaToDelete = mutableListOf<AlbumMediaItem>()
            indexesToDelete.forEach { index ->
                val media = mediaByIndex[index]
                if (media == null) {
                    missingCount += 1
                } else {
                    mediaToDelete += media
                }
            }

            if (mediaToDelete.isEmpty()) {
                return@forEach
            }

            mediaToDelete.forEach { media ->
                deleteFileIfExists(File(albumRootDir, media.localRelativePath))
            }

            val remainingMedia = detail.mediaItems.filterNot { it.index in indexesToDelete }
            val taskDirectory = taskDirectoryFor(taskId)
            if (remainingMedia.isEmpty()) {
                deleteDirectoryRecursively(taskDirectory)
            } else {
                val updatedDetail = detail.copy(
                    totalOutputs = remainingMedia.size + detail.failedCount,
                    savedCount = remainingMedia.size,
                    mediaItems = remainingMedia,
                )
                writeTaskDetailLocked(taskDirectory, updatedDetail)
            }

            affectedTaskCount += 1
            deletedCount += mediaToDelete.size
        }

        val rebuilt = rebuildIndexFromTaskFiles()
        writeIndexLocked(rebuilt)
        publishIndexSnapshot(rebuilt)
        return AlbumDeleteResult(
            requestedCount = requestedCount,
            deletedCount = deletedCount,
            missingCount = missingCount,
            affectedTaskCount = affectedTaskCount,
        )
    }

    private fun archiveSingleOutput(
        taskDirectoryName: String,
        taskDirectory: File,
        output: io.github.c1921.comfyui_assistant.domain.GeneratedOutput,
        index: Int,
        decodePassword: String,
    ): AlbumMediaItem {
        val downloadedBytes = download(output.fileUrl)
        val resolvedExtension = resolveExtension(output.fileType, output.fileUrl)
        val savedKind = resolveSavedKind(output, resolvedExtension)
        val prepared = when (savedKind) {
            OutputMediaKind.VIDEO -> {
                val ext = if (resolvedExtension in VIDEO_EXTENSIONS) resolvedExtension else DEFAULT_VIDEO_EXTENSION
                PreparedArchivedMedia(
                    savedBytes = downloadedBytes,
                    extension = ext,
                    mimeType = resolveVideoMimeType(ext),
                    savedKind = OutputMediaKind.VIDEO,
                    decodedFromDuck = false,
                    decodeOutcomeCode = AlbumDecodeOutcomeCode.NOT_ATTEMPTED,
                )
            }

            OutputMediaKind.IMAGE,
            OutputMediaKind.UNKNOWN,
            -> prepareImageOrDecodedMedia(downloadedBytes, resolvedExtension, decodePassword)
        }

        val fileName = "out_$index.${prepared.extension}"
        val targetFile = File(taskDirectory, fileName)
        writeBytesAtomic(targetFile, prepared.savedBytes)

        return AlbumMediaItem(
            index = index,
            sourceFileUrl = output.fileUrl,
            sourceFileType = output.fileType,
            sourceNodeId = output.nodeId,
            savedMediaKind = prepared.savedKind,
            localRelativePath = "$TASKS_DIRECTORY/$taskDirectoryName/$fileName",
            extension = prepared.extension,
            mimeType = prepared.mimeType,
            fileSizeBytes = prepared.savedBytes.size.toLong(),
            decodedFromDuck = prepared.decodedFromDuck,
            decodeOutcomeCode = prepared.decodeOutcomeCode,
            createdAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun prepareImageOrDecodedMedia(
        downloadedBytes: ByteArray,
        resolvedExtension: String,
        decodePassword: String,
    ): PreparedArchivedMedia {
        return when (val decodeOutcome = decodeMediaIfCarrierImage(downloadedBytes, decodePassword)) {
            is DuckMediaDecodeOutcome.DecodedImage -> {
                val ext = normalizeImageExtension(decodeOutcome.extension)
                PreparedArchivedMedia(
                    savedBytes = decodeOutcome.imageBytes,
                    extension = ext,
                    mimeType = resolveImageMimeType(ext),
                    savedKind = OutputMediaKind.IMAGE,
                    decodedFromDuck = true,
                    decodeOutcomeCode = AlbumDecodeOutcomeCode.DECODED_IMAGE,
                )
            }

            is DuckMediaDecodeOutcome.DecodedVideo -> {
                val ext = normalizeVideoExtension(decodeOutcome.extension)
                PreparedArchivedMedia(
                    savedBytes = decodeOutcome.videoBytes,
                    extension = ext,
                    mimeType = resolveVideoMimeType(ext),
                    savedKind = OutputMediaKind.VIDEO,
                    decodedFromDuck = true,
                    decodeOutcomeCode = AlbumDecodeOutcomeCode.DECODED_VIDEO,
                )
            }

            is DuckMediaDecodeOutcome.Fallback -> {
                val ext = if (resolvedExtension in VIDEO_EXTENSIONS) DEFAULT_IMAGE_EXTENSION
                else normalizeImageExtension(resolvedExtension)
                PreparedArchivedMedia(
                    savedBytes = downloadedBytes,
                    extension = ext,
                    mimeType = resolveImageMimeType(ext),
                    savedKind = OutputMediaKind.IMAGE,
                    decodedFromDuck = false,
                    decodeOutcomeCode = decodeFailureToOutcomeCode(decodeOutcome.reason),
                )
            }
        }
    }

    private fun decodeFailureToOutcomeCode(reason: DuckDecodeFailureReason): AlbumDecodeOutcomeCode {
        return when (reason) {
            DuckDecodeFailureReason.NotCarrierImage -> AlbumDecodeOutcomeCode.FALLBACK_NOT_CARRIER_IMAGE
            DuckDecodeFailureReason.PasswordRequired -> AlbumDecodeOutcomeCode.FALLBACK_PASSWORD_REQUIRED
            DuckDecodeFailureReason.WrongPassword -> AlbumDecodeOutcomeCode.FALLBACK_WRONG_PASSWORD
            is DuckDecodeFailureReason.NonImagePayload -> AlbumDecodeOutcomeCode.FALLBACK_NON_IMAGE_PAYLOAD
            DuckDecodeFailureReason.CorruptedPayload -> AlbumDecodeOutcomeCode.FALLBACK_CORRUPTED_PAYLOAD
        }
    }

    private fun resolveSavedKind(
        output: io.github.c1921.comfyui_assistant.domain.GeneratedOutput,
        resolvedExtension: String,
    ): OutputMediaKind {
        return when (val detected = output.detectMediaKind()) {
            OutputMediaKind.UNKNOWN -> if (resolvedExtension in VIDEO_EXTENSIONS) OutputMediaKind.VIDEO else OutputMediaKind.IMAGE
            else -> detected
        }
    }

    private fun resolveExtension(fileType: String, fileUrl: String): String {
        val normalizedType = fileType.trim().lowercase(Locale.ROOT)
        if (normalizedType.matches(EXTENSION_REGEX)) return normalizedType
        val fromUrl = fileUrl.substringAfterLast('.', "")
            .substringBefore('?')
            .trim()
            .lowercase(Locale.ROOT)
        return if (fromUrl.matches(EXTENSION_REGEX)) fromUrl else DEFAULT_IMAGE_EXTENSION
    }

    private fun normalizeImageExtension(extension: String): String {
        val normalized = extension.trim().removePrefix(".").lowercase(Locale.ROOT)
        return if (normalized in IMAGE_EXTENSIONS) normalized else DEFAULT_IMAGE_EXTENSION
    }

    private fun normalizeVideoExtension(extension: String): String {
        val normalized = extension.trim().removePrefix(".").lowercase(Locale.ROOT)
        return if (normalized in VIDEO_EXTENSIONS) normalized else DEFAULT_VIDEO_EXTENSION
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

    private fun download(fileUrl: String): ByteArray {
        val request = Request.Builder().url(fileUrl).get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed, HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Download failed, empty body.")
            body.bytes()
        }
    }

    private fun readIndexSnapshotWithMigrationLocked(): IndexSnapshot {
        if (!indexFile.exists() || !indexFile.isFile) {
            val rebuilt = rebuildIndexFromTaskFiles()
            writeIndexLocked(rebuilt)
            return rebuilt
        }
        return runCatching {
            val raw = indexFile.readText()
            if (raw.isBlank()) return@runCatching IndexSnapshot(emptyList(), emptyList())
            val obj = JSONObject(raw)
            val schemaVersion = obj.optInt("schemaVersion", 1)
            val tasksFromIndex = parseTaskSummaries(obj.optJSONArray("tasks"))
            if (schemaVersion >= INDEX_SCHEMA_VERSION && obj.has("media")) {
                val mediaFromIndex = parseMediaSummaries(obj.optJSONArray("media"))
                IndexSnapshot(
                    tasks = tasksFromIndex.sortedByDescending { it.savedAtEpochMs },
                    media = mediaFromIndex.sortedByDescending { it.createdAtEpochMs },
                )
            } else {
                val rebuilt = rebuildIndexFromTaskFiles(fallbackTaskSummaries = tasksFromIndex)
                writeIndexLocked(rebuilt)
                rebuilt
            }
        }.getOrElse {
            val rebuilt = rebuildIndexFromTaskFiles()
            writeIndexLocked(rebuilt)
            rebuilt
        }
    }

    private fun parseTaskSummaries(array: JSONArray?): List<AlbumTaskSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(item.toTaskSummary())
            }
        }
    }

    private fun parseMediaSummaries(array: JSONArray?): List<AlbumMediaSummary> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(item.toMediaSummary())
            }
        }
    }

    private fun rebuildIndexFromTaskFiles(
        fallbackTaskSummaries: List<AlbumTaskSummary> = emptyList(),
    ): IndexSnapshot {
        val details = readAllTaskDetails()
        val detailSummaries = details.map { it.toSummary() }
        val detailMedia = details.flatMap { it.toMediaSummaries() }
        val mergedTasks = (fallbackTaskSummaries + detailSummaries)
            .associateBy { it.taskId }
            .values
            .sortedByDescending { it.savedAtEpochMs }
        return IndexSnapshot(
            tasks = mergedTasks,
            media = detailMedia.sortedByDescending { it.createdAtEpochMs },
        )
    }

    private fun readAllTaskDetails(): List<AlbumTaskDetail> {
        if (!tasksDir.exists() || !tasksDir.isDirectory) return emptyList()
        return tasksDir.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val taskFile = File(dir, TASK_JSON_FILE_NAME)
                if (!taskFile.exists() || !taskFile.isFile) return@mapNotNull null
                runCatching { JSONObject(taskFile.readText()).toTaskDetail() }.getOrNull()
            }
    }

    private fun writeTaskDetailLocked(
        taskDirectory: File,
        detail: AlbumTaskDetail,
    ) {
        writeTextAtomic(
            target = File(taskDirectory, TASK_JSON_FILE_NAME),
            text = detail.toJson().toString(2),
        )
    }

    private fun writeIndexLocked(snapshot: IndexSnapshot) {
        val obj = JSONObject().apply {
            put("schemaVersion", INDEX_SCHEMA_VERSION)
            put("tasks", JSONArray().apply { snapshot.tasks.forEach { put(it.toJson()) } })
            put("media", JSONArray().apply { snapshot.media.forEach { put(it.toJson()) } })
        }
        writeTextAtomic(indexFile, obj.toString(2))
    }

    private fun publishIndexSnapshot(snapshot: IndexSnapshot) {
        taskSummariesState.value = snapshot.tasks
        mediaSummariesState.value = snapshot.media
    }

    private fun loadTaskDetailLocked(taskId: String): AlbumTaskDetail? {
        val detailFile = File(taskDirectoryFor(taskId), TASK_JSON_FILE_NAME)
        if (!detailFile.exists() || !detailFile.isFile) return null
        val raw = detailFile.readText()
        if (raw.isBlank()) return null
        return JSONObject(raw).toTaskDetail()
    }

    private fun ensureBaseDirectories() {
        if (!albumRootDir.exists() && !albumRootDir.mkdirs()) {
            throw IllegalStateException("Unable to create internal album root directory.")
        }
        if (!tasksDir.exists() && !tasksDir.mkdirs()) {
            throw IllegalStateException("Unable to create internal album tasks directory.")
        }
    }

    private fun taskDirectoryFor(taskId: String): File {
        return File(tasksDir, sanitizeTaskId(taskId))
    }

    private fun sanitizeTaskId(taskId: String): String {
        val sanitized = taskId.trim().replace(INVALID_TASK_DIR_CHAR_REGEX, "_")
        return sanitized.ifBlank { "unknown_task" }
    }

    private fun normalizeMediaKey(key: AlbumMediaKey): AlbumMediaKey? {
        val taskId = key.taskId.trim()
        if (taskId.isBlank() || key.index <= 0) {
            return null
        }
        return AlbumMediaKey(taskId = taskId, index = key.index)
    }

    private fun deleteFileIfExists(target: File) {
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Unable to delete file: ${target.absolutePath}")
        }
    }

    private fun deleteDirectoryRecursively(directory: File) {
        if (!directory.exists()) {
            return
        }
        if (!directory.deleteRecursively() && directory.exists()) {
            throw IllegalStateException("Unable to delete directory: ${directory.absolutePath}")
        }
    }

    private fun writeBytesAtomic(
        target: File,
        bytes: ByteArray,
    ) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("Unable to create directory: ${parent.absolutePath}")
            }
        }
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { it.write(bytes) }
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Unable to replace file: ${target.absolutePath}")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun writeTextAtomic(
        target: File,
        text: String,
    ) {
        writeBytesAtomic(target, text.toByteArray(Charsets.UTF_8))
    }

    private fun AlbumTaskDetail.toSummary(): AlbumTaskSummary {
        return AlbumTaskSummary(
            taskId = taskId,
            savedAtEpochMs = savedAtEpochMs,
            generationMode = generationMode,
            workflowId = workflowId,
            prompt = prompt,
            totalOutputs = totalOutputs,
            savedCount = savedCount,
            failedCount = failedCount,
        )
    }

    private fun AlbumTaskDetail.toMediaSummaries(): List<AlbumMediaSummary> {
        return mediaItems.map { media ->
            AlbumMediaSummary(
                key = AlbumMediaKey(taskId = taskId, index = media.index),
                createdAtEpochMs = media.createdAtEpochMs,
                savedAtEpochMs = savedAtEpochMs,
                savedMediaKind = media.savedMediaKind,
                localRelativePath = media.localRelativePath,
                mimeType = media.mimeType,
                workflowId = workflowId,
                prompt = prompt,
            )
        }
    }

    private fun AlbumTaskSummary.toJson(): JSONObject {
        return JSONObject().apply {
            put("taskId", taskId)
            put("savedAtEpochMs", savedAtEpochMs)
            put("generationMode", generationMode.name)
            put("workflowId", workflowId)
            put("prompt", prompt)
            put("totalOutputs", totalOutputs)
            put("savedCount", savedCount)
            put("failedCount", failedCount)
        }
    }

    private fun JSONObject.toTaskSummary(): AlbumTaskSummary {
        return AlbumTaskSummary(
            taskId = optString("taskId"),
            savedAtEpochMs = optLong("savedAtEpochMs"),
            generationMode = enumValueOrDefault(optString("generationMode"), GenerationMode.IMAGE),
            workflowId = optString("workflowId"),
            prompt = optString("prompt"),
            totalOutputs = optInt("totalOutputs"),
            savedCount = optInt("savedCount"),
            failedCount = optInt("failedCount"),
        )
    }

    private fun AlbumMediaSummary.toJson(): JSONObject {
        return JSONObject().apply {
            put("taskId", key.taskId)
            put("index", key.index)
            put("createdAtEpochMs", createdAtEpochMs)
            put("savedAtEpochMs", savedAtEpochMs)
            put("savedMediaKind", savedMediaKind.name)
            put("localRelativePath", localRelativePath)
            put("mimeType", mimeType)
            put("workflowId", workflowId)
            put("prompt", prompt)
        }
    }

    private fun JSONObject.toMediaSummary(): AlbumMediaSummary {
        return AlbumMediaSummary(
            key = AlbumMediaKey(taskId = optString("taskId"), index = optInt("index")),
            createdAtEpochMs = optLong("createdAtEpochMs"),
            savedAtEpochMs = optLong("savedAtEpochMs"),
            savedMediaKind = enumValueOrDefault(optString("savedMediaKind"), OutputMediaKind.IMAGE),
            localRelativePath = optString("localRelativePath"),
            mimeType = optString("mimeType"),
            workflowId = optString("workflowId"),
            prompt = optString("prompt"),
        )
    }

    private fun AlbumTaskDetail.toJson(): JSONObject {
        val preset = imagePreset
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("taskId", taskId)
            put("requestSentAtEpochMs", requestSentAtEpochMs)
            put("savedAtEpochMs", savedAtEpochMs)
            put("generationMode", generationMode.name)
            put("workflowId", workflowId)
            put("prompt", prompt)
            put("negative", negative)
            if (preset != null) {
                put(
                    "imagePreset",
                    JSONObject().apply {
                        put("id", preset.id)
                        put("width", preset.width)
                        put("height", preset.height)
                    },
                )
            } else {
                put("imagePreset", JSONObject.NULL)
            }
            if (videoLengthFrames != null) put("videoLengthFrames", videoLengthFrames) else put("videoLengthFrames", JSONObject.NULL)
            if (uploadedImageFileName != null) put("uploadedImageFileName", uploadedImageFileName) else put("uploadedImageFileName", JSONObject.NULL)
            if (promptTipsNodeErrors != null) put("promptTipsNodeErrors", promptTipsNodeErrors) else put("promptTipsNodeErrors", JSONObject.NULL)
            put(
                "nodeInfoList",
                JSONArray().apply {
                    nodeInfoList.forEach { node ->
                        put(
                            JSONObject().apply {
                                put("nodeId", node.nodeId)
                                put("fieldName", node.fieldName)
                                put("fieldValue", node.fieldValue)
                            },
                        )
                    }
                },
            )
            put("totalOutputs", totalOutputs)
            put("savedCount", savedCount)
            put("failedCount", failedCount)
            put(
                "failures",
                JSONArray().apply {
                    failures.forEach { failure ->
                        put(
                            JSONObject().apply {
                                put("index", failure.index)
                                put("reason", failure.reason)
                            },
                        )
                    }
                },
            )
            put(
                "mediaItems",
                JSONArray().apply {
                    mediaItems.forEach { media ->
                        put(
                            JSONObject().apply {
                                put("index", media.index)
                                put("sourceFileUrl", media.sourceFileUrl)
                                put("sourceFileType", media.sourceFileType)
                                if (media.sourceNodeId != null) put("sourceNodeId", media.sourceNodeId) else put("sourceNodeId", JSONObject.NULL)
                                put("savedMediaKind", media.savedMediaKind.name)
                                put("localRelativePath", media.localRelativePath)
                                put("extension", media.extension)
                                put("mimeType", media.mimeType)
                                put("fileSizeBytes", media.fileSizeBytes)
                                put("decodedFromDuck", media.decodedFromDuck)
                                put("decodeOutcomeCode", media.decodeOutcomeCode.name)
                                put("createdAtEpochMs", media.createdAtEpochMs)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun JSONObject.toTaskDetail(): AlbumTaskDetail {
        val imagePresetObj = optJSONObject("imagePreset")
        val nodeInfoJson = optJSONArray("nodeInfoList") ?: JSONArray()
        val failuresJson = optJSONArray("failures") ?: JSONArray()
        val mediaItemsJson = optJSONArray("mediaItems") ?: JSONArray()
        return AlbumTaskDetail(
            schemaVersion = optInt("schemaVersion", TASK_SCHEMA_VERSION),
            taskId = optString("taskId"),
            requestSentAtEpochMs = optLong("requestSentAtEpochMs"),
            savedAtEpochMs = optLong("savedAtEpochMs"),
            generationMode = enumValueOrDefault(optString("generationMode"), GenerationMode.IMAGE),
            workflowId = optString("workflowId"),
            prompt = optString("prompt"),
            negative = optString("negative"),
            imagePreset = imagePresetObj?.let {
                io.github.c1921.comfyui_assistant.domain.ImagePresetSnapshot(
                    id = it.optString("id"),
                    width = it.optInt("width"),
                    height = it.optInt("height"),
                )
            },
            videoLengthFrames = if (isNull("videoLengthFrames")) null else optInt("videoLengthFrames"),
            uploadedImageFileName = if (isNull("uploadedImageFileName")) null else optString("uploadedImageFileName"),
            nodeInfoList = buildList {
                for (i in 0 until nodeInfoJson.length()) {
                    val item = nodeInfoJson.optJSONObject(i) ?: continue
                    add(
                        io.github.c1921.comfyui_assistant.domain.RequestNodeField(
                            nodeId = item.optString("nodeId"),
                            fieldName = item.optString("fieldName"),
                            fieldValue = item.optString("fieldValue"),
                        ),
                    )
                }
            },
            promptTipsNodeErrors = if (isNull("promptTipsNodeErrors")) null else optString("promptTipsNodeErrors"),
            totalOutputs = optInt("totalOutputs"),
            savedCount = optInt("savedCount"),
            failedCount = optInt("failedCount"),
            failures = buildList {
                for (i in 0 until failuresJson.length()) {
                    val item = failuresJson.optJSONObject(i) ?: continue
                    add(
                        io.github.c1921.comfyui_assistant.domain.AlbumSaveFailureItem(
                            index = item.optInt("index"),
                            reason = item.optString("reason"),
                        ),
                    )
                }
            },
            mediaItems = buildList {
                for (i in 0 until mediaItemsJson.length()) {
                    val item = mediaItemsJson.optJSONObject(i) ?: continue
                    add(
                        io.github.c1921.comfyui_assistant.domain.AlbumMediaItem(
                            index = item.optInt("index"),
                            sourceFileUrl = item.optString("sourceFileUrl"),
                            sourceFileType = item.optString("sourceFileType"),
                            sourceNodeId = if (item.isNull("sourceNodeId")) null else item.optString("sourceNodeId"),
                            savedMediaKind = enumValueOrDefault(item.optString("savedMediaKind"), OutputMediaKind.IMAGE),
                            localRelativePath = item.optString("localRelativePath"),
                            extension = item.optString("extension"),
                            mimeType = item.optString("mimeType"),
                            fileSizeBytes = item.optLong("fileSizeBytes"),
                            decodedFromDuck = item.optBoolean("decodedFromDuck"),
                            decodeOutcomeCode = enumValueOrDefault(
                                item.optString("decodeOutcomeCode"),
                                AlbumDecodeOutcomeCode.NOT_ATTEMPTED,
                            ),
                            createdAtEpochMs = item.optLong("createdAtEpochMs"),
                        ),
                    )
                }
            }.sortedBy { it.index },
        )
    }

    private fun <T : Enum<T>> enumValueOrDefault(
        raw: String?,
        fallback: T,
    ): T {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return fallback
        @Suppress("UNCHECKED_CAST")
        val enumClass = fallback.javaClass as Class<T>
        return runCatching { java.lang.Enum.valueOf(enumClass, value) }.getOrDefault(fallback)
    }

    private data class PreparedArchivedMedia(
        val savedBytes: ByteArray,
        val extension: String,
        val mimeType: String,
        val savedKind: OutputMediaKind,
        val decodedFromDuck: Boolean,
        val decodeOutcomeCode: AlbumDecodeOutcomeCode,
    )

    private data class IndexSnapshot(
        val tasks: List<AlbumTaskSummary>,
        val media: List<AlbumMediaSummary>,
    )

    private companion object {
        const val ALBUM_ROOT_DIRECTORY = "internal_album"
        const val TASKS_DIRECTORY = "tasks"
        const val INDEX_FILE_NAME = "index.json"
        const val TASK_JSON_FILE_NAME = "task.json"
        const val TASK_SCHEMA_VERSION = 1
        const val INDEX_SCHEMA_VERSION = 2
        const val DEFAULT_IMAGE_EXTENSION = "jpg"
        const val DEFAULT_VIDEO_EXTENSION = "mp4"

        val EXTENSION_REGEX = Regex("^[a-z0-9]{2,5}$")
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "m4v", "mkv")
        val INVALID_TASK_DIR_CHAR_REGEX = Regex("[^a-zA-Z0-9._-]")
    }
}
