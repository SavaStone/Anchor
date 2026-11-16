package app.opora.ui.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Пустая форма не должна оставлять мёртвую кнопку «Сохранить»: причина
 * блокировки объясняется по нажатию, а переключатели — одна цель для TalkBack.
 */
@RunWith(AndroidJUnit4::class)
class MedEditorValidationTest {
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
        compose.setContent {
            OporaTheme {
                MedEditorScreen(
                    medId = null,
                    onClose = {},
                    repository = repo,
                    db = db
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.med_editor_save)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveWithEmptyNameExplainsWhatIsMissing() {
        compose.onNode(hasText(TestStrings.get(R.string.med_editor_save)) and hasClickAction()).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.med_editor_error_name)).assertExists()
    }

    @Test
    fun stockToggleIsOneAccessibleTarget() {
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(TestStrings.get(R.string.stock_track)))
        compose.onNode(hasText(TestStrings.get(R.string.stock_track)) and isToggleable()).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.stock_remaining)).assertExists()
    }
}
