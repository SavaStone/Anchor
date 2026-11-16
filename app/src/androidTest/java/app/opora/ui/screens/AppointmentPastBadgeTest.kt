package app.opora.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.AppointmentEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.AppointmentsViewModel
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Прошедшее время визита само по себе не означает, что визит состоялся:
 * бейдж «Завершён» должен требовать отметки done.
 */
@RunWith(AndroidJUnit4::class)
class AppointmentPastBadgeTest {
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
        runBlocking {
            repo.saveAppointment(
                AppointmentEntity(
                    title = "Прошлый визит",
                    startsAt = System.currentTimeMillis() - 2 * 60 * 60 * 1000L,
                    durationMinutes = 50,
                    done = false
                )
            )
            repo.saveAppointment(
                AppointmentEntity(
                    title = "Скоро визит",
                    startsAt = System.currentTimeMillis() + 5 * 60 * 1000L,
                    durationMinutes = 50,
                    remindMinutesBefore = 60,
                    done = false
                )
            )
        }
        compose.setContent {
            OporaTheme {
                AppointmentsScreen(vm = AppointmentsViewModel(repo), onBack = {})
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Прошлый визит").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pastNotDoneAppointmentIsNotMarkedCompleted() {
        compose.onAllNodesWithText(TestStrings.get(R.string.appointments_status_done))
            .assertCountEquals(0)
    }

    @Test
    fun elapsedReminderTriggerIsNotPromised() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Скоро визит").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(
            TestStrings.get(
                R.string.appointments_reminder_before,
                TestStrings.plural(R.plurals.duration_hours, 1)
            )
        ).assertCountEquals(0)
        compose.onNodeWithText(TestStrings.get(R.string.appointments_no_reminder)).assertExists()
    }
}
