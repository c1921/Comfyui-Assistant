package io.github.c1921.comfyui_assistant.feature.album

import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail

data class AlbumUiState(
    val mediaList: List<AlbumMediaSummary> = emptyList(),
    val selectedMediaKey: AlbumMediaKey? = null,
    val selectedTaskDetail: AlbumTaskDetail? = null,
    val selectedMediaItem: AlbumMediaItem? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    val isMetadataExpanded: Boolean = false,
    val isDeletingMedia: Boolean = false,
)
