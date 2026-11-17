package app.opora.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import java.util.Locale

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

    @Test
    fun countMetricDoesNotRepeatItsNumberOrOverflowAtNarrowWidth() {
        var fontScale by mutableStateOf(1f)
        var language by mutableStateOf("en")
        compose.setContent {
            OporaTheme {
                val configuration = android.content.res.Configuration().apply {
                    setTo(LocalConfiguration.current)
                    setLocale(Locale.forLanguageTag(language))
                }
                val localizedContext = LocalContext.current.createConfigurationContext(configuration)
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale),
                    LocalConfiguration provides configuration,
                    LocalContext provides localizedContext
                ) {
                    Box(Modifier.width(320.dp)) {
                        MetricTile(
                            value = "14",
                            label = pluralStringResource(R.plurals.mood_mark_label, 14),
                            modifier = Modifier.fillMaxWidth().testTag("mood-metric-tile"),
                            valueTag = "mood-metric-count",
                            labelTag = "mood-metric-count-label"
                        )
                    }
                }
            }
        }
        listOf("ru", "en").forEach { lang ->
            listOf(1f, 1.3f, 2f).forEach { scale ->
                compose.runOnIdle {
                    language = lang
                    fontScale = scale
                }
                compose.waitForIdle()
            val count = compose.onNodeWithTag("mood-metric-count")
            count.assertExists()
            assertEquals("14", count.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.Text
            ].joinToString("") { it.text })
            val labelNode = compose.onNodeWithTag("mood-metric-count-label")
            val label = labelNode.fetchSemanticsNode()
            val labelText = label.config[
                androidx.compose.ui.semantics.SemanticsProperties.Text
            ].joinToString("") { it.text }
            assertFalse("Count is repeated in its label: $labelText", labelText.contains("14"))
            val results = mutableListOf<TextLayoutResult>()
            label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertTrue("Count label has no text layout at fontScale=$scale", results.isNotEmpty())
            assertEquals(
                "Count label was truncated at fontScale=$scale",
                labelText.length,
                results.first().getLineEnd(results.first().lineCount - 1)
            )
            assertTrue(
                "Metric tile exceeded the 320dp viewport at language=$lang fontScale=$scale",
                compose.onNodeWithTag("mood-metric-tile")
                    .fetchSemanticsNode().boundsInRoot.width <= 320f
            )
            val expectedConfig = android.content.res.Configuration(
                InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
            ).apply { setLocale(Locale.forLanguageTag(lang)) }
            val localized = InstrumentationRegistry.getInstrumentation().targetContext
                .createConfigurationContext(expectedConfig).resources
                .getQuantityString(R.plurals.mood_mark_label, 14)
            assertEquals(localized, labelText)
            }
        }
    }
}
