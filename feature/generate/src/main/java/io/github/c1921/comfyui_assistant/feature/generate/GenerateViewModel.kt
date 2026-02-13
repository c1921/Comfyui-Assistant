package io.github.c1921.comfyui_assistant.feature.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.decoder.fallbackOrNull
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.MediaSaver
import io.github.c1921.comfyui_assistant.domain.ConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.WorkflowConfigValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GenerateViewModel(
    private val configRepository: ConfigRepository,
    private val configDraftStore: ConfigDraftStore,
    private val generationRepository: GenerationRepository,
    private val mediaSaver: MediaSaver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var generationJob: Job? = null
    private var lastSubmittedInput: GenerationInput? = null
    private val notifiedDecodeFallbackUrls = mutableSetOf<String>()

    init {
        observeConfigDraft()
        loadPersistedConfig()
    }

    fun onPromptChanged(value: String) {
        _uiState.update { it.copy(prompt = value) }
    }

    fun onNegativeChanged(value: String) {
        _uiState.update { it.copy(negative = value) }
    }

    fun isGenerateEnabled(state: GenerateUiState): Boolean {
        return WorkflowConfigValidator.validateForGenerate(
            config = state.config,
            input = GenerationInput(prompt = state.prompt, negative = state.negative),
        ) == null
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

    fun downloadResult(
        output: GeneratedOutput,
        index: Int,
    ) {
        val state = _uiState.value
        val taskId = when (val generationState = state.generationState) {
            is GenerationState.Success -> generationState.taskId
            else -> "unknown_task"
        }
        viewModelScope.launch {
            mediaSaver.saveToGallery(
                output = output,
                taskId = taskId,
                index = index,
                decodePassword = state.config.decodePassword,
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
                    state.copy(config = config)
                }
            }
        }
    }

    private fun executeGeneration(input: GenerationInput) {
        val config = _uiState.value.config
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
        private val configRepository: ConfigRepository,
        private val configDraftStore: ConfigDraftStore,
        private val generationRepository: GenerationRepository,
        private val mediaSaver: MediaSaver,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GenerateViewModel(
                configRepository = configRepository,
                configDraftStore = configDraftStore,
                generationRepository = generationRepository,
                mediaSaver = mediaSaver,
            ) as T
        }
    }
}
