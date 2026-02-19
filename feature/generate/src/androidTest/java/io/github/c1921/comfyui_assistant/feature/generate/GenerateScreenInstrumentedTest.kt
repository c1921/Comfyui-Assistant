package io.github.c1921.comfyui_assistant.feature.generate

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import io.github.c1921.comfyui_assistant.data.repository.PreviewMediaResolution
import io.github.c1921.comfyui_assistant.data.repository.PreviewMediaResolver
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.ui.UiTestTags
import org.junit.Rule
import org.junit.Test

class GenerateScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retryButton_isVisible_whenStateIsFailed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val state = GenerateUiState(
            generationState = GenerationState.Failed(
                taskId = "task-1",
                errorCode = "805",
                message = "failed",
                failedReason = null,
                promptTipsNodeErrors = null,
            ),
        )

        composeRule.setContent {
            GenerateScreen(
                state = state,
                isGenerateEnabled = true,
                onPromptChanged = {},
                onNegativeChanged = {},
                onGenerationModeChanged = {},
                onImagePresetChanged = {},
                onGenerate = {},
                onRetry = {},
                imageLoader = imageLoader,
                previewMediaResolver = passthroughResolver(),
                onDownloadResult = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun modeSwitch_isVisible_andVideoModeHidesRatioSelector() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val state = GenerateUiState(
            selectedMode = GenerationMode.VIDEO,
            selectedImagePreset = ImageAspectPreset.RATIO_16_9,
        )

        composeRule.setContent {
            GenerateScreen(
                state = state,
                isGenerateEnabled = false,
                onPromptChanged = {},
                onNegativeChanged = {},
                onGenerationModeChanged = {},
                onImagePresetChanged = {},
                onGenerate = {},
                onRetry = {},
                imageLoader = imageLoader,
                previewMediaResolver = passthroughResolver(),
                onDownloadResult = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.GEN_MODE_IMAGE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.GEN_MODE_VIDEO_BUTTON).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.RATIO_SELECTOR).assertCountEquals(0)
        composeRule.onNodeWithText(
            "Complete API key, video workflowId, video prompt nodeId and video prompt fieldName first."
        ).assertIsDisplayed()
    }

    @Test
    fun mixedOutputs_renderImageAndVideoCards() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val state = GenerateUiState(
            selectedMode = GenerationMode.VIDEO,
            generationState = GenerationState.Success(
                taskId = "task-2",
                results = listOf(
                    GeneratedOutput(
                        fileUrl = "https://example.com/output.png",
                        fileType = "png",
                        nodeId = "1",
                    ),
                    GeneratedOutput(
                        fileUrl = "https://example.com/output.mp4",
                        fileType = "mp4",
                        nodeId = "2",
                    ),
                ),
                promptTipsNodeErrors = null,
            ),
        )

        composeRule.setContent {
            GenerateScreen(
                state = state,
                isGenerateEnabled = true,
                onPromptChanged = {},
                onNegativeChanged = {},
                onGenerationModeChanged = {},
                onImagePresetChanged = {},
                onGenerate = {},
                onRetry = {},
                imageLoader = imageLoader,
                previewMediaResolver = passthroughResolver(),
                onDownloadResult = { _, _ -> },
            )
        }

        composeRule.onNodeWithText("URL: https://example.com/output.png").assertIsDisplayed()
        composeRule.onNodeWithText("URL: https://example.com/output.mp4").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.VIDEO_RESULT_PLAYER).assertIsDisplayed()
    }

    @Test
    fun duckVideoResolvedFromImageOutput_rendersVideoPlayer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val state = GenerateUiState(
            selectedMode = GenerationMode.VIDEO,
            generationState = GenerationState.Success(
                taskId = "task-3",
                results = listOf(
                    GeneratedOutput(
                        fileUrl = "https://example.com/duck_payload.png",
                        fileType = "png",
                        nodeId = "3",
                    ),
                ),
                promptTipsNodeErrors = null,
            ),
        )

        composeRule.setContent {
            GenerateScreen(
                state = state,
                isGenerateEnabled = true,
                onPromptChanged = {},
                onNegativeChanged = {},
                onGenerationModeChanged = {},
                onImagePresetChanged = {},
                onGenerate = {},
                onRetry = {},
                imageLoader = imageLoader,
                previewMediaResolver = resolverAsDecodedVideo(),
                onDownloadResult = { _, _ -> },
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UiTestTags.VIDEO_RESULT_PLAYER).assertIsDisplayed()
    }

    private fun passthroughResolver(): PreviewMediaResolver {
        return object : PreviewMediaResolver {
            override suspend fun resolve(
                output: GeneratedOutput,
                decodePassword: String,
            ): PreviewMediaResolution {
                return PreviewMediaResolution(
                    kind = output.detectMediaKind(),
                    playbackUrl = output.fileUrl,
                    isDecodedFromDuck = false,
                )
            }
        }
    }

    private fun resolverAsDecodedVideo(): PreviewMediaResolver {
        return object : PreviewMediaResolver {
            override suspend fun resolve(
                output: GeneratedOutput,
                decodePassword: String,
            ): PreviewMediaResolution {
                return PreviewMediaResolution(
                    kind = OutputMediaKind.VIDEO,
                    playbackUrl = "file:///data/local/tmp/duck_preview.mp4",
                    isDecodedFromDuck = true,
                )
            }
        }
    }
}
