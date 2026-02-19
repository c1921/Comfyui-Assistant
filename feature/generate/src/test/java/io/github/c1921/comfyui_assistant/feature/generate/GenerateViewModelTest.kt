package io.github.c1921.comfyui_assistant.feature.generate

import android.net.Uri
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.InternalAlbumRepository
import io.github.c1921.comfyui_assistant.data.repository.InputImageSelectionStore
import io.github.c1921.comfyui_assistant.data.repository.InputImageUploader
import io.github.c1921.comfyui_assistant.data.repository.PersistedInputImageSelection
import io.github.c1921.comfyui_assistant.data.repository.PersistedInputImageSelections
import io.github.c1921.comfyui_assistant.domain.AlbumSaveResult
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.AlbumTaskSummary
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationRequestSnapshot
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.InMemoryConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GenerateViewModelTest {
    @Test
    fun `retry without previous task keeps idle state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.generationState is GenerationState.Idle)
        Dispatchers.resetMain()
    }

    @Test
    fun `generate submits selected image preset in image mode`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfigWithSizeMapping()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("hello")
        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        viewModel.onImagePresetChanged(ImageAspectPreset.RATIO_3_2)
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(GenerationMode.IMAGE, generationRepository.lastInput?.mode)
        assertEquals(ImageAspectPreset.RATIO_3_2, generationRepository.lastInput?.imagePreset)
        Dispatchers.resetMain()
    }

    @Test
    fun `generate submits video mode input when selected`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfigWithVideoMapping()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("video prompt")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(GenerationMode.VIDEO, generationRepository.lastInput?.mode)
        Dispatchers.resetMain()
    }

    @Test
    fun `generate submits video length frames when length nodeId is configured`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfigWithVideoMapping().copy(videoLengthNodeId = "31")
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("video prompt")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        viewModel.onVideoLengthFramesChanged("96")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(96, generationRepository.lastInput?.videoLengthFrames)
        Dispatchers.resetMain()
    }

    @Test
    fun `generate submits null video length when length nodeId is not configured`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfigWithVideoMapping()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("video prompt")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        viewModel.onVideoLengthFramesChanged("96")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(null, generationRepository.lastInput?.videoLengthFrames)
        Dispatchers.resetMain()
    }

    @Test
    fun `isGenerateEnabled is false for non 1 to 1 when size mapping missing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("hello")
        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        viewModel.onImagePresetChanged(ImageAspectPreset.RATIO_16_9)
        advanceUntilIdle()

        assertFalse(viewModel.isGenerateEnabled(viewModel.uiState.value))
        Dispatchers.resetMain()
    }

    @Test
    fun `isGenerateEnabled remains true for 1 to 1 when size mapping missing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("hello")
        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        viewModel.onImagePresetChanged(ImageAspectPreset.RATIO_1_1)
        advanceUntilIdle()

        assertTrue(viewModel.isGenerateEnabled(viewModel.uiState.value))
        Dispatchers.resetMain()
    }

    @Test
    fun `isGenerateEnabled is false in video mode when video mapping missing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("hello")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()

        assertFalse(viewModel.isGenerateEnabled(viewModel.uiState.value))
        Dispatchers.resetMain()
    }

    @Test
    fun `isGenerateEnabled is false when video length nodeId is configured but value is invalid`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfigWithVideoMapping().copy(videoLengthNodeId = "31")
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("video prompt")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        viewModel.onVideoLengthFramesChanged("0")
        advanceUntilIdle()

        assertFalse(viewModel.isGenerateEnabled(viewModel.uiState.value))
        Dispatchers.resetMain()
    }

    @Test
    fun `retry uses last submitted mode even after switching ui mode`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfigWithVideoMapping()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("video prompt")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        viewModel.generate()
        advanceUntilIdle()
        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(GenerationMode.VIDEO, generationRepository.lastInput?.mode)
        Dispatchers.resetMain()
    }

    @Test
    fun `generate uploads selected image and passes uploaded fileName`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val uploader = FakeInputImageUploader(Result.success("openapi/input_file.png"))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig().copy(imageInputNodeId = "10")),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = uploader,
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("img2img prompt")
        viewModel.onInputImageSelected(Uri.parse("content://media/1"), "picked.png")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(1, uploader.uploadCallCount)
        assertEquals("openapi/input_file.png", generationRepository.lastInput?.uploadedImageFileName)
        Dispatchers.resetMain()
    }

    @Test
    fun `retry reuses previously uploaded image fileName without reupload`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val uploader = FakeInputImageUploader(Result.success("openapi/input_file.png"))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig().copy(imageInputNodeId = "10")),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = uploader,
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("img2img prompt")
        viewModel.onInputImageSelected(Uri.parse("content://media/1"), "picked.png")
        viewModel.generate()
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, uploader.uploadCallCount)
        assertEquals(2, generationRepository.generateCallCount)
        assertEquals("openapi/input_file.png", generationRepository.lastInput?.uploadedImageFileName)
        Dispatchers.resetMain()
    }

    @Test
    fun `generate does not submit task when image upload fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle))
        val uploader = FakeInputImageUploader(Result.failure(IllegalStateException("Upload failed")))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig().copy(imageInputNodeId = "10")),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = uploader,
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("img2img prompt")
        viewModel.onInputImageSelected(Uri.parse("content://media/1"), "picked.png")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(1, uploader.uploadCallCount)
        assertEquals(0, generationRepository.generateCallCount)
        assertTrue(viewModel.uiState.value.generationState is GenerationState.Failed)
        Dispatchers.resetMain()
    }

    @Test
    fun `input image selection is hidden in unmapped mode and restored when returning`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig().copy(imageInputNodeId = "10")),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        val selectedUri = Uri.parse("content://media/image_mode")
        viewModel.onInputImageSelected(selectedUri, "image_mode.png")
        advanceUntilIdle()
        assertEquals(selectedUri, viewModel.uiState.value.selectedInputImageUri)

        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedInputImageUri)

        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        advanceUntilIdle()
        assertEquals(selectedUri, viewModel.uiState.value.selectedInputImageUri)
        Dispatchers.resetMain()
    }

    @Test
    fun `load restores persisted selection for current mode`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val restoredSelection = PersistedInputImageSelection(
            uri = Uri.parse("file:///persisted/image.png"),
            displayName = "persisted.png",
        )
        val selectionStore = FakeInputImageSelectionStore(
            initialSelections = PersistedInputImageSelections(
                imageMode = restoredSelection,
                videoMode = null,
            )
        )
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig().copy(imageInputNodeId = "10")),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = selectionStore,
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()

        assertEquals(restoredSelection.uri, viewModel.uiState.value.selectedInputImageUri)
        assertEquals(restoredSelection.displayName, viewModel.uiState.value.selectedInputImageDisplayName)
        Dispatchers.resetMain()
    }

    @Test
    fun `clear only removes current mode selection`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val imageSelection = PersistedInputImageSelection(
            uri = Uri.parse("file:///persisted/image.png"),
            displayName = "image.png",
        )
        val videoSelection = PersistedInputImageSelection(
            uri = Uri.parse("file:///persisted/video.png"),
            displayName = "video.png",
        )
        val selectionStore = FakeInputImageSelectionStore(
            initialSelections = PersistedInputImageSelections(
                imageMode = imageSelection,
                videoMode = videoSelection,
            )
        )
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfig().copy(
                    imageInputNodeId = "10",
                    videoImageInputNodeId = "13",
                )
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = selectionStore,
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(videoSelection.uri, viewModel.uiState.value.selectedInputImageUri)

        viewModel.onClearInputImage()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedInputImageUri)
        assertTrue(selectionStore.clearedModes.contains(GenerationMode.VIDEO))

        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        advanceUntilIdle()
        assertEquals(imageSelection.uri, viewModel.uiState.value.selectedInputImageUri)
        Dispatchers.resetMain()
    }

    @Test
    fun `different image selections are remembered per mode`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfig().copy(
                    imageInputNodeId = "10",
                    videoImageInputNodeId = "13",
                )
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        val imageModeUri = Uri.parse("content://media/image_mode")
        val videoModeUri = Uri.parse("content://media/video_mode")
        viewModel.onInputImageSelected(imageModeUri, "image_mode.png")
        advanceUntilIdle()
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        viewModel.onInputImageSelected(videoModeUri, "video_mode.png")
        advanceUntilIdle()

        viewModel.onGenerationModeChanged(GenerationMode.IMAGE)
        advanceUntilIdle()
        assertEquals(imageModeUri, viewModel.uiState.value.selectedInputImageUri)

        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(videoModeUri, viewModel.uiState.value.selectedInputImageUri)
        Dispatchers.resetMain()
    }

    @Test
    fun `onVideoInputImageSelectedFromAlbum writes video selection without changing selected mode`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val selectionStore = FakeInputImageSelectionStore()
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfigWithVideoMapping().copy(
                    videoImageInputNodeId = "13",
                )
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = selectionStore,
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        val uri = Uri.parse("file:///internal_album/tasks/task-a/out_1.jpg")
        viewModel.onVideoInputImageSelectedFromAlbum(uri, "out_1.jpg")
        advanceUntilIdle()

        assertEquals(GenerationMode.IMAGE, viewModel.uiState.value.selectedMode)
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(uri, viewModel.uiState.value.selectedInputImageUri)
        assertEquals("out_1.jpg", viewModel.uiState.value.selectedInputImageDisplayName)
        assertTrue(selectionStore.persistedModes.contains(GenerationMode.VIDEO))
        Dispatchers.resetMain()
    }

    @Test
    fun `onVideoInputImageSelectedFromAlbum overwrites previous video selection`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfigWithVideoMapping().copy(
                    videoImageInputNodeId = "13",
                )
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )

        advanceUntilIdle()
        val firstUri = Uri.parse("file:///internal_album/tasks/task-a/out_1.jpg")
        val secondUri = Uri.parse("file:///internal_album/tasks/task-a/out_2.jpg")
        viewModel.onVideoInputImageSelectedFromAlbum(firstUri, "out_1.jpg")
        viewModel.onVideoInputImageSelectedFromAlbum(secondUri, "out_2.jpg")
        advanceUntilIdle()

        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(secondUri, viewModel.uiState.value.selectedInputImageUri)
        assertEquals("out_2.jpg", viewModel.uiState.value.selectedInputImageDisplayName)
        Dispatchers.resetMain()
    }

    @Test
    fun `onVideoInputImageSelectedFromAlbum rejects when video image input nodeId is missing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val messages = mutableListOf<String>()
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfigWithVideoMapping()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )
        val collectJob = launch { viewModel.messages.collect { messages.add(it) } }

        advanceUntilIdle()
        val uri = Uri.parse("file:///internal_album/tasks/task-a/out_1.jpg")
        viewModel.onVideoInputImageSelectedFromAlbum(uri, "out_1.jpg")
        advanceUntilIdle()

        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedInputImageUri)
        assertTrue(messages.any { it.contains("Please configure video image input nodeId first.") })
        collectJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `onVideoInputImageSelectedFromAlbum keeps session selection when persistence fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val messages = mutableListOf<String>()
        val selectionStore = FakeInputImageSelectionStore(
            persistResultProvider = { mode, sourceUri, displayName ->
                if (mode == GenerationMode.VIDEO) {
                    Result.failure(IllegalStateException("disk full"))
                } else {
                    Result.success(
                        PersistedInputImageSelection(
                            uri = sourceUri,
                            displayName = displayName,
                        )
                    )
                }
            }
        )
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(
                validConfigWithVideoMapping().copy(videoImageInputNodeId = "13")
            ),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = selectionStore,
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )
        val collectJob = launch { viewModel.messages.collect { messages.add(it) } }

        advanceUntilIdle()
        val uri = Uri.parse("file:///internal_album/tasks/task-a/out_3.jpg")
        viewModel.onVideoInputImageSelectedFromAlbum(uri, "out_3.jpg")
        advanceUntilIdle()

        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
        advanceUntilIdle()
        assertEquals(uri, viewModel.uiState.value.selectedInputImageUri)
        assertTrue(messages.any { it.contains("Video input image could not be persisted") })
        collectJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `selection remains available when persistence fails and emits message`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val selectionStore = FakeInputImageSelectionStore(
            persistResultProvider = { _, _, _ ->
                Result.failure(IllegalStateException("disk full"))
            }
        )
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig().copy(imageInputNodeId = "10")),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = selectionStore,
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )
        val messages = mutableListOf<String>()
        val collectJob = launch { viewModel.messages.collect { messages.add(it) } }

        advanceUntilIdle()
        val selectedUri = Uri.parse("content://media/session_only")
        viewModel.onInputImageSelected(selectedUri, "session_only.png")
        advanceUntilIdle()

        assertEquals(selectedUri, viewModel.uiState.value.selectedInputImageUri)
        assertTrue(messages.any { it.contains("could not be persisted") })
        collectJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `generation success archives outputs and stores lastArchivedTaskId`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val successState = GenerationState.Success(
            taskId = "task-archive-1",
            results = listOf(
                io.github.c1921.comfyui_assistant.domain.GeneratedOutput(
                    fileUrl = "https://example.com/output.png",
                    fileType = "png",
                    nodeId = "9",
                ),
            ),
            promptTipsNodeErrors = null,
        )
        val generationRepository = FakeGenerationRepository(flowOf(successState))
        val albumRepository = FakeInternalAlbumRepository()
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = albumRepository,
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("archive me")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals(1, albumRepository.archiveCallCount)
        assertNotNull(albumRepository.lastRequestSnapshot)
        assertEquals("task-archive-1", viewModel.uiState.value.lastArchivedTaskId)
        Dispatchers.resetMain()
    }

    @Test
    fun `openLastArchivedTaskInAlbum emits archived taskId`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val successState = GenerationState.Success(
            taskId = "task-open-album-1",
            results = listOf(
                io.github.c1921.comfyui_assistant.domain.GeneratedOutput(
                    fileUrl = "https://example.com/output.png",
                    fileType = "png",
                    nodeId = "9",
                ),
            ),
            promptTipsNodeErrors = null,
        )
        val generationRepository = FakeGenerationRepository(flowOf(successState))
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = generationRepository,
            inputImageUploader = FakeInputImageUploader(),
            inputImageSelectionStore = FakeInputImageSelectionStore(),
            internalAlbumRepository = FakeInternalAlbumRepository(),
        )
        val emittedTaskIds = mutableListOf<String>()
        val collectJob = launch { viewModel.openAlbumRequests.collect { emittedTaskIds += it } }

        advanceUntilIdle()
        viewModel.onPromptChanged("open album")
        viewModel.generate()
        advanceUntilIdle()
        viewModel.openLastArchivedTaskInAlbum()
        advanceUntilIdle()

        assertTrue(emittedTaskIds.contains("task-open-album-1"))
        collectJob.cancel()
        Dispatchers.resetMain()
    }

    private fun validConfig(): WorkflowConfig {
        return WorkflowConfig(
            apiKey = "key",
            workflowId = "workflow",
            promptNodeId = "6",
            promptFieldName = "text",
        )
    }

    private fun validConfigWithSizeMapping(): WorkflowConfig {
        return validConfig().copy(
            sizeNodeId = "5",
        )
    }

    private fun validConfigWithVideoMapping(): WorkflowConfig {
        return validConfig().copy(
            videoWorkflowId = "video-workflow",
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
        )
    }

    private class FakeConfigRepository(
        private val config: WorkflowConfig,
    ) : ConfigRepository {
        override suspend fun loadConfig(): WorkflowConfig = config

        override suspend fun saveConfig(config: WorkflowConfig) = Unit

        override suspend fun clearApiKey() = Unit
    }

    private class FakeGenerationRepository(
        private val flow: Flow<GenerationState>,
    ) : GenerationRepository {
        var lastInput: GenerationInput? = null
        var generateCallCount: Int = 0

        override fun generateAndPoll(
            config: WorkflowConfig,
            input: GenerationInput,
        ): Flow<GenerationState> {
            generateCallCount += 1
            lastInput = input
            return flow
        }
    }

    private class FakeInputImageUploader(
        private val result: Result<String> = Result.success("openapi/default.png"),
    ) : InputImageUploader {
        var uploadCallCount: Int = 0

        override suspend fun uploadInputImage(
            apiKey: String,
            imageUri: Uri,
        ): Result<String> {
            uploadCallCount += 1
            return result
        }
    }

    private class FakeInputImageSelectionStore(
        initialSelections: PersistedInputImageSelections = PersistedInputImageSelections(
            imageMode = null,
            videoMode = null,
        ),
        private val persistResultProvider: (
            mode: GenerationMode,
            sourceUri: Uri,
            displayName: String,
        ) -> Result<PersistedInputImageSelection> = { _, sourceUri, displayName ->
            Result.success(
                PersistedInputImageSelection(
                    uri = sourceUri,
                    displayName = displayName,
                )
            )
        },
    ) : InputImageSelectionStore {
        private var selections = initialSelections
        val clearedModes = mutableListOf<GenerationMode>()
        val persistedModes = mutableListOf<GenerationMode>()

        override suspend fun loadSelections(): PersistedInputImageSelections = selections

        override suspend fun persistSelection(
            mode: GenerationMode,
            sourceUri: Uri,
            displayName: String,
        ): Result<PersistedInputImageSelection> {
            persistedModes += mode
            val result = persistResultProvider(mode, sourceUri, displayName)
            result.onSuccess { persistedSelection ->
                selections = when (mode) {
                    GenerationMode.IMAGE -> selections.copy(imageMode = persistedSelection)
                    GenerationMode.VIDEO -> selections.copy(videoMode = persistedSelection)
                }
            }
            return result
        }

        override suspend fun clearSelection(mode: GenerationMode) {
            clearedModes += mode
            selections = when (mode) {
                GenerationMode.IMAGE -> selections.copy(imageMode = null)
                GenerationMode.VIDEO -> selections.copy(videoMode = null)
            }
        }
    }

    private class FakeInternalAlbumRepository(
        private val archiveResultProvider: (
            requestSnapshot: GenerationRequestSnapshot,
            successState: GenerationState.Success,
            decodePassword: String,
        ) -> Result<AlbumSaveResult> = { _, successState, _ ->
            Result.success(
                AlbumSaveResult(
                    taskId = successState.taskId,
                    totalOutputs = successState.results.size,
                    successCount = successState.results.size,
                    failedCount = 0,
                    failures = emptyList(),
                ),
            )
        },
    ) : InternalAlbumRepository {
        val summariesFlow = MutableStateFlow<List<AlbumTaskSummary>>(emptyList())
        val mediaSummariesFlow = MutableStateFlow<List<AlbumMediaSummary>>(emptyList())
        var archiveCallCount: Int = 0
        var lastRequestSnapshot: GenerationRequestSnapshot? = null

        override suspend fun archiveGeneration(
            requestSnapshot: GenerationRequestSnapshot,
            successState: GenerationState.Success,
            decodePassword: String,
        ): Result<AlbumSaveResult> {
            archiveCallCount += 1
            lastRequestSnapshot = requestSnapshot
            return archiveResultProvider(requestSnapshot, successState, decodePassword)
        }

        override fun observeTaskSummaries(): Flow<List<AlbumTaskSummary>> = summariesFlow
        override fun observeMediaSummaries(): Flow<List<AlbumMediaSummary>> = mediaSummariesFlow

        override suspend fun loadTaskDetail(taskId: String): Result<AlbumTaskDetail> {
            return Result.failure(IllegalStateException("Not implemented in test fake."))
        }

        override suspend fun hasTask(taskId: String): Boolean = false

        override suspend fun findFirstImageKey(taskId: String): Result<AlbumMediaKey?> {
            return Result.success(null)
        }

        override suspend fun findFirstMediaKey(taskId: String): Result<AlbumMediaKey?> {
            return Result.success(null)
        }
    }
}

