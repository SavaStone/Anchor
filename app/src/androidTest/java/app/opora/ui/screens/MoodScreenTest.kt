package app.opora.ui.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.AppLanguage
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.dayMonth
import app.opora.domain.weekday
import app.opora.ui.MoodViewModel
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth

class MoodScreenTest {

    private val locale = AppLanguage.locale(
        InstrumentationRegistry.getInstrumentation().targetContext
    )

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
        // Календарь показывается только при наличии хотя бы одной отметки.
        runBlocking {
            repo.saveMood(
                date = LocalDate.now(),
                mood = 4,
                anxiety = null,
                energy = null,
                sleepMinutes = null,
                emotions = emptyList(),
                symptoms = emptyList(),
                note = ""
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun showScreen(onEditMood: (LocalDate, Long?) -> Unit) {
        compose.setContent {
            OporaTheme {
                MoodScreen(
                    vm = MoodViewModel(repo),
                    onEditMood = onEditMood,
                    onEditEntry = {}
                )
            }
        }
    }

    @Test
    fun tappingPastDateOnlySelectsIt() {
        val past = YearMonth.now().minusMonths(1).atDay(10)
        var openedDate: LocalDate? = null
        showScreen { date, _ -> openedDate = date }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasContentDescription(TestStrings.get(R.string.calendar_prev_month)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(TestStrings.get(R.string.calendar_prev_month)).performClick()

        val description = buildString {
            append(past.weekday(locale).replaceFirstChar { it.titlecase(locale) })
            append(", ")
            append(past.dayMonth(locale))
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasContentDescription(description, substring = true) and hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(
            hasContentDescription(description, substring = true) and hasClickAction()
        ).onFirst().performClick()

        compose.waitForIdle()
        assertNull("Прошедший день должен только выбираться, а не открывать редактор", openedDate)
    }


    @Test
    fun fabUsesSelectedPastDate() {
        val yesterday = LocalDate.now().minusDays(1)
        runBlocking {
            repo.saveMood(
                date = yesterday,
                mood = 3,
                anxiety = null,
                energy = null,
                sleepMinutes = null,
                emotions = emptyList(),
                symptoms = emptyList(),
                note = "",
                occurredAt = yesterday.atTime(0, 3)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        var openedDate: LocalDate? = null
        showScreen { date, _ -> openedDate = date }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(TestStrings.get(R.string.mood_mark_a11y))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val description = buildString {
            append(yesterday.weekday(locale).replaceFirstChar { it.titlecase(locale) })
            append(", ")
            append(yesterday.dayMonth(locale))
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasContentDescription(description, substring = true) and hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(
            hasContentDescription(description, substring = true) and hasClickAction()
        ).onFirst().performClick()
        compose.onNodeWithContentDescription(TestStrings.get(R.string.mood_mark_a11y)).performClick()
        compose.runOnIdle { assertEquals(yesterday, openedDate) }
    }


    @Test
    fun addingFromSelectedPastDaySavesToThatDay() {
        val yesterday = LocalDate.now().minusDays(1)
        runBlocking {
            repo.saveMood(
                date = yesterday,
                mood = 3,
                anxiety = null,
                energy = null,
                sleepMinutes = null,
                emotions = emptyList(),
                symptoms = emptyList(),
                note = "утренняя",
                occurredAt = yesterday.atTime(0, 3)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        showScreen { date, _ ->
            runBlocking {
                repo.saveMood(
                    date = date,
                    mood = 4,
                    anxiety = null,
                    energy = null,
                    sleepMinutes = null,
                    emotions = emptyList(),
                    symptoms = emptyList(),
                    note = "вечерняя",
                    occurredAt = date.atTime(21, 0)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }
        }
        val description = buildString {
            append(yesterday.weekday(locale).replaceFirstChar { it.titlecase(locale) })
            append(", ")
            append(yesterday.dayMonth(locale))
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasContentDescription(description, substring = true) and hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(
            hasContentDescription(description, substring = true) and hasClickAction()
        ).onFirst().performClick()
        compose.onNodeWithContentDescription(TestStrings.get(R.string.mood_mark_a11y)).performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                db.moodDao().byDate(yesterday.toEpochDay()).any { it.note == "вечерняя" }
            }
        }
        val yesterdayEntries = runBlocking { db.moodDao().byDate(yesterday.toEpochDay()) }
        assertEquals(2, yesterdayEntries.size)
        assertTrue(yesterdayEntries.any { it.note == "утренняя" })
        assertTrue(
            runBlocking {
                db.moodDao().byDate(LocalDate.now().toEpochDay()).none { it.note == "вечерняя" }
            }
        )
    }

    @Test
    fun pastDayShowsBothEntryTimes() {
        val yesterday = LocalDate.now().minusDays(1)
        runBlocking {
            listOf(LocalTime.of(0, 3), LocalTime.of(21, 0)).forEach { time ->
                repo.saveMood(
                    date = yesterday,
                    mood = 4,
                    anxiety = null,
                    energy = null,
                    sleepMinutes = null,
                    emotions = emptyList(),
                    symptoms = emptyList(),
                    note = "",
                    occurredAt = yesterday.atTime(time)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            }
        }
        showScreen { _, _ -> }
        val description = buildString {
            append(yesterday.weekday(locale).replaceFirstChar { it.titlecase(locale) })
            append(", ")
            append(yesterday.dayMonth(locale))
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasContentDescription(description, substring = true) and hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(
            hasContentDescription(description, substring = true) and hasClickAction()
        ).onFirst().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("00:03", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("21:00", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("00:03", substring = true).assertExists()
        compose.onNodeWithText("21:00", substring = true).assertExists()
    }

    @Test
    fun tappingTodayOpensEditor() {
        val today = LocalDate.now()
        var openedDate: LocalDate? = null
        showScreen { date, _ -> openedDate = date }

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasContentDescription(
                    TestStrings.get(R.string.a11y_calendar_today, ""),
                    substring = true
                ) and hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(
            hasContentDescription(
                TestStrings.get(R.string.a11y_calendar_today, ""),
                substring = true
            ) and hasClickAction()
        ).onFirst().performClick()

        compose.runOnIdle { assertEquals(today, openedDate) }
    }
}
