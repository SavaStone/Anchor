package app.opora.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.MainActivity
import app.opora.R
import app.opora.OporaApp
import app.opora.data.OporaRepository
import app.opora.data.db.MedicationEntity
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Блокировка показывается поверх корня: NavHost, back stack и поля формы
 * остаются в дереве. На тестовом эмуляторе нет PIN/биометрии, поэтому
 * используется реальный fail-closed экран «Защита недоступна»; разблокировка
 * здесь — осознанное отключение защиты.
 */
@RunWith(AndroidJUnit4::class)
class AppLockStateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as OporaApp).container
    private val settings: SettingsRepository get() = container.settings
    private val repo: OporaRepository get() = container.repository

    @get:Rule(order = 0)
    val preferences = object : ExternalResource() {
        override fun before() {
            runBlocking {
                settings.setOnboardingDone(true)
                settings.setDisclaimerAccepted(true)
                settings.setAppLock(false)
                settings.setLockTimeoutSeconds(0)
            }
        }

        override fun after() {
            runBlocking { settings.setAppLock(false) }
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForToday() {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithContentDescription(TestStrings.get(R.string.tab_meds)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun resetLock() {
        runBlocking { settings.setAppLock(false) }
    }

    private fun lockNow() {
        runBlocking { settings.setAppLock(true) }
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.main_lock_unavailable_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun unlockByDisabling() {
        rule.onNodeWithText(TestStrings.get(R.string.main_disable_lock)).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.main_disable_lock_title)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(TestStrings.get(R.string.main_disable_confirm)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.main_lock_unavailable_title)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun halfFilledMedicationFormSurvivesLock() {
        rule.onNodeWithContentDescription(TestStrings.get(R.string.tab_meds)).performClick()
        rule.onNodeWithContentDescription(TestStrings.get(R.string.add_medication)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.med_editor_title_new)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Черновик")
        rule.onNodeWithText("Черновик").assertExists()

        lockNow()
        unlockByDisabling()

        rule.onNodeWithText("Черновик").assertExists()
    }

    @Test
    fun deepNavigationStackSurvivesLock() {
        rule.onNodeWithContentDescription(TestStrings.get(R.string.tab_journal)).performClick()
        rule.onNodeWithContentDescription(TestStrings.get(R.string.journal_new_a11y)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.journal_done)).fetchSemanticsNodes().isNotEmpty()
        }

        lockNow()
        unlockByDisabling()

        rule.onNodeWithText(TestStrings.get(R.string.journal_done)).assertExists()
    }

    @Test
    fun openStopDialogSurvivesLock(): Unit = runBlocking {
        repo.saveMedication(
            MedicationEntity(
                name = "Тест-диалог",
                dosage = "25 мг",
                timesCsv = "09:00",
                startDate = LocalDate.now().toEpochDay()
            )
        )
        rule.onNodeWithContentDescription(TestStrings.get(R.string.tab_meds)).performClick()
        rule.onNodeWithText(TestStrings.get(R.string.meds_tab_list)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription(TestStrings.get(R.string.meds_more_a11y, "Тест-диалог"), substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithContentDescription(TestStrings.get(R.string.meds_more_a11y, "Тест-диалог"), substring = true)
            .onFirst().performClick()
        rule.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.meds_stop_title)).fetchSemanticsNodes().isNotEmpty()
        }

        lockNow()
        unlockByDisabling()

        rule.onNodeWithText(TestStrings.get(R.string.meds_stop_title)).assertExists()
        Unit
    }

    @Test
    fun backAfterUnlockPopsScreenInsteadOfExiting() {
        rule.onNodeWithContentDescription(TestStrings.get(R.string.tab_journal)).performClick()
        rule.onNodeWithContentDescription(TestStrings.get(R.string.journal_new_a11y)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.journal_done)).fetchSemanticsNodes().isNotEmpty()
        }

        lockNow()
        unlockByDisabling()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.journal_done)).fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText(TestStrings.get(R.string.journal_title)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(TestStrings.get(R.string.journal_title)).assertExists()
    }
}
