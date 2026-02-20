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
    fun `validateForSettings fails when WebDAV enabled but required fields missing`() {
        val config = validConfig().copy(
            webDavEnabled = true,
            webDavServerUrl = "",
            webDavUsername = "user",
            webDavPassword = "pwd",
            webDavSyncPassphrase = "passphrase",
        )

        val error = WorkflowConfigValidator.validateForSettings(config)

        assertEquals("WebDAV server URL is required when sync is enabled.", error)
    }

    @Test
    fun `validateForSettings allows empty WebDAV fields when sync is disabled`() {
        val config = validConfig().copy(
            webDavEnabled = false,
            webDavServerUrl = "",
            webDavUsername = "",
            webDavPassword = "",
            webDavSyncPassphrase = "",
        )

        val error = WorkflowConfigValidator.validateForSettings(config)

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
    fun `validateForGenerate fails when video length nodeId is configured but length is invalid`() {
        val config = validConfig().copy(
            videoWorkflowId = "video-workflow",
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
            videoLengthNodeId = "31",
        )

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.VIDEO,
                videoLengthFrames = null,
            ),
        )

        assertEquals("Please enter a valid video length (> 0).", error)
    }

    @Test
    fun `validateForGenerate allows video mode when length mapping and value are both valid`() {
        val config = validConfig().copy(
            videoWorkflowId = "video-workflow",
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
            videoLengthNodeId = "31",
        )

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.VIDEO,
                videoLengthFrames = 80,
            ),
        )

        assertNull(error)
    }

    @Test
    fun `validateForGenerate fails when image input is selected but image input nodeId is missing`() {
        val config = validConfig()

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.IMAGE,
                hasInputImage = true,
            ),
        )

        assertEquals("Please configure image input nodeId first.", error)
    }

    @Test
    fun `validateForGenerate allows image input when image input nodeId is configured`() {
        val config = validConfig().copy(imageInputNodeId = "10")

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.IMAGE,
                hasInputImage = true,
            ),
        )

        assertNull(error)
    }

    @Test
    fun `validateForGenerate fails when video image input is selected but mapping is missing`() {
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
                hasInputImage = true,
            ),
        )

        assertEquals("Please configure video image input nodeId first.", error)
    }

    @Test
    fun `validateForGenerate allows video image input when mapping is configured`() {
        val config = validConfig().copy(
            videoWorkflowId = "video-workflow",
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
            videoImageInputNodeId = "13",
        )

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.VIDEO,
                hasInputImage = true,
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
