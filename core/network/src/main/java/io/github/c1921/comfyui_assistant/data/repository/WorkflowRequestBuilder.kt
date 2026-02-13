package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.data.network.NodeInfoItem
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import io.github.c1921.comfyui_assistant.domain.WorkflowConfigValidator

object WorkflowRequestBuilder {
    private const val WIDTH_FIELD_NAME = "width"
    private const val HEIGHT_FIELD_NAME = "height"

    fun buildNodeInfoList(config: WorkflowConfig, input: GenerationInput): List<NodeInfoItem> {
        val result = mutableListOf(
            NodeInfoItem(
                nodeId = config.promptNodeId.trim(),
                fieldName = config.promptFieldName.trim(),
                fieldValue = input.prompt.trim(),
            )
        )

        val hasNegativeText = input.negative.isNotBlank()
        val hasNegativeMapping =
            config.negativeNodeId.isNotBlank() && config.negativeFieldName.isNotBlank()
        if (hasNegativeText && hasNegativeMapping) {
            result += NodeInfoItem(
                nodeId = config.negativeNodeId.trim(),
                fieldName = config.negativeFieldName.trim(),
                fieldValue = input.negative.trim(),
            )
        }

        if (WorkflowConfigValidator.hasCompleteImageSizeMapping(config)) {
            result += NodeInfoItem(
                nodeId = config.sizeNodeId.trim(),
                fieldName = WIDTH_FIELD_NAME,
                fieldValue = input.imagePreset.width,
            )
            result += NodeInfoItem(
                nodeId = config.sizeNodeId.trim(),
                fieldName = HEIGHT_FIELD_NAME,
                fieldValue = input.imagePreset.height,
            )
        }
        return result
    }
}
