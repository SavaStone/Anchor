package app.opora.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCryptoTest {

    private val passphrase = "надёжная фраза 42".toCharArray()

    @Test
    fun `encrypt then decrypt returns the original text`() {
        val plain = """{"moods":[{"note":"Привет, мир"}],"version":2}"""
        val encrypted = BackupCrypto.encrypt(plain, passphrase)
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertArrayEquals("OPORA1".toByteArray(Charsets.US_ASCII), encrypted.copyOfRange(0, 6))
        assertNotEquals(plain, String(encrypted, Charsets.UTF_8))
        assertTrue(BackupCrypto.decrypt(encrypted, passphrase) == plain)
    }

    @Test
    fun `same plaintext encrypts to different bytes`() {
        val first = BackupCrypto.encrypt("данные", passphrase)
        val second = BackupCrypto.encrypt("данные", passphrase)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `wrong passphrase fails authentication`() {
        val encrypted = BackupCrypto.encrypt("секрет", passphrase)
        val wrong = "неверная фраза".toCharArray()
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt(encrypted, wrong)
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val encrypted = BackupCrypto.encrypt("секрет", passphrase)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt(encrypted, passphrase)
        }
    }

    @Test
    fun `isEncrypted rejects plain and short input`() {
        assertFalse(BackupCrypto.isEncrypted(ByteArray(0)))
        assertFalse(BackupCrypto.isEncrypted("OPORA1".toByteArray()))
        assertFalse(BackupCrypto.isEncrypted("обычный json".toByteArray()))
    }

    @Test
    fun `decrypt rejects a file without the backup header`() {
        val error = assertThrows(app.opora.data.UserMessageException::class.java) {
            BackupCrypto.decrypt("plain text".toByteArray(), passphrase)
        }
        assertEquals(app.opora.R.string.errors_not_encrypted, error.messageRes)
    }
}
