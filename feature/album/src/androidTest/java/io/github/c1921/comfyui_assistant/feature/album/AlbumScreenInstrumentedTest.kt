package io.github.c1921.comfyui_assistant.feature.album

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
                onDeleteMedia = { _, _ -> },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_MEDIA_GRID).assertIsDisplayed()
        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_MEDIA_ITEM).assertCountEquals(2)
    }

    @Test
    fun mediaGrid_hidesTimestampLabelForImageAndVideoItems() {
        val imageMedia = mediaSummary(taskId = "task-grid-hide-ts-1", index = 1, kind = OutputMediaKind.IMAGE)
        val videoMedia = mediaSummary(taskId = "task-grid-hide-ts-2", index = 2, kind = OutputMediaKind.VIDEO)
        val formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        val imageTimestamp = formatter.format(Instant.ofEpochMilli(imageMedia.createdAtEpochMs))
        val videoTimestamp = formatter.format(Instant.ofEpochMilli(videoMedia.createdAtEpochMs))

        composeRule.setContent {
            AlbumScreen(
                state = AlbumUiState(mediaList = listOf(imageMedia, videoMedia)),
                imageLoader = imageLoader,
                onOpenMedia = {},
                onDeleteMedia = { _, _ -> },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_MEDIA_GRID).assertIsDisplayed()
        composeRule.onAllNodesWithText(imageTimestamp).assertCountEquals(0)
        composeRule.onAllNodesWithText(videoTimestamp).assertCountEquals(0)
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
                onDeleteMedia = { _, _ -> },
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
    fun imageDetail_loadingState_showsImageWithoutLoadingOverlay() {
        val localRelativePath = "tasks/task-loading-1/out_1.jpg"
        prepareInternalAlbumFile(localRelativePath)
        val state = AlbumUiState(
            mediaList = listOf(mediaSummary(taskId = "task-loading-1", index = 1, kind = OutputMediaKind.IMAGE)),
            selectedMediaKey = AlbumMediaKey("task-loading-1", 1),
            isLoadingDetail = true,
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            AlbumScreen(
                state = state,
                imageLoader = imageLoader,
                onOpenMedia = {},
                onDeleteMedia = { _, _ -> },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).assertIsDisplayed()
        composeRule.onAllNodesWithTag("album_detail_loading_overlay").assertCountEquals(0)
    }

    @Test
    fun imageDetail_errorState_showsImageAndErrorOverlay() {
        val localRelativePath = "tasks/task-error-1/out_1.jpg"
        prepareInternalAlbumFile(localRelativePath)
        val state = AlbumUiState(
            mediaList = listOf(mediaSummary(taskId = "task-error-1", index = 1, kind = OutputMediaKind.IMAGE)),
            selectedMediaKey = AlbumMediaKey("task-error-1", 1),
            detailError = "forced error",
            isMetadataExpanded = false,
        )

        composeRule.setContent {
            AlbumScreen(
                state = state,
                imageLoader = imageLoader,
                onOpenMedia = {},
                onDeleteMedia = { _, _ -> },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_ERROR_OVERLAY).assertIsDisplayed()
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
                onDeleteMedia = { _, _ -> },
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
                onDeleteMedia = { _, _ -> },
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
    fun detailPager_crossTaskSwipe_loadingStillShowsImage() {
        val mediaAPath = "tasks/task-cross-a/out_2.jpg"
        val mediaBPath = "tasks/task-cross-b/out_1.jpg"
        prepareInternalAlbumFile(mediaAPath)
        prepareInternalAlbumFile(mediaBPath)
        val mediaAKey = AlbumMediaKey("task-cross-a", 2)
        val mediaBKey = AlbumMediaKey("task-cross-b", 1)
        val initialState = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = "task-cross-b", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-cross-a", index = 2, kind = OutputMediaKind.IMAGE),
            ),
            selectedMediaKey = mediaAKey,
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
                        selectedTaskDetail = null,
                        selectedMediaItem = null,
                        isLoadingDetail = true,
                        detailError = null,
                    )
                },
                onDeleteMedia = { _, _ -> },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).performTouchInput { swipeLeft() }
        composeRule.runOnIdle {
            assertEquals(mediaBKey, openedKeys.last())
        }
        composeRule.onNodeWithTag(UiTestTags.ALBUM_DETAIL_IMAGE).assertIsDisplayed()
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
                onDeleteMedia = { _, _ -> },
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
                onDeleteMedia = { _, _ -> },
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
                onDeleteMedia = { _, _ -> },
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
    fun mediaGrid_longPress_entersSelectionMode() {
        val state = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = "task-select-1", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-select-2", index = 1, kind = OutputMediaKind.IMAGE),
            ),
        )

        composeRule.setContent {
            AlbumScreen(
                state = state,
                imageLoader = imageLoader,
                onOpenMedia = {},
                onDeleteMedia = { _, _ -> },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onAllNodesWithTag(UiTestTags.ALBUM_MEDIA_ITEM).onFirst().performTouchInput { longClick() }
        composeRule.onNodeWithTag(UiTestTags.ALBUM_SELECTION_TOOLBAR).assertIsDisplayed()
        composeRule.onNodeWithText("Selected 1").assertIsDisplayed()
    }

    @Test
    fun mediaGrid_multiSelect_deleteConfirm_invokesCallbackWithAllKeys() {
        val keyA = AlbumMediaKey("task-batch-1", 1)
        val keyB = AlbumMediaKey("task-batch-2", 1)
        val state = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = keyA.taskId, index = keyA.index, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = keyB.taskId, index = keyB.index, kind = OutputMediaKind.IMAGE),
            ),
        )
        var deletedKeys: Set<AlbumMediaKey>? = null
        var fallbackKey: AlbumMediaKey? = AlbumMediaKey("placeholder", 99)

        composeRule.setContent {
            AlbumScreen(
                state = state,
                imageLoader = imageLoader,
                onOpenMedia = {},
                onDeleteMedia = { keys, fallback ->
                    deletedKeys = keys
                    fallbackKey = fallback
                },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {},
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        val mediaNodes = composeRule.onAllNodesWithTag(UiTestTags.ALBUM_MEDIA_ITEM)
        mediaNodes[0].performTouchInput { longClick() }
        mediaNodes[1].performClick()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_BATCH_DELETE_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_DELETE_CONFIRM_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_DELETE_CONFIRM_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(keyA, keyB), deletedKeys)
            assertEquals(null, fallbackKey)
        }
    }

    @Test
    fun metadataSheet_deleteCurrentMedia_confirm_invokesCallbackWithFallback() {
        val media1Path = "tasks/task-delete-detail/out_1.jpg"
        val media2Path = "tasks/task-delete-detail/out_2.jpg"
        prepareInternalAlbumFile(media1Path)
        prepareInternalAlbumFile(media2Path)
        val item1 = mediaItem(
            taskId = "task-delete-detail",
            index = 1,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = media1Path,
        )
        val item2 = mediaItem(
            taskId = "task-delete-detail",
            index = 2,
            kind = OutputMediaKind.IMAGE,
            localRelativePath = media2Path,
        )
        val detail = taskDetail(
            taskId = "task-delete-detail",
            mediaItems = listOf(item1, item2),
        )
        val state = AlbumUiState(
            mediaList = listOf(
                mediaSummary(taskId = "task-delete-detail", index = 1, kind = OutputMediaKind.IMAGE),
                mediaSummary(taskId = "task-delete-detail", index = 2, kind = OutputMediaKind.IMAGE),
            ),
            selectedMediaKey = AlbumMediaKey("task-delete-detail", 1),
            selectedTaskDetail = detail,
            selectedMediaItem = item1,
            isMetadataExpanded = false,
        )
        var deletedKeys: Set<AlbumMediaKey>? = null
        var fallback: AlbumMediaKey? = null

        composeRule.setContent {
            var uiState by remember { mutableStateOf(state) }
            AlbumScreen(
                state = uiState,
                imageLoader = imageLoader,
                onOpenMedia = { key -> uiState = uiState.copy(selectedMediaKey = key) },
                onDeleteMedia = { keys, key ->
                    deletedKeys = keys
                    fallback = key
                },
                onRetryLoadMedia = {},
                onToggleMetadataExpanded = {
                    uiState = uiState.copy(isMetadataExpanded = !uiState.isMetadataExpanded)
                },
                onSendImageToVideoInput = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ALBUM_METADATA_SWIPE_ZONE).performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(UiTestTags.ALBUM_SINGLE_DELETE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_SINGLE_DELETE_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.ALBUM_DELETE_CONFIRM_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(AlbumMediaKey("task-delete-detail", 1)), deletedKeys)
            assertEquals(AlbumMediaKey("task-delete-detail", 2), fallback)
        }
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
                onDeleteMedia = { _, _ -> },
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
