package app.opora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import app.opora.data.prefs.SettingsRepository
import app.opora.domain.DEFAULT_HIDDEN_HOME_BLOCKS
import app.opora.domain.DEFAULT_HOME_ORDER
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeOrderPersistenceTest {
    @Test
    fun orderIsReadByFreshRepositoryInstance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = SettingsRepository(context)
        try {
            first.setHomeBlocks("meds,mood,journal,appointment")
            first.setHiddenHomeBlocks("appointment")
            val fresh = SettingsRepository(context)
            val saved = fresh.settings.first()
            assertEquals("meds,mood,journal,appointment", saved.homeBlocks)
            assertEquals("appointment", saved.hiddenHomeBlocks)
        } finally {
            first.setHomeBlocks(DEFAULT_HOME_ORDER)
            first.setHiddenHomeBlocks(DEFAULT_HIDDEN_HOME_BLOCKS)
        }
    }
}
