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
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class SecureConfigStore(
    context: Context,
    private val webDavHttpClient: OkHttpClient = OkHttpClient(),
    private val payloadCrypto: WebDavPayloadCrypto = WebDavPayloadCrypto(),
    private val syncDecisionEngine: ConfigSyncDecisionEngine = ConfigSyncDecisionEngine(),
) : ConfigRepository, WebDavSyncRepository {
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
        readConfigFromPrefs()
    }

    override suspend fun saveConfig(config: WorkflowConfig) = withContext(ioDispatcher) {
        val normalized = normalizeForLocalSave(config)
        writeConfigToPrefs(
            config = normalized,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    override suspend fun clearApiKey() = withContext(ioDispatcher) {
        prefs.edit {
            putString(KEY_API_KEY, "")
            putLong(KEY_CONFIG_LAST_UPDATED_EPOCH_MS, System.currentTimeMillis())
        }
    }

    override suspend fun syncConfig(trigger: ConfigSyncTrigger): ConfigSyncResult = withContext(ioDispatcher) {
        runCatching {
            syncConfigInternal()
        }.getOrElse { error ->
            ConfigSyncResult.Failed(
                message = error.message?.ifBlank { "Unknown WebDAV sync error." } ?: "Unknown WebDAV sync error.",
            )
        }
    }

    private fun syncConfigInternal(): ConfigSyncResult {
        val localConfig = readConfigFromPrefs()
        if (!localConfig.webDavEnabled) {
            return ConfigSyncResult.Skipped(reason = "WebDAV sync is disabled.")
        }
        if (
            localConfig.webDavServerUrl.isBlank() ||
            localConfig.webDavUsername.isBlank() ||
            localConfig.webDavPassword.isBlank()
        ) {
            return ConfigSyncResult.Skipped(reason = "WebDAV credentials are incomplete.")
        }
        if (localConfig.webDavSyncPassphrase.isBlank()) {
            return ConfigSyncResult.Skipped(reason = "WebDAV sync passphrase is empty.")
        }

        val remoteDirectoryUrl = buildRemoteDirectoryUrl(localConfig.webDavServerUrl)
        val remoteFileUrl = "$remoteDirectoryUrl/$REMOTE_CONFIG_FILE_NAME"
        val authorization = Credentials.basic(
            localConfig.webDavUsername,
            localConfig.webDavPassword,
        )

        val localUpdatedAtEpochMs = readLocalUpdatedAtEpochMs()
        val remotePayload = fetchRemoteEncryptedPayload(
            remoteDirectoryUrl = remoteDirectoryUrl,
            remoteFileUrl = remoteFileUrl,
            authorization = authorization,
        )?.let { encryptedPayload ->
            val decryptedJson = payloadCrypto.decrypt(
                envelopeJson = encryptedPayload,
                passphrase = localConfig.webDavSyncPassphrase,
            ).getOrElse { error ->
                throw IllegalStateException(
                    "Failed to decrypt remote config. Please verify sync passphrase.",
                    error,
                )
            }
            ConfigBackupPayload.fromJson(decryptedJson)
        }

        val action = syncDecisionEngine.decide(
            localUpdatedAtEpochMs = localUpdatedAtEpochMs,
            remoteUpdatedAtEpochMs = remotePayload?.updatedAtEpochMs,
        )
        return when (action) {
            ConfigSyncAction.PUSH_LOCAL_TO_REMOTE -> {
                pushLocalConfigToRemote(
                    localConfig = localConfig,
                    localUpdatedAtEpochMs = localUpdatedAtEpochMs,
                    remoteDirectoryUrl = remoteDirectoryUrl,
                    remoteFileUrl = remoteFileUrl,
                    authorization = authorization,
                )
            }

            ConfigSyncAction.PULL_REMOTE_TO_LOCAL -> {
                val resolvedRemote = checkNotNull(remotePayload) {
                    "Remote payload is missing while pull action was selected."
                }
                val mergedConfig = resolvedRemote.toWorkflowConfigWithLocalWebDav(localConfig)
                writeConfigToPrefs(
                    config = mergedConfig,
                    updatedAtEpochMs = normalizeUpdatedAt(resolvedRemote.updatedAtEpochMs),
                )
                ConfigSyncResult.Pulled(config = readConfigFromPrefs())
            }

            ConfigSyncAction.SKIP_ALREADY_SYNCED -> {
                ConfigSyncResult.Skipped(reason = "Local and remote config are already in sync.")
            }
        }
    }

    private fun pushLocalConfigToRemote(
        localConfig: WorkflowConfig,
        localUpdatedAtEpochMs: Long,
        remoteDirectoryUrl: String,
        remoteFileUrl: String,
        authorization: String,
    ): ConfigSyncResult {
        val effectiveUpdatedAt = normalizeUpdatedAt(localUpdatedAtEpochMs)
        if (effectiveUpdatedAt != localUpdatedAtEpochMs) {
            prefs.edit { putLong(KEY_CONFIG_LAST_UPDATED_EPOCH_MS, effectiveUpdatedAt) }
        }

        val plainPayload = ConfigBackupPayload.fromConfig(
            config = localConfig,
            updatedAtEpochMs = effectiveUpdatedAt,
        ).toJsonString()
        val encryptedPayload = payloadCrypto.encrypt(
            plainText = plainPayload,
            passphrase = localConfig.webDavSyncPassphrase,
        ).getOrElse { error ->
            throw IllegalStateException("Failed to encrypt config backup payload.", error)
        }
        putRemoteEncryptedPayload(
            remoteDirectoryUrl = remoteDirectoryUrl,
            remoteFileUrl = remoteFileUrl,
            authorization = authorization,
            encryptedPayload = encryptedPayload,
        )
        return ConfigSyncResult.Pushed(remotePath = remoteFileUrl)
    }

    private fun fetchRemoteEncryptedPayload(
        remoteDirectoryUrl: String,
        remoteFileUrl: String,
        authorization: String,
    ): String? {
        val request = Request.Builder()
            .url(remoteFileUrl)
            .get()
            .header(HEADER_AUTHORIZATION, authorization)
            .build()
        webDavHttpClient.newCall(request).execute().use { response ->
            return when {
                response.code == 404 -> null
                response.code == HTTP_CONFLICT -> {
                    ensureRemoteDirectory(
                        remoteDirectoryUrl = remoteDirectoryUrl,
                        authorization = authorization,
                    )
                    null
                }
                response.isSuccessful -> {
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        throw IllegalStateException("Remote config payload is empty.")
                    }
                    body
                }

                else -> throw IllegalStateException("WebDAV GET failed, HTTP ${response.code}.")
            }
        }
    }

    private fun putRemoteEncryptedPayload(
        remoteDirectoryUrl: String,
        remoteFileUrl: String,
        authorization: String,
        encryptedPayload: String,
    ) {
        val firstCode = executePutRequest(
            remoteFileUrl = remoteFileUrl,
            authorization = authorization,
            encryptedPayload = encryptedPayload,
        )
        if (firstCode in HTTP_SUCCESS_RANGE) {
            return
        }
        if (firstCode != HTTP_CONFLICT) {
            throw IllegalStateException("WebDAV PUT failed, HTTP $firstCode.")
        }

        ensureRemoteDirectory(
            remoteDirectoryUrl = remoteDirectoryUrl,
            authorization = authorization,
        )
        val retryCode = executePutRequest(
            remoteFileUrl = remoteFileUrl,
            authorization = authorization,
            encryptedPayload = encryptedPayload,
        )
        if (retryCode !in HTTP_SUCCESS_RANGE) {
            throw IllegalStateException("WebDAV PUT retry failed, HTTP $retryCode.")
        }
    }

    private fun executePutRequest(
        remoteFileUrl: String,
        authorization: String,
        encryptedPayload: String,
    ): Int {
        val request = Request.Builder()
            .url(remoteFileUrl)
            .header(HEADER_AUTHORIZATION, authorization)
            .put(encryptedPayload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        webDavHttpClient.newCall(request).execute().use { response ->
            return response.code
        }
    }

    private fun ensureRemoteDirectory(
        remoteDirectoryUrl: String,
        authorization: String,
    ) {
        val request = Request.Builder()
            .url(remoteDirectoryUrl)
            .header(HEADER_AUTHORIZATION, authorization)
            .method(HTTP_METHOD_MKCOL, null)
            .build()
        webDavHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == HTTP_METHOD_NOT_ALLOWED) {
                return
            }
            throw IllegalStateException("WebDAV MKCOL failed, HTTP ${response.code}.")
        }
    }

    private fun readConfigFromPrefs(): WorkflowConfig {
        val sizeNodeId = prefs.getString(KEY_SIZE_NODE_ID, "").orEmpty().ifBlank {
            prefs.getString(KEY_SIZE_WIDTH_NODE_ID_LEGACY, "").orEmpty().ifBlank {
                prefs.getString(KEY_SIZE_HEIGHT_NODE_ID_LEGACY, "").orEmpty()
            }
        }
        return WorkflowConfig(
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
            videoLengthNodeId = prefs.getString(KEY_VIDEO_LENGTH_NODE_ID, "").orEmpty(),
            decodePassword = prefs.getString(KEY_DECODE_PASSWORD, "").orEmpty(),
            webDavEnabled = prefs.getBoolean(KEY_WEBDAV_ENABLED, false),
            webDavServerUrl = prefs.getString(KEY_WEBDAV_SERVER_URL, "").orEmpty(),
            webDavUsername = prefs.getString(KEY_WEBDAV_USERNAME, "").orEmpty(),
            webDavPassword = prefs.getString(KEY_WEBDAV_PASSWORD, "").orEmpty(),
            webDavSyncPassphrase = prefs.getString(KEY_WEBDAV_SYNC_PASSPHRASE, "").orEmpty(),
        )
    }

    private fun writeConfigToPrefs(
        config: WorkflowConfig,
        updatedAtEpochMs: Long,
    ) {
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
            putString(KEY_VIDEO_LENGTH_NODE_ID, config.videoLengthNodeId.trim())
            putString(KEY_DECODE_PASSWORD, config.decodePassword)
            putBoolean(KEY_WEBDAV_ENABLED, config.webDavEnabled)
            putString(KEY_WEBDAV_SERVER_URL, config.webDavServerUrl.trim())
            putString(KEY_WEBDAV_USERNAME, config.webDavUsername.trim())
            putString(KEY_WEBDAV_PASSWORD, config.webDavPassword)
            putString(KEY_WEBDAV_SYNC_PASSPHRASE, config.webDavSyncPassphrase)
            putLong(KEY_CONFIG_LAST_UPDATED_EPOCH_MS, updatedAtEpochMs)
        }
    }

    private fun readLocalUpdatedAtEpochMs(): Long {
        return prefs.getLong(KEY_CONFIG_LAST_UPDATED_EPOCH_MS, 0L)
    }

    private fun normalizeUpdatedAt(value: Long): Long {
        return value.takeIf { it > 0L } ?: System.currentTimeMillis()
    }

    private fun normalizeForLocalSave(config: WorkflowConfig): WorkflowConfig {
        return config.copy(
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
            webDavServerUrl = config.webDavServerUrl.trim(),
            webDavUsername = config.webDavUsername.trim(),
        )
    }

    private fun buildRemoteDirectoryUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "WebDAV server URL is empty." }
        return "$trimmed/$REMOTE_DIRECTORY_NAME"
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
        const val KEY_VIDEO_LENGTH_NODE_ID = "video_length_node_id"
        const val KEY_SIZE_WIDTH_NODE_ID_LEGACY = "size_width_node_id"
        const val KEY_SIZE_HEIGHT_NODE_ID_LEGACY = "size_height_node_id"
        const val KEY_DECODE_PASSWORD = "decode_password"
        const val KEY_WEBDAV_ENABLED = "webdav_enabled"
        const val KEY_WEBDAV_SERVER_URL = "webdav_server_url"
        const val KEY_WEBDAV_USERNAME = "webdav_username"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
        const val KEY_WEBDAV_SYNC_PASSPHRASE = "webdav_sync_passphrase"
        const val KEY_CONFIG_LAST_UPDATED_EPOCH_MS = "config_last_updated_epoch_ms"

        const val REMOTE_DIRECTORY_NAME = "Comfyui-Assistant"
        const val REMOTE_CONFIG_FILE_NAME = "config.v1.enc.json"

        const val HEADER_AUTHORIZATION = "Authorization"
        const val HTTP_CONFLICT = 409
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_METHOD_MKCOL = "MKCOL"
        val HTTP_SUCCESS_RANGE = 200..299
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
