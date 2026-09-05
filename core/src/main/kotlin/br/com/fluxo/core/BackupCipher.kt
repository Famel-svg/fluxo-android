package br.com.fluxo.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.DestroyFailedException

/**
 * Portable password-protected backup envelope. Call off the UI thread.
 *
 * V1: ASCII FLUXOBKP (8 bytes), version (1 byte), salt (16 bytes), nonce (12 bytes),
 * ciphertext, GCM tag (16 bytes). The entire 37-byte header is authenticated as AAD.
 * V1 fixes PBKDF2-HMAC-SHA256 at 600,000 iterations and a 256-bit AES key; no untrusted
 * header field can increase KDF work. Future parameters require a new version.
 *
 * Malformed/unsupported envelopes throw IllegalArgumentException before key derivation.
 * Authentication failures propagate GeneralSecurityException; callers must not restore data.
 * Caller arrays are never cleared or modified. Callers own their eventual erasure; JVM/provider
 * internal copies cannot be guaranteed erased, even though owned key buffers are cleared.
 */
object BackupCipher {
    const val MAX_PLAINTEXT_BYTES = 16 * 1024 * 1024
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 1 + SALT_BYTES + NONCE_BYTES
    const val MAX_BLOB_BYTES = MAX_PLAINTEXT_BYTES + HEADER_BYTES + TAG_BYTES
    private val magic = "FLUXOBKP".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Backup exceeds the supported size." }
        require(password.size >= 12) { "Backup password must contain at least 12 characters." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = magic + byteArrayOf(VERSION.toByte()) + salt + nonce
        return withKey(password, salt) { key ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(header)
            val encrypted = cipher.doFinal(plaintext)
            try {
                header + encrypted
            } finally {
                encrypted.fill(0)
            }
        }
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        require(blob.size in (HEADER_BYTES + TAG_BYTES)..MAX_BLOB_BYTES) {
            "Invalid backup size."
        }
        require(magic.indices.all { blob[it] == magic[it] }) { "Invalid backup format." }
        require(blob[magic.size].toInt() == VERSION) { "Unsupported backup version." }
        val salt = blob.copyOfRange(9, 9 + SALT_BYTES)
        val nonce = blob.copyOfRange(9 + SALT_BYTES, HEADER_BYTES)
        return withKey(password, salt) { key ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(blob, 0, HEADER_BYTES)
            // One-shot authentication: no unauthenticated plaintext is exposed to the caller.
            cipher.doFinal(blob, HEADER_BYTES, blob.size - HEADER_BYTES)
        }
    }

    private fun <T> withKey(password: CharArray, salt: ByteArray, action: (SecretKeySpec) -> T): T {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        try {
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            try {
                val bytes = derived.encoded
                try {
                    val key = SecretKeySpec(bytes, "AES")
                    try {
                        return action(key)
                    } finally {
                        try { key.destroy() } catch (_: DestroyFailedException) { /* Provider limitation. */ }
                    }
                } finally {
                    bytes.fill(0)
                }
            } finally {
                try { derived.destroy() } catch (_: DestroyFailedException) { /* Provider limitation. */ }
            }
        } finally {
            spec.clearPassword()
        }
    }
}
