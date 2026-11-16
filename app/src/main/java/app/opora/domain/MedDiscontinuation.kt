package app.opora.domain

import androidx.annotation.StringRes
import app.opora.R
import java.time.LocalDate
import java.util.Locale

/**
 * Почему приём прекращён. В базе и бэкапе хранится [key], поэтому подпись
 * можно поправить без миграции; неизвестный ключ показываем как есть.
 */
enum class StopReason(val key: String, @StringRes val labelRes: Int) {
    DOCTOR("doctor", R.string.stop_doctor),
    COURSE_FINISHED("course", R.string.stop_course),
    SIDE_EFFECTS("side_effects", R.string.stop_side_effects),
    OTHER("other", R.string.stop_other);

    companion object {
        fun fromKey(key: String?): StopReason? = entries.firstOrNull { it.key == key }
    }
}

/** Display label; falls back to the raw stored key for unknown values. */
fun stopReasonLabel(key: String?, context: android.content.Context): String =
    if (key.isNullOrBlank()) {
        ""
    } else {
        StopReason.fromKey(key)?.let { context.getString(it.labelRes) } ?: key
    }

/**
 * Date for the card: "13 ноября", and for a previous year "13 ноября 2025",
 * so the list never mixes years.
 */
fun discontinuedDateLabel(
    millis: Long,
    today: LocalDate = LocalDate.now(),
    locale: Locale
): String {
    val date = millis.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    return if (date.year == today.year) date.dayMonth(locale) else date.dateWithYear(locale)
}
