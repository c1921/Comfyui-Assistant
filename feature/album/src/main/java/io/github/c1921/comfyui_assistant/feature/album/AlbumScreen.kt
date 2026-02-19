package io.github.c1921.comfyui_assistant.feature.album

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
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

@Composable
fun AlbumScreen(
    state: AlbumUiState,
    onOpenMedia: (AlbumMediaKey) -> Unit,
    onBackToList: () -> Unit,
    onRetryLoadMedia: () -> Unit,
    onToggleMetadataExpanded: () -> Unit,
) {
    if (state.selectedMediaKey == null) {
        AlbumMediaGridContent(
            mediaList = state.mediaList,
            onOpenMedia = onOpenMedia,
        )
        return
    }
    AlbumMediaDetailContent(
        state = state,
        onBackToList = onBackToList,
        onRetryLoadMedia = onRetryLoadMedia,
        onToggleMetadataExpanded = onToggleMetadataExpanded,
    )
}

@Composable
private fun AlbumMediaGridContent(
    mediaList: List<AlbumMediaSummary>,
    onOpenMedia: (AlbumMediaKey) -> Unit,
) {
    val context = LocalContext.current
    val albumRoot = remember(context.filesDir) { File(context.filesDir, "internal_album") }
    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.album_empty))
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.ALBUM_MEDIA_GRID),
        contentPadding = PaddingValues(8.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag(UiTestTags.ALBUM_MEDIA_ITEM),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (localFile.exists()) {
                        if (media.savedMediaKind == OutputMediaKind.VIDEO) {
                            VideoThumbnail(
                                file = localFile,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            AsyncImage(
                                model = Uri.fromFile(localFile),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "N/A",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (media.savedMediaKind == OutputMediaKind.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag(UiTestTags.ALBUM_VIDEO_BADGE),
                        ) {
                            Text(
                                text = stringResource(R.string.album_video_badge),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
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
    onBackToList: () -> Unit,
    onRetryLoadMedia: () -> Unit,
    onToggleMetadataExpanded: () -> Unit,
) {
    val context = LocalContext.current
    val selectedMedia = state.selectedMediaItem
    val selectedKey = state.selectedMediaKey
    val localFile = remember(selectedMedia?.localRelativePath, context.filesDir) {
        selectedMedia?.let { File(context.filesDir, "internal_album/${it.localRelativePath}") }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBackToList) {
                Text(stringResource(R.string.album_back_to_list))
            }
            Spacer(modifier = Modifier.width(8.dp))
            selectedKey?.let {
                Text(stringResource(R.string.album_detail_key_value, it.taskId, it.index))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
        androidx.compose.foundation.Image(
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
        Column(modifier = Modifier.padding(12.dp)) {
            Button(
                onClick = onToggle,
                modifier = Modifier.testTag(UiTestTags.ALBUM_METADATA_TOGGLE),
            ) {
                Text(
                    stringResource(
                        if (isExpanded) R.string.album_metadata_hide else R.string.album_metadata_show,
                    ),
                )
            }
            if (!isExpanded || detail == null || selectedMedia == null) {
                return@Column
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.album_task_id_value, detail.taskId))
            Text(stringResource(R.string.album_request_time_value, formatTimestamp(detail.requestSentAtEpochMs)))
            Text(stringResource(R.string.album_counts_value, detail.savedCount, detail.totalOutputs, detail.failedCount))
            Text(stringResource(R.string.album_mode_value, detail.generationMode.name))
            Text(stringResource(R.string.album_workflow_id_value, detail.workflowId))
            Text(stringResource(R.string.album_prompt_value, detail.prompt))
            Text(stringResource(R.string.album_negative_value, detail.negative))
            detail.videoLengthFrames?.let {
                Text(stringResource(R.string.album_video_frames_value, it))
            }
            detail.uploadedImageFileName?.let {
                Text(stringResource(R.string.album_uploaded_file_value, it))
            }
            detail.promptTipsNodeErrors?.let {
                Text(stringResource(R.string.album_prompt_tips_value, it))
            }
            Text(stringResource(R.string.album_saved_time_value, formatTimestamp(detail.savedAtEpochMs)))
            Text(stringResource(R.string.album_media_path_value, selectedMedia.localRelativePath))
            Text(stringResource(R.string.album_media_type_value, selectedMedia.savedMediaKind.name))
            Text(stringResource(R.string.album_media_decode_value, selectedMedia.decodeOutcomeCode.name))
            Text(stringResource(R.string.album_media_size_value, selectedMedia.fileSizeBytes))
            Text(stringResource(R.string.album_media_source_url_value, selectedMedia.sourceFileUrl))
            Text(stringResource(R.string.album_media_source_type_value, selectedMedia.sourceFileType))
            Text(stringResource(R.string.album_media_source_node_value, selectedMedia.sourceNodeId ?: "-"))

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.album_failures_title))
            if (detail.failures.isEmpty()) {
                Text("-")
            } else {
                detail.failures.forEach { failure ->
                    Text("#${failure.index} ${failure.reason}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.album_node_info_title))
            if (detail.nodeInfoList.isEmpty()) {
                Text("-")
            } else {
                detail.nodeInfoList.forEach { node ->
                    Text("${node.nodeId}.${node.fieldName}=${node.fieldValue}")
                }
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
    } catch (_: Exception) {
        epochMs.toString()
    }
}
