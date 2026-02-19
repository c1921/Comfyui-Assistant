package io.github.c1921.comfyui_assistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.ImageLoader
import io.github.c1921.comfyui_assistant.R
import io.github.c1921.comfyui_assistant.data.repository.PreviewMediaResolver
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.feature.generate.GenerateScreen
import io.github.c1921.comfyui_assistant.feature.generate.GenerateUiState
import io.github.c1921.comfyui_assistant.feature.settings.SettingsScreen
import io.github.c1921.comfyui_assistant.feature.settings.SettingsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge

enum class MainTab {
    Generate,
    Settings,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    generateState: GenerateUiState,
    settingsState: SettingsUiState,
    isGenerateEnabled: Boolean,
    onPromptChanged: (String) -> Unit,
    onNegativeChanged: (String) -> Unit,
    onGenerationModeChanged: (GenerationMode) -> Unit,
    onImagePresetChanged: (ImageAspectPreset) -> Unit,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onDownloadResult: (GeneratedOutput, Int) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onWorkflowIdChanged: (String) -> Unit,
    onPromptNodeIdChanged: (String) -> Unit,
    onPromptFieldNameChanged: (String) -> Unit,
    onNegativeNodeIdChanged: (String) -> Unit,
    onNegativeFieldNameChanged: (String) -> Unit,
    onSizeNodeIdChanged: (String) -> Unit,
    onVideoWorkflowIdChanged: (String) -> Unit,
    onVideoPromptNodeIdChanged: (String) -> Unit,
    onVideoPromptFieldNameChanged: (String) -> Unit,
    onDecodePasswordChanged: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onClearApiKey: () -> Unit,
    imageLoader: ImageLoader,
    previewMediaResolver: PreviewMediaResolver,
    generateMessages: Flow<String>,
    settingsMessages: Flow<String>,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(generateMessages, settingsMessages) {
        merge(generateMessages, settingsMessages).collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == MainTab.Generate,
                    onClick = { onSelectTab(MainTab.Generate) },
                    text = { Text(stringResource(R.string.tab_generate)) },
                )
                Tab(
                    selected = selectedTab == MainTab.Settings,
                    onClick = { onSelectTab(MainTab.Settings) },
                    text = { Text(stringResource(R.string.tab_settings)) },
                )
            }

            when (selectedTab) {
                MainTab.Generate -> GenerateScreen(
                    state = generateState,
                    isGenerateEnabled = isGenerateEnabled,
                    onPromptChanged = onPromptChanged,
                    onNegativeChanged = onNegativeChanged,
                    onGenerationModeChanged = onGenerationModeChanged,
                    onImagePresetChanged = onImagePresetChanged,
                    onGenerate = onGenerate,
                    onRetry = onRetry,
                    imageLoader = imageLoader,
                    previewMediaResolver = previewMediaResolver,
                    onDownloadResult = onDownloadResult,
                )

                MainTab.Settings -> SettingsScreen(
                    state = settingsState,
                    onApiKeyChanged = onApiKeyChanged,
                    onWorkflowIdChanged = onWorkflowIdChanged,
                    onPromptNodeIdChanged = onPromptNodeIdChanged,
                    onPromptFieldNameChanged = onPromptFieldNameChanged,
                    onNegativeNodeIdChanged = onNegativeNodeIdChanged,
                    onNegativeFieldNameChanged = onNegativeFieldNameChanged,
                    onSizeNodeIdChanged = onSizeNodeIdChanged,
                    onVideoWorkflowIdChanged = onVideoWorkflowIdChanged,
                    onVideoPromptNodeIdChanged = onVideoPromptNodeIdChanged,
                    onVideoPromptFieldNameChanged = onVideoPromptFieldNameChanged,
                    onDecodePasswordChanged = onDecodePasswordChanged,
                    onSaveSettings = onSaveSettings,
                    onClearApiKey = onClearApiKey,
                )
            }
        }
    }
}
