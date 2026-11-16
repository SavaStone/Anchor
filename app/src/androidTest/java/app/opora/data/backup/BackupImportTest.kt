package app.opora.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.UserMessageException
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.security.BackupCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class BackupImportTest {
    private lateinit var context: Context
    private lateinit var db: OporaDatabase
    private lateinit var backup: BackupManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupManager(context, db, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun databaseDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(vararg values: Any?) {
            values.forEach { digest.update((it?.toString() ?: "null").toByteArray()) }
            digest.update(byteArrayOf(0))
        }
        db.medicationDao().getAll().sortedBy { it.id }.forEach {
            add(it.id, it.name, it.dosage, it.active, it.inventoryCount, it.updatedAt)
        }
        db.doseDao().getAll().sortedBy { it.id }.forEach {
            add(it.id, it.medicationId, it.scheduledDate, it.scheduledTime, it.status)
        }
        db.moodDao().getAll().sortedBy { it.id }.forEach {
            add(it.id, it.date, it.mood, it.note, it.updatedAt)
        }
        db.journalDao().getAll().sortedBy { it.id }.forEach { add(it.id, it.title, it.body) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Backups report failures as resource ids so the message follows the app locale. */
    private fun messageRes(error: Throwable?): Int? =
        (error as? UserMessageException)?.messageRes

    private fun uri(bytes: ByteArray): Uri {
        val file = File(context.cacheDir, "backup-test-${System.nanoTime()}.bin")
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    @Test
    fun emptyFileIsRejectedAsBackup() = runBlocking {
        val error = runCatching { backup.readBackup(uri(ByteArray(0)), null) }.exceptionOrNull()
        assertEquals(R.string.errors_not_a_backup, messageRes(error))
    }

    @Test
    fun corruptFileIsRejectedAsBackupWithoutTouchingDatabase() = runBlocking {
        db.medicationDao().upsert(
            app.opora.data.db.MedicationEntity(
                name = "Остаётся",
                startDate = 0
            )
        )
        val before = databaseDigest()
        val error = runCatching {
            backup.readBackup(uri("not a backup".toByteArray()), null)
        }.exceptionOrNull()
        assertEquals(R.string.errors_not_a_backup, messageRes(error))
        assertEquals(before, databaseDigest())
    }

    @Test
    fun encryptedFileWithWrongPassphraseIsRejected() = runBlocking {
        val bytes = BackupCrypto.encrypt("{}", "правильная фраза".toCharArray())
        val error = runCatching {
            backup.readBackup(uri(bytes), "неверная фраза".toCharArray())
        }.exceptionOrNull()
        assertEquals(R.string.errors_wrong_passphrase, messageRes(error))
    }

    @Test
    fun wrongPasswordThenCorrectPasswordCanReuseTheSameUri() = runBlocking {
        val plain = """{"formatVersion":2,"exportedAt":1,"appVersion":"1.0.0",""" +
            """"medications":[{"id":"retry-med","name":"Повтор","startDate":20000}]}"""
        val file = uri(BackupCrypto.encrypt(plain, "правильная".toCharArray()))
        val error = runCatching {
            backup.readBackup(file, "неверная".toCharArray())
        }.exceptionOrNull()
        assertEquals(R.string.errors_wrong_passphrase, messageRes(error))

        val payload = backup.readBackup(file, "правильная".toCharArray())
        assertEquals("Повтор", payload.medications.single().name)
    }

    @Test
    fun legacyPayloadWithoutMarkerIsAValidBackup() = runBlocking {
        val payload = backup.readBackup(
            uri("""{"formatVersion":2,"exportedAt":1,"appVersion":"1.0.0"}""".toByteArray()),
            null
        )
        assertEquals(0, payload.totalRecords)
    }

    @Test
    fun webDavTransportAndBackupPasswordsAreSeparate() = runBlocking {
        val settings = SettingsRepository(context)
        try {
            settings.setWebDav(
                "https://example.com/opora",
                "user",
                "webdav-pass",
                "backup-pass"
            )
            val config = settings.webDavConfig()!!
            assertEquals("webdav-pass", config.password)
            assertEquals("backup-pass", config.backupPassword)
        } finally {
            settings.clearWebDav()
        }
    }

    @Test
    fun version2BackupStillRestores() = runBlocking {
        val text = """{"formatVersion":2,"exportedAt":1,"appVersion":"1.0.0",""" +
            """"medications":[{"id":"v2-med","name":"Из версии 2","startDate":20000}]}"""
        val payload = backup.readBackup(uri(text.toByteArray()), null)
        backup.restore(payload)
        assertEquals("Из версии 2", db.medicationDao().getAll().single().name)
    }

    @Test
    fun foreignJsonIsRejectedAsBackup() = runBlocking {
        val error = runCatching {
            backup.readBackup(uri("""{"notes":"чужой файл"}""".toByteArray()), null)
        }.exceptionOrNull()
        assertEquals(R.string.errors_not_a_backup, messageRes(error))
    }
}
