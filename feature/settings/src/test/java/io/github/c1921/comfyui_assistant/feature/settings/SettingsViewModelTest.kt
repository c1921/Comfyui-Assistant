package io.github.c1921.comfyui_assistant.feature.settings

import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.local.ConfigSyncResult
import io.github.c1921.comfyui_assistant.data.local.ConfigSyncTrigger
import io.github.c1921.comfyui_assistant.data.local.WebDavSyncRepository
import io.github.c1921.comfyui_assistant.domain.InMemoryConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
    fun `saveSettings persists image input node mappings`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onImageInputNodeIdChanged("10")
        viewModel.onVideoImageInputNodeIdChanged("13")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals("10", repository.currentConfig.imageInputNodeId)
        assertEquals("13", repository.currentConfig.videoImageInputNodeId)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings persists video length nodeId`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onVideoLengthNodeIdChanged("31")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals("31", repository.currentConfig.videoLengthNodeId)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings does not persist when video mapping is incomplete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onVideoWorkflowIdChanged("video-workflow")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertFalse(repository.saveCalled)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings persists when video mapping is complete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.onVideoWorkflowIdChanged("video-workflow")
        viewModel.onVideoPromptNodeIdChanged("12")
        viewModel.onVideoPromptFieldNameChanged("text")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals("video-workflow", repository.currentConfig.videoWorkflowId)
        assertEquals("12", repository.currentConfig.videoPromptNodeId)
        assertEquals("text", repository.currentConfig.videoPromptFieldName)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings persists when video mapping is empty`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
        )

        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals("", repository.currentConfig.videoWorkflowId)
        assertEquals("", repository.currentConfig.videoPromptNodeId)
        assertEquals("", repository.currentConfig.videoPromptFieldName)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings triggers WebDAV sync after local save`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val syncRepository = FakeWebDavSyncRepository(
            resultProvider = { ConfigSyncResult.Pushed(remotePath = "https://example.com/config.v1.enc.json") },
        )
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
            webDavSyncRepository = syncRepository,
        )

        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertEquals(listOf(ConfigSyncTrigger.SETTINGS_SAVE), syncRepository.triggers)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings still succeeds when WebDAV sync fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val syncRepository = FakeWebDavSyncRepository(
            resultProvider = { ConfigSyncResult.Failed(message = "timeout") },
        )
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
            webDavSyncRepository = syncRepository,
        )
        val messages = mutableListOf<String>()
        val collectJob = launch { viewModel.messages.collect { messages += it } }

        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(repository.saveCalled)
        assertTrue(messages.any { it.contains("Configuration saved.") })
        assertTrue(messages.any { it.contains("WebDAV sync failed: timeout") })
        collectJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `saveSettings applies pulled config from WebDAV sync`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeConfigRepository()
        val remoteConfig = WorkflowConfig(
            workflowId = "remote-workflow",
            promptNodeId = "22",
            promptFieldName = "text",
        )
        val syncRepository = FakeWebDavSyncRepository(
            resultProvider = { ConfigSyncResult.Pulled(config = remoteConfig) },
        )
        val viewModel = SettingsViewModel(
            configRepository = repository,
            configDraftStore = InMemoryConfigDraftStore(),
            webDavSyncRepository = syncRepository,
        )

        advanceUntilIdle()
        viewModel.onWorkflowIdChanged("local-workflow")
        advanceUntilIdle()
        viewModel.saveSettings()
        advanceUntilIdle()

        assertEquals("remote-workflow", viewModel.uiState.value.workflowId)
        assertEquals("22", viewModel.uiState.value.promptNodeId)
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

    private class FakeWebDavSyncRepository(
        private val resultProvider: (ConfigSyncTrigger) -> ConfigSyncResult = {
            ConfigSyncResult.Skipped(reason = "disabled")
        },
    ) : WebDavSyncRepository {
        val triggers = mutableListOf<ConfigSyncTrigger>()

        override suspend fun syncConfig(trigger: ConfigSyncTrigger): ConfigSyncResult {
            triggers += trigger
            return resultProvider(trigger)
        }
    }
}
