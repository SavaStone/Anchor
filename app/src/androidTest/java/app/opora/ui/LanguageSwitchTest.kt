package app.opora.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.AppLanguage
import app.opora.MainActivity
import app.opora.R
import app.opora.OporaApp
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * The picker must change a visible label immediately (the activity is
 * recreated) and persist the choice for the next process start.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as OporaApp).container
    private val settings: SettingsRepository get() = container.settings
    private var previousTag: String = AppLanguage.SYSTEM

    @get:Rule(order = 0)
    val preferences = object : ExternalResource() {
        override fun before() {
            previousTag = runBlocking { settings.languageTag() }
            runBlocking {
                settings.setOnboardingDone(true)
                settings.setDisclaimerAccepted(true)
                settings.setLanguage(AppLanguage.RUSSIAN)
            }
            AppLanguage.setTag(AppLanguage.RUSSIAN)
            AppLanguage.applyToSystem(context, AppLanguage.RUSSIAN)
        }

        override fun after() {
            runBlocking { settings.setLanguage(previousTag) }
            AppLanguage.setTag(previousTag)
            AppLanguage.applyToSystem(context, previousTag)
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetTouchMode() {
        // The next test class must start from the saved language, not the UI state.
        AppLanguage.setTag(runBlocking { settings.languageTag() })
    }

    @Test
    fun switchingLanguageChangesLabelAndSurvivesRestart() {
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithContentDescription("Сегодня").fetchSemanticsNodes().isNotEmpty()
        }
        openMore("Ещё")
        openLanguage()
        rule.onNodeWithText(TestStrings.get(R.string.language_english)).performClick()

        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithContentDescription("Today").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithContentDescription("More").fetchSemanticsNodes().isNotEmpty()
        }
        openMore("More")
        openLanguage()
        rule.onNodeWithText(TestStrings.get(R.string.language_russian)).performClick()

        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithContentDescription("Сегодня").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithContentDescription("Ещё").fetchSemanticsNodes().isNotEmpty()
        }

        // A fresh repository instance reads the same DataStore file a new
        // process would read; the summary language must match the picker.
        assertEquals("ru", runBlocking { SettingsRepository(context).languageTag() })
        assertEquals("ru", AppLanguage.resolveStartupTag(context))
    }

    private fun openMore(label: String) {
        rule.onAllNodes(hasContentDescription(label) and hasClickAction())[0].performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasContentDescription(label) and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openLanguage() {
        val title = hasText(TestStrings.get(R.string.language_title))
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(title)
        rule.onNodeWithText(TestStrings.get(R.string.language_title)).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(TestStrings.get(R.string.language_dialog_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
