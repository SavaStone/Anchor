package app.opora.data.backup

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure rules for folder auto-backups: naming, throttle and retention.
 * Kept free of Android so it can be unit-tested on the JVM.
 */
object AutoBackupPolicy {
    const val PREFIX = "opora-auto-"
    const val SUFFIX = ".opora"

    /** Не чаще раза в час: копия пишется и при старте, и после изменений. */
    const val MIN_INTERVAL_MS = 60L * 60 * 1000
    const val MAX_FILES = 7

    private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** Первая копия получает имя без суффикса: opora-auto-20261114-1540.opora. */
    fun fileName(time: LocalDateTime): String = PREFIX + time.format(NAME_FORMAT) + SUFFIX

    fun isBackupName(name: String): Boolean =
        name.length > PREFIX.length + SUFFIX.length &&
            name.startsWith(PREFIX) &&
            name.endsWith(SUFFIX)

    fun isDue(lastAttemptAt: Long, now: Long): Boolean =
        lastAttemptAt <= 0L || now - lastAttemptAt >= MIN_INTERVAL_MS

    /**
     * Возвращает имена, которые нужно удалить: всё, кроме [keep] самых новых.
     * Имена содержат фиксированную метку времени, поэтому сортировки по имени
     * достаточно и она не зависит от часового пояса файловой системы.
     */
    fun staleNames(names: List<String>, keep: Int = MAX_FILES): List<String> =
        names.filter(::isBackupName).sortedDescending().drop(keep)

    /** Дата копии из имени; null, если имя не наше или повреждено. */
    fun savedAt(name: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!isBackupName(name)) return null
        val stamp = name.substring(PREFIX.length, name.length - SUFFIX.length)
        return runCatching {
            LocalDateTime.parse(stamp, NAME_FORMAT).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
