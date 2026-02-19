package io.github.c1921.comfyui_assistant.feature.generate

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.c1921.comfyui_assistant.data.decoder.coil.DuckDecodeRequestParams.enableDuckAutoDecode
import io.github.c1921.comfyui_assistant.data.repository.PreviewMediaResolution
import io.github.c1921.comfyui_assistant.data.repository.PreviewMediaResolver
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.domain.WorkflowConfigValidator
import io.github.c1921.comfyui_assistant.ui.UiTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    state: GenerateUiState,
    isGenerateEnabled: Boolean,
    onPromptChanged: (String) -> Unit,
    onNegativeChanged: (String) -> Unit,
    onGenerationModeChanged: (GenerationMode) -> Unit,
    onImagePresetChanged: (ImageAspectPreset) -> Unit,
    onVideoLengthFramesChanged: (String) -> Unit,
    onInputImageSelected: (Uri?, String) -> Unit,
    onClearInputImage: () -> Unit,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    imageLoader: ImageLoader,
    previewMediaResolver: PreviewMediaResolver,
    onOpenAlbumForCurrentTask: () -> Unit,
) {
    val context = LocalContext.current
    val generationState = state.generationState
    val isVideoMode = state.selectedMode == GenerationMode.VIDEO
    val isProcessing = generationState is GenerationState.ValidatingConfig ||
        generationState is GenerationState.Submitting ||
        generationState is GenerationState.Queued ||
        generationState is GenerationState.Running ||
        state.isUploadingInputImage
    val inputImageNodeId = if (isVideoMode) state.config.videoImageInputNodeId else state.config.imageInputNodeId
    val canUseInputImage = inputImageNodeId.isNotBlank()
    val showVideoLengthFramesInput = isVideoMode && state.config.videoLengthNodeId.isNotBlank()
    val hasCompleteImageSizeMapping = WorkflowConfigValidator.hasCompleteImageSizeMapping(state.config)
    val needsImageSizeMapping = !isVideoMode &&
        state.selectedImagePreset != ImageAspectPreset.RATIO_1_1 &&
        !hasCompleteImageSizeMapping
    var presetMenuExpanded by remember { mutableStateOf(false) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val displayName = resolveInputImageDisplayName(context, uri)
            onInputImageSelected(uri, displayName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isLoadingConfig) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.selectedMode == GenerationMode.IMAGE,
                onClick = { onGenerationModeChanged(GenerationMode.IMAGE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.GEN_MODE_IMAGE_BUTTON),
            ) {
                Text(stringResource(R.string.gen_mode_image))
            }
            SegmentedButton(
                selected = state.selectedMode == GenerationMode.VIDEO,
                onClick = { onGenerationModeChanged(GenerationMode.VIDEO) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.GEN_MODE_VIDEO_BUTTON),
            ) {
                Text(stringResource(R.string.gen_mode_video))
            }
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.gen_prompt_label)) },
            placeholder = { Text(stringResource(R.string.gen_prompt_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        if (canUseInputImage) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.gen_input_image_title))
                    Button(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.testTag(UiTestTags.INPUT_IMAGE_PICK_BUTTON),
                    ) {
                        Text(stringResource(R.string.gen_input_image_pick_button))
                    }
                    if (state.selectedInputImageUri != null) {
                        AsyncImage(
                            model = state.selectedInputImageUri,
                            imageLoader = imageLoader,
                            contentDescription = stringResource(R.string.gen_input_image_preview_desc),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        val displayName = state.selectedInputImageDisplayName.ifBlank {
                            state.selectedInputImageUri.toString()
                        }
                        Text(
                            text = stringResource(R.string.gen_input_image_selected, displayName),
                            modifier = Modifier.testTag(UiTestTags.INPUT_IMAGE_SELECTED_LABEL),
                        )
                        Button(
                            onClick = onClearInputImage,
                            enabled = !isProcessing,
                            modifier = Modifier.testTag(UiTestTags.INPUT_IMAGE_CLEAR_BUTTON),
                        ) {
                            Text(stringResource(R.string.gen_input_image_clear_button))
                        }
                    }
                    if (state.isUploadingInputImage) {
                        Text(stringResource(R.string.gen_input_image_uploading))
                    }
                }
            }
        }

        if (!isVideoMode) {
            OutlinedTextField(
                value = state.negative,
                onValueChange = onNegativeChanged,
                label = { Text(stringResource(R.string.gen_negative_label)) },
                placeholder = { Text(stringResource(R.string.gen_negative_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            ExposedDropdownMenuBox(
                expanded = presetMenuExpanded,
                onExpandedChange = { presetMenuExpanded = !presetMenuExpanded },
            ) {
                OutlinedTextField(
                    value = stringResource(
                        R.string.gen_ratio_value,
                        state.selectedImagePreset.label,
                        state.selectedImagePreset.width,
                        state.selectedImagePreset.height,
                    ),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.gen_ratio_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag(UiTestTags.RATIO_SELECTOR),
                )
                DropdownMenu(
                    expanded = presetMenuExpanded,
                    onDismissRequest = { presetMenuExpanded = false },
                ) {
                    ImageAspectPreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        R.string.gen_ratio_value,
                                        preset.label,
                                        preset.width,
                                        preset.height,
                                    )
                                )
                            },
                            onClick = {
                                onImagePresetChanged(preset)
                                presetMenuExpanded = false
                            },
                        )
                    }
                }
            }
            if (needsImageSizeMapping) {
                Text(stringResource(R.string.gen_ratio_mapping_hint))
            }
        }
        if (showVideoLengthFramesInput) {
            OutlinedTextField(
                value = state.videoLengthFramesText,
                onValueChange = onVideoLengthFramesChanged,
                label = { Text(stringResource(R.string.gen_video_length_frames_label)) },
                placeholder = { Text(stringResource(R.string.gen_video_length_frames_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.VIDEO_LENGTH_FRAMES_INPUT),
            )
        }

        if (!isGenerateEnabled) {
            Text(
                stringResource(
                    if (isVideoMode) R.string.gen_video_config_hint
                    else R.string.gen_config_hint
                )
            )
        }

        Button(
            onClick = onGenerate,
            enabled = isGenerateEnabled && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.GENERATE_BUTTON),
        ) {
            Text(
                stringResource(
                    if (isProcessing) R.string.gen_running_button
                    else R.string.gen_generate_button
                )
            )
        }

        GenerationStatusCard(state = generationState, onRetry = onRetry)

        if (generationState is GenerationState.Success) {
            Text(stringResource(R.string.gen_results_title))
            generationState.results.forEach { result ->
                when (resolveOutputKind(result, fallbackMode = state.selectedMode)) {
                    OutputMediaKind.IMAGE -> ResolvedImageResultCard(
                        result = result,
                        decodePassword = state.config.decodePassword,
                        imageLoader = imageLoader,
                        context = context,
                        previewMediaResolver = previewMediaResolver,
                        onOpenAlbumForCurrentTask = onOpenAlbumForCurrentTask,
                    )

                    OutputMediaKind.VIDEO -> VideoResultCard(
                        result = result,
                        playbackUrl = result.fileUrl,
                        onOpenAlbumForCurrentTask = onOpenAlbumForCurrentTask,
                    )

                    OutputMediaKind.UNKNOWN -> Unit
                }
            }
        }

        val activeWorkflowId = if (isVideoMode) state.config.videoWorkflowId else state.config.workflowId
        Text(
            stringResource(
                R.string.gen_workflow_id_value,
                activeWorkflowId.ifBlank { stringResource(R.string.gen_not_set) },
            )
        )
        Text(
            stringResource(
                if (state.config.apiKey.isBlank()) R.string.gen_api_key_not_set
                else R.string.gen_api_key_configured
            )
        )
        Text(stringResource(R.string.gen_workflow_tip))
    }
}

