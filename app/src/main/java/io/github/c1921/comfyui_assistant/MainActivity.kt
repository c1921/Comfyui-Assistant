package io.github.c1921.comfyui_assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.c1921.comfyui_assistant.feature.album.AlbumViewModel
import io.github.c1921.comfyui_assistant.feature.generate.GenerateViewModel
import io.github.c1921.comfyui_assistant.feature.settings.SettingsViewModel
import io.github.c1921.comfyui_assistant.domain.AlbumOpenTarget
import io.github.c1921.comfyui_assistant.ui.MainScreen
import io.github.c1921.comfyui_assistant.ui.MainTab
import io.github.c1921.comfyui_assistant.ui.theme.ComfyuiAssistantTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    private val generateViewModel: GenerateViewModel by viewModels {
        GenerateViewModel.Factory(
            configRepository = appContainer.configRepository,
            configDraftStore = appContainer.configDraftStore,
            generationRepository = appContainer.generationRepository,
            inputImageUploader = appContainer.inputImageUploader,
            inputImageSelectionStore = appContainer.inputImageSelectionStore,
            internalAlbumRepository = appContainer.internalAlbumRepository,
        )
    }

    private val albumViewModel: AlbumViewModel by viewModels {
        AlbumViewModel.Factory(
            internalAlbumRepository = appContainer.internalAlbumRepository,
        )
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(
            configRepository = appContainer.configRepository,
            configDraftStore = appContainer.configDraftStore,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComfyuiAssistantTheme {
                var selectedTab by remember { mutableStateOf(MainTab.Generate) }
                val generateState by generateViewModel.uiState.collectAsState()
                val albumState by albumViewModel.uiState.collectAsState()
                val settingsState by settingsViewModel.uiState.collectAsState()
                LaunchedEffect(generateViewModel.openAlbumRequests) {
                    generateViewModel.openAlbumRequests.collectLatest { taskId ->
                        albumViewModel.openByTarget(AlbumOpenTarget.Task(taskId))
                        selectedTab = MainTab.Album
                    }
                }
                MainScreen(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    generateState = generateState,
                    albumState = albumState,
                    settingsState = settingsState,
                    isGenerateEnabled = generateViewModel.isGenerateEnabled(generateState),
                    onPromptChanged = generateViewModel::onPromptChanged,
                    onNegativeChanged = generateViewModel::onNegativeChanged,
                    onGenerationModeChanged = generateViewModel::onGenerationModeChanged,
                    onImagePresetChanged = generateViewModel::onImagePresetChanged,
                    onVideoLengthFramesChanged = generateViewModel::onVideoLengthFramesChanged,
                    onInputImageSelected = generateViewModel::onInputImageSelected,
                    onClearInputImage = generateViewModel::onClearInputImage,
                    onGenerate = generateViewModel::generate,
                    onRetry = generateViewModel::retry,
                    onOpenAlbumForCurrentTask = generateViewModel::openLastArchivedTaskInAlbum,
                    onOpenAlbumMedia = albumViewModel::openMedia,
                    onBackFromAlbumDetail = albumViewModel::backToList,
                    onRetryLoadAlbumMedia = albumViewModel::retryLoadSelectedMedia,
                    onToggleAlbumMetadataExpanded = albumViewModel::toggleMetadataExpanded,
                    onSendAlbumImageToVideoInput = generateViewModel::onVideoInputImageSelectedFromAlbum,
                    onApiKeyChanged = settingsViewModel::onApiKeyChanged,
                    onWorkflowIdChanged = settingsViewModel::onWorkflowIdChanged,
                    onPromptNodeIdChanged = settingsViewModel::onPromptNodeIdChanged,
                    onPromptFieldNameChanged = settingsViewModel::onPromptFieldNameChanged,
                    onNegativeNodeIdChanged = settingsViewModel::onNegativeNodeIdChanged,
                    onNegativeFieldNameChanged = settingsViewModel::onNegativeFieldNameChanged,
                    onSizeNodeIdChanged = settingsViewModel::onSizeNodeIdChanged,
                    onImageInputNodeIdChanged = settingsViewModel::onImageInputNodeIdChanged,
                    onVideoWorkflowIdChanged = settingsViewModel::onVideoWorkflowIdChanged,
                    onVideoPromptNodeIdChanged = settingsViewModel::onVideoPromptNodeIdChanged,
                    onVideoPromptFieldNameChanged = settingsViewModel::onVideoPromptFieldNameChanged,
                    onVideoImageInputNodeIdChanged = settingsViewModel::onVideoImageInputNodeIdChanged,
                    onVideoLengthNodeIdChanged = settingsViewModel::onVideoLengthNodeIdChanged,
                    onDecodePasswordChanged = settingsViewModel::onDecodePasswordChanged,
                    onSaveSettings = settingsViewModel::saveSettings,
                    onClearApiKey = settingsViewModel::clearApiKey,
                    imageLoader = appContainer.imageLoader,
                    previewMediaResolver = appContainer.previewMediaResolver,
                    generateMessages = generateViewModel.messages,
                    albumMessages = albumViewModel.messages,
                    settingsMessages = settingsViewModel.messages,
                )
            }
        }
    }
}
