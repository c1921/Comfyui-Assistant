package io.github.c1921.comfyui_assistant.feature.album

import io.github.c1921.comfyui_assistant.data.repository.InternalAlbumRepository
import io.github.c1921.comfyui_assistant.domain.AlbumDecodeOutcomeCode
import io.github.c1921.comfyui_assistant.domain.AlbumDeleteResult
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun `openMedia reuses loaded task detail for same task`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val detail = detail(
            taskId = "task-cache",
            mediaItems = listOf(
                mediaItem(index = 1, kind = OutputMediaKind.IMAGE),
                mediaItem(index = 2, kind = OutputMediaKind.IMAGE),
            ),
        )
        val repository = FakeInternalAlbumRepository(
            mediaList = listOf(
                mediaSummary(taskId = "task-cache", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-cache", index = 2, kind = OutputMediaKind.IMAGE),
            ),
            detailByTaskId = mapOf("task-cache" to detail),
        )
        val viewModel = AlbumViewModel(repository)

        viewModel.openMedia(AlbumMediaKey("task-cache", 1))
        advanceUntilIdle()
        assertEquals(1, repository.loadTaskDetailCallCount)

        viewModel.openMedia(AlbumMediaKey("task-cache", 2))
        advanceUntilIdle()

        assertEquals(1, repository.loadTaskDetailCallCount)
        assertEquals(2, viewModel.uiState.value.selectedMediaItem?.index)
        Dispatchers.resetMain()
    }

    @Test
    fun `openMedia uses cache when revisiting a previous task`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val detailTaskA = detail(
            taskId = "task-a",
            mediaItems = listOf(mediaItem(index = 1, kind = OutputMediaKind.IMAGE)),
        )
        val detailTaskB = detail(
            taskId = "task-b",
            mediaItems = listOf(mediaItem(index = 1, kind = OutputMediaKind.IMAGE)),
        )
        val repository = FakeInternalAlbumRepository(
            mediaList = listOf(
                mediaSummary(taskId = "task-a", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-b", index = 1, kind = OutputMediaKind.IMAGE),
            ),
            detailByTaskId = mapOf(
                "task-a" to detailTaskA,
                "task-b" to detailTaskB,
            ),
        )
        val viewModel = AlbumViewModel(repository)

        viewModel.openMedia(AlbumMediaKey("task-a", 1))
        advanceUntilIdle()
        viewModel.openMedia(AlbumMediaKey("task-b", 1))
        advanceUntilIdle()
        viewModel.openMedia(AlbumMediaKey("task-a", 1))
        advanceUntilIdle()

        assertEquals(1, repository.loadCountForTask("task-a"))
        assertEquals(1, repository.loadCountForTask("task-b"))
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteMedia from detail navigates to fallback media`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val detail = detail(
            taskId = "task-delete-fallback",
            mediaItems = listOf(
                mediaItem(index = 1, kind = OutputMediaKind.IMAGE),
                mediaItem(index = 2, kind = OutputMediaKind.IMAGE),
            ),
        )
        val repository = FakeInternalAlbumRepository(
            mediaList = listOf(
                mediaSummary(taskId = "task-delete-fallback", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-delete-fallback", index = 2, kind = OutputMediaKind.IMAGE),
            ),
            detailByTaskId = mapOf("task-delete-fallback" to detail),
        )
        val viewModel = AlbumViewModel(repository)
        viewModel.openMedia(AlbumMediaKey("task-delete-fallback", 1))
        advanceUntilIdle()

        viewModel.deleteMedia(
            keys = setOf(AlbumMediaKey("task-delete-fallback", 1)),
            fallbackKeyAfterDelete = AlbumMediaKey("task-delete-fallback", 2),
        )
        advanceUntilIdle()

        assertEquals(setOf(AlbumMediaKey("task-delete-fallback", 1)), repository.lastDeleteKeys)
        assertEquals(AlbumMediaKey("task-delete-fallback", 2), viewModel.uiState.value.selectedMediaKey)
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteMedia updates deleting state while request is running`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeInternalAlbumRepository(
            mediaList = listOf(mediaSummary(taskId = "task-delete-loading", index = 1, kind = OutputMediaKind.IMAGE)),
            deleteResultProvider = { keys ->
                delay(100)
                Result.success(
                    AlbumDeleteResult(
                        requestedCount = keys.size,
                        deletedCount = keys.size,
                        missingCount = 0,
                        affectedTaskCount = 1,
                    ),
                )
            },
        )
        val viewModel = AlbumViewModel(repository)

        viewModel.deleteMedia(setOf(AlbumMediaKey("task-delete-loading", 1)))
        runCurrent()
        assertTrue(viewModel.uiState.value.isDeletingMedia)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDeletingMedia)
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteMedia failure emits message and keeps current selection`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val detail = detail(
            taskId = "task-delete-fail",
            mediaItems = listOf(mediaItem(index = 1, kind = OutputMediaKind.IMAGE)),
        )
        val repository = FakeInternalAlbumRepository(
            mediaList = listOf(mediaSummary(taskId = "task-delete-fail", index = 1, kind = OutputMediaKind.IMAGE)),
            detailByTaskId = mapOf("task-delete-fail" to detail),
            deleteResultProvider = {
                Result.failure(IllegalStateException("forced delete failure"))
            },
        )
        val viewModel = AlbumViewModel(repository)
        viewModel.openMedia(AlbumMediaKey("task-delete-fail", 1))
        advanceUntilIdle()
        val firstMessage = async { viewModel.messages.first() }

        viewModel.deleteMedia(
            keys = setOf(AlbumMediaKey("task-delete-fail", 1)),
            fallbackKeyAfterDelete = null,
        )
        advanceUntilIdle()

        assertEquals(AlbumMediaKey("task-delete-fail", 1), viewModel.uiState.value.selectedMediaKey)
        assertTrue(firstMessage.await().contains("Failed to delete media"))
        Dispatchers.resetMain()
    }

    private fun mediaSummary(
        taskId: String,
        index: Int,
        kind: OutputMediaKind,
    ): AlbumMediaSummary {
        return AlbumMediaSummary(
            key = AlbumMediaKey(taskId = taskId, index = index),
            createdAtEpochMs = 2000L + index,
            savedAtEpochMs = 1000L,
            savedMediaKind = kind,
            localRelativePath = "tasks/$taskId/out_$index.${if (kind == OutputMediaKind.VIDEO) "mp4" else "jpg"}",
            mimeType = if (kind == OutputMediaKind.VIDEO) "video/mp4" else "image/jpeg",
            workflowId = "wf",
            prompt = "prompt",
        )
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
        private val deleteResultProvider: suspend (Set<AlbumMediaKey>) -> Result<AlbumDeleteResult> = { keys ->
            Result.success(
                AlbumDeleteResult(
                    requestedCount = keys.size,
                    deletedCount = keys.size,
                    missingCount = 0,
                    affectedTaskCount = keys.map { it.taskId }.toSet().size,
                ),
            )
        },
    ) : InternalAlbumRepository {
        private val taskSummariesFlow = MutableStateFlow<List<AlbumTaskSummary>>(emptyList())
        private val mediaSummariesFlow = MutableStateFlow(mediaList)
        private val loadTaskDetailCallCountByTask = mutableMapOf<String, Int>()
        private val detailByTaskIdMutable = detailByTaskId.toMutableMap()
        var loadTaskDetailCallCount: Int = 0
            private set
        var deleteMediaCallCount: Int = 0
            private set
        var lastDeleteKeys: Set<AlbumMediaKey>? = null
            private set

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
            loadTaskDetailCallCount += 1
            loadTaskDetailCallCountByTask[taskId] = (loadTaskDetailCallCountByTask[taskId] ?: 0) + 1
            val detail = detailByTaskIdMutable[taskId]
                ?: return Result.failure(IllegalStateException("Task not found in fake repo"))
            return Result.success(detail)
        }

        fun loadCountForTask(taskId: String): Int = loadTaskDetailCallCountByTask[taskId] ?: 0

        override suspend fun hasTask(taskId: String): Boolean = detailByTaskIdMutable.containsKey(taskId)

        override suspend fun findFirstImageKey(taskId: String): Result<AlbumMediaKey?> {
            return Result.success(firstImageKeyByTaskId[taskId])
        }

        override suspend fun findFirstMediaKey(taskId: String): Result<AlbumMediaKey?> {
            return Result.success(firstMediaKeyByTaskId[taskId])
        }

        override suspend fun deleteMedia(keys: Set<AlbumMediaKey>): Result<AlbumDeleteResult> {
            deleteMediaCallCount += 1
            lastDeleteKeys = keys
            val result = deleteResultProvider(keys)
            result.onSuccess {
                if (keys.isNotEmpty()) {
                    mediaSummariesFlow.value = mediaSummariesFlow.value.filterNot { summary ->
                        summary.key in keys
                    }
                    keys.groupBy { it.taskId }.forEach { (taskId, taskKeys) ->
                        val detail = detailByTaskIdMutable[taskId] ?: return@forEach
                        val removedIndexes = taskKeys.map { it.index }.toSet()
                        val remaining = detail.mediaItems.filterNot { it.index in removedIndexes }
                        if (remaining.isEmpty()) {
                            detailByTaskIdMutable.remove(taskId)
                        } else {
                            detailByTaskIdMutable[taskId] = detail.copy(
                                totalOutputs = remaining.size + detail.failedCount,
                                savedCount = remaining.size,
                                mediaItems = remaining,
                            )
                        }
                    }
                }
            }
            return result
        }
    }
}

