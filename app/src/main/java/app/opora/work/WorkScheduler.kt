package app.opora.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.opora.data.db.AppointmentEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WorkScheduler(private val context: Context) {

    fun restoreAll() {
        scheduleMaintenance()
        scheduleAutoSync()
        scheduleAutoBackup()
    }

    fun scheduleMaintenance() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "opora_maintenance",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Ежедневная страховка: если приложение неделю не открывали, копия всё
     * равно появится в выбранной папке. Без папки или пароля воркер сам
     * завершится, ничего не записав.
     */
    fun scheduleAutoBackup() {
        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "opora_auto_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Отдельная задача синхронизации: только при наличии сети, локальные копии от неё не зависят. */
    fun scheduleAutoSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "opora_auto_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAutoSync() {
        WorkManager.getInstance(context).cancelUniqueWork("opora_auto_sync")
    }

    /**
     * Отменяет фоновые задачи, не привязанные к настройкам пользователя.
     * Напоминание о настроении управляется настройкой и здесь не трогается.
     */
    fun cancelAllScheduled() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("opora_maintenance")
        wm.cancelUniqueWork("opora_auto_sync")
    }

    fun scheduleDailyMood(enabled: Boolean, time: String) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork("opora_daily_mood")
            return
        }
        val target = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(21, 0))
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()

        val request = PeriodicWorkRequestBuilder<DailyMoodWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        // CANCEL_AND_REENQUEUE, а не UPDATE: при смене времени нужно применить новый initial delay.
        wm.enqueueUniquePeriodicWork(
            "opora_daily_mood",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    fun scheduleAppointment(appointment: AppointmentEntity) {
        val trigger = appointment.startsAt - appointment.remindMinutesBefore * 60_000L
        val delay = trigger - System.currentTimeMillis()
        if (delay <= 0) {
            // Время уже прошло: убираем возможное старое напоминание, новое не ставим.
            cancelAppointment(appointment.id)
            return
        }
        val request = OneTimeWorkRequestBuilder<AppointmentWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("appointmentId" to appointment.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "opora_appt_${appointment.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAppointment(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("opora_appt_$id")
    }
}
