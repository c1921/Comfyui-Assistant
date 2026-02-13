package io.github.c1921.comfyui_assistant.feature.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.c1921.comfyui_assistant.data.decoder.coil.DuckDecodeRequestParams.enableDuckAutoDecode
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.WorkflowConfigValidator
import io.github.c1921.comfyui_assistant.ui.UiTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    state: GenerateUiState,
    isGenerateEnabled: Boolean,
    onPromptChanged: (String) -> Unit,
    onNegativeChanged: (String) -> Unit,
    onImagePresetChanged: (ImageAspectPreset) -> Unit,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    imageLoader: ImageLoader,
    onDownloadResult: (GeneratedOutput, Int) -> Unit,
) {
    val context = LocalContext.current
    val generationState = state.generationState
    val isProcessing = generationState is GenerationState.ValidatingConfig ||
        generationState is GenerationState.Submitting ||
        generationState is GenerationState.Queued ||
        generationState is GenerationState.Running
    val hasCompleteImageSizeMapping = WorkflowConfigValidator.hasCompleteImageSizeMapping(state.config)
    val needsImageSizeMapping =
        state.selectedImagePreset != ImageAspectPreset.RATIO_1_1 && !hasCompleteImageSizeMapping
    var presetMenuExpanded by remember { mutableStateOf(false) }

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

        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.gen_prompt_label)) },
            placeholder = { Text(stringResource(R.string.gen_prompt_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetMenuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .testTag(UiTestTags.RATIO_SELECTOR),
            )
            ExposedDropdownMenu(
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

        if (!isGenerateEnabled) {
            Text(stringResource(R.string.gen_config_hint))
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
            generationState.results.forEachIndexed { index, result ->
                ResultCard(
                    result = result,
                    decodePassword = state.config.decodePassword,
                    imageLoader = imageLoader,
                    context = context,
                    index = index,
                    onDownloadResult = onDownloadResult,
                )
            }
        }

        Text(
            stringResource(
                R.string.gen_workflow_id_value,
                state.config.workflowId.ifBlank { stringResource(R.string.gen_not_set) },
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
private fun ResultCard(
    result: GeneratedOutput,
    decodePassword: String,
    imageLoader: ImageLoader,
    context: android.content.Context,
    index: Int,
    onDownloadResult: (GeneratedOutput, Int) -> Unit,
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
                onClick = { onDownloadResult(result, index + 1) },
            ) {
                Text(stringResource(R.string.gen_download_button))
            }
        }
    }
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
