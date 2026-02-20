package io.github.c1921.comfyui_assistant.feature.album

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import io.github.c1921.comfyui_assistant.domain.AlbumDecodeOutcomeCode
import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.ui.UiTestTags
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlbumScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(ApplicationProvider.getApplicationContext()).build()
    }

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
                imageLoader = imageLoader,
                onOpenMedia = {},
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_MEDIA_GRID).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_MEDIA_ITEM).assertCountEquals(2)
    }

    @Test
    fun imageDetail_swipeUpShowsMetadataSheet() {
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
            mediaList = listOf(mediaSummary(taskId = "task-image-1", index = 1, kind = OutputMediaKind.IMAGE)),
            selectedMediaKey = AlbumMediaKey("task-image-1", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = mediaItem,
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            var uiState by remember { mutableStateOf(state) }
            AlbumScreen(
                state = uiState,
                imageLoader = imageLoader,
                onOpenMedia = { key -> uiState = uiState.copy(selectedMediaKey = key) },
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_METADATA_SHEET).assertCountEquals(0)
        composeRule.onNodeWithTag(UiTestTags.ALBUM_METADATA_SWIPE_ZONE).performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(UiTestTags.ALBUM_METADATA_SHEET).assertIsDisplayed()
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
            mediaList = listOf(mediaSummary(taskId = "task-video-1", index = 1, kind = OutputMediaKind.VIDEO)),
            selectedMediaKey = AlbumMediaKey("task-video-1", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = mediaItem,
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            AlbumScreen(
                state = state,
                imageLoader = imageLoader,
                onOpenMedia = {},
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_VIDEO).assertIsDisplayed()
    }

    @Test
    fun detailPager_swipeLeft_opensNextMedia() {
        val media1Path = "tasks/task-swipe/out_1.jpg"
        val media2Path = "tasks/task-swipe/out_2.jpg"
        prepareInternalAlbumFile(media1Path)
        prepareInternalAlbumFile(media2Path)
        val item1 = mediaItem(
            taskId = "task-swipe",
            index = 1,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = media1Path,
        )
        val item2 = mediaItem(
            taskId = "task-swipe",
            index = 2,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = media2Path,
        )
        val detail = taskDetail(taskId = "task-swipe", mediaItems = listOf(item1, item2))
        val initialState = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = "task-swipe", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-swipe", index = 2, kind = OutputMediaKind.IMAGE),
            ),
            selectedMediaKey = AlbumMediaKey("task-swipe", 2),
            selectedTaskDetail = detail,
            selectedMediaItem = item2,
            isMetadataExpanded = false,
        )
        val openedKeys = mutableListOf<AlbumMediaKey>()

        composeRule.setContent {
            var uiState by remember { mutableStateOf(initialState) }
            AlbumScreen(
                state = uiState,
                imageLoader = imageLoader,
                onOpenMedia = { key ->
                    openedKeys += key
                    uiState = uiState.copy(
                        selectedMediaKey = key,
                        selectedMediaItem = if (key.index == 1) item1 else item2,
                    )
                },
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals(AlbumMediaKey("task-swipe", 1), openedKeys.last())
        }
    }

    @Test
    fun detailPager_onFirstPage_swipeRight_stopsAtBoundary() {
        val media1Path = "tasks/task-edge/out_1.jpg"
        val media2Path = "tasks/task-edge/out_2.jpg"
        prepareInternalAlbumFile(media1Path)
        prepareInternalAlbumFile(media2Path)
        val item2 = mediaItem(
            taskId = "task-edge",
            index = 2,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = media2Path,
        )
        val initialState = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = "task-edge", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-edge", index = 2, kind = OutputMediaKind.IMAGE),
            ),
            selectedMediaKey = AlbumMediaKey("task-edge", 2),
            selectedTaskDetail = taskDetail(taskId = "task-edge", mediaItems = listOf(item2)),
            selectedMediaItem = item2,
            isMetadataExpanded = false,
        )
        var openCalls = 0

        composeRule.setContent {
            var uiState by remember { mutableStateOf(initialState) }
            AlbumScreen(
                state = uiState,
                imageLoader = imageLoader,
                onOpenMedia = { key ->
                    openCalls += 1
                    uiState = uiState.copy(selectedMediaKey = key)
                },
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_PAGER).performTouchInput { swipeRight() }
        composeRule.runOnIdle {
            assertEquals(0, openCalls)
        }
    }

    @Test
    fun imageDetail_sendToVideoInputButton_isVisibleInMetadataSheet_andInvokesCallback() {
        val localRelativePath = "tasks/task-send-1/out_1.jpg"
        val localFile = prepareInternalAlbumFile(localRelativePath)
        val mediaItem = mediaItem(
            taskId = "task-send-1",
            index = 1,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = localRelativePath,
        )
        val detail = taskDetail(
            taskId = "task-send-1",
            mediaItems = listOf(mediaItem),
        )
        val state = AlbumUiState(
            mediaList = listOf(mediaSummary(taskId = "task-send-1", index = 1, kind = OutputMediaKind.IMAGE)),
            selectedMediaKey = AlbumMediaKey("task-send-1", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = mediaItem,
            isMetadataExpanded = false,
        )
        var sentUri: Uri? = null
        var sentDisplayName: String? = null

        composeRule.setContent {
            var uiState by remember { mutableStateOf(state) }
            AlbumScreen(
                state = uiState,
                imageLoader = imageLoader,
                onOpenMedia = { key -> uiState = uiState.copy(selectedMediaKey = key) },
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { uri, displayName ->
                    sentUri = uri
                    sentDisplayName = displayName
                },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_METADATA_SWIPE_ZONE).performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(UiTestTags.ALBUM_SEND_TO_VIDEO_INPUT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_SEND_TO_VIDEO_INPUT_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(Uri.fromFile(localFile), sentUri)
            assertEquals(localFile.name, sentDisplayName)
        }
    }

    @Test
    fun videoDetail_sendToVideoInputButton_isHiddenInMetadataSheet() {
        val localRelativePath = "tasks/task-send-video-1/out_1.mp4"
        prepareInternalAlbumFile(localRelativePath)
        val mediaItem = mediaItem(
            taskId = "task-send-video-1",
            index = 1,
            kind = OutputMediaKind.VIDEO,
            localRelativePath = localRelativePath,
        )
        val detail = taskDetail(
            taskId = "task-send-video-1",
            mediaItems = listOf(mediaItem),
        )
        val state = AlbumUiState(
            mediaList = listOf(mediaSummary(taskId = "task-send-video-1", index = 1, kind = OutputMediaKind.VIDEO)),
            selectedMediaKey = AlbumMediaKey("task-send-video-1", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = mediaItem,
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            var uiState by remember { mutableStateOf(state) }
            AlbumScreen(
                state = uiState,
                imageLoader = imageLoader,
                onOpenMedia = { key -> uiState = uiState.copy(selectedMediaKey = key) },
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_METADATA_SWIPE_ZONE).performTouchInput { swipeUp() }
        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_SEND_TO_VIDEO_INPUT_BUTTON).assertCountEquals(0)
    }

    @Test
    fun mediaGrid_largeMixedList_scrollAndOpenItem_staysInteractive() {
        val mediaList = (1..60).map { index ->
            val kind = if (index % 3 == 0) OutputMediaKind.VIDEO else OutputMediaKind.IMAGE
            mediaSummary(taskId = "task-large", index = index, kind = kind)
        }
        var openedKey: AlbumMediaKey? = null

        composeRule.setContent {
            AlbumScreen(
                state = AlbumUiState(mediaList = mediaList),
                imageLoader = imageLoader,
                onOpenMedia = { key -> openedKey = key },
                onBackToList = {},
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_MEDIA_GRID).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_MEDIA_GRID).performScrollToIndex(59)
        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_MEDIA_ITEM).onFirst().performClick()
        composeRule.runOnIdle {
            assertEquals(true, openedKey != null)
        }
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