@Composable
private fun ResolvedImageResultCard(
    result: GeneratedOutput,
    decodePassword: String,
    imageLoader: ImageLoader,
    context: android.content.Context,
    previewMediaResolver: PreviewMediaResolver,
    onOpenAlbumForCurrentTask: () -> Unit,
) {
    val fallbackResolution = remember(result.fileUrl) {
        PreviewMediaResolution(
            kind = OutputMediaKind.IMAGE,
            playbackUrl = result.fileUrl,
            isDecodedFromDuck = false,
        )
    }
    var previewResolution by remember(result.fileUrl, decodePassword) { mutableStateOf(fallbackResolution) }

    LaunchedEffect(result.fileUrl, result.fileType, decodePassword) {
        previewResolution = runCatching {
            previewMediaResolver.resolve(result, decodePassword)
        }.getOrElse {
            fallbackResolution
        }
    }

    if (previewResolution.kind == OutputMediaKind.VIDEO && previewResolution.playbackUrl.isNotBlank()) {
        VideoResultCard(
            result = result,
            playbackUrl = previewResolution.playbackUrl,
            onOpenAlbumForCurrentTask = onOpenAlbumForCurrentTask,
        )
        return
    }

    ImageResultCard(
        result = result,
        decodePassword = decodePassword,
        imageLoader = imageLoader,
        context = context,
        onOpenAlbumForCurrentTask = onOpenAlbumForCurrentTask,
    )
}

