package app.opora.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.JournalEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.screens.JournalScreen
import app.opora.ui.screens.TodayScreen
import app.opora.ui.screens.MedsScreen
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LargeDataTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: OporaDatabase
    private lateinit var repo: OporaRepository
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
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
    fun journalWith1200EntriesOpensWithoutOom() = runBlocking {
        val entries = buildList {
            add(
                JournalEntity(
                    id = "fresh",
                    title = "Свежая запись",
                    body = "Видна первой",
                    entryDate = today.toEpochDay(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            repeat(1200) { index ->
                val day = today.minusDays((index % 365 + 1).toLong())
                add(
                    JournalEntity(
                        id = "bulk-$index",
                        title = "Запись $index",
                        body = "Текст записи $index",
                        entryDate = day.toEpochDay(),
                        createdAt = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        updatedAt = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                )
            }
        }
        db.journalDao().insertAll(entries)
        val vm = JournalViewModel(repo)
        val started = SystemClock.elapsedRealtime()

        compose.setContent {
            OporaTheme {
                JournalScreen(vm = vm, leftHanded = false, onOpen = {}, onNew = {})
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Свежая запись").fetchSemanticsNodes().isNotEmpty()
        }

        val elapsed = SystemClock.elapsedRealtime() - started
        println("LARGE_JOURNAL_MS=$elapsed")
        assertTrue("journal took ${elapsed}ms", elapsed < 15_000)
    }

    @Test
    fun todayWith120MedsAnd5000DosesFirstContent() = runBlocking {
        val meds = (0 until 120).map { index ->
            MedicationEntity(
                id = "perf-med-$index",
                name = "Перф лекарство $index",
                dosage = "1 таблетка",
                timesCsv = "08:00,20:00",
                startDate = today.minusDays(400).toEpochDay()
            )
        }
        db.medicationDao().insertAll(meds)
        val doses = (0 until 5000).map { index ->
            val med = meds[index % meds.size]
            val day = if (index < 120) today else today.minusDays(1 + ((index - 120) % 399).toLong())
            DoseLogEntity(
                id = "perf-dose-$index",
                medicationId = med.id,
                medicationName = med.name,
                dosage = med.dosage,
                scheduledDate = day.toEpochDay(),
                scheduledTime = if (index % 2 == 0) "08:00" else "20:00",
                scheduledAt = day.atTime(if (index % 2 == 0) 8 else 20, 0)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                status = if (day == today) "pending" else if (index % 3 == 0) "missed" else "taken"
            )
        }
        db.doseDao().insertAll(doses)
        runBlocking {
            repeat(5000) { index ->
                db.moodDao().insert(
                    app.opora.data.db.MoodEntity(
                        date = today.minusDays((index % 400).toLong()).toEpochDay(),
                        mood = index % 5 + 1
                    )
                )
            }
        }
        val vm = TodayViewModel(repo)
        val started = SystemClock.elapsedRealtime()
        compose.setContent {
            OporaTheme {
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
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.home_block_meds))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        println("LARGE_TODAY_MS=$elapsed")
    }

    @Test
    fun stockWith120MedsFirstContent() = runBlocking {
        val meds = (0 until 120).map { index ->
            MedicationEntity(
                id = "stock-med-$index",
                name = "Склад $index",
                dosage = "1 таблетка",
                timesCsv = "09:00",
                startDate = today.minusDays(30).toEpochDay(),
                inventoryCount = 50.0 + index,
                refillThreshold = 5.0
            )
        }
        db.medicationDao().insertAll(meds)
        val vm = MedsViewModel(repo)
        val started = SystemClock.elapsedRealtime()
        compose.setContent {
            OporaTheme {
                MedsScreen(
                    vm = vm,
                    onAddMedication = {},
                    onEditMedication = {},
                    leftHanded = false
                )
            }
        }
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.tab_meds))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val listTab = TestStrings.get(R.string.meds_tab_list)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(listTab).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(listTab).performClick()
        val stockTitle = TestStrings.get(R.string.stock_card_title)
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(stockTitle))
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(stockTitle).fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        println("LARGE_STOCK_MS=$elapsed")
    }

    @Test
    fun medsWith600DosesOpenWithoutOom() = runBlocking {
        val meds = (0 until 3).map { index ->
            MedicationEntity(
                id = "bulk-med-$index",
                name = "Тест $index",
                dosage = "1 таблетка",
                timesCsv = "09:00,21:00",
                startDate = today.minusDays(30).toEpochDay()
            )
        }
        db.medicationDao().insertAll(meds)
        val doses = buildList {
            (0 until 600).forEach { index ->
                val med = meds[index % meds.size]
                val day = today.minusDays((index % 30).toLong())
                val time = if (index % 2 == 0) "09:00" else "21:00"
                add(
                    DoseLogEntity(
                        id = "bulk-dose-$index",
                        medicationId = med.id,
                        medicationName = med.name,
                        dosage = med.dosage,
                        scheduledDate = day.toEpochDay(),
                        scheduledTime = time,
                        scheduledAt = day.atTime(9 + (index % 12), 0)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli(),
                        status = if (index % 3 == 0) "taken" else "pending"
                    )
                )
            }
        }
        db.doseDao().insertAll(doses)
        val vm = MedsViewModel(repo)
        val started = SystemClock.elapsedRealtime()

        compose.setContent {
            OporaTheme {
                MedsScreen(
                    vm = vm,
                    onAddMedication = {},
                    onEditMedication = {},
                    leftHanded = false
                )
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Тест 0").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(TestStrings.get(R.string.meds_today_header))
                    .fetchSemanticsNodes().isNotEmpty()
        }

        val elapsed = SystemClock.elapsedRealtime() - started
        println("LARGE_MEDS_MS=$elapsed")
        assertTrue("meds took ${elapsed}ms", elapsed < 15_000)
    }
}
