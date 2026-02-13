package io.github.c1921.comfyui_assistant.feature.settings

import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.domain.InMemoryConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `saveSettings does not persist when negative mapping is incomplete`() = runTest {
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onNegativeNodeIdChanged("7")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(!repository.saveCalled)
        Dispatchers.resetMain()
    }

    private class FakeConfigRepository(
        initialConfig: WorkflowConfig = WorkflowConfig(),
    ) : ConfigRepository {
        private var config = initialConfig
        var clearCalled: Boolean = false
        var saveCalled: Boolean = false

        override suspend fun loadConfig(): WorkflowConfig = config

        override suspend fun saveConfig(config: WorkflowConfig) {
            saveCalled = true
            this.config = config
        }

        override suspend fun clearApiKey() {
            clearCalled = true
            config = config.copy(apiKey = "")
        }
    }
}
