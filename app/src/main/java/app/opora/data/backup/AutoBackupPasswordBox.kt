package app.opora.data.backup

import app.opora.security.SecretBox

/**
 * Обёртка над Keystore-шифрованием пароля автобэкапа. Пароль никогда не
 * попадает в DataStore открытым текстом: хранится только AES/GCM-конверт.
 * Если ключ Keystore сброшен (переустановка, сброс телефона), [open] вернёт
 * null, а [isLost] позволит честно сказать об этом в интерфейсе.
 */
class AutoBackupPasswordBox(private val secret: SecretBox) {

    fun seal(password: CharArray): String? {
        if (password.isEmpty()) return null
        return secret.encrypt(String(password))
    }

    fun open(sealed: String?): CharArray? {
        if (sealed.isNullOrBlank()) return null
        return secret.decrypt(sealed)?.takeIf { it.isNotEmpty() }?.toCharArray()
    }

    fun isLost(sealed: String?): Boolean = !sealed.isNullOrBlank() && open(sealed) == null
}
