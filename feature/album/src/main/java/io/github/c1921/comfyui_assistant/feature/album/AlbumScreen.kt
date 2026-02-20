package io.github.c1921.comfyui_assistant.feature.album

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import io.github.c1921.comfyui_assistant.domain.AlbumMediaItem
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.AlbumMediaSummary
import io.github.c1921.comfyui_assistant.domain.AlbumTaskDetail
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.ui.UiTestTags
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

@Composable
fun AlbumScreen(
    state: AlbumUiState,
    onOpenMedia: (AlbumMediaKey) -> Unit,
    onBackToList: () -> Unit,
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
            totalCount = state.mediaList.size,
            imageCount = state.mediaList.count { it.savedMediaKind == OutputMediaKind.IMAGE },
            videoCount = state.mediaList.count { it.savedMediaKind == OutputMediaKind.VIDEO },
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
        navigationMediaList = navigationMediaList,
        onOpenMedia = onOpenMedia,
        onBackToList = onBackToList,
        onRetryLoadMedia = onRetryLoadMedia,
        onToggleMetadataExpanded = onToggleMetadataExpanded,
        onSendImageToVideoInput = onSendImageToVideoInput,
    )
}

@Composable
private fun AlbumMediaGridContent(
    mediaList: List<AlbumMediaSummary>,
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
                    key = { "${it.key.taskId}_${it.key.index}" },
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
                                    modifier = Modifier.fillMaxSize(),
                                )

                                else -> AsyncImage(
                                    model = Uri.fromFile(localFile),
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

@Composable
private fun AlbumMediaDetailContent(
    state: AlbumUiState,
    navigationMediaList: List<AlbumMediaSummary>,
    onOpenMedia: (AlbumMediaKey) -> Unit,
    onBackToList: () -> Unit,
    onRetryLoadMedia: () -> Unit,
    onToggleMetadataExpanded: () -> Unit,
    onSendImageToVideoInput: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val selectedMedia = state.selectedMediaItem
    val localFile = remember(selectedMedia?.localRelativePath, context.filesDir) {
        selectedMedia?.let { File(context.filesDir, "internal_album/${it.localRelativePath}") }
    }
    val sendableImageFile = localFile?.takeIf {
        selectedMedia?.savedMediaKind == OutputMediaKind.IMAGE && it.exists()
    }
    val selectedKey = state.selectedMediaKey
    val currentPosition = remember(navigationMediaList, selectedKey) {
        navigationMediaList.indexOfFirst { it.key == selectedKey }
    }
    val previousKey = remember(navigationMediaList, currentPosition) {
        if (currentPosition > 0) {
            navigationMediaList[currentPosition - 1].key
        } else {
            null
        }
    }
    val nextKey = remember(navigationMediaList, currentPosition) {
        if (currentPosition in 0 until navigationMediaList.lastIndex) {
            navigationMediaList[currentPosition + 1].key
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBackToList) {
                Text(stringResource(R.string.album_back_to_list))
            }
            if (currentPosition >= 0 && navigationMediaList.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.album_detail_position,
                        currentPosition + 1,
                        navigationMediaList.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { previousKey?.let(onOpenMedia) },
                enabled = previousKey != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.album_prev))
            }
            FilledTonalButton(
                onClick = { nextKey?.let(onOpenMedia) },
                enabled = nextKey != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.album_next))
            }
            if (sendableImageFile != null) {
                Button(
                    onClick = {
                        onSendImageToVideoInput(Uri.fromFile(sendableImageFile), sendableImageFile.name)
                    },
                    modifier = Modifier
                        .weight(1.35f)
                        .testTag(UiTestTags.ALBUM_SEND_TO_VIDEO_INPUT_BUTTON),
                ) {
                    Text(stringResource(R.string.album_send_to_video_input))
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoadingDetail -> CircularProgressIndicator()
                    !state.detailError.isNullOrBlank() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.album_detail_error, state.detailError))
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRetryLoadMedia) {
                                Text(stringResource(R.string.album_retry_load))
                            }
                        }
                    }

                    selectedMedia == null || localFile == null || !localFile.exists() -> {
                        Text(stringResource(R.string.album_media_missing))
                    }

                    selectedMedia.savedMediaKind == OutputMediaKind.VIDEO -> {
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
                                    .clip(MaterialTheme.shapes.medium)
                                    .testTag(UiTestTags.ALBUM_DETAIL_VIDEO),
                            )
                        }
                    }

                    else -> ZoomableImage(
                        imageUri = Uri.fromFile(localFile),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(UiTestTags.ALBUM_DETAIL_IMAGE),
                    )
                }
            }
        }

        MetadataPanel(
            detail = state.selectedTaskDetail,
            selectedMedia = selectedMedia,
            isExpanded = state.isMetadataExpanded,
            onToggle = onToggleMetadataExpanded,
        )
    }
}

@Composable
private fun ZoomableImage(
    imageUri: Uri,
    modifier: Modifier = Modifier,
) {
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .pointerInput(imageUri) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            }
            .pointerInput(imageUri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    offset = if (nextScale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
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
    modifier: Modifier = Modifier,
) {
    val bitmap: Bitmap? = remember(file.absolutePath) {
        runCatching {
            ThumbnailUtils.createVideoThumbnail(file, Size(512, 512), null)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "VIDEO",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MetadataPanel(
    detail: AlbumTaskDetail?,
    selectedMedia: AlbumMediaItem?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.ALBUM_METADATA_PANEL),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.album_metadata_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onToggle,
                    modifier = Modifier.testTag(UiTestTags.ALBUM_METADATA_TOGGLE),
                ) {
                    Text(
                        stringResource(
                            if (isExpanded) R.string.album_metadata_hide else R.string.album_metadata_show,
                        ),
                    )
                }
            }

            if (detail == null || selectedMedia == null) {
                Text(
                    text = stringResource(R.string.album_metadata_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
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
            }
        }
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
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
    } catch (_: Exception) {
        epochMs.toString()
    }
}
