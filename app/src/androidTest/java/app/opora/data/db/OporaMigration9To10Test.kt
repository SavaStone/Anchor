package app.opora.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import java.io.File

/**
 * Миграция 9→10 на настоящей базе предыдущей версии: файл снят с
 * работающего приложения до добавления снимков расписания.
 */
@RunWith(AndroidJUnit4::class)
class OporaMigration9To10Test {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "opora-migration-9-10.db"
    private lateinit var dbFile: File

    @Before
    fun copyFixture() {
        dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach {
            if (it.exists()) it.delete()
        }
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("opora-v9.db").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    @After
    fun cleanup() {
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach {
            runCatching { it.delete() }
        }
    }

    @Test
    fun realV9DatabaseMigratesAndBackfillsSnapshotsAndPause(): Unit = runBlocking {
        val pausedAt = 1_760_000_000_000L
        val raw = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        val distinctGroups = raw.rawQuery(
            "SELECT COUNT(*) FROM (SELECT DISTINCT medicationId, scheduledDate " +
                "FROM dose_logs WHERE isPrn = 0)",
            null
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        val medsBefore = raw.rawQuery("SELECT COUNT(*) FROM medications", null)
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        val dosesBefore = raw.rawQuery("SELECT COUNT(*) FROM dose_logs", null)
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        val moodsBefore = raw.rawQuery("SELECT COUNT(*) FROM mood_entries", null)
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        raw.execSQL(
            "UPDATE medications SET active = 0, discontinuedAt = NULL, updatedAt = ? " +
                "WHERE id = (SELECT id FROM medications LIMIT 1)",
            arrayOf<Any?>(pausedAt)
        )
        raw.close()

        val migrated = Room.databaseBuilder(context, OporaDatabase::class.java, name)
            .addMigrations(OporaDatabase.MIGRATION_9_10, OporaDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
        try {
            val version = migrated.openHelper.writableDatabase.version
            assertEquals(11, version)

            assertEquals(medsBefore, migrated.medicationDao().getAll().size)
            assertEquals(dosesBefore, migrated.doseDao().getAll().size)
            assertEquals(moodsBefore, migrated.moodDao().getAll().size)

            val snapshots = migrated.scheduleSnapshotDao().getAll()
            assertEquals(distinctGroups, snapshots.size)
            assertTrue(snapshots.all { it.assumed })
            assertTrue(snapshots.any { it.timesCsv.isNotBlank() })
            assertTrue(snapshots.all { it.timesCsv.split(',').none { t -> t.isBlank() } })

            assertTrue(
                migrated.journalDao().getAll().all { it.searchText.isNotBlank() }
            )
            val pauses = migrated.medicationPauseDao().getAll()
            assertEquals(1, pauses.size)
            val pause = pauses.single()
            assertNotNull(pause)
            assertEquals(pausedAt, pause.startAt)
            assertEquals(null, pause.endAt)
            assertTrue(pause.assumed)
        } finally {
            migrated.close()
        }
    }
}
