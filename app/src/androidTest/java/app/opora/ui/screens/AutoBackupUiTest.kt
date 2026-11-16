package app.opora.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.OporaApp
import app.opora.R
import app.opora.data.backup.AutoBackupFailure
import app.opora.data.prefs.AppSettings
import app.opora.ui.LocalAppContainer
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as OporaApp).container
    private var current by mutableStateOf(AppSettings())

    @Before
    fun setUp(): Unit = runBlocking {
        container.settings.clearAutoBackupFolder()
        container.settings.clearAutoBackupPassword()
        container.settings.clearBackupTimestamps()
        container.settings.setRestorePromptDismissed(true)
        current = container.settings.current()
    }

    @After
    fun tearDown(): Unit = runBlocking {
        container.settings.clearAutoBackupFolder()
        container.settings.clearAutoBackupPassword()
        container.settings.clearBackupTimestamps()
        container.settings.setRestorePromptDismissed(false)
    }

    private fun renderBackupScreen() {
        compose.setContent {
            OporaTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    BackupScreen(settings = current, onBack = {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun notConfiguredShowsHonestStatus() {
        renderBackupScreen()
        val text = TestStrings.get(R.string.backup_auto_not_configured)
        scrollTo(text)
        compose.onNodeWithText(text).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.backup_auto_not_configured_hint))
            .assertExists()
    }

    @Test
    fun configuredFolderPasswordAndErrorAreVisible(): Unit = runBlocking {
        container.settings.setAutoBackupFolder("content://opora.test/tree/folder", "OporaTest")
        assertTrue(container.settings.setAutoBackupPassword("opora-pass".toCharArray()))
        container.settings.setLastAutoBackupAt(1_760_000_000_000L)
        container.settings.recordAutoBackupError(
            AutoBackupFailure.WRITE_FAILED.code,
            1_760_000_100_000L
        )
        compose.runOnIdle { current = runBlocking { container.settings.current() } }
        renderBackupScreen()

        scrollTo(TestStrings.get(R.string.backup_auto_password_state_set))
        compose.onNodeWithText(TestStrings.get(R.string.backup_auto_password_state_set))
            .assertExists()
        compose.onNodeWithText("OporaTest").assertExists()

        val error = TestStrings.get(
            R.string.backup_auto_status_error,
            TestStrings.get(R.string.backup_auto_error_write)
        )
        scrollTo(error)
        compose.onNodeWithText(error).assertExists()
    }

    @Test
    fun channelSwitchSectionExplainsReinstallSteps() {
        renderBackupScreen()
        val title = TestStrings.get(R.string.backup_channel_title)
        scrollTo(title)
        compose.onNodeWithText(title).assertExists()

        val expand = TestStrings.get(R.string.backup_channel_expand)
        scrollTo(expand)
        compose.onNodeWithText(expand).performClick()

        val step1 = TestStrings.get(R.string.backup_channel_step1)
        scrollTo(step1)
        compose.onNodeWithText(step1).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.backup_channel_warning)).assertExists()
    }

    @Test
    fun foundBackupDialogRestoresFromFolder() {
        var restored = false
        compose.setContent {
            OporaTheme {
                RestoreFoundDialog(
                    title = TestStrings.get(R.string.restore_found_title),
                    body = TestStrings.get(
                        R.string.restore_found_folder_body,
                        "14.11.2026, 15:40"
                    ),
                    canRestore = true,
                    onRestore = { restored = true },
                    onChoose = {},
                    onLater = {},
                    onDismissRequest = {}
                )
            }
        }
        compose.onNodeWithText(TestStrings.get(R.string.restore_found_title)).assertExists()
        val body = TestStrings.get(
            R.string.restore_found_folder_body,
            "14.11.2026, 15:40"
        )
        compose.onNodeWithText(body).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.restore_found_restore)).performClick()
        assertTrue(restored)
    }

    @Test
    fun foundBackupDialogOffersFileChoiceWithoutKnownUri() {
        var chosen = false
        compose.setContent {
            OporaTheme {
                RestoreFoundDialog(
                    title = TestStrings.get(R.string.restore_found_title),
                    body = TestStrings.get(
                        R.string.restore_found_downloads_body,
                        "14.11.2026, 15:40"
                    ),
                    canRestore = false,
                    onRestore = {},
                    onChoose = { chosen = true },
                    onLater = {},
                    onDismissRequest = {}
                )
            }
        }
        compose.onNodeWithText(TestStrings.get(R.string.restore_found_title)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.restore_found_choose)).performClick()
        assertTrue(chosen)
    }

    @Test
    fun restoreSummaryShowsCounts() {
        compose.setContent {
            OporaTheme {
                RestoreSummaryDialog(
                    medications = 2,
                    doses = 5,
                    moods = 3,
                    journal = 1,
                    onDone = {}
                )
            }
        }
        val summary = TestStrings.get(R.string.restore_summary_body, 2, 5, 3, 1)
        compose.onNodeWithText(TestStrings.get(R.string.restore_summary_title)).assertExists()
        compose.onNodeWithText(summary).assertExists()
    }
}
