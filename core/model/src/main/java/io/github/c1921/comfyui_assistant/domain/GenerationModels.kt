package io.github.c1921.comfyui_assistant.domain

import java.util.Locale

data class WorkflowConfig(
    val apiKey: String = "",
    val workflowId: String = "",
    val promptNodeId: String = "",
    val promptFieldName: String = "",
    val negativeNodeId: String = "",
    val negativeFieldName: String = "",
    val sizeNodeId: String = "",
    val imageInputNodeId: String = "",
    val videoWorkflowId: String = "",
    val videoPromptNodeId: String = "",
    val videoPromptFieldName: String = "",
    val videoImageInputNodeId: String = "",
    val decodePassword: String = "",
)

enum class GenerationMode {
    IMAGE,
    VIDEO,
}

data class GenerationInput(
    val prompt: String,
    val negative: String,
    val mode: GenerationMode = GenerationMode.IMAGE,
    val imagePreset: ImageAspectPreset = ImageAspectPreset.RATIO_1_1,
    val hasInputImage: Boolean = false,
    val uploadedImageFileName: String = "",
)

enum class ImageAspectPreset(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
) {
    RATIO_1_1(
        id = "1:1",
        label = "1:1",
        width = 1024,
        height = 1024,
    ),
    RATIO_3_4(
        id = "3:4",
        label = "3:4",
        width = 880,
        height = 1184,
    ),
    RATIO_4_3(
        id = "4:3",
        label = "4:3",
        width = 1184,
        height = 880,
    ),
    RATIO_9_16(
        id = "9:16",
        label = "9:16",
        width = 752,
        height = 1392,
    ),
    RATIO_16_9(
        id = "16:9",
        label = "16:9",
        width = 1392,
        height = 752,
    ),
    RATIO_2_3(
        id = "2:3",
        label = "2:3",
        width = 832,
        height = 1248,
    ),
    RATIO_3_2(
        id = "3:2",
        label = "3:2",
        width = 1248,
        height = 832,
    );
}

enum class OutputMediaKind {
    IMAGE,
    VIDEO,
    UNKNOWN,
}

data class GeneratedOutput(
    val fileUrl: String,
    val fileType: String,
    val nodeId: String?,
) {
    fun detectMediaKind(): OutputMediaKind {
        val normalizedType = fileType.trim().lowercase(Locale.ROOT)
        if (normalizedType in IMAGE_EXTENSIONS) return OutputMediaKind.IMAGE
        if (normalizedType in VIDEO_EXTENSIONS) return OutputMediaKind.VIDEO

        val extension = fileUrl.substringAfterLast('.', "")
            .substringBefore('?')
            .trim()
            .lowercase(Locale.ROOT)
        if (extension in IMAGE_EXTENSIONS) return OutputMediaKind.IMAGE
        if (extension in VIDEO_EXTENSIONS) return OutputMediaKind.VIDEO
        return OutputMediaKind.UNKNOWN
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "m4v", "mkv")
    }
}

data class FailedReason(
    val nodeName: String?,
    val exceptionMessage: String?,
    val nodeId: String?,
    val traceback: List<String>,
)

sealed interface GenerationState {
    data object Idle : GenerationState
    data object ValidatingConfig : GenerationState
    data object Submitting : GenerationState

    data class Queued(
        val taskId: String,
        val pollCount: Int,
        val promptTipsNodeErrors: String?,
    ) : GenerationState

    data class Running(
        val taskId: String,
        val pollCount: Int,
        val promptTipsNodeErrors: String?,
    ) : GenerationState

    data class Success(
        val taskId: String,
        val results: List<GeneratedOutput>,
        val promptTipsNodeErrors: String?,
    ) : GenerationState

    data class Failed(
        val taskId: String?,
        val errorCode: String?,
        val message: String,
        val failedReason: FailedReason?,
        val promptTipsNodeErrors: String?,
    ) : GenerationState

    data class Timeout(
        val taskId: String,
    ) : GenerationState
}
