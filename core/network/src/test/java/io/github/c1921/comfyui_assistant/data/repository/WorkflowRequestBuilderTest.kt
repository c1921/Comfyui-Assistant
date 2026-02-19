package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.domain.GenerationInput
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.ImageAspectPreset
import io.github.c1921.comfyui_assistant.domain.WorkflowConfig
import io.github.c1921.comfyui_assistant.domain.WorkflowConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowRequestBuilderTest {
    @Test
    fun `buildNodeInfoList includes prompt and negative when configured`() {
        val config = WorkflowConfig(
            promptNodeId = "6",
            promptFieldName = "text",
            negativeNodeId = "7",
            negativeFieldName = "text",
        )
        val input = GenerationInput(
            prompt = "a cat in city",
            negative = "blurry",
            mode = GenerationMode.IMAGE,
        )

        val result = WorkflowRequestBuilder.buildNodeInfoList(config, input)

        assertEquals(2, result.size)
        assertEquals("6", result[0].nodeId)
        assertEquals("text", result[0].fieldName)
        assertEquals("a cat in city", result[0].fieldValue)
        assertEquals("7", result[1].nodeId)
        assertEquals("text", result[1].fieldName)
        assertEquals("blurry", result[1].fieldValue)
    }

    @Test
    fun `buildNodeInfoList only includes prompt when negative is empty`() {
        val config = WorkflowConfig(
            promptNodeId = "6",
            promptFieldName = "text",
            negativeNodeId = "7",
            negativeFieldName = "text",
        )
        val input = GenerationInput(
            prompt = "a cat in city",
            negative = "",
            mode = GenerationMode.IMAGE,
        )

        val result = WorkflowRequestBuilder.buildNodeInfoList(config, input)

        assertEquals(1, result.size)
        assertEquals("6", result[0].nodeId)
    }

    @Test
    fun `buildNodeInfoList includes width and height when size mapping configured`() {
        val config = WorkflowConfig(
            promptNodeId = "6",
            promptFieldName = "text",
            sizeNodeId = "5",
        )
        val input = GenerationInput(
            prompt = "a cat in city",
            negative = "",
            mode = GenerationMode.IMAGE,
            imagePreset = ImageAspectPreset.RATIO_16_9,
        )

        val result = WorkflowRequestBuilder.buildNodeInfoList(config, input)

        assertEquals(3, result.size)
        assertEquals("6", result[0].nodeId)
        assertEquals("5", result[1].nodeId)
        assertEquals("width", result[1].fieldName)
        assertEquals(1392, result[1].fieldValue)
        assertEquals("5", result[2].nodeId)
        assertEquals("height", result[2].fieldName)
        assertEquals(752, result[2].fieldValue)
    }

    @Test
    fun `buildNodeInfoList ignores size when mapping is not configured`() {
        val config = WorkflowConfig(
            promptNodeId = "6",
            promptFieldName = "text",
        )
        val input = GenerationInput(
            prompt = "a cat in city",
            negative = "",
            mode = GenerationMode.IMAGE,
            imagePreset = ImageAspectPreset.RATIO_16_9,
        )

        val result = WorkflowRequestBuilder.buildNodeInfoList(config, input)

        assertEquals(1, result.size)
        assertEquals("6", result[0].nodeId)
    }

    @Test
    fun `buildNodeInfoList uses video prompt mapping in video mode`() {
        val config = WorkflowConfig(
            videoPromptNodeId = "12",
            videoPromptFieldName = "text",
        )
        val input = GenerationInput(
            prompt = "a panda dancing",
            negative = "unused",
            mode = GenerationMode.VIDEO,
            imagePreset = ImageAspectPreset.RATIO_16_9,
        )

        val result = WorkflowRequestBuilder.buildNodeInfoList(config, input)

        assertEquals(1, result.size)
        assertEquals("12", result[0].nodeId)
        assertEquals("text", result[0].fieldName)
        assertEquals("a panda dancing", result[0].fieldValue)
    }

    @Test
    fun `validateForGenerate returns null when required image fields are present`() {
        val config = WorkflowConfig(
            apiKey = "test-key",
            workflowId = "workflow-1",
            promptNodeId = "6",
            promptFieldName = "text",
        )

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(
                prompt = "test prompt",
                negative = "",
                mode = GenerationMode.IMAGE,
            ),
        )

        assertNull(error)
    }
}
