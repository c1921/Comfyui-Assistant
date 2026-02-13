package io.github.c1921.comfyui_assistant.domain

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

        if (
            input.imagePreset != ImageAspectPreset.RATIO_1_1 &&
            !hasCompleteImageSizeMapping(config)
        ) {
            return "Selected ratio requires width/height mapping in Settings."
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

    fun hasCompleteImageSizeMapping(config: WorkflowConfig): Boolean {
        return config.sizeNodeId.isNotBlank()
    }
}
