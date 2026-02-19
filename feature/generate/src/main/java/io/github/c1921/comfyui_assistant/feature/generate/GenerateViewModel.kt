package io.github.c1921.comfyui_assistant.feature.generate

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.decoder.fallbackOrNull
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.InputImageUploader
import io.github.c1921.comfyui_assistant.data.repository.InputImageSelectionStore
import io.github.c1921.comfyui_assistant.data.repository.MediaSaver
import io.github.c1921.comfyui_assistant.data.repository.PersistedInputImageSelection
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
    private val inputImageSelectionStore: InputImageSelectionStore,
    private val mediaSaver: MediaSaver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var generationJob: Job? = null
    private var lastSubmittedInput: GenerationInput? = null
    private val notifiedDecodeFallbackUrls = mutableSetOf<String>()
    private var imageModeSelection: PersistedInputImageSelection? = null
    private var videoModeSelection: PersistedInputImageSelection? = null

    init {
        observeConfigDraft()
        loadPersistedConfig()
        loadPersistedInputSelections()
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

    fun onVideoLengthFramesChanged(value: String) {
        _uiState.update { it.copy(videoLengthFramesText = value) }
    }

    fun onGenerationModeChanged(value: GenerationMode) {
        _uiState.update { state ->
            val updatedState = state.copy(selectedMode = value)
            updatedState.copyWithVisibleInputImage(resolveVisibleInputImage(updatedState))
        }
    }

    fun onInputImageSelected(
        uri: Uri?,
        displayName: String,
    ) {
        val selectedMode = _uiState.value.selectedMode
        if (uri == null) {
            onClearInputImage()
            return
        }

        val normalizedDisplayName = displayName.trim().ifBlank {
            uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { uri.toString() }
        }
        setModeSelection(
            mode = selectedMode,
            selection = PersistedInputImageSelection(
                uri = uri,
                displayName = normalizedDisplayName,
            ),
        )
        refreshVisibleInputImage()

        viewModelScope.launch {
            val persistResult = try {
                inputImageSelectionStore.persistSelection(
                    mode = selectedMode,
                    sourceUri = uri,
                    displayName = normalizedDisplayName,
                )
            } catch (error: Exception) {
                Result.failure(error)
            }
            persistResult.onSuccess { persistedSelection ->
                setModeSelection(selectedMode, persistedSelection)
                refreshVisibleInputImage()
            }.onFailure { error ->
                val reason = error.message?.ifBlank { "unknown error." } ?: "unknown error."
                emitMessage("Selected image could not be persisted: $reason")
            }
        }
    }

    fun onClearInputImage() {
        val selectedMode = _uiState.value.selectedMode
        setModeSelection(selectedMode, null)
        refreshVisibleInputImage()
        viewModelScope.launch {
            runCatching {
                inputImageSelectionStore.clearSelection(selectedMode)
            }.onFailure { error ->
                val reason = error.message?.ifBlank { "unknown error." } ?: "unknown error."
                emitMessage("Selected image cleanup failed: $reason")
            }
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
                videoLengthFrames = resolveVideoLengthFrames(state),
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
                videoLengthFrames = resolveVideoLengthFrames(latestState),
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
                    val updatedState = state.copy(config = config)
                    updatedState.copyWithVisibleInputImage(resolveVisibleInputImage(updatedState))
                }
            }
        }
    }

    private fun loadPersistedInputSelections() {
        viewModelScope.launch {
            val persistedSelections = runCatching {
                inputImageSelectionStore.loadSelections()
            }.getOrElse { error ->
                val reason = error.message?.ifBlank { "unknown error." } ?: "unknown error."
                emitMessage("Failed to load persisted input image: $reason")
                return@launch
            }
            imageModeSelection = persistedSelections.imageMode
            videoModeSelection = persistedSelections.videoMode
            refreshVisibleInputImage()
        }
    }

    private fun setModeSelection(
        mode: GenerationMode,
        selection: PersistedInputImageSelection?,
    ) {
        when (mode) {
            GenerationMode.IMAGE -> imageModeSelection = selection
            GenerationMode.VIDEO -> videoModeSelection = selection
        }
    }

    private fun getModeSelection(mode: GenerationMode): PersistedInputImageSelection? {
        return when (mode) {
            GenerationMode.IMAGE -> imageModeSelection
            GenerationMode.VIDEO -> videoModeSelection
        }
    }

    private fun resolveVisibleInputImage(state: GenerateUiState): PersistedInputImageSelection? {
        if (!WorkflowConfigValidator.hasInputImageMapping(state.config, state.selectedMode)) {
            return null
        }
        return getModeSelection(state.selectedMode)
    }

    private fun refreshVisibleInputImage() {
        _uiState.update { state ->
            state.copyWithVisibleInputImage(resolveVisibleInputImage(state))
        }
    }

    private fun GenerateUiState.copyWithVisibleInputImage(
        selection: PersistedInputImageSelection?,
    ): GenerateUiState {
        return copy(
            selectedInputImageUri = selection?.uri,
            selectedInputImageDisplayName = selection?.displayName.orEmpty(),
        )
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

    private fun resolveVideoLengthFrames(state: GenerateUiState): Int? {
        if (state.selectedMode != GenerationMode.VIDEO) return null
        if (state.config.videoLengthNodeId.isBlank()) return null
        return state.videoLengthFramesText.trim().toIntOrNull()?.takeIf { it > 0 }
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
        private val inputImageSelectionStore: InputImageSelectionStore,
        private val mediaSaver: MediaSaver,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GenerateViewModel(
                configRepository = configRepository,
                configDraftStore = configDraftStore,
                generationRepository = generationRepository,
                inputImageUploader = inputImageUploader,
                inputImageSelectionStore = inputImageSelectionStore,
                mediaSaver = mediaSaver,
            ) as T
        }
    }
}
