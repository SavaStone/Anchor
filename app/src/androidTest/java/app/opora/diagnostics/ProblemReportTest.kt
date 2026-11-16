package app.opora.diagnostics

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.BuildConfig
import app.opora.OporaApp
import app.opora.R
import app.opora.notifications.MedicationAlarms
import app.opora.ui.LocalAppContainer
import app.opora.ui.SettingsViewModel
import app.opora.ui.TestStrings
import app.opora.ui.screens.MoreScreen
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProblemReportTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as OporaApp).container

    @Before
    fun setUp(): Unit = runBlocking {
        container.settings.setOnboardingDone(true)
        Unit
    }

    @Test
    fun problemReportRowIsVisibleInMore() {
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
                        onOpenAbout = {}
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        val title = TestStrings.get(R.string.more_problem_report)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(title))
        val reportBounds = compose.onNodeWithText(title)
            .assertIsDisplayed()
            .getBoundsInRoot()
        val aboutBounds = compose.onNodeWithText(TestStrings.get(R.string.about_title))
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertTrue(
            "Report row (${reportBounds.bottom}) must be above About (${aboutBounds.top})",
            reportBounds.bottom <= aboutBounds.top
        )
        val body = TestStrings.get(R.string.more_problem_report_body)
        compose.onNodeWithText(body).assertExists()
    }

    @Test
    fun reportTextAndIntentCarryDiagnostics() = runBlocking {
        val share = ProblemReport.create(context, container.settings.current())
        val language = context.resources.configuration.locales[0].language
        assertTrue(share.text.contains(BuildConfig.VERSION_NAME))
        assertTrue(share.text.contains(BuildConfig.VERSION_CODE.toString()))
        assertTrue(share.text.contains(language))
        assertTrue(share.text.contains(ProblemReport.RECIPIENT))
        assertTrue(share.text.contains("проверьте", ignoreCase = true) || share.text.contains("review", ignoreCase = true))

        val intent = ProblemReport.intent(context, share)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(
            context.getString(R.string.problem_report_subject),
            intent.getStringExtra(Intent.EXTRA_SUBJECT)
        )
        assertEquals(share.text, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertArrayEquals(
            arrayOf(ProblemReport.RECIPIENT),
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
        )
    }

    @Test
    fun noCrashAttachmentMeansNoStreamOrGrant() {
        val share = ProblemShare(text = "report body", subject = "subject", crashFile = null)
        val intent = ProblemReport.intent(context, share)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertArrayEquals(
            arrayOf(ProblemReport.RECIPIENT),
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
        )
        assertNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertNull(intent.clipData)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun missingCrashFileIsOmittedWithoutFailingShare() {
        val missing = File(context.cacheDir, "missing-crash-${System.currentTimeMillis()}.txt")
        assertFalse(missing.exists())
        val share = ProblemShare(text = "report body", subject = "subject", crashFile = missing)

        val intent = ProblemReport.intent(context, share)
        assertNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertNull(intent.clipData)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)

        var chooser: Intent? = null
        val opened = ProblemReport.share(
            context = context,
            share = share,
            available = { true },
            launch = { chooser = it }
        )
        assertTrue(opened)
        val target = IntentCompat.getParcelableExtra(
            chooser!!,
            Intent.EXTRA_INTENT,
            Intent::class.java
        )
        assertNotNull(target)
        assertNull(IntentCompat.getParcelableExtra(target!!, Intent.EXTRA_STREAM, Uri::class.java))
    }

    @Test
    fun latestCrashFileIsAttachedReadableAndSanitized() = runBlocking {
        val secret = "https://user:secret@example.org/path?token=topsecret"
        val crash = CrashLogger.record(
            context,
            Thread.currentThread(),
            IllegalStateException("probe crash for report $secret")
        )
        assertNotNull(crash)
        val file = crash!!
        try {
            val share = ProblemReport.create(context, container.settings.current())
            assertTrue(share.text.contains(file.name))
            val intent = ProblemReport.intent(context, share)
            val uri = IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            )
            assertNotNull(uri)
            assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            val clip = intent.clipData
            assertNotNull(clip)
            val clipData = clip!!
            assertEquals(1, clipData.itemCount)
            assertEquals(uri, clipData.getItemAt(0).uri)
            val body = context.contentResolver.openInputStream(uri!!)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("crash URI is not readable")
            assertTrue(body.contains("IllegalStateException"))
            assertTrue(body.contains("[redacted-url]"))
            assertFalse(body.contains("user:secret"))
            assertFalse(body.contains("topsecret"))
            assertFalse(body.contains("example.org"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun latestIgnoresEmptyAndForeignCrashFiles() {
        val dir = File(context.filesDir, "crashes").apply { mkdirs() }
        val valid = CrashLogger.record(
            context,
            Thread.currentThread(),
            IllegalStateException("valid crash probe")
        )
        assertNotNull(valid)
        val empty = File(dir, "crash-99999999-999999.txt")
        val foreign = File(dir, "notes-${System.currentTimeMillis()}.txt")
        try {
            empty.writeText("")
            foreign.writeText("not a crash log")
            val latest = CrashLogger.latest(context)
            assertNotNull(latest)
            val latestFile = latest!!
            assertFalse(latestFile == empty)
            assertFalse(latestFile == foreign)
            assertTrue(CrashLogPolicy.isCrashName(latestFile.name))
            assertTrue(latestFile.length() > 0L)
            assertTrue(latestFile.canRead())
        } finally {
            empty.delete()
            foreign.delete()
            valid?.delete()
        }
    }

    @Test
    fun chooserCarriesRecipientAndTextTarget() {
        val share = ProblemShare(text = "report body", subject = "subject", crashFile = null)
        var chooser: Intent? = null
        val opened = ProblemReport.share(
            context = context,
            share = share,
            available = { true },
            launch = { chooser = it }
        )
        assertTrue(opened)
        val intent = chooser!!
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(title)
        assertTrue(title!!.contains(ProblemReport.RECIPIENT))
        val target = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_INTENT,
            Intent::class.java
        )
        assertNotNull(target)
        assertArrayEquals(
            arrayOf(ProblemReport.RECIPIENT),
            target!!.getStringArrayExtra(Intent.EXTRA_EMAIL)
        )
        assertEquals("text/plain", target.type)
    }

    @Test
    fun shareReturnsFalseWhenNoHandler() {
        var launched = false
        val opened = ProblemReport.share(
            context = context,
            share = ProblemShare(text = "report body", subject = "subject", crashFile = null),
            available = { false },
            launch = { launched = true }
        )
        assertFalse(opened)
        assertFalse(launched)
    }

    @Test
    fun shareReturnsFalseWhenLaunchFails() {
        val opened = ProblemReport.share(
            context = context,
            share = ProblemShare(text = "report body", subject = "subject", crashFile = null),
            available = { true },
            launch = { error("no activity to launch chooser") }
        )
        assertFalse(opened)
    }
}
