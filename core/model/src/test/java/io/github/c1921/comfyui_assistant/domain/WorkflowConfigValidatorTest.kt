package io.github.c1921.comfyui_assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowConfigValidatorTest {
    @Test
    fun `validateForGenerate allows 1 to 1 ratio without size mapping`() {
        val config = validConfig()

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                imagePreset = ImageAspectPreset.RATIO_1_1,
            ),
        )

        assertNull(error)
    }

    @Test
    fun `validateForGenerate fails for non 1 to 1 ratio when size mapping is missing`() {
        val config = validConfig()

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                imagePreset = ImageAspectPreset.RATIO_16_9,
            ),
        )

        assertEquals("Selected ratio requires width/height mapping in Settings.", error)
    }

    @Test
    fun `validateMappingConsistency allows single size node mapping`() {
        val config = validConfig().copy(sizeNodeId = "5")

        val error = WorkflowConfigValidator.validateMappingConsistency(config)

        assertNull(error)
    }

    private fun validConfig(): WorkflowConfig {
        return WorkflowConfig(
            apiKey = "api-key",
            workflowId = "workflow-1",
            promptNodeId = "6",
            promptFieldName = "text",
        )
    }
}
