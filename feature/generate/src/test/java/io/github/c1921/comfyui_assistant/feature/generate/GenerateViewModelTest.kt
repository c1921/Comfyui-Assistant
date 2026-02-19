package io.github.c1921.comfyui_assistant.feature.generate

import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.repository.DownloadToGalleryResult
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.MediaSaver
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.InMemoryConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
            mediaSaver = FakeMediaSaver(),
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
            mediaSaver = FakeMediaSaver(),
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
            mediaSaver = FakeMediaSaver(),
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
    fun `isGenerateEnabled is false for non 1 to 1 when size mapping missing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            mediaSaver = FakeMediaSaver(),
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
            mediaSaver = FakeMediaSaver(),
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
            mediaSaver = FakeMediaSaver(),
        )

        advanceUntilIdle()
        viewModel.onPromptChanged("hello")
        viewModel.onGenerationModeChanged(GenerationMode.VIDEO)
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
            mediaSaver = FakeMediaSaver(),
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

        override fun generateAndPoll(
            config: WorkflowConfig,
            input: GenerationInput,
        ): Flow<GenerationState> {
            lastInput = input
            return flow
        }
    }

    private class FakeMediaSaver : MediaSaver {
        override suspend fun saveToGallery(
            output: GeneratedOutput,
            taskId: String,
            index: Int,
            decodePassword: String,
        ): Result<DownloadToGalleryResult> {
            return Result.success(
                DownloadToGalleryResult(
                    fileName = "fake.png",
                    savedKind = OutputMediaKind.IMAGE,
                    decodeOutcome = null,
                )
            )
        }
    }
}
