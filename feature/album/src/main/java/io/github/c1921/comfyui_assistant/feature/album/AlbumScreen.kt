package io.github.c1921.comfyui_assistant.feature.album

import android.content.Context
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.ui.UiTestTags
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max

private enum class AlbumMediaFilter {
    ALL,
    IMAGES,
    VIDEOS,
}

private enum class AlbumSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

private data class MediaCounts(
    val imageCount: Int = 0,
    val videoCount: Int = 0,
)

@Composable
fun AlbumScreen(
    state: AlbumUiState,
    imageLoader: ImageLoader,
    onOpenMedia: (AlbumMediaKey) -> Unit,
    onRetryLoadMedia: () -> Unit,
    onToggleMetadataExpanded: () -> Unit,
    onSendImageToVideoInput: (Uri, String) -> Unit,
) {
    var selectedFilterName by rememberSaveable { mutableStateOf(AlbumMediaFilter.ALL.name) }
    var sortOrderName by rememberSaveable { mutableStateOf(AlbumSortOrder.NEWEST_FIRST.name) }
    val selectedFilter = remember(selectedFilterName) {
        AlbumMediaFilter.entries.firstOrNull { it.name == selectedFilterName } ?: AlbumMediaFilter.ALL
    }
    val sortOrder = remember(sortOrderName) {
        AlbumSortOrder.entries.firstOrNull { it.name == sortOrderName } ?: AlbumSortOrder.NEWEST_FIRST
    }
    val visibleMediaList = remember(state.mediaList, selectedFilter, sortOrder) {
        buildVisibleMediaList(
            media = state.mediaList,
            filter = selectedFilter,
            sortOrder = sortOrder,
        )
    }
    val mediaCounts = remember(state.mediaList) {
        var imageCount = 0
        var videoCount = 0
        state.mediaList.forEach { media ->
            when (media.savedMediaKind) {
                OutputMediaKind.IMAGE -> imageCount += 1
                OutputMediaKind.VIDEO -> videoCount += 1
                else -> Unit
            }
        }
        MediaCounts(imageCount = imageCount, videoCount = videoCount)
    }
    val defaultNavigationList = remember(state.mediaList) {
        state.mediaList.sortedWith(mediaSummaryComparator(AlbumSortOrder.NEWEST_FIRST))
    }
    val navigationMediaList = remember(visibleMediaList, defaultNavigationList, state.selectedMediaKey) {
        val selectedKey = state.selectedMediaKey
        if (selectedKey != null && visibleMediaList.any { it.key == selectedKey }) {
            visibleMediaList
        } else {
            defaultNavigationList
        }
    }
    if (state.selectedMediaKey == null) {
        AlbumMediaGridContent(
            mediaList = visibleMediaList,
            imageLoader = imageLoader,
            totalCount = state.mediaList.size,
            imageCount = mediaCounts.imageCount,
            videoCount = mediaCounts.videoCount,
            selectedFilter = selectedFilter,
            onFilterSelected = { filter -> selectedFilterName = filter.name },
            sortOrder = sortOrder,
            onToggleSortOrder = {
                sortOrderName = if (sortOrder == AlbumSortOrder.NEWEST_FIRST) {
                    AlbumSortOrder.OLDEST_FIRST.name
                } else {
                    AlbumSortOrder.NEWEST_FIRST.name
                }
            },
            onOpenMedia = onOpenMedia,
        )
        return
    }
    AlbumMediaDetailContent(
        state = state,
        imageLoader = imageLoader,
        navigationMediaList = navigationMediaList,
        onOpenMedia = onOpenMedia,
        onRetryLoadMedia = onRetryLoadMedia,
        onToggleMetadataExpanded = onToggleMetadataExpanded,
        onSendImageToVideoInput = onSendImageToVideoInput,
    )
}

