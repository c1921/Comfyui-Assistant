package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.data.network.NodeInfoItem
import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig

object WorkflowConfigValidator {
    fun validateMappingConsistency(config: WorkflowConfig): String? {
        val hasNegativeNode = config.negativeNodeId.isNotBlank()
        val hasNegativeField = config.negativeFieldName.isNotBlank()
        if (hasNegativeNode.xor(hasNegativeField)) {
            return "Negative mapping requires both nodeId and fieldName."
        }
        return null
    }

    fun validateForGenerate(config: WorkflowConfig, input: GenerationInput): String? {
        validateMappingConsistency(config)?.let { return it }
        if (config.apiKey.isBlank()) return "Please configure API key first."
        if (config.workflowId.isBlank()) return "Please configure workflowId first."
        if (config.promptNodeId.isBlank() || config.promptFieldName.isBlank()) {
            return "Please configure prompt node mapping first."
        }
        if (input.prompt.isBlank()) return "Prompt cannot be empty."

        val hasNegativeText = input.negative.isNotBlank()
        val hasNegativeMapping =
            config.negativeNodeId.isNotBlank() && config.negativeFieldName.isNotBlank()
        if (hasNegativeText && !hasNegativeMapping) {
            return "Negative prompt is provided, but negative mapping is not configured."
        }
        return null
    }

    fun isGenerateReady(config: WorkflowConfig, prompt: String): Boolean {
        val validation = validateForGenerate(
            config = config,
            input = GenerationInput(prompt = prompt, negative = ""),
        )
        return validation == null
    }
}

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
