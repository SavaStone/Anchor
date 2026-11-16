package app.opora.data.backup

import app.opora.security.SecretBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupPasswordBoxTest {

    /** Мок Keystore: детерминированный конверт, который можно «сбросить». */
    private class FakeSecretBox(var keyAvailable: Boolean = true) : SecretBox {
        override fun encrypt(plain: String): String? =
            if (keyAvailable) "sealed:$plain" else null

        override fun decrypt(encoded: String): String? =
            if (keyAvailable && encoded.startsWith("sealed:")) {
                encoded.removePrefix("sealed:")
            } else {
                null
            }
    }

    @Test
    fun `password is stored sealed and opens back`() {
        val box = AutoBackupPasswordBox(FakeSecretBox())
        val sealed = box.seal("opora-pass".toCharArray())
        assertEquals("sealed:opora-pass", sealed)
        assertNotEquals("opora-pass", sealed)
        assertArrayEquals(
            "opora-pass".toCharArray(),
            box.open(sealed)
        )
        assertFalse(box.isLost(sealed))
    }

    @Test
    fun `reset keystore is reported as lost instead of silently replaced`() {
        val secret = FakeSecretBox()
        val box = AutoBackupPasswordBox(secret)
        val sealed = box.seal("opora-pass".toCharArray())
        secret.keyAvailable = false
        assertNull(box.open(sealed))
        assertTrue(box.isLost(sealed))
    }

    @Test
    fun `empty or missing password is not a lost key`() {
        val box = AutoBackupPasswordBox(FakeSecretBox())
        assertNull(box.seal(CharArray(0)))
        assertNull(box.open(null))
        assertNull(box.open(""))
        assertFalse(box.isLost(null))
        assertFalse(box.isLost(""))
    }

    @Test
    fun `failed encryption does not yield a stored envelope`() {
        val box = AutoBackupPasswordBox(FakeSecretBox(keyAvailable = false))
        assertNull(box.seal("opora-pass".toCharArray()))
    }
}
