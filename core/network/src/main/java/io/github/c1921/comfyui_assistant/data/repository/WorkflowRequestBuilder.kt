package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.data.network.NodeInfoItem
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

object WorkflowRequestBuilder {
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
        return result
    }
}
