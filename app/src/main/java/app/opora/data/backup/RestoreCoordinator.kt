package app.opora.data.backup

import app.opora.R

import android.content.Context
import app.opora.data.OporaRepository
import app.opora.data.db.AppointmentEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.notifications.MedicationAlarms
import app.opora.work.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class RestoreStage { SNAPSHOT, WRITING, REARMING }

sealed interface RestoreState {
    data object Idle : RestoreState
    data class Running(val stage: RestoreStage) : RestoreState
    data class Success(val records: Int) : RestoreState
    data class Failed(val message: String) : RestoreState
    data object Cancelled : RestoreState
}

/**
 * Импорт живёт в scope приложения, а не экрана: поворот не отменяет
 * восстановление и не оставляет снятые будильники. Прогресс и отмена
 * доступны из UI через [state].
 */
class RestoreCoordinator(
    private val context: Context,
    private val db: OporaDatabase,
    private val repository: OporaRepository,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val workScheduler: WorkScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val state: StateFlow<RestoreState> = _state.asStateFlow()
    private var job: Job? = null

    /** Тестовый тормоз, чтобы поворот/отмена успели вмешаться; в проде 0. */
    var stageDelayMs: Long = 0

    fun start(payload: BackupPayload): Boolean {
        if (job?.isActive == true) return false
        job = scope.launch {
            try {
                _state.value = RestoreState.Running(RestoreStage.SNAPSHOT)
                // Сначала снимок текущих данных; до него ничего не отменяем.
                backup.createRestorePoint(settings.current().userName)
                if (stageDelayMs > 0) delay(stageDelayMs)
                cancelBeforeRestore()
                _state.value = RestoreState.Running(RestoreStage.WRITING)
                if (stageDelayMs > 0) delay(stageDelayMs)
                backup.restore(payload)
                _state.value = RestoreState.Running(RestoreStage.REARMING)
                if (stageDelayMs > 0) delay(stageDelayMs)
                rearm(payload.appointments)
                _state.value = RestoreState.Success(payload.totalRecords)
            } catch (cancelled: CancellationException) {
                // Транзакция Room откатывается отменой; будильники и визиты
                // возвращаем по тому, что реально осталось в базе.
                withContext(NonCancellable) { runCatching { rearm(null) } }
                _state.value = RestoreState.Cancelled
                throw cancelled
            } catch (failure: Exception) {
                withContext(NonCancellable) { runCatching { rearm(null) } }
                _state.value = RestoreState.Failed(
                    context.getString(R.string.errors_restore_failed)
                )
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
    }

    fun clear() {
        if (job?.isActive != true) _state.value = RestoreState.Idle
    }

    private suspend fun cancelBeforeRestore() {
        db.appointmentDao().getAll().forEach { workScheduler.cancelAppointment(it.id) }
        workScheduler.cancelAllScheduled()
        val alarms = MedicationAlarms(context, db)
        db.doseDao().getAll().forEach { runCatching { alarms.cancelSnooze(it.id) } }
        alarms.cancelAll()
    }

    private suspend fun rearm(payloadAppointments: List<AppointmentEntity>?) {
        runCatching { repository.ensureTodayLogs() }
        runCatching { MedicationAlarms(context, db).scheduleAll() }
        runCatching { workScheduler.restoreAll() }
        val appointments = payloadAppointments ?: db.appointmentDao().getAll()
        appointments.filter { !it.done }.forEach { appointment ->
            runCatching { workScheduler.scheduleAppointment(appointment) }
        }
        runCatching {
            val current = settings.current()
            if (current.webDavEnabled && current.autoSync) {
                workScheduler.scheduleAutoSync()
            }
            workScheduler.scheduleDailyMood(current.moodReminder, current.moodReminderTime)
        }
    }
}
