package io.github.c1921.comfyui_assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.ui.MainScreen
import io.github.c1921.comfyui_assistant.ui.MainViewModel
import io.github.c1921.comfyui_assistant.ui.theme.ComfyuiAssistantTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            configStore = appContainer.configStore,
            generationRepository = appContainer.generationRepository,
            imageDownloader = appContainer.imageDownloader,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComfyuiAssistantTheme {
                val state by viewModel.uiState.collectAsState()
                MainScreen(
                    state = state,
                    isGenerateEnabled = viewModel.isGenerateEnabled(state),
                    onSelectTab = viewModel::selectTab,
                    onApiKeyChanged = viewModel::onApiKeyChanged,
                    onWorkflowIdChanged = viewModel::onWorkflowIdChanged,
                    onPromptNodeIdChanged = viewModel::onPromptNodeIdChanged,
                    onPromptFieldNameChanged = viewModel::onPromptFieldNameChanged,
                    onNegativeNodeIdChanged = viewModel::onNegativeNodeIdChanged,
                    onNegativeFieldNameChanged = viewModel::onNegativeFieldNameChanged,
                    onPromptChanged = viewModel::onPromptChanged,
                    onNegativeChanged = viewModel::onNegativeChanged,
                    onSaveSettings = viewModel::saveSettings,
                    onClearApiKey = viewModel::clearApiKey,
                    onGenerate = viewModel::generate,
                    onRetry = viewModel::retry,
                    onDownloadResult = { fileUrl, fileType, index ->
                        viewModel.downloadResult(
                            context = applicationContext,
                            output = GeneratedOutput(
                                fileUrl = fileUrl,
                                fileType = fileType,
                                nodeId = null,
                            ),
                            index = index,
                        )
                    },
                    messages = viewModel.messages,
                )
            }
        }
    }
}
