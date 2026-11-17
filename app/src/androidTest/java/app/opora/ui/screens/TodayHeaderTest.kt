package app.opora.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.AppLanguage
import app.opora.data.OporaRepository
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.dayMonth
import app.opora.domain.weekdayDate
import app.opora.ui.TodayViewModel
import app.opora.ui.TestStrings
import app.opora.R
import app.opora.ui.theme.OporaTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Крупный системный шрифт должен увеличивать headlineLarge, а не ужимать
 * дату обратно через autoSize.
 */
@RunWith(AndroidJUnit4::class)
class TodayHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var vm: TodayViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vm = TodayViewModel(OporaRepository(context, db, SettingsRepository(context)))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun headerKeepsFullMonthWithoutOverflowAtAllScales() {
        var fontScale by mutableStateOf(1f)
        val locale = AppLanguage.locale(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val dateText = LocalDate.now().weekdayDate(locale)
        val month = LocalDate.now().dayMonth(locale)
        assertTrue(dateText.contains(month, ignoreCase = true))

        compose.setContent {
            OporaTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    Box(Modifier.width(360.dp)) {
                        TodayScreen(
                            vm = vm,
                            onEditMood = { _, _ -> },
                            onOpenMeds = {},
                            onAddMedication = {},
                            onOpenJournal = {},
                            onOpenAppointments = {},
                        )
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(dateText).fetchSemanticsNodes().isNotEmpty()
        }
        listOf(1f, 1.3f, 2f).forEach { scale ->
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            val node = compose.onNodeWithText(dateText).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            val action = node.config[SemanticsActions.GetTextLayoutResult].action
            assertNotNull("Нет layout-результата при fontScale=$scale", action)
            action!!.invoke(results)
            assertTrue("Нет layout-результата при fontScale=$scale", results.isNotEmpty())
            assertFalse(
                "Дата обрезана при fontScale=$scale: ${results.first().lineCount} строк",
                results.first().hasVisualOverflow
            )
            assertTrue(
                "Слишком много строк при fontScale=$scale",
                results.first().lineCount <= 2
            )
        }
    }

    @Test
    fun homeMoodTapOpensSelectionFlowWithChosenLevel() {
        var selected: Pair<LocalDate, Int>? = null
        compose.setContent {
            OporaTheme {
                TodayScreen(
                    vm = vm,
                    onEditMood = { _, _ -> },
                    onSelectMood = { date, level -> selected = date to level },
                    onOpenMeds = {},
                    onAddMedication = {},
                    onOpenJournal = {},
                    onOpenAppointments = {}
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(LocalDate.now().weekdayDate(
                AppLanguage.locale(InstrumentationRegistry.getInstrumentation().targetContext)
            )).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(TestStrings.get(R.string.today_mood_question))
            .assertDoesNotExist()
        compose.onNodeWithContentDescription(TestStrings.moodDescription(3)).performClick()
        assertTrue("Home mood tap did not request the editor with the selected level", selected == (LocalDate.now() to 3))
    }

}
