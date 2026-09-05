package br.com.fluxo.core

import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    private val password = "Minha senha íntegra 🔐 2026".toCharArray()

    @Test fun `round trip preserves unicode data and caller owned arrays`() {
        val plaintext = "{\"categoria\":\"Alimentação\",\"nota\":\"Pix recebido 💰\"}".toByteArray()
        val original = plaintext.copyOf()
        val originalPassword = password.copyOf()
        val blob = BackupCipher.encrypt(plaintext, password)
        val originalBlob = blob.copyOf()
        assertArrayEquals(original, BackupCipher.decrypt(blob, password))
        assertArrayEquals(original, plaintext)
        assertArrayEquals(originalPassword, password)
        assertArrayEquals(originalBlob, blob)
    }

    @Test fun `each encryption generates independent salt and nonce`() {
        val first = BackupCipher.encrypt(byteArrayOf(1, 2, 3), password)
        val second = BackupCipher.encrypt(byteArrayOf(1, 2, 3), password)
        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(9, 25).contentEquals(second.copyOfRange(9, 25)))
        assertFalse(first.copyOfRange(25, 37).contentEquals(second.copyOfRange(25, 37)))
        assertArrayEquals(byteArrayOf(1, 2, 3), BackupCipher.decrypt(second, password))
    }

    @Test fun `wrong password and tampered salt nonce ciphertext or tag fail authentication`() {
        val blob = BackupCipher.encrypt("financial record".toByteArray(), password)
        val wrongPassword = "Outra senha diferente".toCharArray()
        assertThrows(AEADBadTagException::class.java) { BackupCipher.decrypt(blob, wrongPassword) }
        // Salt and nonce are part of the authenticated header, as are magic and version.
        listOf(9, 25, 37, blob.lastIndex).forEach { index ->
            val changed = blob.copyOf()
            changed[index] = (changed[index].toInt() xor 1).toByte()
            assertThrows("Tamper at $index", AEADBadTagException::class.java) {
                BackupCipher.decrypt(changed, password)
            }
        }
    }

    @Test fun `invalid magic versions and undersized headers are rejected`() {
        val blob = BackupCipher.encrypt(byteArrayOf(1), password)
        val wrongMagic = blob.copyOf().also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(wrongMagic, password) }
        listOf(0, 2, 127, 255).forEach { version ->
            val changed = blob.copyOf().also { it[8] = version.toByte() }
            assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(changed, password) }
        }
        listOf(0, 8, 9, 25, 37, 52).forEach { length ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupCipher.decrypt(blob.copyOf(length), password)
            }
        }
    }

    @Test fun `truncation and appended data cannot produce plaintext`() {
        val blob = BackupCipher.encrypt(ByteArray(32) { it.toByte() }, password)
        assertThrows(AEADBadTagException::class.java) {
            BackupCipher.decrypt(blob.copyOf(blob.size - 1), password)
        }
        assertThrows(AEADBadTagException::class.java) {
            BackupCipher.decrypt(blob + byteArrayOf(0), password)
        }
    }

    @Test fun `password minimum applies to encryption without mutating caller`() {
        val short = "12345678901".toCharArray()
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.encrypt(byteArrayOf(), short) }
        assertArrayEquals("12345678901".toCharArray(), short)
        val minimum = "123456789012".toCharArray()
        assertArrayEquals(byteArrayOf(), BackupCipher.decrypt(BackupCipher.encrypt(byteArrayOf(), minimum), minimum))
    }

    @Test fun `exact size ceiling round trips and over ceiling inputs are rejected`() {
        val largest = ByteArray(BackupCipher.MAX_PLAINTEXT_BYTES) { (it % 251).toByte() }
        val blob = BackupCipher.encrypt(largest, password)
        assertEquals(BackupCipher.MAX_BLOB_BYTES, blob.size)
        assertArrayEquals(largest, BackupCipher.decrypt(blob, password))
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.encrypt(ByteArray(BackupCipher.MAX_PLAINTEXT_BYTES + 1), password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(ByteArray(BackupCipher.MAX_BLOB_BYTES + 1), password)
        }
    }
}
