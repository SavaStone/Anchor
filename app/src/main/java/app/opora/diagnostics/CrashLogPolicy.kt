package app.opora.diagnostics

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Правила хранения файлов падений: имя и ротация. Чистые функции, чтобы
 * проверять их на JVM без Android.
 */
object CrashLogPolicy {
    const val MAX_FILES = 10

    private const val PREFIX = "crash-"
    private const val SUFFIX = ".txt"
    private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun fileName(time: LocalDateTime): String = PREFIX + time.format(NAME_FORMAT) + SUFFIX

    fun isCrashName(name: String): Boolean =
        name.length > PREFIX.length + SUFFIX.length &&
            name.startsWith(PREFIX) &&
            name.endsWith(SUFFIX)

    /** Имена к удалению: всё, кроме [keep] самых новых (сортировка по времени в имени). */
    fun staleNames(names: List<String>, keep: Int = MAX_FILES): List<String> =
        names.filter(::isCrashName).sortedDescending().drop(keep)
}
