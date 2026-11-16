package app.opora.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.opora.OporaApp
import app.opora.AppLanguage
import app.opora.R
import app.opora.domain.dateTime
import app.opora.notifications.Notify
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "OporaWorker"

private fun Throwable.isTransientNetworkError(): Boolean {
    // WebDavClient заворачивает сетевые ошибки в IllegalStateException,
    // поэтому проверяем всю цепочку cause, а не только верхний уровень.
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 5) {
        if (current is IOException) return true
        current = current.cause
        depth++
    }
    return false
}

class MaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OporaApp ?: return Result.success()
        val container = app.container
        return try {
            try {
                container.repository.ensureTodayLogs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "ensureTodayLogs failed", e)
            }

            val settings = container.settings.current()

            if (settings.refillReminders) {
                val prefs = applicationContext.getSharedPreferences("opora_refill", Context.MODE_PRIVATE)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                container.db.medicationDao().getAll()
                    .filter { it.active && it.inventoryCount != null && it.refillThreshold != null }
                    .filter { it.inventoryCount!! <= it.refillThreshold!! }
                    .forEach { med ->
                        if (prefs.getString(med.id, null) != today) {
                            Notify.refill(applicationContext, med)
                            prefs.edit().putString(med.id, today).apply()
                        }
                    }
            }

            if (settings.autoBackupFolderUri.isNotBlank()) {
                try {
                    container.autoBackup.run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "auto backup failed", e)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "maintenance failed", e)
            if (e.isTransientNetworkError()) Result.retry() else Result.success()
        }
    }
}

/**
 * Автосинхронизация с WebDAV. Запускается только при подключённой сети.
 *
 * Сейчас копия загружается по принципу last-write-wins: локальное состояние
 * перезаписывает файл на сервере. TODO: сравнивать время изменения удалённого
 * файла с lastSyncAt/локальными правками, если WebDAV это позволяет.
 */
class AutoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OporaApp ?: return Result.success()
        val container = app.container
        val settings = try {
            container.settings.current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "failed to read settings", e)
            return if (e.isTransientNetworkError()) Result.retry() else Result.success()
        }
        if (!settings.webDavEnabled || !settings.autoSync) return Result.success()

        val result = container.sync.upload()
        val error = result.exceptionOrNull() ?: return Result.success()
        if (error is CancellationException) throw error

        Log.w(TAG, "auto sync failed", error)
        try {
            container.settings.setLastSync(
                container.settings.current().lastSyncAt,
                applicationContext.getString(R.string.errors_sync_failed)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "failed to store sync error", e)
        }
        return if (error.isTransientNetworkError()) Result.retry() else Result.success()
    }
}

class DailyMoodWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notify.dailyMood(applicationContext)
        return Result.success()
    }
}

/**
 * Ежедневная копия в выбранную папку. Часовой throttle внутри
 * AutoBackupManager не даёт дублировать копию, если приложение уже писало её.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? OporaApp ?: return Result.success()
        return try {
            app.container.autoBackup.run()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "auto backup worker failed", e)
            Result.success()
        }
    }
}

class AppointmentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OporaApp ?: return Result.success()
        val id = inputData.getString("appointmentId") ?: return Result.success()
        val appointment = app.container.db.appointmentDao().getAll().firstOrNull { it.id == id }
            ?: return Result.success()
        if (appointment.done) return Result.success()
        val localized = AppLanguage.wrap(applicationContext)
        Notify.appointment(
            localized,
            title = localized.getString(R.string.notif_appointment_title, appointment.title),
            detail = listOfNotNull(
                appointment.startsAt.dateTime(AppLanguage.locale(localized)),
                appointment.doctor.takeIf { it.isNotBlank() },
                appointment.place.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            id = appointment.id
        )
        return Result.success()
    }
}
