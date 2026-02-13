package io.github.c1921.comfyui_assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureConfigStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun loadConfig(): WorkflowConfig = withContext(Dispatchers.IO) {
        WorkflowConfig(
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            workflowId = prefs.getString(KEY_WORKFLOW_ID, "").orEmpty(),
            promptNodeId = prefs.getString(KEY_PROMPT_NODE_ID, "").orEmpty(),
            promptFieldName = prefs.getString(KEY_PROMPT_FIELD_NAME, "").orEmpty(),
            negativeNodeId = prefs.getString(KEY_NEGATIVE_NODE_ID, "").orEmpty(),
            negativeFieldName = prefs.getString(KEY_NEGATIVE_FIELD_NAME, "").orEmpty(),
        )
    }

    suspend fun saveConfig(config: WorkflowConfig) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_WORKFLOW_ID, config.workflowId.trim())
            .putString(KEY_PROMPT_NODE_ID, config.promptNodeId.trim())
            .putString(KEY_PROMPT_FIELD_NAME, config.promptFieldName.trim())
            .putString(KEY_NEGATIVE_NODE_ID, config.negativeNodeId.trim())
            .putString(KEY_NEGATIVE_FIELD_NAME, config.negativeFieldName.trim())
            .apply()
    }

    suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, "").apply()
    }

    private companion object {
        const val PREFS_NAME = "runninghub_secure_config"
        const val KEY_API_KEY = "api_key"
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_PROMPT_NODE_ID = "prompt_node_id"
        const val KEY_PROMPT_FIELD_NAME = "prompt_field_name"
        const val KEY_NEGATIVE_NODE_ID = "negative_node_id"
        const val KEY_NEGATIVE_FIELD_NAME = "negative_field_name"
    }
}
