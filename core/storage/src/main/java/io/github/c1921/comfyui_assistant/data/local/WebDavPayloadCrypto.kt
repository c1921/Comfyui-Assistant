package io.github.c1921.comfyui_assistant.data.local

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WebDavPayloadCrypto(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(
        plainText: String,
        passphrase: String,
    ): Result<String> {
        return runCatching {
            require(passphrase.isNotBlank()) { "Sync passphrase cannot be empty." }
            val salt = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)
            val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
            val key = deriveAesKey(
                passphrase = passphrase,
                salt = salt,
                iterations = DEFAULT_ITERATIONS,
            )
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            JSONObject().apply {
                put("schemaVersion", ENVELOPE_SCHEMA_VERSION)
                put("salt", salt.base64())
                put("iv", iv.base64())
                put("ciphertext", ciphertext.base64())
                put("iterations", DEFAULT_ITERATIONS)
            }.toString()
        }
    }

    fun decrypt(
        envelopeJson: String,
        passphrase: String,
    ): Result<String> {
        return runCatching {
            require(passphrase.isNotBlank()) { "Sync passphrase cannot be empty." }
            val envelope = JSONObject(envelopeJson)
            val schemaVersion = envelope.optInt("schemaVersion", -1)
            require(schemaVersion == ENVELOPE_SCHEMA_VERSION) { "Unsupported crypto schema: $schemaVersion" }

            val iterations = envelope.optInt("iterations", DEFAULT_ITERATIONS).coerceAtLeast(1)
            val salt = envelope.optString("salt").decodeBase64()
            val iv = envelope.optString("iv").decodeBase64()
            val ciphertext = envelope.optString("ciphertext").decodeBase64()

            val key = deriveAesKey(
                passphrase = passphrase,
                salt = salt,
                iterations = iterations,
            )
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(ciphertext)
            plainBytes.toString(Charsets.UTF_8)
        }
    }

    private fun deriveAesKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
    ): SecretKeySpec {
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keySpec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            iterations,
            KEY_LENGTH_BITS,
        )
        val secret = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(secret, AES_KEY_ALGORITHM)
    }

    private fun ByteArray.base64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    private fun String.decodeBase64(): ByteArray {
        return Base64.getDecoder().decode(this)
    }

    private companion object {
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val AES_KEY_ALGORITHM = "AES"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_LENGTH_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val SALT_LENGTH = 16
        const val IV_LENGTH = 12
        const val DEFAULT_ITERATIONS = 120_000
        const val ENVELOPE_SCHEMA_VERSION = 1
    }
}

