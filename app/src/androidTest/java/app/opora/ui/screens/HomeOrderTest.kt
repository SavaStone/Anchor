package app.opora.ui.screens

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.MainActivity
import app.opora.OporaApp
import app.opora.R
import app.opora.ui.TestStrings

import app.opora.data.prefs.SettingsRepository
import app.opora.domain.DEFAULT_HIDDEN_HOME_BLOCKS
import app.opora.domain.DEFAULT_HOME_ORDER
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeOrderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = (context.applicationContext as OporaApp).container.settings

    @get:Rule(order = 0)
    val preferences = object : ExternalResource() {
        override fun before() {
            runBlocking {
                settings.setOnboardingDone(true)
                settings.setDisclaimerAccepted(true)
                settings.setAppLock(false)
                settings.setHomeBlocks(DEFAULT_HOME_ORDER)
                settings.setHiddenHomeBlocks("appointment")
            }
        }

        override fun after() {
            runBlocking {
                settings.setHomeBlocks(DEFAULT_HOME_ORDER)
                settings.setHiddenHomeBlocks(DEFAULT_HIDDEN_HOME_BLOCKS)
            }
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        rule.waitUntil(timeoutMillis = 15_000) {
            (rule.onAllNodesWithText(TestStrings.get(R.string.home_block_meds)).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText(TestStrings.get(R.string.empty_med_title)).fetchSemanticsNodes().isNotEmpty()) &&
                rule.onAllNodesWithText(TestStrings.get(R.string.home_block_mood)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMedsFirst() {
        // В полном прогоне в базе могут остаться лекарства: тогда блок
        // подписан «Лекарства», иначе — пустым состоянием.
        val medsText = if (rule.onAllNodesWithText(TestStrings.get(R.string.home_block_meds))
                .fetchSemanticsNodes().isNotEmpty()
        ) TestStrings.get(R.string.home_block_meds) else TestStrings.get(R.string.empty_med_title)
        val meds = rule.onNodeWithText(medsText).fetchSemanticsNode().boundsInRoot
        val mood = rule.onNodeWithText(TestStrings.get(R.string.home_block_mood)).fetchSemanticsNode().boundsInRoot
        assertTrue("Лекарства должны быть выше настроения: meds=$meds mood=$mood", meds.top < mood.top)
    }

    @Test
    fun orderChangedInSettingsAppliesAndSurvivesActivityRecreation() {
        waitForHome()
        rule.onNodeWithContentDescription(TestStrings.get(R.string.tab_more)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription(TestStrings.get(R.string.more_home_move_up_cd, TestStrings.get(R.string.home_block_meds)))
        )
        rule.onNodeWithContentDescription(TestStrings.get(R.string.more_home_move_up_cd, TestStrings.get(R.string.home_block_meds))).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { settings.settings.first().homeBlocks.startsWith("meds") }
        }
        rule.onNodeWithContentDescription(TestStrings.get(R.string.tab_today)).performClick()
        waitForHome()
        assertMedsFirst()

        rule.activityRule.scenario.recreate()
        waitForHome()
        assertMedsFirst()
    }
}
