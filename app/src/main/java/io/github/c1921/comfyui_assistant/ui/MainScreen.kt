package io.github.c1921.comfyui_assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.c1921.comfyui_assistant.domain.GenerationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    isGenerateEnabled: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onWorkflowIdChanged: (String) -> Unit,
    onPromptNodeIdChanged: (String) -> Unit,
    onPromptFieldNameChanged: (String) -> Unit,
    onNegativeNodeIdChanged: (String) -> Unit,
    onNegativeFieldNameChanged: (String) -> Unit,
    onPromptChanged: (String) -> Unit,
    onNegativeChanged: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onClearApiKey: () -> Unit,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onDownloadResult: (String, String, Int) -> Unit,
    messages: Flow<String>,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(messages) {
        messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("RunningHub Workflow Assistant") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == MainTab.Generate,
                    onClick = { onSelectTab(MainTab.Generate) },
                    text = { Text("Generate") },
                )
                Tab(
                    selected = state.selectedTab == MainTab.Settings,
                    onClick = { onSelectTab(MainTab.Settings) },
                    text = { Text("Settings") },
                )
            }

            when (state.selectedTab) {
                MainTab.Generate -> GenerateTab(
                    state = state,
                    isGenerateEnabled = isGenerateEnabled,
                    onPromptChanged = onPromptChanged,
                    onNegativeChanged = onNegativeChanged,
                    onGenerate = onGenerate,
                    onRetry = onRetry,
                    onDownloadResult = onDownloadResult,
                )

                MainTab.Settings -> SettingsTab(
                    state = state,
                    onApiKeyChanged = onApiKeyChanged,
                    onWorkflowIdChanged = onWorkflowIdChanged,
                    onPromptNodeIdChanged = onPromptNodeIdChanged,
                    onPromptFieldNameChanged = onPromptFieldNameChanged,
                    onNegativeNodeIdChanged = onNegativeNodeIdChanged,
                    onNegativeFieldNameChanged = onNegativeFieldNameChanged,
                    onSaveSettings = onSaveSettings,
                    onClearApiKey = onClearApiKey,
                )
            }
        }
    }
}

@Composable
private fun GenerateTab(
    state: MainUiState,
    isGenerateEnabled: Boolean,
    onPromptChanged: (String) -> Unit,
    onNegativeChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onDownloadResult: (String, String, Int) -> Unit,
) {
    val generationState = state.generationState
    val isProcessing = generationState is GenerationState.ValidatingConfig ||
        generationState is GenerationState.Submitting ||
        generationState is GenerationState.Queued ||
        generationState is GenerationState.Running

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
            label = { Text("Prompt") },
            placeholder = { Text("Enter positive prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        OutlinedTextField(
            value = state.negative,
            onValueChange = onNegativeChanged,
            label = { Text("Negative (optional)") },
            placeholder = { Text("Enter negative prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        if (!isGenerateEnabled) {
            Text("Complete API key, workflowId, prompt nodeId and prompt fieldName first.")
        }

        Button(
            onClick = onGenerate,
            enabled = isGenerateEnabled && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.GENERATE_BUTTON),
        ) {
            Text(if (isProcessing) "Running..." else "Generate")
        }

        StatusCard(state = generationState, onRetry = onRetry)

        if (generationState is GenerationState.Success) {
            Text("Results")
            generationState.results.forEachIndexed { index, result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = result.fileUrl,
                            contentDescription = "Generated image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Type: ${result.fileType.ifBlank { "unknown" }}")
                        Text("URL: ${result.fileUrl}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onDownloadResult(result.fileUrl, result.fileType, index + 1) },
                        ) {
                            Text("Download to gallery")
                        }
                    }
                }
            }
        }

        Text("workflowId: ${state.workflowId.ifBlank { "not set" }}")
        Text(if (state.apiKey.isBlank()) "API key: not set" else "API key: configured")
        Text("Tip: this workflow must run successfully once on RunningHub web first.")
    }
}

@Composable
private fun SettingsTab(
    state: MainUiState,
    onApiKeyChanged: (String) -> Unit,
    onWorkflowIdChanged: (String) -> Unit,
    onPromptNodeIdChanged: (String) -> Unit,
    onPromptFieldNameChanged: (String) -> Unit,
    onNegativeNodeIdChanged: (String) -> Unit,
    onNegativeFieldNameChanged: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onClearApiKey: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = state.workflowId,
            onValueChange = onWorkflowIdChanged,
            label = { Text("workflowId") },
            placeholder = { Text("Example: 1980237776367083521") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.promptNodeId,
            onValueChange = onPromptNodeIdChanged,
            label = { Text("Prompt nodeId") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.promptFieldName,
            onValueChange = onPromptFieldNameChanged,
            label = { Text("Prompt fieldName") },
            placeholder = { Text("Example: text") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.negativeNodeId,
            onValueChange = onNegativeNodeIdChanged,
            label = { Text("Negative nodeId (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.negativeFieldName,
            onValueChange = onNegativeFieldNameChanged,
            label = { Text("Negative fieldName (optional)") },
            placeholder = { Text("Example: text") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onSaveSettings,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.SAVE_SETTINGS_BUTTON),
            ) {
                Text("Save config")
            }
            TextButton(
                onClick = onClearApiKey,
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear API key")
            }
        }
        Text("nodeId and fieldName can be found in exported workflow API JSON.")
    }
}

@Composable
private fun StatusCard(
    state: GenerationState,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                GenerationState.Idle -> Text("Status: idle")
                GenerationState.ValidatingConfig -> Text("Status: validating config")
                GenerationState.Submitting -> Text("Status: submitting task")
                is GenerationState.Queued -> {
                    Text("Status: queued")
                    Text("taskId: ${state.taskId}")
                    Text("poll count: ${state.pollCount}")
                    state.promptTipsNodeErrors?.let { Text("node tips:\n$it") }
                }

                is GenerationState.Running -> {
                    Text("Status: running")
                    Text("taskId: ${state.taskId}")
                    Text("poll count: ${state.pollCount}")
                    state.promptTipsNodeErrors?.let { Text("node tips:\n$it") }
                }

                is GenerationState.Success -> {
                    Text("Status: success")
                    Text("taskId: ${state.taskId}")
                    Text("results: ${state.results.size}")
                    state.promptTipsNodeErrors?.let { Text("node tips:\n$it") }
                }

                is GenerationState.Failed -> {
                    Text("Status: failed")
                    state.errorCode?.let { Text("error code: $it") }
                    Text("message: ${state.message}")
                    state.failedReason?.nodeName?.let { Text("failed node: $it") }
                    state.failedReason?.nodeId?.let { Text("failed nodeId: $it") }
                    state.failedReason?.exceptionMessage?.let { Text("exception: $it") }
                    state.promptTipsNodeErrors?.let { Text("node tips:\n$it") }
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }

                is GenerationState.Timeout -> {
                    Text("Status: timeout")
                    Text("taskId: ${state.taskId}")
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
