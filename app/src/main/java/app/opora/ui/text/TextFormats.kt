package app.opora.ui.text

import android.content.Context
import app.opora.AppLanguage
import app.opora.R
import app.opora.data.db.MedicationEntity
import app.opora.domain.MedSchedule
import app.opora.domain.TagDef
import app.opora.domain.hhmm
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Resolves a built-in tag through its resource; custom tags keep user text. */
fun TagDef.displayLabel(context: Context): String =
    if (labelRes != 0) context.getString(labelRes) else label.ifBlank { key }

fun List<TagDef>.displayLabel(context: Context, key: String): String =
    firstOrNull { it.key == key }?.displayLabel(context) ?: key

/** "7 ч 30 мин" / "7 h 30 min"; null minutes are an em dash. */
fun formatSleep(context: Context, minutes: Int?): String {
    if (minutes == null) return context.getString(R.string.common_dash)
    val hours = minutes / 60
    val rest = minutes % 60
    val res = context.resources
    return when {
        minutes < 60 -> res.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
        rest == 0 -> res.getQuantityString(R.plurals.duration_hours, hours, hours)
        else -> context.getString(R.string.duration_hours_minutes, hours, rest)
    }
}

/** A reminder offset such as "за 1 ч" / "in 2 days". */
fun formatDuration(context: Context, minutes: Int): String {
    val res = context.resources
    return when {
        minutes >= 24 * 60 -> {
            val days = minutes / (24 * 60)
            res.getQuantityString(R.plurals.duration_days, days, days)
        }
        minutes >= 60 -> {
            val hours = minutes / 60
            res.getQuantityString(R.plurals.duration_hours, hours, hours)
        }
        else -> res.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
    }
}

fun weekdayShort(day: DayOfWeek, context: Context): String =
    day.getDisplayName(TextStyle.SHORT, AppLanguage.locale(context))

/**
 * "Каждый день · 08:00, 20:00" / "Every day · 08:00, 20:00".
 * Lives outside the domain because it needs resources for plurals.
 */
fun scheduleSummary(context: Context, med: MedicationEntity): String {
    val locale = AppLanguage.locale(context)
    val times = MedSchedule.parseTimes(med).joinToString(", ") { it.hhmm() }
    val base = when (med.scheduleType) {
        "weekly" -> MedSchedule.parseWeekdays(med).sorted()
            .joinToString(", ") { weekdayShort(DayOfWeek.of(it), context) }
        "interval" -> {
            val days = context.resources.getQuantityString(
                R.plurals.days, med.intervalDays, med.intervalDays
            )
            context.getString(R.string.meds_schedule_interval, days)
        }
        "as_needed" -> context.getString(R.string.meds_schedule_as_needed)
        else -> context.getString(R.string.meds_schedule_daily)
    }
    return if (med.scheduleType == "as_needed") {
        base
    } else {
        context.getString(R.string.meds_schedule_with_times, base, times)
    }
}
