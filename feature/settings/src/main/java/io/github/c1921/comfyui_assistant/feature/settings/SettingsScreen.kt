package io.github.c1921.comfyui_assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.c1921.comfyui_assistant.ui.UiTestTags

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onWorkflowIdChanged: (String) -> Unit,
    onPromptNodeIdChanged: (String) -> Unit,
    onPromptFieldNameChanged: (String) -> Unit,
    onNegativeNodeIdChanged: (String) -> Unit,
    onNegativeFieldNameChanged: (String) -> Unit,
    onDecodePasswordChanged: (String) -> Unit,
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
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = state.workflowId,
            onValueChange = onWorkflowIdChanged,
            label = { Text(stringResource(R.string.settings_workflow_id_label)) },
            placeholder = { Text(stringResource(R.string.settings_workflow_id_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.promptNodeId,
            onValueChange = onPromptNodeIdChanged,
            label = { Text(stringResource(R.string.settings_prompt_node_id_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.promptFieldName,
            onValueChange = onPromptFieldNameChanged,
            label = { Text(stringResource(R.string.settings_prompt_field_name_label)) },
            placeholder = { Text(stringResource(R.string.settings_field_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.negativeNodeId,
            onValueChange = onNegativeNodeIdChanged,
            label = { Text(stringResource(R.string.settings_negative_node_id_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.negativeFieldName,
            onValueChange = onNegativeFieldNameChanged,
            label = { Text(stringResource(R.string.settings_negative_field_name_label)) },
            placeholder = { Text(stringResource(R.string.settings_field_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.decodePassword,
            onValueChange = onDecodePasswordChanged,
            label = { Text(stringResource(R.string.settings_decode_password_label)) },
            placeholder = { Text(stringResource(R.string.settings_decode_password_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
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
                Text(stringResource(R.string.settings_save_button))
            }
            TextButton(
                onClick = onClearApiKey,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_clear_api_key_button))
            }
        }
        Text(stringResource(R.string.settings_help_text))
    }
}
