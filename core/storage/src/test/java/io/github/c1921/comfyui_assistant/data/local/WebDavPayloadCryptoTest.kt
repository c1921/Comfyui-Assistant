package io.github.c1921.comfyui_assistant.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavPayloadCryptoTest {
    private val crypto = WebDavPayloadCrypto()

    @Test
    fun `encrypt and decrypt roundtrip returns original payload`() {
        val plain = """{"apiKey":"abc","updatedAtEpochMs":123456789}"""
        val passphrase = "sync-passphrase"

        val encrypted = crypto.encrypt(plain, passphrase).getOrThrow()
        val decrypted = crypto.decrypt(encrypted, passphrase).getOrThrow()

        assertEquals(plain, decrypted)
    }

    @Test
    fun `decrypt fails when passphrase is wrong`() {
        val plain = """{"workflowId":"wf-1"}"""
        val encrypted = crypto.encrypt(plain, "correct-pass").getOrThrow()

        val decrypted = crypto.decrypt(encrypted, "wrong-pass")

        assertTrue(decrypted.isFailure)
    }

    @Test
    fun `encrypt uses randomized salt and iv`() {
        val plain = """{"prompt":"hello"}"""
        val passphrase = "same-passphrase"

        val first = crypto.encrypt(plain, passphrase).getOrThrow()
        val second = crypto.encrypt(plain, passphrase).getOrThrow()

        assertNotEquals(first, second)
    }
}
