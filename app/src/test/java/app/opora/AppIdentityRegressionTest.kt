package app.opora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the split between the Android application id and the Kotlin namespace.
 *
 * The app ships as `io.github.savastone.anchor` while sources keep the
 * `app.opora` namespace, so the two ids differ. Anything that Android resolves by
 * package at runtime — the FileProvider authority, settings deep links, explicit
 * component names — must therefore derive from the build or the manifest instead
 * of a hardcoded legacy id. A hardcoded `app.opora` would silently break PDF and
 * problem-report sharing (authority mismatch) or point system settings at the
 * older installed package.
 *
 * Runs on the JVM: it reads `BuildConfig` and the module sources, no emulator.
 */
class AppIdentityRegressionTest {

    private val applicationId = "io.github.savastone.anchor"
    private val namespace = "app.opora"
    private val legacyApplicationId = "app.opora"

    private val root = projectRoot()

    @Test
    fun `build config ships the new application id`() {
        assertEquals(applicationId, BuildConfig.APPLICATION_ID)
        assertNotEquals(legacyApplicationId, BuildConfig.APPLICATION_ID)
        assertEquals("1.0.1-beta", BuildConfig.VERSION_NAME)
        assertTrue(
            "Code must not decrease below the installed new-id build for in-place updates",
            BuildConfig.VERSION_CODE >= 4
        )
    }

    @Test
    fun `manifest derives provider authority from application id`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

        assertTrue(
            "FileProvider authority must stay `${applicationId}.fileprovider` via the manifest placeholder",
            manifest.contains("android:authorities=\"\${applicationId}.fileprovider\"")
        )
        assertFalse(
            "FileProvider authority must not be hardcoded to the legacy id",
            manifest.contains("android:authorities=\"$legacyApplicationId")
        )
        assertFalse(
            "AGP derives the package from the namespace; a manifest package= attribute would override it",
            Regex("""<\s*manifest[^>]*\spackage\s*=""").containsMatchIn(manifest)
        )
    }

    @Test
    fun `components stay relative to the namespace not the application id`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

        listOf(
            ".OporaApp",
            ".MainActivity",
            ".BiometricActivity",
            ".notifications.MedAlarmReceiver",
            ".notifications.MedActionReceiver",
            ".notifications.BootReceiver"
        ).forEach { component ->
            assertTrue(
                "Explicit component $component must resolve against namespace $namespace",
                manifest.contains("android:name=\"$component\"")
            )
        }
        assertFalse(
            "No component may be named after the application id: it resolves against the namespace",
            manifest.contains("android:name=\"$applicationId")
        )
    }

    @Test
    fun `runtime integrations never hardcode the legacy package`() {
        val offenders = mutableListOf<String>()
        mainSources().forEach { file ->
            val text = file.readText()
            listOf(
                "\"$legacyApplicationId.fileprovider\"",
                "\"package:$legacyApplicationId\"",
                "\"$legacyApplicationId\""
            ).forEach { token ->
                if (text.contains(token)) {
                    offenders += "${file.relativeTo(root).invariantSeparatorsPath} -> $token"
                }
            }
        }
        assertTrue(
            "Runtime package lookups must derive from context.packageName:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `file provider call sites derive the authority from the context`() {
        val call = Regex("""FileProvider\.getUriForFile\(""")
        val sites = mainSources().flatMap { file ->
            val text = file.readText()
            call.findAll(text).map { match ->
                val arguments = text.substring(match.range.last + 1).take(240)
                file.relativeTo(root).invariantSeparatorsPath to arguments
            }
        }
        assertTrue("Expected FileProvider.getUriForFile call sites", sites.isNotEmpty())
        sites.forEach { (path, arguments) ->
            assertTrue(
                "$path: FileProvider authority must come from context.packageName",
                arguments.contains("packageName")
            )
            assertTrue(
                "$path: FileProvider authority must use the .fileprovider suffix",
                arguments.contains(".fileprovider")
            )
        }
    }

    @Test
    fun `settings and alarm intents target the current package`() {
        val more = File(root, "app/src/main/java/app/opora/ui/screens/MoreScreen.kt").readText()

        assertTrue(
            "Notification settings must receive the current package",
            more.contains("Settings.EXTRA_APP_PACKAGE, context.packageName")
        )
        assertTrue(
            "Exact-alarm settings must target the current package URI",
            more.contains("package:\${context.packageName}")
        )
    }

    private fun mainSources(): List<File> =
        File(root, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun projectRoot(): File {
        var dir: File? = File("").absoluteFile
        repeat(6) {
            if (File(dir, "app/src/main/res").isDirectory) return dir!!
            dir = dir?.parentFile
        }
        error("project root not found from ${File("").absolutePath}")
    }
}
