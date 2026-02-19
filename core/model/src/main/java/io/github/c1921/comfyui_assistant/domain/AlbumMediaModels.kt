package io.github.c1921.comfyui_assistant.domain

data class AlbumMediaKey(
    val taskId: String,
    val index: Int,
)

data class AlbumMediaSummary(
    val key: AlbumMediaKey,
    val createdAtEpochMs: Long,
    val savedAtEpochMs: Long,
    val savedMediaKind: OutputMediaKind,
    val localRelativePath: String,
    val mimeType: String,
    val workflowId: String,
    val prompt: String,
)

sealed interface AlbumOpenTarget {
    data class Media(
        val key: AlbumMediaKey,
    ) : AlbumOpenTarget

    data class Task(
        val taskId: String,
    ) : AlbumOpenTarget
}
