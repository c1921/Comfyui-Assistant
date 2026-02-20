package io.github.c1921.comfyui_assistant.data.local

import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import org.json.JSONObject

internal data class ConfigBackupPayload(
    val apiKey: String,
    val workflowId: String,
    val promptNodeId: String,
    val promptFieldName: String,
    val negativeNodeId: String,
    val negativeFieldName: String,
    val sizeNodeId: String,
    val imageInputNodeId: String,
    val videoWorkflowId: String,
    val videoPromptNodeId: String,
    val videoPromptFieldName: String,
    val videoImageInputNodeId: String,
    val videoLengthNodeId: String,
    val decodePassword: String,
    val updatedAtEpochMs: Long,
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("apiKey", apiKey)
            put("workflowId", workflowId)
            put("promptNodeId", promptNodeId)
            put("promptFieldName", promptFieldName)
            put("negativeNodeId", negativeNodeId)
            put("negativeFieldName", negativeFieldName)
            put("sizeNodeId", sizeNodeId)
            put("imageInputNodeId", imageInputNodeId)
            put("videoWorkflowId", videoWorkflowId)
            put("videoPromptNodeId", videoPromptNodeId)
            put("videoPromptFieldName", videoPromptFieldName)
            put("videoImageInputNodeId", videoImageInputNodeId)
            put("videoLengthNodeId", videoLengthNodeId)
            put("decodePassword", decodePassword)
            put("updatedAtEpochMs", updatedAtEpochMs)
        }.toString()
    }

    fun toWorkflowConfigWithLocalWebDav(
        localWebDavConfig: WorkflowConfig,
    ): WorkflowConfig {
        return localWebDavConfig.copy(
            apiKey = apiKey,
            workflowId = workflowId,
            promptNodeId = promptNodeId,
            promptFieldName = promptFieldName,
            negativeNodeId = negativeNodeId,
            negativeFieldName = negativeFieldName,
            sizeNodeId = sizeNodeId,
            imageInputNodeId = imageInputNodeId,
            videoWorkflowId = videoWorkflowId,
            videoPromptNodeId = videoPromptNodeId,
            videoPromptFieldName = videoPromptFieldName,
            videoImageInputNodeId = videoImageInputNodeId,
            videoLengthNodeId = videoLengthNodeId,
            decodePassword = decodePassword,
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun fromConfig(
            config: WorkflowConfig,
            updatedAtEpochMs: Long,
        ): ConfigBackupPayload {
            return ConfigBackupPayload(
                apiKey = config.apiKey.trim(),
                workflowId = config.workflowId.trim(),
                promptNodeId = config.promptNodeId.trim(),
                promptFieldName = config.promptFieldName.trim(),
                negativeNodeId = config.negativeNodeId.trim(),
                negativeFieldName = config.negativeFieldName.trim(),
                sizeNodeId = config.sizeNodeId.trim(),
                imageInputNodeId = config.imageInputNodeId.trim(),
                videoWorkflowId = config.videoWorkflowId.trim(),
                videoPromptNodeId = config.videoPromptNodeId.trim(),
                videoPromptFieldName = config.videoPromptFieldName.trim(),
                videoImageInputNodeId = config.videoImageInputNodeId.trim(),
                videoLengthNodeId = config.videoLengthNodeId.trim(),
                decodePassword = config.decodePassword,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }

        fun fromJson(rawJson: String): ConfigBackupPayload {
            val obj = JSONObject(rawJson)
            val schemaVersion = obj.optInt("schemaVersion", SCHEMA_VERSION)
            require(schemaVersion == SCHEMA_VERSION) {
                "Unsupported backup schema version: $schemaVersion"
            }
            return ConfigBackupPayload(
                apiKey = obj.optString("apiKey"),
                workflowId = obj.optString("workflowId"),
                promptNodeId = obj.optString("promptNodeId"),
                promptFieldName = obj.optString("promptFieldName"),
                negativeNodeId = obj.optString("negativeNodeId"),
                negativeFieldName = obj.optString("negativeFieldName"),
                sizeNodeId = obj.optString("sizeNodeId"),
                imageInputNodeId = obj.optString("imageInputNodeId"),
                videoWorkflowId = obj.optString("videoWorkflowId"),
                videoPromptNodeId = obj.optString("videoPromptNodeId"),
                videoPromptFieldName = obj.optString("videoPromptFieldName"),
                videoImageInputNodeId = obj.optString("videoImageInputNodeId"),
                videoLengthNodeId = obj.optString("videoLengthNodeId"),
                decodePassword = obj.optString("decodePassword"),
                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", 0L),
            )
        }
    }
}

