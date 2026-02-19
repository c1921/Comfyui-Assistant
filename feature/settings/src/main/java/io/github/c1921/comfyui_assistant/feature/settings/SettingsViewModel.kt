package io.github.c1921.comfyui_assistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.domain.ConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.WorkflowConfigValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val configRepository: ConfigRepository,
    private val configDraftStore: ConfigDraftStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        observeConfigDraft()
        loadPersistedConfig()
    }

    fun onApiKeyChanged(value: String) {
        configDraftStore.update { it.copy(apiKey = value) }
    }

    fun onWorkflowIdChanged(value: String) {
        configDraftStore.update { it.copy(workflowId = value) }
    }

    fun onPromptNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(promptNodeId = value) }
    }

    fun onPromptFieldNameChanged(value: String) {
        configDraftStore.update { it.copy(promptFieldName = value) }
    }

    fun onNegativeNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(negativeNodeId = value) }
    }

    fun onNegativeFieldNameChanged(value: String) {
        configDraftStore.update { it.copy(negativeFieldName = value) }
    }

    fun onSizeNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(sizeNodeId = value) }
    }

    fun onImageInputNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(imageInputNodeId = value) }
    }

    fun onVideoWorkflowIdChanged(value: String) {
        configDraftStore.update { it.copy(videoWorkflowId = value) }
    }

    fun onVideoPromptNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(videoPromptNodeId = value) }
    }

    fun onVideoPromptFieldNameChanged(value: String) {
        configDraftStore.update { it.copy(videoPromptFieldName = value) }
    }

    fun onVideoImageInputNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(videoImageInputNodeId = value) }
    }

    fun onVideoLengthNodeIdChanged(value: String) {
        configDraftStore.update { it.copy(videoLengthNodeId = value) }
    }

    fun onDecodePasswordChanged(value: String) {
        configDraftStore.update { it.copy(decodePassword = value) }
    }

    fun saveSettings() {
        val current = _uiState.value
        val config = current.toWorkflowConfig()
        val mappingError = WorkflowConfigValidator.validateForSettings(config)
        if (mappingError != null) {
            emitMessage(mappingError)
            return
        }
        viewModelScope.launch {
            configRepository.saveConfig(config)
            emitMessage("Configuration saved.")
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            configRepository.clearApiKey()
            configDraftStore.update { it.copy(apiKey = "") }
            emitMessage("API key cleared.")
        }
    }

    private fun loadPersistedConfig() {
        viewModelScope.launch {
            val config = configRepository.loadConfig()
            configDraftStore.update { config }
            _uiState.update { it.copy(isLoadingConfig = false) }
        }
    }

    private fun observeConfigDraft() {
        viewModelScope.launch {
            configDraftStore.draft.collectLatest { config ->
                _uiState.update { state ->
                    state.copy(
                        apiKey = config.apiKey,
                        workflowId = config.workflowId,
                        promptNodeId = config.promptNodeId,
                        promptFieldName = config.promptFieldName,
                        negativeNodeId = config.negativeNodeId,
                        negativeFieldName = config.negativeFieldName,
                        sizeNodeId = config.sizeNodeId,
                        imageInputNodeId = config.imageInputNodeId,
                        videoWorkflowId = config.videoWorkflowId,
                        videoPromptNodeId = config.videoPromptNodeId,
                        videoPromptFieldName = config.videoPromptFieldName,
                        videoImageInputNodeId = config.videoImageInputNodeId,
                        videoLengthNodeId = config.videoLengthNodeId,
                        decodePassword = config.decodePassword,
                    )
                }
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    class Factory(
        private val configRepository: ConfigRepository,
        private val configDraftStore: ConfigDraftStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                configRepository = configRepository,
                configDraftStore = configDraftStore,
            ) as T
        }
    }
}
