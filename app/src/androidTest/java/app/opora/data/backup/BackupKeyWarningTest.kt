package app.opora.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupKeyWarningTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "opora_auto_warning_test"
    private val prefs by lazy {
        context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
    }
    private lateinit var db: OporaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs.edit().putString("key", "not-a-valid-encrypted-key").remove("key_changed").commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        db.close()
    }

    @Test
    fun invalidStoredKeyProducesExplicitWarning() = runBlocking {
        val backup = BackupManager(
            context,
            db,
            SettingsRepository(context),
            autoPrefsName = prefsName
        )
        assertNotNull(backup.keyWarning())
    }
}
