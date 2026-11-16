package app.opora.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.MedSchedule
import app.opora.domain.hhmm
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MedicationAlarms(
    private val context: Context,
    private val db: OporaDatabase
) {
    companion object {
        const val ACTION_MED = "app.opora.action.MED_ALARM"
        const val ACTION_SNOOZE_ALARM = "app.opora.action.SNOOZE_ALARM"
        private const val MAX_SLOTS = 24
    }

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val settings = SettingsRepository(context)

    private fun requestCode(medId: String, slot: Int): Int =
        ("med|$medId|$slot").hashCode() and 0x7FFFFFFF

    private fun slotIntent(medId: String, slot: Int, date: LocalDate, time: LocalTime, at: Long): Intent =
        Intent(context, MedAlarmReceiver::class.java).apply {
            action = ACTION_MED
            putExtra("medId", medId)
            putExtra("slot", slot)
            putExtra("epochDay", date.toEpochDay())
            putExtra("time", time.hhmm())
            putExtra("scheduledAt", at)
        }

    private fun slotPendingIntent(medId: String, slot: Int, date: LocalDate, time: LocalTime, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(medId, slot),
            slotIntent(medId, slot, date, time, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelSlot(medId: String, slot: Int) {
        val intent = Intent(context, MedAlarmReceiver::class.java).apply { action = ACTION_MED }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(medId, slot),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    suspend fun scheduleAll() {
        val all = db.medicationDao().getAll()
        for (med in all) {
            for (slot in 0 until MAX_SLOTS) cancelSlot(med.id, slot)
        }

        if (!settings.current().medReminders) return

        val now = LocalDateTime.now()
        for (med in all.filter { it.active && it.scheduleType != "as_needed" }) {
            val times = MedSchedule.parseTimes(med)
            times.take(MAX_SLOTS).forEachIndexed { index, time ->
                val next = MedSchedule.nextForTime(med, time, now) ?: return@forEachIndexed
                val inMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                setExact(inMillis, slotPendingIntent(med.id, index, next.toLocalDate(), time, inMillis))
            }
        }
    }

    fun scheduleSnooze(logId: String, atMillis: Long) {
        val intent = Intent(context, MedAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra("logId", logId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ("snooze|$logId").hashCode() and 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(atMillis, pi)
    }

    fun cancelSnooze(logId: String) {
        val intent = Intent(context, MedAlarmReceiver::class.java).apply { action = ACTION_SNOOZE_ALARM }
        val pi = PendingIntent.getBroadcast(
            context,
            ("snooze|$logId").hashCode() and 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /** Re-arm snoozes that were pending when the process/device died. */
    suspend fun restoreSnoozes() {
        val now = System.currentTimeMillis()
        runCatching {
            db.doseDao().snoozedAfter(now).forEach { log ->
                scheduleSnooze(log.id, log.snoozeUntil ?: return@forEach)
            }
        }
    }

    fun cancelAll() {
        // Best effort: iterate known medications; stale alarms are guarded by the receiver.
        val dbMeds = db.medicationDao()
        kotlinx.coroutines.runBlocking {
            runCatching {
                dbMeds.getAll().forEach { med ->
                    for (slot in 0 until MAX_SLOTS) cancelSlot(med.id, slot)
                }
            }
        }
    }

    /** Cancel every scheduled slot of one medication (used before deleting it). */
    fun cancelMedication(id: String) {
        for (slot in 0 until MAX_SLOTS) cancelSlot(id, slot)
    }

    private fun setExact(atMillis: Long, pendingIntent: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            }
        }.onFailure {
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent) }
        }
    }
}
