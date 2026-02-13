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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun `saveSettings does not persist when negative mapping is incomplete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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

        assertFalse(repository.saveCalled)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings persists when size nodeId is provided`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onSizeNodeIdChanged("5")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals("5", repository.currentConfig.sizeNodeId)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings persists with size nodeId`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onSizeNodeIdChanged("5")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals("5", repository.currentConfig.sizeNodeId)
        Dispatchers.resetMain()
    }

    private class FakeConfigRepository(
        initialConfig: WorkflowConfig = WorkflowConfig(),
    ) : ConfigRepository {
        var currentConfig = initialConfig
            private set
        var clearCalled: Boolean = false
        var saveCalled: Boolean = false

        override suspend fun loadConfig(): WorkflowConfig = currentConfig

        override suspend fun saveConfig(config: WorkflowConfig) {
            saveCalled = true
            currentConfig = config
        }

        override suspend fun clearApiKey() {
            clearCalled = true
            currentConfig = currentConfig.copy(apiKey = "")
        }
    }
}
