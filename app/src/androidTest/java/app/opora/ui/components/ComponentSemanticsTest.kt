package app.opora.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.AppLanguage
import app.opora.R
import app.opora.data.db.MoodEntity
import app.opora.domain.weekdayDate
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val locale = AppLanguage.locale(
        InstrumentationRegistry.getInstrumentation().targetContext
    )

    /** The calendar cell labels a day as "weekday, date year". */
    private fun dayBase(date: LocalDate): String = "${date.weekdayDate(locale)} ${date.year}"

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { OporaTheme { content() } }
    }

    @Test
    fun moodFace_exposesRadioRoleAndRussianLabel() {
        screen { MoodFace(level = 3, selected = false, onClick = {}) }

        rule.onNodeWithContentDescription(TestStrings.moodDescription(3))
            .assertExists()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun moodFace_hasMinimumTouchTarget() {
        screen { MoodFace(level = 1, selected = false, onClick = {}) }

        rule.onNodeWithContentDescription(TestStrings.moodDescription(1))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun moodFace_selectedStateIsAnnounced() {
        screen { MoodFace(level = 5, selected = true, onClick = {}) }

        rule.onNodeWithContentDescription(TestStrings.moodDescription(5)).assertIsSelected()
    }

    @Test
    fun moodFace_tapInvokesCallback() {
        var clicked = false
        screen { MoodFace(level = 4, selected = false, onClick = { clicked = true }) }

        rule.onNodeWithContentDescription(TestStrings.moodDescription(4)).performClick()
        assertTrue(clicked)
    }

    @Test
    fun decorativeMoodFace_isNotClickable() {
        screen { MoodFace(level = 2, selected = false, onClick = null) }

        rule.onNodeWithContentDescription(TestStrings.moodDescription(2)).assertDoesNotExist()
    }

    @Test
    fun screenTitle_isHeadingAndBackIsLabelled() {
        screen { ScreenTitle(title = TestStrings.get(R.string.tab_meds), onBack = {}) }

        rule.onNodeWithText(TestStrings.get(R.string.tab_meds))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        rule.onNodeWithContentDescription(TestStrings.get(R.string.common_back)).assertHasClickAction()
    }

    @Test
    fun ringProgress_exposesRange() {
        screen { RingProgress(percent = 40) }

        rule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.4f, 0f..1f)
            )
        ).assertExists()
    }

    @Test
    fun ringProgress_withoutDataSaysNoData() {
        screen { RingProgress(percent = null) }

        rule.onNodeWithContentDescription(TestStrings.get(R.string.common_no_data)).assertExists()
    }

    @Test
    fun confirmDialog_canDisableDestructiveAction() {
        screen {
            ConfirmDialog(
                title = TestStrings.get(R.string.journal_delete_title),
                text = TestStrings.get(R.string.journal_delete_body),
                confirmEnabled = false,
                onConfirm = {},
                onDismiss = {}
            )
        }

        rule.onNodeWithText(TestStrings.get(R.string.action_delete)).assertIsNotEnabled()
        rule.onNodeWithText(TestStrings.get(R.string.common_cancel)).assertIsEnabled()
    }

    @Test
    fun confirmDialog_reportsConfirmAndDismiss() {
        var confirmed = false
        var dismissed = false
        screen {
            ConfirmDialog(
                title = TestStrings.get(R.string.journal_delete_title),
                text = TestStrings.get(R.string.journal_delete_body),
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true }
            )
        }

        rule.onNodeWithText(TestStrings.get(R.string.action_delete)).performClick()
        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun calendarCell_hasDateMoodDescriptionAnd48dpHeight() {
        screen {
            MoodCalendar(
                month = YearMonth.of(2026, 11),
                entries = emptyMap<Long, List<MoodEntity>>(),
                selected = LocalDate.of(2026, 11, 8),
                onSelect = {},
                onMonthChange = {},
                today = LocalDate.of(2026, 11, 8)
            )
        }

        val selectedDay = LocalDate.of(2026, 11, 8)
        val futureDay = LocalDate.of(2026, 11, 25)
        rule.onNodeWithContentDescription(dayBase(selectedDay), substring = true)
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .assertIsSelected()
        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_calendar_future, dayBase(futureDay))
        )
            .assertExists()
            .assertIsNotEnabled()
    }

    @Test
    fun calendarCell_clickSelectsDate() {
        var selected: LocalDate? = null
        screen {
            MoodCalendar(
                month = YearMonth.of(2026, 11),
                entries = emptyMap<Long, List<MoodEntity>>(),
                selected = LocalDate.of(2026, 11, 8),
                onSelect = { selected = it },
                onMonthChange = {},
                today = LocalDate.of(2026, 11, 20)
            )
        }

        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_calendar_no_mood, dayBase(LocalDate.of(2026, 11, 10)))
        ).performClick()
        assertEquals(LocalDate.of(2026, 11, 10), selected)
    }

    @Test
    fun calendarCell_selectedFlagMovesWithState() {
        var selected by mutableStateOf(LocalDate.of(2026, 11, 8))
        screen {
            MoodCalendar(
                month = YearMonth.of(2026, 11),
                entries = emptyMap<Long, List<MoodEntity>>(),
                selected = selected,
                onSelect = { selected = it },
                onMonthChange = {},
                today = LocalDate.of(2026, 11, 20)
            )
        }

        val day = LocalDate.of(2026, 11, 10)
        val base = dayBase(day)
        val unselected = TestStrings.get(R.string.a11y_calendar_no_mood, base)
        rule.onNodeWithContentDescription(unselected).performClick()
        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_calendar_selected, unselected)
        ).assertIsSelected()
        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_calendar_no_mood, dayBase(LocalDate.of(2026, 11, 8)))
        ).assertIsNotSelected()
        assertFalse(selected == LocalDate.of(2026, 11, 8))
    }

    @Test
    fun calendarCell_describesSeveralMoodsForOneDay() {
        val day = LocalDate.of(2026, 11, 10)
        screen {
            MoodCalendar(
                month = YearMonth.of(2026, 11),
                entries = mapOf(
                    day.toEpochDay() to listOf(
                        MoodEntity(date = day.toEpochDay(), mood = 2),
                        MoodEntity(date = day.toEpochDay(), mood = 4)
                    )
                ),
                selected = LocalDate.of(2026, 11, 8),
                onSelect = {},
                onMonthChange = {},
                today = LocalDate.of(2026, 11, 20)
            )
        }

        val moods = listOf(2, 4).joinToString(", ") { TestStrings.mood(it).lowercase() }
        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_calendar_moods, dayBase(day), moods),
            substring = true
        ).assertExists()
    }

    @Test
    fun moodTrendChart_hasRussianSummary() {
        val end = LocalDate.of(2026, 11, 20)
        screen {
            MoodTrendChart(
                entries = listOf(
                    MoodEntity(date = end.minusDays(1).toEpochDay(), mood = 2),
                    MoodEntity(date = end.toEpochDay(), mood = 4)
                ),
                days = 7,
                endDate = end
            )
        }

        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.chart_mood_a11y, 2, 2, 4, 4),
            substring = true
        )
            .assertExists()
    }

    @Test
    fun anxietyTrendChart_hasRussianSummary() {
        val end = LocalDate.of(2026, 11, 20)
        screen {
            AnxietyTrendChart(
                entries = listOf(
                    MoodEntity(date = end.minusDays(1).toEpochDay(), mood = 3, anxiety = 2),
                    MoodEntity(date = end.toEpochDay(), mood = 3, anxiety = 6)
                ),
                days = 7,
                endDate = end
            )
        }

        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.chart_anxiety_a11y, 2, 2, 6),
            substring = true
        )
            .assertExists()
    }

    @Test
    fun moodLegend_announcesScale() {
        screen { MoodLegend() }

        rule.onNodeWithContentDescription(
            TestStrings.get(R.string.calendar_mood_scale_a11y)
        )
            .assertExists()
    }
}
