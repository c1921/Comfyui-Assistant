package io.github.c1921.comfyui_assistant.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.local.SecureConfigStore
import io.github.c1921.comfyui_assistant.data.decoder.fallbackOrNull
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.ImageDownloader
import io.github.c1921.comfyui_assistant.data.repository.WorkflowConfigValidator
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val configStore: SecureConfigStore,
    private val generationRepository: GenerationRepository,
    private val imageDownloader: ImageDownloader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var generationJob: Job? = null
    private var lastSubmittedInput: GenerationInput? = null
    private val notifiedDecodeFallbackUrls = mutableSetOf<String>()

    init {
        loadConfig()
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.update { it.copy(apiKey = value) }
    }

    fun onWorkflowIdChanged(value: String) {
        _uiState.update { it.copy(workflowId = value) }
    }

    fun onPromptNodeIdChanged(value: String) {
        _uiState.update { it.copy(promptNodeId = value) }
    }

    fun onPromptFieldNameChanged(value: String) {
        _uiState.update { it.copy(promptFieldName = value) }
    }

    fun onNegativeNodeIdChanged(value: String) {
        _uiState.update { it.copy(negativeNodeId = value) }
    }

    fun onNegativeFieldNameChanged(value: String) {
        _uiState.update { it.copy(negativeFieldName = value) }
    }

    fun onDecodePasswordChanged(value: String) {
        _uiState.update { it.copy(decodePassword = value) }
    }

    fun onPromptChanged(value: String) {
        _uiState.update { it.copy(prompt = value) }
    }

    fun onNegativeChanged(value: String) {
        _uiState.update { it.copy(negative = value) }
    }

    fun saveSettings() {
        val current = _uiState.value
        val config = current.toWorkflowConfig()
        val mappingError = WorkflowConfigValidator.validateMappingConsistency(config)
        if (mappingError != null) {
            emitMessage(mappingError)
            return
        }
        viewModelScope.launch {
            configStore.saveConfig(config)
            emitMessage("Configuration saved.")
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            configStore.clearApiKey()
            _uiState.update { it.copy(apiKey = "") }
            emitMessage("API key cleared.")
        }
    }

    fun generate() {
        if (generationJob?.isActive == true) {
            emitMessage("Task is already running.")
            return
        }
        val state = _uiState.value
        val input = GenerationInput(
            prompt = state.prompt.trim(),
            negative = state.negative.trim(),
        )
        lastSubmittedInput = input
        executeGeneration(input)
    }

    fun retry() {
        val input = lastSubmittedInput
        if (input == null) {
            emitMessage("No previous task to retry.")
            return
        }
        if (generationJob?.isActive == true) {
            emitMessage("Task is already running.")
            return
        }
        executeGeneration(input)
    }

    fun isGenerateEnabled(state: MainUiState): Boolean {
        val config = state.toWorkflowConfig()
        return WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(prompt = state.prompt, negative = state.negative),
        ) == null
    }

    fun downloadResult(
        context: Context,
        output: GeneratedOutput,
        index: Int,
    ) {
        val state = _uiState.value
        val taskId = when (val generationState = state.generationState) {
            is GenerationState.Success -> generationState.taskId
            else -> "unknown_task"
        }
        viewModelScope.launch {
            imageDownloader.downloadToGallery(
                context = context,
                fileUrl = output.fileUrl,
                fileType = output.fileType,
                taskId = taskId,
                index = index,
                decodePassword = state.decodePassword,
            ).onSuccess { downloadResult ->
                emitMessage("Saved to gallery: ${downloadResult.fileName}")
                val fallback = downloadResult.decodeOutcome.fallbackOrNull()
                if (
                    fallback != null &&
                    fallback.shouldNotifyUser &&
                    notifiedDecodeFallbackUrls.add(output.fileUrl)
                ) {
                    emitMessage(fallback.message)
                }
            }.onFailure { error ->
                emitMessage("Save failed: ${error.message.orEmpty()}")
            }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val config = configStore.loadConfig()
            _uiState.update {
                it.copy(
                    isLoadingConfig = false,
                    apiKey = config.apiKey,
                    workflowId = config.workflowId,
                    promptNodeId = config.promptNodeId,
                    promptFieldName = config.promptFieldName,
                    negativeNodeId = config.negativeNodeId,
                    negativeFieldName = config.negativeFieldName,
                    decodePassword = config.decodePassword,
                )
            }
        }
    }

    private fun executeGeneration(input: GenerationInput) {
        val config = _uiState.value.toWorkflowConfig()
        generationJob = viewModelScope.launch {
            generationRepository.generateAndPoll(config, input).collect { generationState ->
                _uiState.update { it.copy(generationState = generationState) }
                when (generationState) {
                    is GenerationState.Success -> {
                        emitMessage("Generation completed: ${generationState.results.size} result(s).")
                    }

                    is GenerationState.Failed -> emitMessage(generationState.message)
                    is GenerationState.Timeout -> emitMessage("Task timed out, please retry manually.")
                    else -> Unit
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
        private val configStore: SecureConfigStore,
        private val generationRepository: GenerationRepository,
        private val imageDownloader: ImageDownloader,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                configStore = configStore,
                generationRepository = generationRepository,
                imageDownloader = imageDownloader,
            ) as T
        }
    }
}
