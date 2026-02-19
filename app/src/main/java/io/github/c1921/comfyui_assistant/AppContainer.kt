package io.github.c1921.comfyui_assistant

import android.content.Context
import coil.ImageLoader
import io.github.c1921.comfyui_assistant.data.local.ConfigRepository
import io.github.c1921.comfyui_assistant.data.local.SecureConfigStore
import io.github.c1921.comfyui_assistant.data.decoder.coil.DuckAutoDecodeDecoder
import io.github.c1921.comfyui_assistant.data.network.RunningHubApiClient
import io.github.c1921.comfyui_assistant.data.repository.DuckPreviewMediaResolver
import io.github.c1921.comfyui_assistant.data.repository.GenerationRepository
import io.github.c1921.comfyui_assistant.data.repository.ImageDownloader
import io.github.c1921.comfyui_assistant.data.repository.InputImageUploader
import io.github.c1921.comfyui_assistant.data.repository.MediaSaver
import io.github.c1921.comfyui_assistant.data.repository.PreviewMediaResolver
import io.github.c1921.comfyui_assistant.data.repository.RunningHubInputImageUploader
import io.github.c1921.comfyui_assistant.data.repository.RunningHubGenerationRepository
import io.github.c1921.comfyui_assistant.domain.ConfigDraftStore
import io.github.c1921.comfyui_assistant.domain.InMemoryConfigDraftStore

class AppContainer(
    context: Context,
) {
    private val okHttpClient = RunningHubApiClient.createOkHttpClient()
    private val apiService = RunningHubApiClient.createService(okHttpClient)

    val configRepository: ConfigRepository = SecureConfigStore(context)
    val configDraftStore: ConfigDraftStore = InMemoryConfigDraftStore()
    val generationRepository: GenerationRepository = RunningHubGenerationRepository(apiService)
    val inputImageUploader: InputImageUploader = RunningHubInputImageUploader(context, apiService)
    val mediaSaver: MediaSaver = ImageDownloader(context, okHttpClient)
    val previewMediaResolver: PreviewMediaResolver = DuckPreviewMediaResolver(context, okHttpClient)
    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(okHttpClient)
        .components {
            add(DuckAutoDecodeDecoder.Factory())
        }
        .build()
}
