package app.opora

import android.app.Application
import app.opora.diagnostics.CrashLogger
import app.opora.notifications.MedicationAlarms
import app.opora.notifications.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class OporaApp : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * Identifies the current OS process. A new token is generated whenever the
     * process is recreated, so the UI can tell a rotation from a cold start.
     */
    val processToken: String = UUID.randomUUID().toString()

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(newBase: android.content.Context) {
        AppLanguage.setTag(AppLanguage.resolveStartupTag(newBase))
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        container = AppContainer(this)
        Notify.ensureChannels(this)
        container.workScheduler.restoreAll()
        startupScope.launch {
            val expected = AppLanguage.currentTag()
            if (container.settings.current().language != expected) {
                container.settings.setLanguage(expected)
            }
            AppLanguage.applyToSystem(this@OporaApp, expected)
            runCatching { container.repository.ensureTodayLogs() }
            runCatching { MedicationAlarms(this@OporaApp, container.db).scheduleAll() }
            runCatching { container.autoBackup.run() }
        }
    }
}
