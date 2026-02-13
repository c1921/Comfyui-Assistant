package io.github.c1921.comfyui_assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import io.github.c1921.comfyui_assistant.feature.generate.GenerateViewModel
import io.github.c1921.comfyui_assistant.feature.settings.SettingsViewModel
import io.github.c1921.comfyui_assistant.ui.MainScreen
import io.github.c1921.comfyui_assistant.ui.MainTab
import io.github.c1921.comfyui_assistant.ui.theme.ComfyuiAssistantTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    private val generateViewModel: GenerateViewModel by viewModels {
        GenerateViewModel.Factory(
            configRepository = appContainer.configRepository,
            configDraftStore = appContainer.configDraftStore,
            generationRepository = appContainer.generationRepository,
            mediaSaver = appContainer.mediaSaver,
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
                val settingsState by settingsViewModel.uiState.collectAsState()
                MainScreen(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    generateState = generateState,
                    settingsState = settingsState,
                    isGenerateEnabled = generateViewModel.isGenerateEnabled(generateState),
                    onPromptChanged = generateViewModel::onPromptChanged,
                    onNegativeChanged = generateViewModel::onNegativeChanged,
                    onGenerate = generateViewModel::generate,
                    onRetry = generateViewModel::retry,
                    onDownloadResult = generateViewModel::downloadResult,
                    onApiKeyChanged = settingsViewModel::onApiKeyChanged,
                    onWorkflowIdChanged = settingsViewModel::onWorkflowIdChanged,
                    onPromptNodeIdChanged = settingsViewModel::onPromptNodeIdChanged,
                    onPromptFieldNameChanged = settingsViewModel::onPromptFieldNameChanged,
                    onNegativeNodeIdChanged = settingsViewModel::onNegativeNodeIdChanged,
                    onNegativeFieldNameChanged = settingsViewModel::onNegativeFieldNameChanged,
                    onDecodePasswordChanged = settingsViewModel::onDecodePasswordChanged,
                    onSaveSettings = settingsViewModel::saveSettings,
                    onClearApiKey = settingsViewModel::clearApiKey,
                    imageLoader = appContainer.imageLoader,
                    generateMessages = generateViewModel.messages,
                    settingsMessages = settingsViewModel.messages,
                )
            }
        }
    }
}
