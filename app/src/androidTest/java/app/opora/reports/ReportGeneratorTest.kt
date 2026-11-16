package app.opora.reports

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.OporaRepository
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportGeneratorTest {
    private lateinit var context: Context
    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        repo = OporaRepository(context, db, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun emptyReportIsGeneratedWithoutCrash() = runBlocking {
        val report = ReportGenerator(context, repo, settings)
            .generate(days = 30, includeJournal = true, includeSymptoms = true)

        assertTrue("PDF must exist", report.exists())
        assertTrue("PDF must not be empty", report.length() > 0)
        assertTrue(report.delete())
    }
}
