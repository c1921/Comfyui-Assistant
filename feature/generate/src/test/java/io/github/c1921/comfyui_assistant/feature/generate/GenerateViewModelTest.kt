package io.github.c1921.comfyui_assistant.feature.generate

import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.repository.DownloadToGalleryResult
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.MediaSaver
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.InMemoryConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `retry without previous task keeps idle state`() = runTest {
        Dispatchers.setMain(dispatcher)
        val viewModel = GenerateViewModel(
            configRepository = FakeConfigRepository(validConfig()),
            configDraftStore = InMemoryConfigDraftStore(),
            generationRepository = FakeGenerationRepository(flowOf(GenerationState.Idle)),
            mediaSaver = FakeMediaSaver(),
        )

        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.generationState is GenerationState.Idle)
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
        override fun generateAndPoll(
            config: WorkflowConfig,
            input: GenerationInput,
        ): Flow<GenerationState> = flow
    }

    private class FakeMediaSaver : MediaSaver {
        override suspend fun saveToGallery(
            output: GeneratedOutput,
            taskId: String,
            index: Int,
            decodePassword: String,
        ): Result<DownloadToGalleryResult> {
            error("Not needed in this test")
        }
    }
}