@Composable
private fun ImageResultCard(
    result: GeneratedOutput,
    decodePassword: String,
    imageLoader: ImageLoader,
    context: android.content.Context,
    onOpenAlbumForCurrentTask: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val imageRequest = remember(result.fileUrl, decodePassword) {
                ImageRequest.Builder(context)
                    .data(result.fileUrl)
                    .enableDuckAutoDecode(decodePassword)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = stringResource(R.string.gen_result_image_desc),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.gen_result_type_value,
                    result.fileType.ifBlank { stringResource(R.string.gen_unknown) },
                )
            )
            Text(stringResource(R.string.gen_result_url_value, result.fileUrl))
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenAlbumForCurrentTask,
                modifier = Modifier.testTag(UiTestTags.VIEW_ALBUM_BUTTON),
            ) {
                Text(stringResource(R.string.gen_view_album_button))
            }
        }
    }
}

@Composable
private fun VideoResultCard(
    result: GeneratedOutput,
    playbackUrl: String,
    onOpenAlbumForCurrentTask: () -> Unit,
) {
    val videoUri = remember(playbackUrl) { Uri.parse(playbackUrl) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            key(playbackUrl) {
                AndroidView(
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            val mediaController = MediaController(viewContext)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setVideoURI(videoUri)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                start()
                            }
                            setOnErrorListener { _, _, _ -> false }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag(UiTestTags.VIDEO_RESULT_PLAYER),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.gen_result_type_value,
                    result.fileType.ifBlank { stringResource(R.string.gen_unknown) },
                )
            )
            Text(stringResource(R.string.gen_result_url_value, result.fileUrl))
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenAlbumForCurrentTask,
                modifier = Modifier.testTag(UiTestTags.VIEW_ALBUM_BUTTON),
            ) {
                Text(stringResource(R.string.gen_view_album_button))
            }
        }
    }
}

private fun resolveOutputKind(
    output: GeneratedOutput,
    fallbackMode: GenerationMode,
): OutputMediaKind {
    return when (val detected = output.detectMediaKind()) {
        OutputMediaKind.UNKNOWN ->
            if (fallbackMode == GenerationMode.VIDEO) OutputMediaKind.VIDEO else OutputMediaKind.IMAGE

        else -> detected
    }
}

private fun resolveInputImageDisplayName(
    context: android.content.Context,
    uri: Uri,
): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getString(0)?.trim().orEmpty()
                if (displayName.isNotBlank()) return displayName
            }
        }
    return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { uri.toString() }
}

@Composable
private fun GenerationStatusCard(
    state: GenerationState,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                GenerationState.Idle -> Text(stringResource(R.string.gen_status_idle))
                GenerationState.ValidatingConfig -> Text(stringResource(R.string.gen_status_validating))
                GenerationState.Submitting -> Text(stringResource(R.string.gen_status_submitting))

                is GenerationState.Queued -> {
                    Text(stringResource(R.string.gen_status_queued))
                    Text(stringResource(R.string.gen_task_id_value, state.taskId))
                    Text(stringResource(R.string.gen_poll_count_value, state.pollCount))
                    state.promptTipsNodeErrors?.let {
                        Text(stringResource(R.string.gen_node_tips_value, it))
                    }
                }

                is GenerationState.Running -> {
                    Text(stringResource(R.string.gen_status_running))
                    Text(stringResource(R.string.gen_task_id_value, state.taskId))
                    Text(stringResource(R.string.gen_poll_count_value, state.pollCount))
                    state.promptTipsNodeErrors?.let {
                        Text(stringResource(R.string.gen_node_tips_value, it))
                    }
                }

                is GenerationState.Success -> {
                    Text(stringResource(R.string.gen_status_success))
                    Text(stringResource(R.string.gen_task_id_value, state.taskId))
                    Text(stringResource(R.string.gen_results_count_value, state.results.size))
                    state.promptTipsNodeErrors?.let {
                        Text(stringResource(R.string.gen_node_tips_value, it))
                    }
                }

                is GenerationState.Failed -> {
                    Text(stringResource(R.string.gen_status_failed))
                    state.errorCode?.let { Text(stringResource(R.string.gen_error_code_value, it)) }
                    Text(stringResource(R.string.gen_message_value, state.message))
                    state.failedReason?.nodeName?.let {
                        Text(stringResource(R.string.gen_failed_node_value, it))
                    }
                    state.failedReason?.nodeId?.let {
                        Text(stringResource(R.string.gen_failed_node_id_value, it))
                    }
                    state.failedReason?.exceptionMessage?.let {
                        Text(stringResource(R.string.gen_exception_value, it))
                    }
                    state.promptTipsNodeErrors?.let {
                        Text(stringResource(R.string.gen_node_tips_value, it))
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag(UiTestTags.RETRY_BUTTON),
                    ) {
                        Text(stringResource(R.string.gen_retry_button))
                    }
                }

                is GenerationState.Timeout -> {
                    Text(stringResource(R.string.gen_status_timeout))
                    Text(stringResource(R.string.gen_task_id_value, state.taskId))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag(UiTestTags.RETRY_BUTTON),
                    ) {
                        Text(stringResource(R.string.gen_retry_button))
                    }
                }
            }
        }
    }
}
