package io.github.c1921.comfyui_assistant.data.repository

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.github.c1921.comfyui_assistant.data.network.CreateTaskData
import io.github.c1921.comfyui_assistant.data.network.CreateTaskRequest
import io.github.c1921.comfyui_assistant.data.network.CreateTaskResponse
import io.github.c1921.comfyui_assistant.data.network.OutputsResponse
import io.github.c1921.comfyui_assistant.data.network.QueryOutputsRequest
import io.github.c1921.comfyui_assistant.data.network.RunningHubApiService
import io.github.c1921.comfyui_assistant.data.network.UploadMediaResponse
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.MultipartBody

class RunningHubGenerationRepositoryTest {
    @Test
    fun `generateAndPoll emits queued running and success states`() = runTest {
        val fakeService = FakeRunningHubApiService(
            createResponse = CreateTaskResponse(
                code = 0,
                msg = "success",
                data = CreateTaskData(
                    netWssUrl = null,
                    taskId = JsonPrimitive("task-1"),
                    clientId = null,
                    taskStatus = "QUEUED",
                    promptTips = null,
                ),
            ),
            outputsResponses = mutableListOf(
                OutputsResponse(code = 813, msg = "APIKEY_TASK_IS_QUEUED", data = null),
                OutputsResponse(
                    code = 804,
                    msg = "APIKEY_TASK_IS_RUNNING",
                    data = JsonParser.parseString("""{"netWssUrl":"wss://example"}"""),
                ),
                OutputsResponse(
                    code = 0,
                    msg = "success",
                    data = JsonParser.parseString(
                        """
                        [
                          {"fileUrl":"https://example.com/output.png","fileType":"png","nodeId":"9"}
                        ]
                        """.trimIndent()
                    ),
                ),
            ),
        )

        val repository = RunningHubGenerationRepository(
            apiService = fakeService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            pollIntervalMs = 0L,
            maxPollCount = 5,
        )

        val states = repository.generateAndPoll(
            config = validConfig(),
            input = GenerationInput(
                prompt = "hello",
                negative = "",
                mode = GenerationMode.IMAGE,
            ),
        ).toList()

        assertTrue(states.any { it is GenerationState.Queued })
        assertTrue(states.any { it is GenerationState.Running })
        assertTrue(states.last() is GenerationState.Success)
    }

    @Test
    fun `generateAndPoll emits failed state with reason when code 805`() = runTest {
        val fakeService = FakeRunningHubApiService(
            createResponse = CreateTaskResponse(
                code = 0,
                msg = "success",
                data = CreateTaskData(
                    netWssUrl = null,
                    taskId = JsonPrimitive("task-2"),
                    clientId = null,
                    taskStatus = "QUEUED",
                    promptTips = null,
                ),
            ),
            outputsResponses = mutableListOf(
                OutputsResponse(
                    code = 805,
                    msg = "APIKEY_TASK_STATUS_ERROR",
                    data = JsonParser.parseString(
                        """
                        {
                          "failedReason":{
                            "node_name":"KSampler",
                            "exception_message":"missing input",
                            "node_id":"3",
                            "traceback":["line1","line2"]
                          }
                        }
                        """.trimIndent()
                    ),
                ),
            ),
        )

        val repository = RunningHubGenerationRepository(
            apiService = fakeService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            pollIntervalMs = 0L,
            maxPollCount = 5,
        )

        val states = repository.generateAndPoll(
            config = validConfig(),
            input = GenerationInput(
                prompt = "hello",
                negative = "",
                mode = GenerationMode.IMAGE,
            ),
        ).toList()

        val failed = states.last() as GenerationState.Failed
        assertTrue(failed.message.contains("missing input"))
        assertTrue(failed.failedReason?.nodeName == "KSampler")
    }

    @Test
    fun `generateAndPoll uses video workflowId in video mode`() = runTest {
        val fakeService = FakeRunningHubApiService(
            createResponse = CreateTaskResponse(
                code = 0,
                msg = "success",
                data = CreateTaskData(
                    netWssUrl = null,
                    taskId = JsonPrimitive("task-3"),
                    clientId = null,
                    taskStatus = "QUEUED",
                    promptTips = null,
                ),
            ),
            outputsResponses = mutableListOf(
                OutputsResponse(
                    code = 0,
                    msg = "success",
                    data = JsonParser.parseString(
                        """
                        [
                          {"fileUrl":"https://example.com/output.mp4","fileType":"mp4","nodeId":"21"}
                        ]
                        """.trimIndent()
                    ),
                ),
            ),
        )

        val repository = RunningHubGenerationRepository(
            apiService = fakeService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            pollIntervalMs = 0L,
            maxPollCount = 5,
        )

        repository.generateAndPoll(
            config = validConfig().copy(
                videoWorkflowId = "video-workflow-id",
                videoPromptNodeId = "12",
                videoPromptFieldName = "text",
            ),
            input = GenerationInput(
                prompt = "video prompt",
                negative = "",
                mode = GenerationMode.VIDEO,
            ),
        ).toList()

        assertEquals("video-workflow-id", fakeService.lastCreateRequest?.workflowId)
        assertEquals(1, fakeService.lastCreateRequest?.nodeInfoList?.size)
    }

    private fun validConfig(): WorkflowConfig {
        return WorkflowConfig(
            apiKey = "api-key",
            workflowId = "workflow-id",
            promptNodeId = "6",
            promptFieldName = "text",
        )
    }

    private class FakeRunningHubApiService(
        private val createResponse: CreateTaskResponse,
        private val outputsResponses: MutableList<OutputsResponse>,
    ) : RunningHubApiService {
        var lastCreateRequest: CreateTaskRequest? = null

        override suspend fun createWorkflowTask(
            authorization: String,
            request: CreateTaskRequest,
        ): CreateTaskResponse {
            lastCreateRequest = request
            return createResponse
        }

        override suspend fun queryWorkflowOutputs(
            authorization: String,
            request: QueryOutputsRequest,
        ): OutputsResponse {
            return if (outputsResponses.isNotEmpty()) {
                outputsResponses.removeAt(0)
            } else {
                OutputsResponse(code = 813, msg = "APIKEY_TASK_IS_QUEUED", data = null)
            }
        }

        override suspend fun uploadMediaBinary(
            authorization: String,
            file: MultipartBody.Part,
        ): UploadMediaResponse {
            throw UnsupportedOperationException("Not required for this test")
        }
    }
}
