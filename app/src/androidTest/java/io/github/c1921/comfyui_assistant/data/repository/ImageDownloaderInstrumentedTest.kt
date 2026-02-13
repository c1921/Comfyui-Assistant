package io.github.c1921.comfyui_assistant.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ImageDownloaderInstrumentedTest {
    @Test
    fun saveToGallery_returnsFailure_whenEndpointUnavailable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()
        val downloader = ImageDownloader(context, httpClient)

        val result = downloader.saveToGallery(
            output = GeneratedOutput(
                fileUrl = "http://127.0.0.1:1/unreachable.png",
                fileType = "png",
                nodeId = null,
            ),
            taskId = "task-1",
            index = 1,
            decodePassword = "",
        )

        assertTrue(result.isFailure)
    }
}
