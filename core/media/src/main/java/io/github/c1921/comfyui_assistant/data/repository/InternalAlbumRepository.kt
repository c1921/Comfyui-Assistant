package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.domain.AlbumSaveResult
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.AlbumTaskSummary
import io.github.c1921.comfyui_assistant.domain.GenerationRequestSnapshot
import io.github.c1921.comfyui_assistant.domain.GenerationState
import kotlinx.coroutines.flow.Flow

interface InternalAlbumRepository {
    suspend fun archiveGeneration(
        requestSnapshot: GenerationRequestSnapshot,
        successState: GenerationState.Success,
        decodePassword: String,
    ): Result<AlbumSaveResult>

    fun observeTaskSummaries(): Flow<List<AlbumTaskSummary>>

    fun observeMediaSummaries(): Flow<List<AlbumMediaSummary>>

    suspend fun loadTaskDetail(taskId: String): Result<AlbumTaskDetail>

    suspend fun hasTask(taskId: String): Boolean

    suspend fun findFirstImageKey(taskId: String): Result<AlbumMediaKey?>

    suspend fun findFirstMediaKey(taskId: String): Result<AlbumMediaKey?>
}
