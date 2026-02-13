package io.github.c1921.comfyui_assistant.data.repository

interface RunningHubMessageMapper {
    fun map(code: Int?, fallbackMessage: String?): String
}

class DefaultRunningHubMessageMapper : RunningHubMessageMapper {
    override fun map(code: Int?, fallbackMessage: String?): String {
        return when (code) {
            802 -> "API key is invalid or unauthorized."
            803 -> "nodeInfoList does not match the workflow mapping."
            810 -> "Workflow is not saved or has never run successfully on web."
            416, 812 -> "Insufficient balance."
            1003 -> "Rate limit exceeded, please retry later."
            1011, 1005 -> "System is busy, please retry later."
            805 -> "Task failed, please verify node configuration."
            813 -> "Task is queued."
            804 -> "Task is running."
            else -> fallbackMessage?.takeIf { it.isNotBlank() } ?: "Request failed, please retry."
        }
    }
}
