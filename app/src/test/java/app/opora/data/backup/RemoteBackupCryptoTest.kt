package app.opora.data.backup

import app.opora.security.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBackupCryptoTest {
    @Test
    fun newBackupPasswordDecryptsNewFile() {
        val bytes = BackupCrypto.encrypt("""{"app":"opora"}""", "backup-pass".toCharArray())
        assertEquals(
            """{"app":"opora"}""",
            decryptRemoteBackup(bytes, "backup-pass", "webdav-pass")
        )
    }

    @Test
    fun legacyWebDavPasswordStillReadsOldFile() {
        val bytes = BackupCrypto.encrypt("""{"app":"opora"}""", "webdav-pass".toCharArray())
        assertEquals(
            """{"app":"opora"}""",
            decryptRemoteBackup(bytes, "new-backup-pass", "webdav-pass")
        )
    }

    @Test(expected = IllegalStateException::class)
    fun wrongPasswordsFailWithFriendlyError() {
        val bytes = BackupCrypto.encrypt("""{"app":"opora"}""", "other".toCharArray())
        decryptRemoteBackup(bytes, "new", "old")
    }
}
