package io.github.c1921.comfyui_assistant.feature.generate

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.decoder.fallbackOrNull
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.InputImageUploader
import io.github.c1921.comfyui_assistant.data.repository.MediaSaver
import io.github.c1921.comfyui_assistant.domain.ConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
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
    private val inputImageUploader: InputImageUploader,
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

    fun onImagePresetChanged(value: ImageAspectPreset) {
        _uiState.update { it.copy(selectedImagePreset = value) }
    }

    fun onGenerationModeChanged(value: GenerationMode) {
        _uiState.update { state ->
            if (
                state.selectedInputImageUri != null &&
                !WorkflowConfigValidator.hasInputImageMapping(state.config, value)
            ) {
                state.copy(
                    selectedMode = value,
                    selectedInputImageUri = null,
                    selectedInputImageDisplayName = "",
                )
            } else {
                state.copy(selectedMode = value)
            }
        }
    }

    fun onInputImageSelected(
        uri: Uri?,
        displayName: String,
    ) {
        _uiState.update {
            it.copy(
                selectedInputImageUri = uri,
                selectedInputImageDisplayName = displayName,
            )
        }
    }

    fun onClearInputImage() {
        _uiState.update {
            it.copy(
                selectedInputImageUri = null,
                selectedInputImageDisplayName = "",
            )
        }
    }

    fun isGenerateEnabled(state: GenerateUiState): Boolean {
        return WorkflowConfigValidator.validateForGenerate(
            config = state.config,
            input = GenerationInput(
                prompt = state.prompt,
                negative = state.negative,
                mode = state.selectedMode,
                imagePreset = state.selectedImagePreset,
                hasInputImage = state.selectedInputImageUri != null,
            ),
        ) == null
    }

    fun generate() {
        val state = _uiState.value
        if (generationJob?.isActive == true || state.isUploadingInputImage) {
            emitMessage("Task is already running.")
            return
        }
        viewModelScope.launch {
            val latestState = _uiState.value
            val initialInput = GenerationInput(
                prompt = latestState.prompt.trim(),
                negative = latestState.negative.trim(),
                mode = latestState.selectedMode,
                imagePreset = latestState.selectedImagePreset,
                hasInputImage = latestState.selectedInputImageUri != null,
            )
            val validationError = WorkflowConfigValidator.validateForGenerate(
                config = latestState.config,
                input = initialInput,
            )
            if (validationError != null) {
                updateGenerateFailure(validationError)
                emitMessage(validationError)
                return@launch
            }

            val uploadedImageFileName = if (latestState.selectedInputImageUri != null) {
                _uiState.update { it.copy(isUploadingInputImage = true) }
                val uploadResult = try {
                    inputImageUploader.uploadInputImage(
                        apiKey = latestState.config.apiKey,
                        imageUri = latestState.selectedInputImageUri,
                    )
                } catch (error: Exception) {
                    Result.failure(error)
                } finally {
                    _uiState.update { it.copy(isUploadingInputImage = false) }
                }
                uploadResult.getOrElse { error ->
                    val message = error.message?.ifBlank { "Upload failed." } ?: "Upload failed."
                    updateGenerateFailure(message)
                    emitMessage(message)
                    return@launch
                }
            } else {
                ""
            }

            val input = initialInput.copy(uploadedImageFileName = uploadedImageFileName)
            lastSubmittedInput = input
            executeGeneration(input)
        }
    }

    fun retry() {
        val input = lastSubmittedInput
        if (input == null) {
            emitMessage("No previous task to retry.")
            return
        }
        if (generationJob?.isActive == true || _uiState.value.isUploadingInputImage) {
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
                val fallback = downloadResult.decodeOutcome?.fallbackOrNull()
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
                    val shouldClearInputImage = state.selectedInputImageUri != null &&
                        !WorkflowConfigValidator.hasInputImageMapping(config, state.selectedMode)
                    state.copy(
                        config = config,
                        selectedInputImageUri = if (shouldClearInputImage) null else state.selectedInputImageUri,
                        selectedInputImageDisplayName = if (shouldClearInputImage) "" else state.selectedInputImageDisplayName,
                    )
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

    private fun updateGenerateFailure(message: String) {
        _uiState.update {
            it.copy(
                generationState = GenerationState.Failed(
                    taskId = null,
                    errorCode = null,
                    message = message,
                    failedReason = null,
                    promptTipsNodeErrors = null,
                )
            )
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
        private val inputImageUploader: InputImageUploader,
        private val mediaSaver: MediaSaver,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GenerateViewModel(
                configRepository = configRepository,
                configDraftStore = configDraftStore,
                generationRepository = generationRepository,
                inputImageUploader = inputImageUploader,
                mediaSaver = mediaSaver,
            ) as T
        }
    }
}
