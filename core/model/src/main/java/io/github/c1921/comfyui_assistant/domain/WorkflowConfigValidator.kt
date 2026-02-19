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

    fun validateForSettings(config: WorkflowConfig): String? {
        validateMappingConsistency(config)?.let { return it }

        val hasVideoWorkflowId = config.videoWorkflowId.isNotBlank()
        val hasVideoPromptNodeId = config.videoPromptNodeId.isNotBlank()
        val hasVideoPromptFieldName = config.videoPromptFieldName.isNotBlank()
        val hasAnyVideoMapping = hasVideoWorkflowId || hasVideoPromptNodeId || hasVideoPromptFieldName
        val hasCompleteVideoMapping =
            hasVideoWorkflowId && hasVideoPromptNodeId && hasVideoPromptFieldName
        if (hasAnyVideoMapping && !hasCompleteVideoMapping) {
            return "Video mapping requires workflowId, prompt nodeId and prompt fieldName."
        }
        return null
    }

    fun validateForGenerate(config: WorkflowConfig, input: GenerationInput): String? {
        validateMappingConsistency(config)?.let { return it }
        return when (input.mode) {
            GenerationMode.IMAGE -> validateForImageGenerate(config, input)
            GenerationMode.VIDEO -> validateForVideoGenerate(config, input)
        }
    }

    fun validateForVideoGenerate(config: WorkflowConfig, input: GenerationInput): String? {
        if (config.apiKey.isBlank()) return "Please configure API key first."
        if (config.videoWorkflowId.isBlank()) return "Please configure video workflowId first."
        if (config.videoPromptNodeId.isBlank() || config.videoPromptFieldName.isBlank()) {
            return "Please configure video prompt node mapping first."
        }
        if (input.hasInputImage && config.videoImageInputNodeId.isBlank()) {
            return "Please configure video image input nodeId first."
        }
        if (input.prompt.isBlank()) return "Prompt cannot be empty."
        return null
    }

    private fun validateForImageGenerate(config: WorkflowConfig, input: GenerationInput): String? {
        if (config.apiKey.isBlank()) return "Please configure API key first."
        if (config.workflowId.isBlank()) return "Please configure workflowId first."
        if (config.promptNodeId.isBlank() || config.promptFieldName.isBlank()) {
            return "Please configure prompt node mapping first."
        }
        if (input.hasInputImage && config.imageInputNodeId.isBlank()) {
            return "Please configure image input nodeId first."
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

    fun hasInputImageMapping(
        config: WorkflowConfig,
        mode: GenerationMode,
    ): Boolean {
        return when (mode) {
            GenerationMode.IMAGE -> config.imageInputNodeId.isNotBlank()
            GenerationMode.VIDEO -> config.videoImageInputNodeId.isNotBlank()
        }
    }
}
