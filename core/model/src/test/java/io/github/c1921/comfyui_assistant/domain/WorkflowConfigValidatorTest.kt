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
                mode = GenerationMode.IMAGE,
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
                mode = GenerationMode.IMAGE,
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

    @Test
    fun `validateForSettings fails when video mapping is incomplete`() {
        val config = validConfig().copy(videoWorkflowId = "video-workflow")

        val error = WorkflowConfigValidator.validateForSettings(config)

        assertEquals(
            "Video mapping requires workflowId, prompt nodeId and prompt fieldName.",
            error,
        )
    }

    @Test
    fun `validateForSettings allows complete video mapping`() {
        val config = validConfig().copy(
            videoWorkflowId = "video-workflow",
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
        )

        val error = WorkflowConfigValidator.validateForSettings(config)

        assertNull(error)
    }

    @Test
    fun `validateForGenerate ignores incomplete video mapping in image mode`() {
        val config = validConfig().copy(videoWorkflowId = "video-workflow")

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.IMAGE,
                imagePreset = ImageAspectPreset.RATIO_1_1,
            ),
        )

        assertNull(error)
    }

    @Test
    fun `validateForGenerate fails for video mode when video mapping is missing`() {
        val config = validConfig()

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.VIDEO,
            ),
        )

        assertEquals("Please configure video workflowId first.", error)
    }

    @Test
    fun `validateForGenerate allows video mode when mapping is complete`() {
        val config = validConfig().copy(
            videoWorkflowId = "video-workflow",
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
        )

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.VIDEO,
            ),
        )

        assertNull(error)
    }

    @Test
    fun `detectMediaKind returns image for png output`() {
        val output = GeneratedOutput(
            fileUrl = "https://example.com/output.png",
            fileType = "png",
            nodeId = null,
        )

        assertEquals(OutputMediaKind.IMAGE, output.detectMediaKind())
    }

    @Test
    fun `detectMediaKind returns video for mp4 output`() {
        val output = GeneratedOutput(
            fileUrl = "https://example.com/output.mp4",
            fileType = "mp4",
            nodeId = null,
        )

        assertEquals(OutputMediaKind.VIDEO, output.detectMediaKind())
    }

    @Test
    fun `detectMediaKind returns unknown for unsupported output`() {
        val output = GeneratedOutput(
            fileUrl = "https://example.com/output.bin",
            fileType = "bin",
            nodeId = null,
        )

        assertEquals(OutputMediaKind.UNKNOWN, output.detectMediaKind())
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
