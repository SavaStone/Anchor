package app.opora.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import app.opora.AppLanguage
import app.opora.R
import app.opora.domain.DoseDayState
import app.opora.domain.weekdayDate
import app.opora.domain.DoseStreak
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MedsStreakCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allWindowDaysAreAtLeast48DpOn360Width() {
        val today = LocalDate.of(2026, 11, 10)
        val days = (13L downTo 0L).map { offset ->
            val date = today.minusDays(offset)
            DoseDayState(
                date = date,
                expected = 1,
                taken = 0,
                pending = 1,
                isToday = false
            )
        }
        val streak = DoseStreak(current = 0, days = days)

        compose.setContent {
            OporaTheme {
                Box(Modifier.width(360.dp)) {
                    FlameStreakCard(
                        streak = streak,
                        nextDoseText = null,
                        onForgive = {},
                        onUnforgive = {}
                    )
                }
            }
        }

        val locale = AppLanguage.locale(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val missed = TestStrings.get(R.string.meds_day_missed)
        // 14 дней окна не обрезаются до семи; каждая ячейка — не меньше 48dp.
        compose.onAllNodesWithContentDescription(
            TestStrings.get(R.string.meds_day_a11y, "", missed),
            substring = true
        ).assertCountEquals(14)
        // 9 ноября 2026 — понедельник, день без отметки: ячейка кликабельна.
        listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 28)).forEach { date ->
            compose.onNodeWithContentDescription(
                TestStrings.get(R.string.meds_day_a11y, date.weekdayDate(locale), missed)
            )
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }
}
