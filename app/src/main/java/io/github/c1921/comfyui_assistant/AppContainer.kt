package io.github.c1921.comfyui_assistant

import android.content.Context
import io.github.c1921.comfyui_assistant.data.local.SecureConfigStore
import io.github.c1921.comfyui_assistant.data.network.RunningHubApiClient
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.ImageDownloader
import io.github.c1921.comfyui_assistant.data.repository.RunningHubGenerationRepository

class AppContainer(
    context: Context,
) {
    private val okHttpClient = RunningHubApiClient.createOkHttpClient()
    private val apiService = RunningHubApiClient.createService(okHttpClient)

    val configStore = SecureConfigStore(context)
    val generationRepository: GenerationRepository = RunningHubGenerationRepository(apiService)
    val imageDownloader = ImageDownloader(okHttpClient)
}
