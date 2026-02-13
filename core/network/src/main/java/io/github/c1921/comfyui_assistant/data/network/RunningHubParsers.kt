package io.github.c1921.comfyui_assistant.data.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.github.c1921.comfyui_assistant.domain.FailedReason
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput

object RunningHubParsers {
    private val gson = Gson()

    fun parseTaskId(taskId: JsonElement?): String? {
        if (taskId == null || taskId.isJsonNull) return null
        return when {
            taskId.isJsonPrimitive -> taskId.asJsonPrimitive.asString
            else -> taskId.toString()
        }
    }

    fun parseOutputs(data: JsonElement?): List<GeneratedOutput> {
        if (data == null || !data.isJsonArray) return emptyList()
        val listType = object : TypeToken<List<OutputsResultItem>>() {}.type
        val rawItems: List<OutputsResultItem> = gson.fromJson(data, listType)
        return rawItems.mapNotNull { item ->
            val url = item.fileUrl?.trim().orEmpty()
            if (url.isBlank()) {
                null
            } else {
                GeneratedOutput(
                    fileUrl = url,
                    fileType = item.fileType?.trim().orEmpty(),
                    nodeId = item.nodeId?.trim(),
                )
            }
        }
    }

    fun parseFailedReason(data: JsonElement?): FailedReason? {
        if (data == null || !data.isJsonObject) return null
        val failedData = gson.fromJson(data, OutputsFailedData::class.java) ?: return null
        val reason = failedData.failedReason ?: return null
        return FailedReason(
            nodeName = reason.nodeName,
            exceptionMessage = reason.exceptionMessage,
            nodeId = reason.nodeId,
            traceback = reason.traceback.orEmpty(),
        )
    }

    fun extractPromptTipsNodeErrors(promptTips: String?): String? {
        if (promptTips.isNullOrBlank()) return null
        return try {
            val parsed = JsonParser.parseString(promptTips)
            if (!parsed.isJsonObject) return null
            val obj = parsed.asJsonObject
            if (!obj.has("node_errors")) return null
            val nodeErrors = obj.get("node_errors")
            if (nodeErrors == null || nodeErrors.isJsonNull) return null
            if (!nodeErrors.isJsonObject) return nodeErrors.toString()
            val errorsObj: JsonObject = nodeErrors.asJsonObject
            if (errorsObj.entrySet().isEmpty()) return null
            errorsObj.entrySet().joinToString(separator = "\n") { (nodeId, error) ->
                "Node $nodeId: ${error.toString()}"
            }
        } catch (_: Exception) {
            null
        }
    }
}
