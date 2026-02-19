package io.github.c1921.comfyui_assistant

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.c1921.comfyui_assistant.ui.UiTestTags
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class MainScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabsAndCoreButtonsAreVisible() {
        val generateNode = composeRule.onNodeWithTag(UiTestTags.GENERATE_BUTTON).fetchSemanticsNode()
        assertNotNull(generateNode)
        composeRule.onNodeWithTag(UiTestTags.TAB_ALBUM).performClick()
        composeRule.onNodeWithText("No archived media yet.").fetchSemanticsNode()
        composeRule.onNodeWithText("Settings").performClick()
        val saveNode = composeRule.onNodeWithTag(UiTestTags.SAVE_SETTINGS_BUTTON).fetchSemanticsNode()
        assertNotNull(saveNode)
    }
}
