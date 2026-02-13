package io.github.c1921.comfyui_assistant.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RunningHubErrorMapperTest {
    private val mapper = DefaultRunningHubMessageMapper()

    @Test
    fun `map returns expected message for 803`() {
        val message = mapper.map(803, "fallback")
        assertEquals("nodeInfoList does not match the workflow mapping.", message)
    }

    @Test
    fun `map returns fallback for unknown code`() {
        val message = mapper.map(9999, "fallback")
        assertEquals("fallback", message)
    }
}
