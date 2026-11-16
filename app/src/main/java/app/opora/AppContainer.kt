package app.opora

import android.content.Context
import androidx.room.InvalidationTracker
import app.opora.data.OporaRepository
import app.opora.data.backup.AutoBackupManager
import app.opora.data.backup.BackupManager
import app.opora.data.backup.RestoreCoordinator
import app.opora.data.backup.SyncManager
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: OporaDatabase = OporaDatabase.build(appContext)
    val settings: SettingsRepository = SettingsRepository(appContext)
    val workScheduler: WorkScheduler = WorkScheduler(appContext)
    val repository: OporaRepository = OporaRepository(appContext, db, settings, workScheduler)
    val backup: BackupManager = BackupManager(appContext, db, settings)
    val autoBackup: AutoBackupManager = AutoBackupManager(appContext, settings, backup, db)
    val sync: SyncManager = SyncManager(appContext, db, settings, backup)
    val restore: RestoreCoordinator = RestoreCoordinator(
        appContext, db, repository, backup, settings, workScheduler
    )

    private val autoBackupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingAutoBackup: Job? = null

    /**
     * Любая запись в базу ставит копию в очередь. Сама очередь склеивает серию
     * изменений (например, массовый импорт) в одну попытку, а часовой throttle
     * в [AutoBackupManager.run] не даёт писать чаще раза в час.
     */
    private val invalidationObserver = object : InvalidationTracker.Observer(AUTO_BACKUP_TABLES) {
        override fun onInvalidated(tables: Set<String>) {
            requestAutoBackup()
        }
    }

    init {
        db.invalidationTracker.addObserver(invalidationObserver)
    }

    fun requestAutoBackup() {
        if (pendingAutoBackup?.isActive == true) return
        pendingAutoBackup = autoBackupScope.launch {
            delay(AUTO_BACKUP_DEBOUNCE_MS)
            runCatching { autoBackup.run() }
        }
    }

    private companion object {
        const val AUTO_BACKUP_DEBOUNCE_MS = 5_000L

        val AUTO_BACKUP_TABLES = arrayOf(
            "mood_entries",
            "medications",
            "dose_logs",
            "schedule_snapshots",
            "medication_pauses",
            "journal_entries",
            "custom_tags",
            "positive_entries",
            "appointments",
            "safety_plan",
            "forgiven_dose_days"
        )
    }
}