@Composable
private fun AlbumMediaGridContent(
    mediaList: List<AlbumMediaSummary>,
    imageLoader: ImageLoader,
    totalCount: Int,
    imageCount: Int,
    videoCount: Int,
    selectedFilter: AlbumMediaFilter,
    onFilterSelected: (AlbumMediaFilter) -> Unit,
    sortOrder: AlbumSortOrder,
    onToggleSortOrder: () -> Unit,
    onOpenMedia: (AlbumMediaKey) -> Unit,
) {
    val context = LocalContext.current
    val albumRoot = remember(context.filesDir) { File(context.filesDir, "internal_album") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.album_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.album_summary_counts, totalCount, imageCount, videoCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = selectedFilter == AlbumMediaFilter.ALL,
                    onClick = { onFilterSelected(AlbumMediaFilter.ALL) },
                    label = {
                        Text(stringResource(R.string.album_filter_all_with_count, totalCount))
                    },
                )
                FilterChip(
                    selected = selectedFilter == AlbumMediaFilter.IMAGES,
                    onClick = { onFilterSelected(AlbumMediaFilter.IMAGES) },
                    label = {
                        Text(stringResource(R.string.album_filter_images_with_count, imageCount))
                    },
                )
                FilterChip(
                    selected = selectedFilter == AlbumMediaFilter.VIDEOS,
                    onClick = { onFilterSelected(AlbumMediaFilter.VIDEOS) },
                    label = {
                        Text(stringResource(R.string.album_filter_videos_with_count, videoCount))
                    },
                )
                FilledTonalButton(onClick = onToggleSortOrder) {
                    Text(
                        text = stringResource(
                            if (sortOrder == AlbumSortOrder.NEWEST_FIRST) {
                                R.string.album_sort_newest
                            } else {
                                R.string.album_sort_oldest
                            },
                        ),
                    )
                }
            }
        }

        if (mediaList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.album_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag(UiTestTags.ALBUM_MEDIA_GRID),
        ) {
            val columns = max(2, (maxWidth / 132.dp).toInt())
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = mediaList,
                    // Lazy layouts use saveable state restoration; keys must be Bundle-saveable on Android.
                    key = { it.localRelativePath },
                ) { media ->
                    val localFile = remember(media.localRelativePath) {
                        File(albumRoot, media.localRelativePath)
                    }
                    Card(
                        onClick = { onOpenMedia(media.key) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .testTag(UiTestTags.ALBUM_MEDIA_ITEM),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                !localFile.exists() -> MissingMediaPlaceholder()
                                media.savedMediaKind == OutputMediaKind.VIDEO -> VideoThumbnail(
                                    file = localFile,
                                    imageLoader = imageLoader,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                else -> AsyncImage(
                                    model = localFile,
                                    imageLoader = imageLoader,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.72f),
                                            ),
                                        ),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = formatTimestamp(media.createdAtEpochMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (media.savedMediaKind == OutputMediaKind.VIDEO) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .testTag(UiTestTags.ALBUM_VIDEO_BADGE),
                                ) {
                                    Text(
                                        text = stringResource(R.string.album_video_badge),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AlbumMediaDetailContent(
    state: AlbumUiState,
    imageLoader: ImageLoader,
    navigationMediaList: List<AlbumMediaSummary>,
    onOpenMedia: (AlbumMediaKey) -> Unit,
    onRetryLoadMedia: () -> Unit,
    onToggleMetadataExpanded: () -> Unit,
    onSendImageToVideoInput: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val selectedKey = state.selectedMediaKey
    val currentPosition = remember(navigationMediaList, selectedKey) {
        navigationMediaList.indexOfFirst { it.key == selectedKey }
    }
    val selectedPage = if (currentPosition >= 0) currentPosition else 0
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { navigationMediaList.size },
    )
    val latestSelectedKey by rememberUpdatedState(selectedKey)
    var isCurrentImageZoomed by remember(selectedKey) { mutableStateOf(false) }

    LaunchedEffect(selectedPage, navigationMediaList.size) {
        if (navigationMediaList.isEmpty()) {
            return@LaunchedEffect
        }
        if (selectedPage in 0 until pagerState.pageCount && pagerState.currentPage != selectedPage) {
            pagerState.scrollToPage(selectedPage)
        }
    }

    LaunchedEffect(pagerState, navigationMediaList) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                prefetchNeighborImages(
                    page = page,
                    navigationMediaList = navigationMediaList,
                    context = context,
                    imageLoader = imageLoader,
                )
                val key = navigationMediaList.getOrNull(page)?.key ?: return@collect
                if (key != latestSelectedKey) {
                    onOpenMedia(key)
                }
            }
    }

    LaunchedEffect(pagerState.currentPage, navigationMediaList) {
        val currentMedia = navigationMediaList.getOrNull(pagerState.currentPage)
        if (currentMedia?.savedMediaKind != OutputMediaKind.IMAGE) {
            isCurrentImageZoomed = false
        }
    }

    val currentSummary = navigationMediaList.getOrNull(pagerState.currentPage)
    val currentFile = remember(currentSummary?.localRelativePath, context.filesDir) {
        currentSummary?.let { File(context.filesDir, "internal_album/${it.localRelativePath}") }
    }
    val sendableImageFile = currentFile?.takeIf {
        currentSummary?.savedMediaKind == OutputMediaKind.IMAGE && it.exists()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (navigationMediaList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.album_media_missing), color = Color.White)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !isCurrentImageZoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(UiTestTags.ALBUM_DETAIL_PAGER),
            ) { page ->
                val summary = navigationMediaList[page]
                val localFile = remember(summary.localRelativePath, context.filesDir) {
                    File(context.filesDir, "internal_album/${summary.localRelativePath}")
                }
                AlbumDetailPage(
                    summary = summary,
                    localFile = localFile,
                    imageLoader = imageLoader,
                    selectedKey = selectedKey,
                    detailError = state.detailError,
                    onRetryLoadMedia = onRetryLoadMedia,
                    onZoomedStateChanged = if (page == pagerState.currentPage) {
                        { isZoomed -> isCurrentImageZoomed = isZoomed }
                    } else {
                        {}
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(52.dp)
                .testTag(UiTestTags.ALBUM_METADATA_SWIPE_ZONE)
                .pointerInput(state.isMetadataExpanded) {
                    var dragTotal = 0f
                    var opened = false
                    detectVerticalDragGestures(
                        onDragEnd = {
                            dragTotal = 0f
                            opened = false
                        },
                        onDragCancel = {
                            dragTotal = 0f
                            opened = false
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (state.isMetadataExpanded || opened) {
                                return@detectVerticalDragGestures
                            }
                            dragTotal += dragAmount
                            if (dragTotal <= -56f) {
                                opened = true
                                onToggleMetadataExpanded()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!state.isMetadataExpanded) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.7f)),
                )
            }
        }

        if (state.isMetadataExpanded) {
            ModalBottomSheet(
                onDismissRequest = onToggleMetadataExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.ALBUM_METADATA_SHEET),
            ) {
                MetadataSheetContent(
                    detail = state.selectedTaskDetail,
                    selectedMedia = state.selectedMediaItem,
                    sendableImageFile = sendableImageFile,
                    onSendImageToVideoInput = onSendImageToVideoInput,
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailPage(
    summary: AlbumMediaSummary,
    localFile: File,
    imageLoader: ImageLoader,
    selectedKey: AlbumMediaKey?,
    detailError: String?,
    onRetryLoadMedia: () -> Unit,
    onZoomedStateChanged: (Boolean) -> Unit,
) {
    val isSelectedPage = summary.key == selectedKey

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !localFile.exists() -> Text(
                text = stringResource(R.string.album_media_missing),
                color = Color.White,
            )

            summary.savedMediaKind == OutputMediaKind.VIDEO -> {
                key(localFile.absolutePath) {
                    AndroidView(
                        factory = { viewContext ->
                            VideoView(viewContext).apply {
                                val mediaController = MediaController(viewContext)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setVideoURI(Uri.fromFile(localFile))
                                setOnPreparedListener { player ->
                                    player.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .let { base ->
                                if (isSelectedPage) base.testTag(UiTestTags.ALBUM_DETAIL_VIDEO) else base
                            },
                    )
                }
            }

            else -> ZoomableImage(
                imageUri = Uri.fromFile(localFile),
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxSize()
                    .let { base ->
                        if (isSelectedPage) base.testTag(UiTestTags.ALBUM_DETAIL_IMAGE) else base
                    },
                onZoomedStateChanged = onZoomedStateChanged,
            )
        }

        if (isSelectedPage && !detailError.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.74f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .testTag(UiTestTags.ALBUM_DETAIL_ERROR_OVERLAY),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.album_detail_error, detailError), color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetryLoadMedia) {
                        Text(stringResource(R.string.album_retry_load))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataSheetContent(
    detail: AlbumTaskDetail?,
    selectedMedia: AlbumMediaItem?,
    sendableImageFile: File?,
    onSendImageToVideoInput: (Uri, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.ALBUM_METADATA_PANEL)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.album_metadata_title),
            style = MaterialTheme.typography.titleSmall,
        )

        if (sendableImageFile != null) {
            Button(
                onClick = {
                    onSendImageToVideoInput(Uri.fromFile(sendableImageFile), sendableImageFile.name)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.ALBUM_SEND_TO_VIDEO_INPUT_BUTTON),
            ) {
                Text(stringResource(R.string.album_send_to_video_input))
            }
        }

        if (detail == null || selectedMedia == null) {
            Text(
                text = stringResource(R.string.album_metadata_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MetadataLine(stringResource(R.string.album_task_id_value, detail.taskId))
            MetadataLine(stringResource(R.string.album_request_time_value, formatTimestamp(detail.requestSentAtEpochMs)))
            MetadataLine(stringResource(R.string.album_counts_value, detail.savedCount, detail.totalOutputs, detail.failedCount))
            MetadataLine(stringResource(R.string.album_mode_value, detail.generationMode.name))
            MetadataLine(stringResource(R.string.album_workflow_id_value, detail.workflowId))
            MetadataLine(stringResource(R.string.album_prompt_value, detail.prompt))
            MetadataLine(stringResource(R.string.album_negative_value, detail.negative))
            detail.videoLengthFrames?.let {
                MetadataLine(stringResource(R.string.album_video_frames_value, it))
            }
            detail.uploadedImageFileName?.let {
                MetadataLine(stringResource(R.string.album_uploaded_file_value, it))
            }
            detail.promptTipsNodeErrors?.let {
                MetadataLine(stringResource(R.string.album_prompt_tips_value, it))
            }
            MetadataLine(stringResource(R.string.album_saved_time_value, formatTimestamp(detail.savedAtEpochMs)))
            MetadataLine(stringResource(R.string.album_media_path_value, selectedMedia.localRelativePath))
            MetadataLine(stringResource(R.string.album_media_type_value, selectedMedia.savedMediaKind.name))
            MetadataLine(stringResource(R.string.album_media_decode_value, selectedMedia.decodeOutcomeCode.name))
            MetadataLine(stringResource(R.string.album_media_size_value, selectedMedia.fileSizeBytes))
            MetadataLine(stringResource(R.string.album_media_source_url_value, selectedMedia.sourceFileUrl))
            MetadataLine(stringResource(R.string.album_media_source_type_value, selectedMedia.sourceFileType))
            MetadataLine(stringResource(R.string.album_media_source_node_value, selectedMedia.sourceNodeId ?: "-"))

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = stringResource(R.string.album_failures_title),
                style = MaterialTheme.typography.labelLarge,
            )
            if (detail.failures.isEmpty()) {
                MetadataLine("-")
            } else {
                detail.failures.forEach { failure ->
                    MetadataLine("#${failure.index} ${failure.reason}")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = stringResource(R.string.album_node_info_title),
                style = MaterialTheme.typography.labelLarge,
            )
            if (detail.nodeInfoList.isEmpty()) {
                MetadataLine("-")
            } else {
                detail.nodeInfoList.forEach { node ->
                    MetadataLine("${node.nodeId}.${node.fieldName}=${node.fieldValue}")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ZoomableImage(
    imageUri: Uri,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    onZoomedStateChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUri, context) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .crossfade(false)
            .build()
    }
    val loadingPlaceholder = remember { ColorPainter(Color(0xFF2A2A2A)) }
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(imageUri) {
        onZoomedStateChanged(false)
    }
    Box(
        modifier = modifier
            .pointerInput(imageUri) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                        onZoomedStateChanged(scale > 1f)
                    },
                )
            }
            .let { base ->
                if (scale > 1f) {
                    base.pointerInput(imageUri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = nextScale
                            offset = if (nextScale > 1f) offset + pan else Offset.Zero
                            onZoomedStateChanged(nextScale > 1f)
                        }
                    }
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            placeholder = loadingPlaceholder,
            error = loadingPlaceholder,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

@Composable
private fun MissingMediaPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "N/A",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoThumbnail(
    file: File,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(file.absolutePath) {
        ImageRequest.Builder(context)
            .data(file)
            .videoFrameMillis(0)
            .crossfade(false)
            .build()
    }
    Box(modifier = modifier) {
        MissingMediaPlaceholder()
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MetadataLine(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun prefetchNeighborImages(
    page: Int,
    navigationMediaList: List<AlbumMediaSummary>,
    context: Context,
    imageLoader: ImageLoader,
) {
    val neighborPages = intArrayOf(page - 1, page + 1)
    neighborPages.forEach { neighborPage ->
        val neighborSummary = navigationMediaList.getOrNull(neighborPage) ?: return@forEach
        if (neighborSummary.savedMediaKind != OutputMediaKind.IMAGE) {
            return@forEach
        }
        val localFile = File(context.filesDir, "internal_album/${neighborSummary.localRelativePath}")
        if (!localFile.exists()) {
            return@forEach
        }
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(localFile)
                .crossfade(false)
                .build(),
        )
    }
}

private fun buildVisibleMediaList(
    media: List<AlbumMediaSummary>,
    filter: AlbumMediaFilter,
    sortOrder: AlbumSortOrder,
): List<AlbumMediaSummary> {
    return media
        .asSequence()
        .filter { summary ->
            when (filter) {
                AlbumMediaFilter.ALL -> true
                AlbumMediaFilter.IMAGES -> summary.savedMediaKind == OutputMediaKind.IMAGE
                AlbumMediaFilter.VIDEOS -> summary.savedMediaKind == OutputMediaKind.VIDEO
            }
        }
        .sortedWith(mediaSummaryComparator(sortOrder))
        .toList()
}

private fun mediaSummaryComparator(sortOrder: AlbumSortOrder): Comparator<AlbumMediaSummary> {
    return when (sortOrder) {
        AlbumSortOrder.NEWEST_FIRST -> compareByDescending<AlbumMediaSummary> { it.createdAtEpochMs }
            .thenByDescending { it.savedAtEpochMs }
            .thenByDescending { it.key.taskId }
            .thenByDescending { it.key.index }

        AlbumSortOrder.OLDEST_FIRST -> compareBy<AlbumMediaSummary> { it.createdAtEpochMs }
            .thenBy { it.savedAtEpochMs }
            .thenBy { it.key.taskId }
            .thenBy { it.key.index }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    return runCatching {
        TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMs))
    }.getOrElse {
        epochMs.toString()
    }
}

private val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    .withZone(ZoneId.systemDefault())
