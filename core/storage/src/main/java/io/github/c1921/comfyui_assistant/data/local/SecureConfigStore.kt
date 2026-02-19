package io.github.c1921.comfyui_assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureConfigStore(
    context: Context,
) : ConfigRepository {
    private val appContext = context.applicationContext

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun loadConfig(): WorkflowConfig = withContext(ioDispatcher) {
        val sizeNodeId = prefs.getString(KEY_SIZE_NODE_ID, "").orEmpty().ifBlank {
            prefs.getString(KEY_SIZE_WIDTH_NODE_ID_LEGACY, "").orEmpty().ifBlank {
                prefs.getString(KEY_SIZE_HEIGHT_NODE_ID_LEGACY, "").orEmpty()
            }
        }
        WorkflowConfig(
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            workflowId = prefs.getString(KEY_WORKFLOW_ID, "").orEmpty(),
            promptNodeId = prefs.getString(KEY_PROMPT_NODE_ID, "").orEmpty(),
            promptFieldName = prefs.getString(KEY_PROMPT_FIELD_NAME, "").orEmpty(),
            negativeNodeId = prefs.getString(KEY_NEGATIVE_NODE_ID, "").orEmpty(),
            negativeFieldName = prefs.getString(KEY_NEGATIVE_FIELD_NAME, "").orEmpty(),
            sizeNodeId = sizeNodeId,
            imageInputNodeId = prefs.getString(KEY_IMAGE_INPUT_NODE_ID, "").orEmpty(),
            videoWorkflowId = prefs.getString(KEY_VIDEO_WORKFLOW_ID, "").orEmpty(),
            videoPromptNodeId = prefs.getString(KEY_VIDEO_PROMPT_NODE_ID, "").orEmpty(),
            videoPromptFieldName = prefs.getString(KEY_VIDEO_PROMPT_FIELD_NAME, "").orEmpty(),
            videoImageInputNodeId = prefs.getString(KEY_VIDEO_IMAGE_INPUT_NODE_ID, "").orEmpty(),
            decodePassword = prefs.getString(KEY_DECODE_PASSWORD, "").orEmpty(),
        )
    }

    override suspend fun saveConfig(config: WorkflowConfig) = withContext(ioDispatcher) {
        prefs.edit {
            putString(KEY_API_KEY, config.apiKey.trim())
            putString(KEY_WORKFLOW_ID, config.workflowId.trim())
            putString(KEY_PROMPT_NODE_ID, config.promptNodeId.trim())
            putString(KEY_PROMPT_FIELD_NAME, config.promptFieldName.trim())
            putString(KEY_NEGATIVE_NODE_ID, config.negativeNodeId.trim())
            putString(KEY_NEGATIVE_FIELD_NAME, config.negativeFieldName.trim())
            putString(KEY_SIZE_NODE_ID, config.sizeNodeId.trim())
            putString(KEY_IMAGE_INPUT_NODE_ID, config.imageInputNodeId.trim())
            putString(KEY_VIDEO_WORKFLOW_ID, config.videoWorkflowId.trim())
            putString(KEY_VIDEO_PROMPT_NODE_ID, config.videoPromptNodeId.trim())
            putString(KEY_VIDEO_PROMPT_FIELD_NAME, config.videoPromptFieldName.trim())
            putString(KEY_VIDEO_IMAGE_INPUT_NODE_ID, config.videoImageInputNodeId.trim())
            putString(KEY_DECODE_PASSWORD, config.decodePassword)
        }
    }

    override suspend fun clearApiKey() = withContext(ioDispatcher) {
        prefs.edit { putString(KEY_API_KEY, "") }
    }

    private companion object {
        const val PREFS_NAME = "runninghub_secure_config"
        const val KEY_API_KEY = "api_key"
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_PROMPT_NODE_ID = "prompt_node_id"
        const val KEY_PROMPT_FIELD_NAME = "prompt_field_name"
        const val KEY_NEGATIVE_NODE_ID = "negative_node_id"
        const val KEY_NEGATIVE_FIELD_NAME = "negative_field_name"
        const val KEY_SIZE_NODE_ID = "size_node_id"
        const val KEY_IMAGE_INPUT_NODE_ID = "image_input_node_id"
        const val KEY_VIDEO_WORKFLOW_ID = "video_workflow_id"
        const val KEY_VIDEO_PROMPT_NODE_ID = "video_prompt_node_id"
        const val KEY_VIDEO_PROMPT_FIELD_NAME = "video_prompt_field_name"
        const val KEY_VIDEO_IMAGE_INPUT_NODE_ID = "video_image_input_node_id"
        const val KEY_SIZE_WIDTH_NODE_ID_LEGACY = "size_width_node_id"
        const val KEY_SIZE_HEIGHT_NODE_ID_LEGACY = "size_height_node_id"
        const val KEY_DECODE_PASSWORD = "decode_password"
    }
}
