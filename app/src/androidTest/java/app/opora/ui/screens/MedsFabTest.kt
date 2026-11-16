package app.opora.ui.screens

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.MedsViewModel
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MedsFabTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = OporaRepository(context, db, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addMedicationFabIsClickable() {
        var opened = false
        compose.setContent {
            OporaTheme {
                MedsScreen(
                    vm = MedsViewModel(repo),
                    onAddMedication = { opened = true },
                    onEditMedication = {}
                )
            }
        }

        compose.onNodeWithContentDescription(TestStrings.get(R.string.add_medication))
            .assertExists()
            .assertHasClickAction()
            .performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
