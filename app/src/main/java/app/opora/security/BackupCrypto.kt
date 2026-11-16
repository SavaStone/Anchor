package app.opora.security

import app.opora.R
import app.opora.data.UserMessageException

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, passphrase-based encryption for backup files.
 * Format: "OPORA1" | salt (16) | iv (12) | AES-GCM ciphertext+tag
 */
object BackupCrypto {
    private val MAGIC = "OPORA1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + SALT_BYTES + IV_BYTES &&
            MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun encrypt(plain: String, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = derive(salt, passphrase)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return MAGIC + salt + iv + body
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): String {
        if (!isEncrypted(bytes)) throw UserMessageException(R.string.errors_not_encrypted)
        var offset = MAGIC.size
        val salt = bytes.copyOfRange(offset, offset + SALT_BYTES); offset += SALT_BYTES
        val iv = bytes.copyOfRange(offset, offset + IV_BYTES); offset += IV_BYTES
        val body = bytes.copyOfRange(offset, bytes.size)
        val key = derive(salt, passphrase)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun derive(salt: ByteArray, passphrase: CharArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}
