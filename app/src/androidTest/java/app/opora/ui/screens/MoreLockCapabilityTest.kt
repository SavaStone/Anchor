package app.opora.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.OporaApp
import app.opora.R
import app.opora.notifications.MedicationAlarms
import app.opora.ui.LocalAppContainer
import app.opora.ui.SettingsViewModel
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreLockCapabilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as OporaApp).container
    private var pinSet by mutableStateOf(false)

    @Before
    fun setUp(): Unit = runBlocking {
        container.settings.setOnboardingDone(true)
        container.settings.setAppLock(false)
        Unit
    }

    @After
    fun tearDown(): Unit = runBlocking {
        container.settings.setAppLock(false)
        Unit
    }

    @Test
    fun pinSetWhileScreenOpenEnablesLockWithoutFalseHelp() = runBlocking {
        val vm = SettingsViewModel(
            container.settings,
            container.workScheduler,
            MedicationAlarms(context, container.db),
            container.repository
        )
        compose.setContent {
            OporaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    MoreScreen(
                        vm = vm,
                        onOpenAppointments = {},
                        onOpenBackup = {},
                        onOpenReport = {},
                        onOpenAbout = {},
                        canLockOverride = { pinSet }
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(TestStrings.get(R.string.more_lock_title)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(TestStrings.get(R.string.more_lock_title)))
        pinSet = true // PIN выставили в системе, не уходя с открытого экрана
        compose.onNodeWithText(TestStrings.get(R.string.more_lock_title)).performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            runBlocking { container.settings.settings.first().appLock }
        }
        assertFalse(
            compose.onAllNodesWithText(TestStrings.get(R.string.more_lock_help_title))
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(container.settings.settings.first().appLock)
    }
}
