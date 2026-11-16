package app.opora.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.opora.OporaApp
import app.opora.data.db.DoseLogEntity
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.MedSchedule
import app.opora.domain.hhmm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as? OporaApp ?: run { pending.finish(); return }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    MedicationAlarms.ACTION_MED -> handleMedication(context, app, intent)
                    MedicationAlarms.ACTION_SNOOZE_ALARM -> handleSnooze(context, app, intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleMedication(context: Context, app: OporaApp, intent: Intent) {
        val medId = intent.getStringExtra("medId") ?: return
        val epochDay = intent.getLongExtra("epochDay", -1)
        val time = intent.getStringExtra("time") ?: return
        if (epochDay < 0) return
        val med = app.container.repository.medicationOnce(medId) ?: return
        if (!med.active) return
        // Ignore stale alarms after schedule/date/timezone changes.
        if (epochDay != LocalDate.now().toEpochDay()) return
        val endDate = med.endDate ?: 0L
        if (endDate > 0 && epochDay > endDate) return
        if (MedSchedule.parseTimes(med).none { it.hhmm() == time }) return

        val settings = SettingsRepository(context).current()
        if (!settings.medReminders) return

        val dao = app.container.db.doseDao()
        val log = dao.find(medId, epochDay, time) ?: DoseLogEntity(
            id = DoseLogEntity.scheduledId(medId, epochDay, time),
            medicationId = med.id,
            medicationName = med.name,
            dosage = med.dosage,
            color = med.color,
            scheduledDate = epochDay,
            scheduledTime = time,
            scheduledAt = intent.getLongExtra("scheduledAt", System.currentTimeMillis()),
            status = "pending"
        ).also { dao.insertIgnore(it) }.let { dao.find(medId, epochDay, time) ?: it }

        if (log.status == "pending") {
            // Пока будильник шёл, приём могли прекратить: ни отметки, ни
            // напоминания уже быть не должно.
            val current = app.container.repository.medicationOnce(medId)
            if (current?.active != true) {
                dao.deletePendingById(log.id)
                return
            }
            Notify.medication(context, log, med)
        }
        // Arm the next occurrence of every slot (including this one).
        MedicationAlarms(context, app.container.db).scheduleAll()
    }

    private suspend fun handleSnooze(context: Context, app: OporaApp, intent: Intent) {
        val logId = intent.getStringExtra("logId") ?: return
        val dao = app.container.db.doseDao()
        val log = dao.byId(logId) ?: return
        if (log.status != "pending") return
        val settings = SettingsRepository(context).current()
        if (!settings.medReminders) return
        val med = app.container.repository.medicationOnce(log.medicationId)
        // Отложенное напоминание не должно пережить прекращение приёма.
        if (med?.active != true) return
        Notify.medication(context, log, med)
    }
}

class MedActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logId = intent.getStringExtra("logId") ?: return
        val pending = goAsync()
        val app = context.applicationContext as? OporaApp ?: run { pending.finish(); return }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Notify.ACTION_TAKEN -> app.container.repository.setDoseStatus(logId, "taken")
                    Notify.ACTION_SKIP -> app.container.repository.setDoseStatus(logId, "skipped")
                    Notify.ACTION_SNOOZE -> app.container.repository.snoozeDose(logId, 10)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        val pending = goAsync()
        val app = context.applicationContext as? OporaApp ?: run { pending.finish(); return }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Notify.ensureChannels(context)
                app.container.repository.ensureTodayLogs()
                val alarms = MedicationAlarms(context, app.container.db)
                alarms.scheduleAll()
                alarms.restoreSnoozes()
                app.container.workScheduler.restoreAll()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("OporaBoot", "Не удалось восстановить напоминания", t)
            } finally {
                pending.finish()
            }
        }
    }
}
