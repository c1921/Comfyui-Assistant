package io.github.c1921.comfyui_assistant.data.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class NodeInfoItem(
    val nodeId: String,
    val fieldName: String,
    val fieldValue: Any,
)

data class CreateTaskRequest(
    val apiKey: String,
    val workflowId: String,
    val nodeInfoList: List<NodeInfoItem>,
    val addMetadata: Boolean = true,
)

data class CreateTaskData(
    val netWssUrl: String?,
    val taskId: JsonElement?,
    val clientId: String?,
    val taskStatus: String?,
    val promptTips: String?,
)

data class CreateTaskResponse(
    val code: Int,
    val msg: String,
    val data: CreateTaskData?,
)

data class QueryOutputsRequest(
    val apiKey: String,
    val taskId: String,
)

data class OutputsResultItem(
    val fileUrl: String?,
    val fileType: String?,
    val taskCostTime: String?,
    val nodeId: String?,
    val thirdPartyConsumeMoney: String?,
    val consumeMoney: String?,
    val consumeCoins: String?,
)

data class OutputsFailedReason(
    @SerializedName("node_name")
    val nodeName: String?,
    @SerializedName("exception_message")
    val exceptionMessage: String?,
    @SerializedName("node_id")
    val nodeId: String?,
    val traceback: List<String>?,
)

data class OutputsFailedData(
    val failedReason: OutputsFailedReason?,
)

data class OutputsRunningData(
    val netWssUrl: String?,
)

data class OutputsResponse(
    val code: Int,
    val msg: String,
    val data: JsonElement?,
)

interface RunningHubApiService {
    @POST("/task/openapi/create")
    suspend fun createWorkflowTask(
        @Header("Authorization") authorization: String,
        @Body request: CreateTaskRequest,
    ): CreateTaskResponse

    @POST("/task/openapi/outputs")
    suspend fun queryWorkflowOutputs(
        @Header("Authorization") authorization: String,
        @Body request: QueryOutputsRequest,
    ): OutputsResponse
}
