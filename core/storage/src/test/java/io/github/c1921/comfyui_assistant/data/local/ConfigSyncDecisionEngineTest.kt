package io.github.c1921.comfyui_assistant.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigSyncDecisionEngineTest {
    private val engine = ConfigSyncDecisionEngine()

    @Test
    fun `decide chooses pull when remote is newer`() {
        val action = engine.decide(
            localUpdatedAtEpochMs = 100L,
            remoteUpdatedAtEpochMs = 200L,
        )

        assertEquals(ConfigSyncAction.PULL_REMOTE_TO_LOCAL, action)
    }

    @Test
    fun `decide chooses push when local is newer`() {
        val action = engine.decide(
            localUpdatedAtEpochMs = 300L,
            remoteUpdatedAtEpochMs = 200L,
        )

        assertEquals(ConfigSyncAction.PUSH_LOCAL_TO_REMOTE, action)
    }

    @Test
    fun `decide skips when timestamps are equal`() {
        val action = engine.decide(
            localUpdatedAtEpochMs = 1234L,
            remoteUpdatedAtEpochMs = 1234L,
        )

        assertEquals(ConfigSyncAction.SKIP_ALREADY_SYNCED, action)
    }

    @Test
    fun `decide chooses push when remote is missing`() {
        val action = engine.decide(
            localUpdatedAtEpochMs = 1234L,
            remoteUpdatedAtEpochMs = null,
        )

        assertEquals(ConfigSyncAction.PUSH_LOCAL_TO_REMOTE, action)
    }
}

