package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.data.network.CreateTaskRequest
import io.github.c1921.comfyui_assistant.data.network.QueryOutputsRequest
import io.github.c1921.comfyui_assistant.data.network.RunningHubApiService
import io.github.c1921.comfyui_assistant.data.network.RunningHubParsers
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class RunningHubGenerationRepository(
    private val apiService: RunningHubApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMs: Long = 5_000L,
    private val maxPollCount: Int = 120,
) : GenerationRepository {

    override fun generateAndPoll(
        config: WorkflowConfig,
        input: GenerationInput,
    ): Flow<GenerationState> = flow {
        emit(GenerationState.ValidatingConfig)
        WorkflowConfigValidator.validateForGenerate(config, input)?.let { message ->
            emit(
                GenerationState.Failed(
                    taskId = null,
                    errorCode = null,
                    message = message,
                    failedReason = null,
                    promptTipsNodeErrors = null,
                )
            )
            return@flow
        }

        emit(GenerationState.Submitting)
        val createResponse = try {
            apiService.createWorkflowTask(
                authorization = authHeader(config.apiKey),
                request = CreateTaskRequest(
                    apiKey = config.apiKey.trim(),
                    workflowId = config.workflowId.trim(),
                    nodeInfoList = WorkflowRequestBuilder.buildNodeInfoList(config, input),
                    addMetadata = true,
                ),
            )
        } catch (error: Exception) {
            emit(
                GenerationState.Failed(
                    taskId = null,
                    errorCode = null,
                    message = "Create task failed: ${error.message.orEmpty()}",
                    failedReason = null,
                    promptTipsNodeErrors = null,
                )
            )
            return@flow
        }

        if (createResponse.code != 0) {
            emit(
                GenerationState.Failed(
                    taskId = null,
                    errorCode = createResponse.code.toString(),
                    message = RunningHubErrorMapper.map(createResponse.code, createResponse.msg),
                    failedReason = null,
                    promptTipsNodeErrors = null,
                )
            )
            return@flow
        }

        val taskId = RunningHubParsers.parseTaskId(createResponse.data?.taskId)
        if (taskId.isNullOrBlank()) {
            emit(
                GenerationState.Failed(
                    taskId = null,
                    errorCode = null,
                    message = "taskId is missing in create-task response.",
                    failedReason = null,
                    promptTipsNodeErrors = null,
                )
            )
            return@flow
        }

        val promptTipsNodeErrors =
            RunningHubParsers.extractPromptTipsNodeErrors(createResponse.data?.promptTips)

        var pollCount = 0
        while (pollCount < maxPollCount) {
            pollCount++
            val outputs = try {
                apiService.queryWorkflowOutputs(
                    authorization = authHeader(config.apiKey),
                    request = QueryOutputsRequest(
                        apiKey = config.apiKey.trim(),
                        taskId = taskId,
                    ),
                )
            } catch (error: Exception) {
                emit(
                    GenerationState.Failed(
                        taskId = taskId,
                        errorCode = null,
                        message = "Query task failed: ${error.message.orEmpty()}",
                        failedReason = null,
                        promptTipsNodeErrors = promptTipsNodeErrors,
                    )
                )
                return@flow
            }

            when (outputs.code) {
                0 -> {
                    val results = RunningHubParsers.parseOutputs(outputs.data)
                    if (results.isEmpty()) {
                        emit(
                            GenerationState.Failed(
                                taskId = taskId,
                                errorCode = "0",
                                message = "Task completed but returned no valid outputs.",
                                failedReason = null,
                                promptTipsNodeErrors = promptTipsNodeErrors,
                            )
                        )
                    } else {
                        emit(
                            GenerationState.Success(
                                taskId = taskId,
                                results = results,
                                promptTipsNodeErrors = promptTipsNodeErrors,
                            )
                        )
                    }
                    return@flow
                }

                813 -> {
                    emit(
                        GenerationState.Queued(
                            taskId = taskId,
                            pollCount = pollCount,
                            promptTipsNodeErrors = promptTipsNodeErrors,
                        )
                    )
                }

                804 -> {
                    emit(
                        GenerationState.Running(
                            taskId = taskId,
                            pollCount = pollCount,
                            promptTipsNodeErrors = promptTipsNodeErrors,
                        )
                    )
                }

                805 -> {
                    val failedReason = RunningHubParsers.parseFailedReason(outputs.data)
                    val baseMessage = RunningHubErrorMapper.map(outputs.code, outputs.msg)
                    val detail = failedReason?.exceptionMessage?.takeIf { it.isNotBlank() }
                    emit(
                        GenerationState.Failed(
                            taskId = taskId,
                            errorCode = outputs.code.toString(),
                            message = detail?.let { "$baseMessage: $it" } ?: baseMessage,
                            failedReason = failedReason,
                            promptTipsNodeErrors = promptTipsNodeErrors,
                        )
                    )
                    return@flow
                }

                else -> {
                    emit(
                        GenerationState.Failed(
                            taskId = taskId,
                            errorCode = outputs.code.toString(),
                            message = RunningHubErrorMapper.map(outputs.code, outputs.msg),
                            failedReason = null,
                            promptTipsNodeErrors = promptTipsNodeErrors,
                        )
                    )
                    return@flow
                }
            }
            delay(pollIntervalMs)
        }

        emit(GenerationState.Timeout(taskId = taskId))
    }.flowOn(ioDispatcher)

    private fun authHeader(apiKey: String): String = "Bearer ${apiKey.trim()}"
}
