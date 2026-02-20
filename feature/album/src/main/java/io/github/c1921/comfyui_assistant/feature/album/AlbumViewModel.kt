package io.github.c1921.comfyui_assistant.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.comfyui_assistant.data.repository.InternalAlbumRepository
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumOpenTarget
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import java.util.LinkedHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumViewModel(
    private val internalAlbumRepository: InternalAlbumRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val taskDetailCache = object : LinkedHashMap<String, AlbumTaskDetail>(
        DETAIL_CACHE_CAPACITY,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AlbumTaskDetail>): Boolean {
            return size > DETAIL_CACHE_CAPACITY
        }
    }

    init {
        observeMediaSummaries()
    }

    fun openByTarget(target: AlbumOpenTarget) {
        when (target) {
            is AlbumOpenTarget.Media -> openMedia(target.key)
            is AlbumOpenTarget.Task -> openTaskFirstMedia(target.taskId)
        }
    }

    fun openMedia(key: AlbumMediaKey) {
        val normalizedKey = normalizeMediaKey(key)
        if (normalizedKey == null) {
            emitMessage("Invalid media target.")
            return
        }
        val cachedDetail = _uiState.value.selectedTaskDetail?.takeIf { it.taskId == normalizedKey.taskId }
            ?: taskDetailCache[normalizedKey.taskId]
        val cachedMedia = cachedDetail?.mediaItems?.firstOrNull { it.index == normalizedKey.index }
        if (cachedDetail != null && cachedMedia != null) {
            _uiState.update {
                it.copy(
                    selectedMediaKey = normalizedKey,
                    selectedTaskDetail = cachedDetail,
                    selectedMediaItem = cachedMedia,
                    isLoadingDetail = false,
                    detailError = null,
                    isMetadataExpanded = false,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedMediaKey = normalizedKey,
                selectedTaskDetail = null,
                selectedMediaItem = null,
                isLoadingDetail = true,
                detailError = null,
                isMetadataExpanded = false,
            )
        }
        loadDetailForMedia(normalizedKey)
    }

    fun backToList() {
        _uiState.update {
            it.copy(
                selectedMediaKey = null,
                selectedTaskDetail = null,
                selectedMediaItem = null,
                isLoadingDetail = false,
                detailError = null,
                isMetadataExpanded = false,
            )
        }
    }

    fun retryLoadSelectedMedia() {
        val key = _uiState.value.selectedMediaKey
        if (key == null) {
            emitMessage("No media selected.")
            return
        }
        openMedia(key)
    }

    fun toggleMetadataExpanded() {
        _uiState.update { it.copy(isMetadataExpanded = !it.isMetadataExpanded) }
    }

    private fun observeMediaSummaries() {
        viewModelScope.launch {
            internalAlbumRepository.observeMediaSummaries().collectLatest { media ->
                _uiState.update { it.copy(mediaList = media) }
            }
        }
    }

    private fun openTaskFirstMedia(taskId: String) {
        val normalizedTaskId = taskId.trim()
        if (normalizedTaskId.isBlank()) {
            emitMessage("Invalid taskId.")
            return
        }
        viewModelScope.launch {
            val firstImage = internalAlbumRepository.findFirstImageKey(normalizedTaskId).getOrNull()
            val selectedKey = firstImage ?: internalAlbumRepository.findFirstMediaKey(normalizedTaskId).getOrNull()
            if (selectedKey == null) {
                emitMessage("Task has no media.")
                return@launch
            }
            openMedia(selectedKey)
        }
    }

    private fun loadDetailForMedia(key: AlbumMediaKey) {
        viewModelScope.launch {
            internalAlbumRepository.loadTaskDetail(key.taskId)
                .onSuccess { detail ->
                    taskDetailCache[detail.taskId] = detail
                    val selectedMediaItem = detail.mediaItems.firstOrNull { it.index == key.index }
                    if (selectedMediaItem == null) {
                        val reason = "Media #${key.index} not found in task ${key.taskId}."
                        _uiState.update {
                            it.copy(
                                selectedTaskDetail = detail,
                                selectedMediaItem = null,
                                isLoadingDetail = false,
                                detailError = reason,
                                isMetadataExpanded = false,
                            )
                        }
                        emitMessage(reason)
                        return@onSuccess
                    }

                    _uiState.update {
                        it.copy(
                            selectedTaskDetail = detail,
                            selectedMediaItem = selectedMediaItem,
                            isLoadingDetail = false,
                            detailError = null,
                            isMetadataExpanded = false,
                        )
                    }
                }
                .onFailure { error ->
                    val reason = error.message?.ifBlank { "unknown error." } ?: "unknown error."
                    _uiState.update {
                        it.copy(
                            selectedTaskDetail = null,
                            selectedMediaItem = null,
                            isLoadingDetail = false,
                            detailError = reason,
                            isMetadataExpanded = false,
                        )
                    }
                    emitMessage("Failed to load task detail: $reason")
                }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    private fun normalizeMediaKey(key: AlbumMediaKey): AlbumMediaKey? {
        val taskId = key.taskId.trim()
        if (taskId.isBlank() || key.index <= 0) {
            return null
        }
        return AlbumMediaKey(taskId = taskId, index = key.index)
    }

    private companion object {
        const val DETAIL_CACHE_CAPACITY = 20
    }

    class Factory(
        private val internalAlbumRepository: InternalAlbumRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlbumViewModel(
                internalAlbumRepository = internalAlbumRepository,
            ) as T
        }
    }
}
