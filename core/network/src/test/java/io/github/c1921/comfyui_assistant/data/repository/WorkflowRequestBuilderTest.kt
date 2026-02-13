package io.github.c1921.comfyui_assistant.data.repository

import io.github.c1921.comfyui_assistant.domain.GenerationInput
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
        )

        val result = WorkflowRequestBuilder.buildNodeInfoList(config, input)

        assertEquals(1, result.size)
        assertEquals("6", result[0].nodeId)
    }

    @Test
    fun `validateForGenerate returns null when required fields are present`() {
        val config = WorkflowConfig(
            apiKey = "test-key",
            workflowId = "workflow-1",
            promptNodeId = "6",
            promptFieldName = "text",
        )

        val error = WorkflowConfigValidator.validateForGenerate(
            config = config,
            input = GenerationInput(prompt = "test prompt", negative = ""),
        )

        assertNull(error)
    }
}
