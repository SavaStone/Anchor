package app.opora.ui

import android.Manifest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.opora.MainActivity
import app.opora.OporaApp
import app.opora.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Сквозной smoke-тест основного ежедневного сценария:
 * онбординг (если показывается) → быстрая отметка настроения → запись в базе.
 *
 * Тест не зависит от предыдущих данных: если онбординг уже пройден, шаг
 * пропускается; повторная отметка настроения допустима.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val repo
        get() = (InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as OporaApp).container.repository

    private fun todayMoodCount(): Int = runBlocking {
        repo.moodEntries().first().count { it.date == LocalDate.now().toEpochDay() }
    }

    @Test
    fun onboardingOrToday_quickMoodIsSaved() {
        val next = TestStrings.get(R.string.onboarding_next)
        val start = TestStrings.get(R.string.onboarding_start)
        val consent = TestStrings.get(
            R.string.onboarding_consent_title,
            TestStrings.get(R.string.app_name)
        )

        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasContentDescription(TestStrings.moodDescription(4)))
                    .fetchSemanticsNodes().isNotEmpty()
        }

        if (rule.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText(next).performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(next).performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText(start).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText(consent).performClick()
            rule.onNodeWithText(start).performClick()
        }

        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodes(hasContentDescription(TestStrings.moodDescription(4)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        val before = todayMoodCount()
        rule.onNodeWithContentDescription(TestStrings.moodDescription(4)).performClick()

        // Быстрая отметка не показывает снекбар; доказательство — новая
        // запись в базе, а не текст, которого в интерфейсе больше нет.
        rule.waitUntil(timeoutMillis = 10_000) { todayMoodCount() > before }
    }
}
