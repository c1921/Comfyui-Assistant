package io.github.c1921.comfyui_assistant.domain

data class WorkflowConfig(
    val apiKey: String = "",
    val workflowId: String = "",
    val promptNodeId: String = "",
    val promptFieldName: String = "",
    val negativeNodeId: String = "",
    val negativeFieldName: String = "",
    val decodePassword: String = "",
)

data class GenerationInput(
    val prompt: String,
    val negative: String,
)

data class GeneratedOutput(
    val fileUrl: String,
    val fileType: String,
    val nodeId: String?,
)

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
