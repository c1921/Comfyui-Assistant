package io.github.c1921.comfyui_assistant.feature.album

import io.github.c1921.comfyui_assistant.data.repository.InternalAlbumRepository
import io.github.c1921.comfyui_assistant.domain.AlbumDecodeOutcomeCode
import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumOpenTarget
import io.github.c1921.comfyui_assistant.domain.AlbumSaveFailureItem
import io.github.c1921.comfyui_assistant.domain.AlbumSaveResult
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.AlbumTaskSummary
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationRequestSnapshot
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    @Test
    fun `openByTarget Task opens first image first`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val detail = detail(
            taskId = "task-a",
            mediaItems = listOf(
                mediaItem(index = 1, kind = OutputMediaKind.VIDEO),
                mediaItem(index = 2, kind = OutputMediaKind.IMAGE),
            ),
        )
        val repository = FakeInternalAlbumRepository(
            detailByTaskId = mapOf("task-a" to detail),
            firstImageKeyByTaskId = mapOf("task-a" to AlbumMediaKey("task-a", 2)),
            firstMediaKeyByTaskId = mapOf("task-a" to AlbumMediaKey("task-a", 1)),
        )
        val viewModel = AlbumViewModel(repository)

        viewModel.openByTarget(AlbumOpenTarget.Task("task-a"))
        advanceUntilIdle()

        assertEquals(AlbumMediaKey("task-a", 2), viewModel.uiState.value.selectedMediaKey)
        assertEquals(2, viewModel.uiState.value.selectedMediaItem?.index)
        Dispatchers.resetMain()
    }

    @Test
    fun `openByTarget Task falls back to first media when no image exists`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val detail = detail(
            taskId = "task-b",
            mediaItems = listOf(mediaItem(index = 1, kind = OutputMediaKind.VIDEO)),
        )
        val repository = FakeInternalAlbumRepository(
            detailByTaskId = mapOf("task-b" to detail),
            firstImageKeyByTaskId = mapOf("task-b" to null),
            firstMediaKeyByTaskId = mapOf("task-b" to AlbumMediaKey("task-b", 1)),
        )
        val viewModel = AlbumViewModel(repository)

        viewModel.openByTarget(AlbumOpenTarget.Task("task-b"))
        advanceUntilIdle()

        assertEquals(AlbumMediaKey("task-b", 1), viewModel.uiState.value.selectedMediaKey)
        assertEquals(1, viewModel.uiState.value.selectedMediaItem?.index)
        Dispatchers.resetMain()
    }

    @Test
    fun `metadata panel is collapsed by default and toggle works`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = AlbumViewModel(FakeInternalAlbumRepository())

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMetadataExpanded)
        viewModel.toggleMetadataExpanded()
        assertTrue(viewModel.uiState.value.isMetadataExpanded)
        viewModel.toggleMetadataExpanded()
        assertFalse(viewModel.uiState.value.isMetadataExpanded)
        Dispatchers.resetMain()
    }

    private fun mediaItem(
        index: Int,
        kind: OutputMediaKind,
    ): AlbumMediaItem {
        return AlbumMediaItem(
            index = index,
            sourceFileUrl = "https://example.com/out_$index",
            sourceFileType = if (kind == OutputMediaKind.VIDEO) "mp4" else "png",
            sourceNodeId = index.toString(),
            savedMediaKind = kind,
            localRelativePath = "tasks/task/out_$index.${if (kind == OutputMediaKind.VIDEO) "mp4" else "jpg"}",
            extension = if (kind == OutputMediaKind.VIDEO) "mp4" else "jpg",
            mimeType = if (kind == OutputMediaKind.VIDEO) "video/mp4" else "image/jpeg",
            fileSizeBytes = 1024L + index,
            decodedFromDuck = false,
            decodeOutcomeCode = AlbumDecodeOutcomeCode.NOT_ATTEMPTED,
            createdAtEpochMs = 1000L + index,
        )
    }

    private fun detail(
        taskId: String,
        mediaItems: List<AlbumMediaItem>,
    ): AlbumTaskDetail {
        return AlbumTaskDetail(
            schemaVersion = 1,
            taskId = taskId,
            requestSentAtEpochMs = 100,
            savedAtEpochMs = 200,
            generationMode = GenerationMode.IMAGE,
            workflowId = "workflow",
            prompt = "prompt",
            negative = "",
            imagePreset = null,
            videoLengthFrames = null,
            uploadedImageFileName = null,
            nodeInfoList = emptyList(),
            promptTipsNodeErrors = null,
            totalOutputs = mediaItems.size,
            savedCount = mediaItems.size,
            failedCount = 0,
            failures = emptyList(),
            mediaItems = mediaItems,
        )
    }

    private class FakeInternalAlbumRepository(
        private val mediaList: List<AlbumMediaSummary> = emptyList(),
        private val detailByTaskId: Map<String, AlbumTaskDetail> = emptyMap(),
        private val firstImageKeyByTaskId: Map<String, AlbumMediaKey?> = emptyMap(),
        private val firstMediaKeyByTaskId: Map<String, AlbumMediaKey?> = emptyMap(),
    ) : InternalAlbumRepository {
        private val taskSummariesFlow = MutableStateFlow<List<AlbumTaskSummary>>(emptyList())
        private val mediaSummariesFlow = MutableStateFlow(mediaList)

        override suspend fun archiveGeneration(
            requestSnapshot: GenerationRequestSnapshot,
            successState: GenerationState.Success,
            decodePassword: String,
        ): Result<AlbumSaveResult> {
            return Result.success(
                AlbumSaveResult(
                    taskId = successState.taskId,
                    totalOutputs = successState.results.size,
                    successCount = successState.results.size,
                    failedCount = 0,
                    failures = emptyList<AlbumSaveFailureItem>(),
                ),
            )
        }

        override fun observeTaskSummaries(): Flow<List<AlbumTaskSummary>> = taskSummariesFlow

        override fun observeMediaSummaries(): Flow<List<AlbumMediaSummary>> = mediaSummariesFlow

        override suspend fun loadTaskDetail(taskId: String): Result<AlbumTaskDetail> {
            val detail = detailByTaskId[taskId]
                ?: return Result.failure(IllegalStateException("Task not found in fake repo"))
            return Result.success(detail)
        }

        override suspend fun hasTask(taskId: String): Boolean = detailByTaskId.containsKey(taskId)

        override suspend fun findFirstImageKey(taskId: String): Result<AlbumMediaKey?> {
            return Result.success(firstImageKeyByTaskId[taskId])
        }

        override suspend fun findFirstMediaKey(taskId: String): Result<AlbumMediaKey?> {
            return Result.success(firstMediaKeyByTaskId[taskId])
        }
    }
}

