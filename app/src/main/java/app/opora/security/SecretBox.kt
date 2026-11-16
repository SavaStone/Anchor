package app.opora.security

/**
 * Seals small local secrets with a device-bound key. Production uses the
 * Android Keystore ([KeystoreSecretBox]); tests inject a deterministic fake
 * to prove that plaintext never reaches storage and that a reset key is
 * detected instead of being silently replaced.
 */
interface SecretBox {
    fun encrypt(plain: String): String?

    fun decrypt(encoded: String): String?
}

object KeystoreSecretBox : SecretBox {
    override fun encrypt(plain: String): String? = SecretStore.encrypt(plain)

    override fun decrypt(encoded: String): String? = SecretStore.decrypt(encoded)
}
