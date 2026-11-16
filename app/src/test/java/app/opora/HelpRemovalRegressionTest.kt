package app.opora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the removal of the built-in Help screen, hotline/emergency numbers and
 * the "safety" route. Runs on the JVM and reads the module sources/resources, so
 * no emulator is needed.
 *
 * Author resources and links are required: `strings_author.xml` in both locales
 * and the exact SavaStone YouTube/GitHub URLs in the author screen.
 */
class HelpRemovalRegressionTest {

    private val root = projectRoot()

    private val forbiddenKeys = setOf(
        "today_help",
        "today_safety_a11y",
        "more_section_help",
        "more_safety_title",
        "more_safety_body",
        "about_section_help",
        "about_emergency_label",
        "about_hotline_label",
        "about_hotline_note",
        "about_help_note_local",
        "about_no_emergency_number",
        "about_call_cd",
        "about_call_hotline_cd",
        "about_call_action",
        "about_dial_failed"
    )

    private val forbiddenSourceTokens = listOf(
        "app.opora.data.SupportContacts",
        "SupportContacts",
        "SafetyScreen",
        "onOpenSafety",
        "openRoute(\"safety\")",
        "R.string.today_help",
        "R.string.today_safety_a11y",
        "R.string.more_safety_",
        "R.string.more_section_help",
        "R.string.about_section_help",
        "R.string.about_emergency_",
        "R.string.about_hotline_",
        "R.string.about_help_note_local",
        "R.string.about_no_emergency_number",
        "R.string.about_call_",
        "R.string.about_dial_failed",
        "Intent.ACTION_DIAL",
        "\"8 800 2000 122\"",
        "\"8-800-2000-122\"",
        "\"88002000122\"",
        "\"112\"",
        "\"911\""
    )

    @Test
    fun `help screens and support contacts are deleted`() {
        listOf(
            "app/src/main/java/app/opora/ui/screens/SafetyScreen.kt",
            "app/src/main/java/app/opora/data/SupportContacts.kt",
            "app/src/test/java/app/opora/data/SupportContactsTest.kt"
        ).forEach { relative ->
            assertFalse("$relative must be removed", File(root, relative).exists())
        }
    }

    @Test
    fun `string resources contain no help-only keys`() {
        val offenders = mutableListOf<String>()
        stringResourceFiles().forEach { file ->
            stringEntries(file).forEach { (key, _) ->
                if (key.startsWith("safety_") || key in forbiddenKeys) {
                    offenders += "${file.name}:$key"
                }
            }
        }
        assertTrue(
            "Help/crisis-plan resources must be gone:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `string resources contain no built-in help numbers`() {
        val offenders = mutableListOf<String>()
        stringResourceFiles().forEach { file ->
            stringEntries(file).forEach { (key, value) ->
                val normalized = value.replace(" ", "").replace("-", "")
                val hasStandalone = Regex("""(?<!\d)(112|911)(?!\d)""").containsMatchIn(value)
                if (normalized.contains("88002000122") || normalized.contains("8002000122") || hasStandalone) {
                    offenders += "${file.name}:$key = $value"
                }
            }
        }
        assertTrue(
            "Built-in emergency/hotline numbers must not live in resources:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `sources contain no removed help route calls or dialing`() {
        val sourceRoot = File(root, "app/src/main/java")
        val offenders = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                forbiddenSourceTokens.forEach { token ->
                    if (text.contains(token)) {
                        offenders += "${file.relativeTo(root).invariantSeparatorsPath} -> $token"
                    }
                }
            }
        assertTrue(
            "Built-in Help must not come back through sources:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `author resources and exact links are required`() {
        val ru = File(root, "app/src/main/res/values/strings_author.xml")
        val en = File(root, "app/src/main/res/values-en/strings_author.xml")
        assertTrue("values/strings_author.xml is required", ru.exists())
        assertTrue("values-en/strings_author.xml is required", en.exists())
        assertEquals(
            "Author keys must be translated in both locales",
            stringEntries(ru).map { it.first }.toSet(),
            stringEntries(en).map { it.first }.toSet()
        )

        val values = stringEntries(ru).map { it.second } + stringEntries(en).map { it.second }
        val joined = values.joinToString(" ").lowercase()
        assertTrue("Author resources must name SavaStone", joined.contains("savastone"))
        assertTrue("Author resources must mention GitHub", joined.contains("github"))
        assertTrue("Author resources must mention YouTube", joined.contains("youtube"))
        assertFalse(
            "Author links must use https",
            values.any { Regex("""(?i)http://""").containsMatchIn(it) }
        )

        val authorScreen = File(root, "app/src/main/java/app/opora/ui/screens/AuthorScreen.kt")
        assertTrue("AuthorScreen.kt is required", authorScreen.exists())
        val source = authorScreen.readText()
        assertTrue(
            "SavaStone YouTube URL must be exact",
            source.contains("https://www.youtube.com/@SavaStone")
        )
        assertTrue(
            "SavaStone GitHub URL must be exact",
            source.contains("https://github.com/SavaStone")
        )
        assertFalse("Author links must not use http://", source.contains("\"http://"))
    }

    @Test
    fun `repository is linked without planned copy and author has no avatar`() {
        val about = File(root, "app/src/main/java/app/opora/ui/screens/AboutScreen.kt").readText()
        assertTrue(about.contains("https://github.com/SavaStone/Anchor"))
        assertTrue(about.contains("Intent.ACTION_VIEW"))
        assertTrue(about.contains("OporaCard(onClick"))
        val author = File(root, "app/src/main/java/app/opora/ui/screens/AuthorScreen.kt").readText()
        assertFalse(author.contains("AuthorMonogram"))
        for (locale in listOf("values", "values-en")) {
            val keys = stringEntries(File(root, "app/src/main/res/$locale/strings_author.xml"))
                .map { it.first }
            assertFalse("author_monogram" in keys)
            assertFalse("about_repo_status" in keys)
            assertFalse("about_repo_body" in keys)
        }
    }

    private fun stringResourceFiles(): List<File> {
        val res = File(root, "app/src/main/res")
        return res.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .flatMap { dir ->
                dir.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }
                    .toList()
            }
            .sortedBy { it.path }
    }

    private fun stringEntries(file: File): List<Pair<String, String>> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
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
