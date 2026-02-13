package io.github.c1921.comfyui_assistant.feature.generate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
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
                onImagePresetChanged = {},
                onGenerate = {},
                onRetry = {},
                imageLoader = imageLoader,
                onDownloadResult = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun ratioSelector_andMissingMappingHint_areVisible_forNonSquarePreset() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val state = GenerateUiState(
            selectedImagePreset = ImageAspectPreset.RATIO_16_9,
        )

        composeRule.setContent {
            GenerateScreen(
                state = state,
                isGenerateEnabled = false,
                onPromptChanged = {},
                onNegativeChanged = {},
                onImagePresetChanged = {},
                onGenerate = {},
                onRetry = {},
                imageLoader = imageLoader,
                onDownloadResult = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.RATIO_SELECTOR).assertIsDisplayed()
        composeRule.onNodeWithText("Selected ratio requires width/height mapping in Settings.")
            .assertIsDisplayed()
    }
}
