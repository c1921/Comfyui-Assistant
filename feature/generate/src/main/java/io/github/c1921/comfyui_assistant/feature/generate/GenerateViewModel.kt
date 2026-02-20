package io.github.c1921.comfyui_assistant.feature.generate

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.local.ConfigSyncResult
import io.github.c1921.comfyui_assistant.data.local.ConfigSyncTrigger
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.local.NoOpWebDavSyncRepository
import io.github.c1921.comfyui_assistant.data.local.WebDavSyncRepository
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.InternalAlbumRepository
import io.github.c1921.comfyui_assistant.data.repository.InputImageUploader
import io.github.c1921.comfyui_assistant.data.repository.InputImageSelectionStore
import io.github.c1921.comfyui_assistant.data.repository.PersistedInputImageSelection
import io.github.c1921.comfyui_assistant.data.repository.WorkflowRequestBuilder
import io.github.c1921.comfyui_assistant.domain.ConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.GenerationRequestSnapshot
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.ImagePresetSnapshot
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.RequestNodeField
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
    private val internalAlbumRepository: InternalAlbumRepository,
    private val webDavSyncRepository: WebDavSyncRepository = NoOpWebDavSyncRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _openAlbumRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openAlbumRequests: SharedFlow<String> = _openAlbumRequests.asSharedFlow()

    private var generationJob: Job? = null
    private var lastSubmittedInput: GenerationInput? = null
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
        applySelectionForMode(
            mode = selectedMode,
            uri = uri,
            displayName = displayName,
            persistFailureMessagePrefix = "Selected image could not be persisted",
        )
    }

    fun onVideoInputImageSelectedFromAlbum(
        uri: Uri,
        displayName: String,
    ) {
        if (_uiState.value.config.videoImageInputNodeId.isBlank()) {
            emitMessage("Please configure video image input nodeId first.")
            return
        }
        applySelectionForMode(
            mode = GenerationMode.VIDEO,
            uri = uri,
            displayName = displayName,
            persistFailureMessagePrefix = "Video input image could not be persisted",
            persistSuccessMessage = "Image sent to video input.",
        )
    }

    private fun applySelectionForMode(
        mode: GenerationMode,
        uri: Uri,
        displayName: String,
        persistFailureMessagePrefix: String,
        persistSuccessMessage: String? = null,
    ) {
        val normalizedDisplayName = normalizeInputImageDisplayName(uri, displayName)
        setModeSelection(
            mode = mode,
            selection = PersistedInputImageSelection(
                uri = uri,
                displayName = normalizedDisplayName,
            ),
        )
        refreshVisibleInputImage()
        viewModelScope.launch {
            val persistResult = try {
                inputImageSelectionStore.persistSelection(
                    mode = mode,
                    sourceUri = uri,
                    displayName = normalizedDisplayName,
                )
            } catch (error: Exception) {
                Result.failure(error)
            }
            persistResult.onSuccess { persistedSelection ->
                setModeSelection(mode, persistedSelection)
                refreshVisibleInputImage()
                persistSuccessMessage?.let { emitMessage(it) }
            }.onFailure { error ->
                val reason = error.message?.ifBlank { "unknown error." } ?: "unknown error."
                emitMessage("$persistFailureMessagePrefix: $reason")
            }
        }
    }

    private fun normalizeInputImageDisplayName(
        uri: Uri,
        displayName: String,
    ): String {
        return displayName.trim().ifBlank {
            uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { uri.toString() }
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

    fun openLastArchivedTaskInAlbum() {
        val state = _uiState.value
        val taskId = when (val generationState = state.generationState) {
            is GenerationState.Success -> generationState.taskId
            else -> state.lastArchivedTaskId
        }.trim()
        if (taskId.isBlank()) {
            emitMessage("No archived task yet.")
            return
        }
        _openAlbumRequests.tryEmit(taskId)
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
        val requestSnapshot = buildRequestSnapshot(config, input)
        generationJob = viewModelScope.launch {
            generationRepository.generateAndPoll(config, input).collect { generationState ->
                _uiState.update { it.copy(generationState = generationState) }
                when (generationState) {
                    is GenerationState.Success -> {
                        emitMessage("Generation completed: ${generationState.results.size} result(s).")
                        val archiveResult = internalAlbumRepository.archiveGeneration(
                            requestSnapshot = requestSnapshot,
                            successState = generationState,
                            decodePassword = config.decodePassword,
                        )
                        archiveResult.onSuccess { result ->
                            _uiState.update { it.copy(lastArchivedTaskId = result.taskId) }
                            if (result.failedCount > 0) {
                                emitMessage(
                                    "Saved internally: ${result.successCount}/${result.totalOutputs}, failed: ${result.failedCount}",
                                )
                            } else {
                                emitMessage("Saved internally: ${result.successCount}/${result.totalOutputs}")
                            }
                            when (val syncResult = webDavSyncRepository.syncConfig(ConfigSyncTrigger.AFTER_ARCHIVE)) {
                                is ConfigSyncResult.Skipped -> Unit
                                is ConfigSyncResult.Pushed -> emitMessage("WebDAV sync: configuration pushed.")
                                is ConfigSyncResult.Pulled -> {
                                    configDraftStore.update { syncResult.config }
                                    emitMessage("WebDAV sync: newer remote configuration applied.")
                                }

                                is ConfigSyncResult.Failed -> {
                                    emitMessage("WebDAV sync failed: ${syncResult.message}")
                                }
                            }
                        }.onFailure { error ->
                            val reason = error.message?.ifBlank { "unknown error." } ?: "unknown error."
                            emitMessage("Internal save failed: $reason")
                        }
                    }

                    is GenerationState.Failed -> emitMessage(generationState.message)
                    is GenerationState.Timeout -> emitMessage("Task timed out, please retry manually.")
                    else -> Unit
                }
            }
        }
    }

    private fun buildRequestSnapshot(
        config: io.github.c1921.comfyui_assistant.domain.WorkflowConfig,
        input: GenerationInput,
    ): GenerationRequestSnapshot {
        val nodeInfoList = WorkflowRequestBuilder.buildNodeInfoList(config, input).map { node ->
            RequestNodeField(
                nodeId = node.nodeId,
                fieldName = node.fieldName,
                fieldValue = node.fieldValue.toString(),
            )
        }
        val workflowId = when (input.mode) {
            GenerationMode.IMAGE -> config.workflowId.trim()
            GenerationMode.VIDEO -> config.videoWorkflowId.trim()
        }
        val imagePreset = if (input.mode == GenerationMode.IMAGE) {
            ImagePresetSnapshot(
                id = input.imagePreset.id,
                width = input.imagePreset.width,
                height = input.imagePreset.height,
            )
        } else {
            null
        }
        return GenerationRequestSnapshot(
            requestSentAtEpochMs = System.currentTimeMillis(),
            generationMode = input.mode,
            workflowId = workflowId,
            prompt = input.prompt,
            negative = input.negative,
            imagePreset = imagePreset,
            videoLengthFrames = input.videoLengthFrames,
            uploadedImageFileName = input.uploadedImageFileName.trim().ifBlank { null },
            nodeInfoList = nodeInfoList,
        )
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
        private val internalAlbumRepository: InternalAlbumRepository,
        private val webDavSyncRepository: WebDavSyncRepository = NoOpWebDavSyncRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GenerateViewModel(
                configRepository = configRepository,
                configDraftStore = configDraftStore,
                generationRepository = generationRepository,
                inputImageUploader = inputImageUploader,
                inputImageSelectionStore = inputImageSelectionStore,
                internalAlbumRepository = internalAlbumRepository,
                webDavSyncRepository = webDavSyncRepository,
            ) as T
        }
    }
}
