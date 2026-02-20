package io.github.c1921.comfyui_assistant.data.local

import android.app.Application
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SecureConfigStoreWebDavSyncTest {
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `sync creates remote directory when GET returns 409 then pushes config`() {
        val store = createStore()
        saveSyncConfig(store)
        mockWebServer.enqueue(MockResponse().setResponseCode(409))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val result = runBlocking {
            store.syncConfig(ConfigSyncTrigger.SETTINGS_SAVE)
        }

        assertTrue(result is ConfigSyncResult.Pushed)
        assertEquals(3, mockWebServer.requestCount)
        assertEquals("GET", mockWebServer.takeRequest().method)
        assertEquals("MKCOL", mockWebServer.takeRequest().method)
        assertEquals("PUT", mockWebServer.takeRequest().method)
    }

    @Test
    fun `sync treats MKCOL 405 as success after GET 409 and pushes config`() {
        val store = createStore()
        saveSyncConfig(store)
        mockWebServer.enqueue(MockResponse().setResponseCode(409))
        mockWebServer.enqueue(MockResponse().setResponseCode(405))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val result = runBlocking {
            store.syncConfig(ConfigSyncTrigger.SETTINGS_SAVE)
        }

        assertTrue(result is ConfigSyncResult.Pushed)
        assertEquals(3, mockWebServer.requestCount)
        assertEquals("GET", mockWebServer.takeRequest().method)
        assertEquals("MKCOL", mockWebServer.takeRequest().method)
        assertEquals("PUT", mockWebServer.takeRequest().method)
    }

    @Test
    fun `sync returns failed when MKCOL fails after GET 409`() {
        val store = createStore()
        saveSyncConfig(store)
        mockWebServer.enqueue(MockResponse().setResponseCode(409))
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val result = runBlocking {
            store.syncConfig(ConfigSyncTrigger.SETTINGS_SAVE)
        }

        assertTrue(result is ConfigSyncResult.Failed)
        val failed = result as ConfigSyncResult.Failed
        assertTrue(failed.message.contains("403"))
        assertEquals(2, mockWebServer.requestCount)
        assertEquals("GET", mockWebServer.takeRequest().method)
        assertEquals("MKCOL", mockWebServer.takeRequest().method)
    }

    @Test
    fun `sync still pushes config when GET returns 404`() {
        val store = createStore()
        saveSyncConfig(store)
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setResponseCode(201))

        val result = runBlocking {
            store.syncConfig(ConfigSyncTrigger.SETTINGS_SAVE)
        }

        assertTrue(result is ConfigSyncResult.Pushed)
        assertEquals(2, mockWebServer.requestCount)
        assertEquals("GET", mockWebServer.takeRequest().method)
        assertEquals("PUT", mockWebServer.takeRequest().method)
    }

    private fun createStore(): SecureConfigStore {
        val appContext = RuntimeEnvironment.getApplication() as Application
        return SecureConfigStore(
            context = appContext,
            webDavHttpClient = OkHttpClient(),
        )
    }

    private fun saveSyncConfig(store: SecureConfigStore) {
        val baseUrl = mockWebServer.url("/dav-root").toString().trimEnd('/')
        runBlocking {
            store.saveConfig(
                WorkflowConfig(
                    apiKey = "api-key",
                    workflowId = "workflow-id",
                    promptNodeId = "6",
                    promptFieldName = "text",
                    webDavEnabled = true,
                    webDavServerUrl = baseUrl,
                    webDavUsername = "user",
                    webDavPassword = "password",
                    webDavSyncPassphrase = "sync-passphrase",
                ),
            )
        }
    }
}

