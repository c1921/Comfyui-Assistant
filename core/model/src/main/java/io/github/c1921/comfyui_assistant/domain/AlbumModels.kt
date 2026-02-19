package io.github.c1921.comfyui_assistant.domain

data class RequestNodeField(
    val nodeId: String,
    val fieldName: String,
    val fieldValue: String,
)

data class ImagePresetSnapshot(
    val id: String,
    val width: Int,
    val height: Int,
)

data class GenerationRequestSnapshot(
    val requestSentAtEpochMs: Long,
    val generationMode: GenerationMode,
    val workflowId: String,
    val prompt: String,
    val negative: String,
    val imagePreset: ImagePresetSnapshot?,
    val videoLengthFrames: Int?,
    val uploadedImageFileName: String?,
    val nodeInfoList: List<RequestNodeField>,
)

data class AlbumSaveFailureItem(
    val index: Int,
    val reason: String,
)

data class AlbumSaveResult(
    val taskId: String,
    val totalOutputs: Int,
    val successCount: Int,
    val failedCount: Int,
    val failures: List<AlbumSaveFailureItem>,
)

enum class AlbumDecodeOutcomeCode {
    DECODED_IMAGE,
    DECODED_VIDEO,
    FALLBACK_NOT_CARRIER_IMAGE,
    FALLBACK_PASSWORD_REQUIRED,
    FALLBACK_WRONG_PASSWORD,
    FALLBACK_NON_IMAGE_PAYLOAD,
    FALLBACK_CORRUPTED_PAYLOAD,
    NOT_ATTEMPTED,
}

data class AlbumMediaItem(
    val index: Int,
    val sourceFileUrl: String,
    val sourceFileType: String,
    val sourceNodeId: String?,
    val savedMediaKind: OutputMediaKind,
    val localRelativePath: String,
    val extension: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val decodedFromDuck: Boolean,
    val decodeOutcomeCode: AlbumDecodeOutcomeCode,
    val createdAtEpochMs: Long,
)

data class AlbumTaskSummary(
    val taskId: String,
    val savedAtEpochMs: Long,
    val generationMode: GenerationMode,
    val workflowId: String,
    val prompt: String,
    val totalOutputs: Int,
    val savedCount: Int,
    val failedCount: Int,
)

data class AlbumTaskDetail(
    val schemaVersion: Int,
    val taskId: String,
    val requestSentAtEpochMs: Long,
    val savedAtEpochMs: Long,
    val generationMode: GenerationMode,
    val workflowId: String,
    val prompt: String,
    val negative: String,
    val imagePreset: ImagePresetSnapshot?,
    val videoLengthFrames: Int?,
    val uploadedImageFileName: String?,
    val nodeInfoList: List<RequestNodeField>,
    val promptTipsNodeErrors: String?,
    val totalOutputs: Int,
    val savedCount: Int,
    val failedCount: Int,
    val failures: List<AlbumSaveFailureItem>,
    val mediaItems: List<AlbumMediaItem>,
)
