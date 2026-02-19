package io.github.c1921.comfyui_assistant.feature.album

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.c1921.comfyui_assistant.domain.AlbumDecodeOutcomeCode
import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.ui.UiTestTags
import org.junit.Rule
import org.junit.Test
import java.io.File

class AlbumScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediaGrid_rendersItemsAndVideoBadge() {
        val state = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = "task-grid-1", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-grid-2", index = 1, kind = OutputMediaKind.VIDEO),
            ),
        )

        composeRule.setContent {
            AlbumScreen(
                state = state,
                onOpenMedia = {},
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_MEDIA_GRID).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_MEDIA_ITEM).assertCountEquals(2)
        composeRule.onNodeWithTag(UiTestTags.ALBUM_VIDEO_BADGE).assertIsDisplayed()
    }

    @Test
    fun imageDetail_supportsMetadataToggle() {
        val localRelativePath = "tasks/task-image-1/out_1.jpg"
        prepareInternalAlbumFile(localRelativePath)
        val mediaItem = mediaItem(
            taskId = "task-image-1",
            index = 1,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = localRelativePath,
        )
        val detail = taskDetail(
            taskId = "task-image-1",
            mediaItems = listOf(mediaItem),
        )
        val state = AlbumUiState(
            selectedMediaKey = AlbumMediaKey("task-image-1", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = mediaItem,
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            var isMetadataExpanded by remember { mutableStateOf(false) }
            AlbumScreen(
                state = state.copy(isMetadataExpanded = isMetadataExpanded),
                onOpenMedia = {},
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = { isMetadataExpanded = !isMetadataExpanded },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).assertIsDisplayed()
        composeRule.onAllNodesWithText("taskId: task-image-1").assertCountEquals(0)
        composeRule.onNodeWithTag(UiTestTags.ALBUM_METADATA_TOGGLE).performClick()
        composeRule.onNodeWithText("taskId: task-image-1").assertIsDisplayed()
    }

    @Test
    fun videoDetail_showsVideoPlayer() {
        val localRelativePath = "tasks/task-video-1/out_1.mp4"
        prepareInternalAlbumFile(localRelativePath)
        val mediaItem = mediaItem(
            taskId = "task-video-1",
            index = 1,
            kind = OutputMediaKind.VIDEO,
            localRelativePath = localRelativePath,
        )
        val detail = taskDetail(
            taskId = "task-video-1",
            mediaItems = listOf(mediaItem),
        )
        val state = AlbumUiState(
            selectedMediaKey = AlbumMediaKey("task-video-1", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = mediaItem,
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            AlbumScreen(
                state = state,
                onOpenMedia = {},
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_VIDEO).assertIsDisplayed()
    }

    private fun mediaSummary(
        taskId: String,
        index: Int,
        kind: OutputMediaKind,
    ): AlbumMediaSummary {
        return AlbumMediaSummary(
            key = AlbumMediaKey(taskId = taskId, index = index),
            createdAtEpochMs = 2000L + index,
            savedAtEpochMs = 1000L,
            savedMediaKind = kind,
            localRelativePath = "tasks/$taskId/out_$index.${if (kind == OutputMediaKind.VIDEO) "mp4" else "jpg"}",
            mimeType = if (kind == OutputMediaKind.VIDEO) "video/mp4" else "image/jpeg",
            workflowId = "wf",
            prompt = "prompt",
        )
    }

    private fun mediaItem(
        taskId: String,
        index: Int,
        kind: OutputMediaKind,
        localRelativePath: String,
    ): AlbumMediaItem {
        return AlbumMediaItem(
            index = index,
            sourceFileUrl = "https://example.com/$taskId/$index",
            sourceFileType = if (kind == OutputMediaKind.VIDEO) "mp4" else "png",
            sourceNodeId = "1",
            savedMediaKind = kind,
            localRelativePath = localRelativePath,
            extension = if (kind == OutputMediaKind.VIDEO) "mp4" else "jpg",
            mimeType = if (kind == OutputMediaKind.VIDEO) "video/mp4" else "image/jpeg",
            fileSizeBytes = 16,
            decodedFromDuck = false,
            decodeOutcomeCode = AlbumDecodeOutcomeCode.NOT_ATTEMPTED,
            createdAtEpochMs = 2000L + index,
        )
    }

    private fun taskDetail(
        taskId: String,
        mediaItems: List<AlbumMediaItem>,
    ): AlbumTaskDetail {
        return AlbumTaskDetail(
            schemaVersion = 1,
            taskId = taskId,
            requestSentAtEpochMs = 100,
            savedAtEpochMs = 200,
            generationMode = GenerationMode.IMAGE,
            workflowId = "wf",
            prompt = "prompt",
            negative = "negative",
            imagePreset = null,
            videoLengthFrames = null,
            uploadedImageFileName = null,
            nodeInfoList = emptyList(),
            promptTipsNodeErrors = null,
            totalOutputs = mediaItems.size,
            savedCount = mediaItems.size,
            failedCount = 0,
            failures = emptyList(),
            mediaItems = mediaItems,
        )
    }

    private fun prepareInternalAlbumFile(relativePath: String): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = File(context.filesDir, "internal_album/$relativePath")
        target.parentFile?.mkdirs()
        target.writeBytes(byteArrayOf(0x00, 0x01, 0x02))
        return target
    }
}
