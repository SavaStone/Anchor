package app.opora

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the localization contract: user-facing text in the Compose layer must
 * come from resources. Comments and KDoc may stay Russian; string literals may not.
 *
 * The test runs on the JVM and reads sources from the module directory, so no
 * emulator is needed. Any exception belongs in [allowlist] with a reason.
 */
class CyrillicLiteralScanTest {

    /** file relative path -> reason. Keep this list empty unless unavoidable. */
    private val allowlist = emptyMap<String, String>()

    @Test
    fun uiSourcesHaveNoCyrillicStringLiterals() {
        val root = uiSourceRoot()
        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (relative in allowlist) return@forEach
                cyrillicLiterals(file.readText()).forEach { (line, literal) ->
                    offenders += "$relative:$line  $literal"
                }
            }
        assertTrue(
            "Кириллические литералы должны жить в strings.xml:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    private fun uiSourceRoot(): File {
        var dir: File? = File("").absoluteFile
        repeat(5) {
            val direct = File(dir, "src/main/java/app/opora/ui")
            if (direct.isDirectory) return direct
            val fromRoot = File(dir, "app/src/main/java/app/opora/ui")
            if (fromRoot.isDirectory) return fromRoot
            dir = dir?.parentFile
        }
        error("ui sources not found from ${File("").absolutePath}")
    }

    /** A small lexer: skips comments, returns string literal contents with Cyrillic. */
    private fun cyrillicLiterals(code: String): List<Pair<Int, String>> {
        val found = mutableListOf<Pair<Int, String>>()
        var i = 0
        var line = 1
        while (i < code.length) {
            when {
                code.startsWith("//", i) -> {
                    while (i < code.length && code[i] != '\n') i++
                }
                code.startsWith("/*", i) -> {
                    i += 2
                    while (i < code.length && !code.startsWith("*/", i)) {
                        if (code[i] == '\n') line++
                        i++
                    }
                    i = (i + 2).coerceAtMost(code.length)
                }
                code.startsWith("\"\"\"", i) -> {
                    val startLine = line
                    i += 3
                    val value = StringBuilder()
                    while (i < code.length && !code.startsWith("\"\"\"", i)) {
                        if (code[i] == '\n') line++
                        value.append(code[i])
                        i++
                    }
                    i = (i + 3).coerceAtMost(code.length)
                    if (value.any(::isCyrillic)) found += startLine to value.toString()
                }
                code[i] == '"' -> {
                    val startLine = line
                    i++
                    val value = StringBuilder()
                    while (i < code.length && code[i] != '"') {
                        if (code[i] == '\\' && i + 1 < code.length) {
                            value.append(code[i]).append(code[i + 1])
                            i += 2
                            continue
                        }
                        if (code[i] == '\n') line++
                        value.append(code[i])
                        i++
                    }
                    i++
                    if (value.any(::isCyrillic)) {
                        found += startLine to "\"${value.toString().take(80)}\""
                    }
                }
                code[i] == '\n' -> {
                    line++
                    i++
                }
                else -> i++
            }
        }
        return found
    }

    private fun isCyrillic(c: Char): Boolean = c in 'А'..'я' || c == 'Ё' || c == 'ё'
}
