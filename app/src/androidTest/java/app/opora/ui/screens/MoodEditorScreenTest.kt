package app.opora.ui.screens

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.MoodViewModel
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Выход из отметки: без выбранного уровня детали не должны теряться молча,
 * но обычный сценарий «выбрал лицо → назад» обязан оставаться без диалога.
 */
class MoodEditorScreenTest {

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

    private fun showEditor(onClose: () -> Unit) {
        compose.setContent {
            OporaTheme {
                MoodEditorScreen(
                    initialDate = LocalDate.now(),
                    vm = MoodViewModel(repo),
                    onClose = onClose
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.mood_scale_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun toggleAnxiety() {
        compose.onNode(hasText(TestStrings.get(R.string.mood_anxiety_title)) and isToggleable()).performClick()
    }

    private fun pressBack() {
        compose.onNodeWithContentDescription(TestStrings.get(R.string.common_back)).performClick()
    }

    @Test
    fun timePickerIsAvailable() {
        showEditor {}
        compose.onNodeWithText(
            TestStrings.get(R.string.mood_time_chip, "").trimEnd(),
            substring = true
        ).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.common_pick_time)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.common_pick_time)).assertExists()
    }

    @Test
    fun detailsWithoutMoodAsksAndReturnsToScale() {
        var closed = false
        showEditor { closed = true }
        toggleAnxiety()
        pressBack()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.mood_need_mood_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.mood_choose_mood)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.mood_need_mood_title)).fetchSemanticsNodes().isEmpty()
        }
        assertFalse(closed)

        compose.onNodeWithContentDescription(TestStrings.moodDescription(3)).performClick()
        // Шапка с «Назад» — первый элемент LazyColumn, после прокрутки к шкале
        // она ещё в дереве, но тесту надёжнее вернуться к началу списка.
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) { closed }
    }

    @Test
    fun detailsWithoutMoodCanExitWithoutSaving() {
        var closed = false
        showEditor { closed = true }
        toggleAnxiety()
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.mood_exit_without_saving)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.mood_exit_without_saving)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { closed }
    }

    @Test
    fun moodOnlyClosesWithoutDialog() {
        var closed = false
        showEditor { closed = true }
        compose.onNodeWithContentDescription(TestStrings.moodDescription(3)).performClick()
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) { closed }

        assertTrue(
            compose.onAllNodesWithText(TestStrings.get(R.string.mood_need_mood_title)).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun emptyFormClosesWithoutDialog() {
        var closed = false
        showEditor { closed = true }
        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) { closed }

        assertTrue(
            compose.onAllNodesWithText(TestStrings.get(R.string.mood_need_mood_title)).fetchSemanticsNodes().isEmpty()
        )
    }
}
