package app.opora.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import app.opora.AppLanguage
import app.opora.domain.CalendarEvent
import app.opora.domain.CalendarExtra
import app.opora.domain.dateTime
import java.time.Instant
import java.time.ZoneId

/**
 * Opens the system calendar insert dialog. The app never writes events itself
 * and needs no calendar permission: the chosen calendar app owns the dialog.
 */
fun Context.openCalendarInsert(event: CalendarEvent): Boolean {
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        event.extras().forEach { (key, value) ->
            when (value) {
                is Long -> putExtra(key, value)
                is Int -> putExtra(key, value)
                is Boolean -> putExtra(key, value)
                else -> putExtra(key, value.toString())
            }
        }
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Plain-text fallback for devices without any calendar app. */
fun CalendarEvent.copyText(context: Context): String {
    val locale = AppLanguage.locale(context)
    val start = Instant.ofEpochMilli(beginMillis)
        .atZone(ZoneId.of(timeZone))
        .toLocalDateTime()
        .dateTime(locale)
    return listOfNotNull(
        title,
        start,
        location.takeIf { it.isNotBlank() },
        description.takeIf { it.isNotBlank() }
    ).joinToString("\n")
}

fun Context.copyCalendarEvent(event: CalendarEvent) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(event.title, event.copyText(this))
    )
}
