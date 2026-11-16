package app.opora.data.backup

import android.content.Context
import app.opora.R
import app.opora.data.UserMessageException
import app.opora.data.db.OporaDatabase
import app.opora.data.userMessage
import app.opora.data.prefs.SettingsRepository
import app.opora.security.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SyncManager(
    private val context: Context,
    private val db: OporaDatabase,
    private val settings: SettingsRepository,
    private val backup: BackupManager,
    private val clientFactory: (String, String, String) -> WebDavClient = { url, user, password ->
        WebDavClient(url, user, password)
    }
) {
    companion object {
        const val FILE_NAME = "opora-backup.opora"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun test(url: String, user: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            clientFactory(url, user, password).test()
        }

    suspend fun upload(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = settings.webDavConfig() ?: throw UserMessageException(R.string.errors_webdav_not_configured)
            val payload = backup.createPayload()
            val plain = json.encodeToString(payload)
            if (cfg.backupPassword.isBlank()) {
                throw UserMessageException(R.string.errors_backup_password_required)
            }
            val encrypted = BackupCrypto.encrypt(plain, cfg.backupPassword.toCharArray())
            clientFactory(cfg.url, cfg.user, cfg.password).upload(FILE_NAME, encrypted).getOrThrow()
            val now = System.currentTimeMillis()
            settings.setLastSync(now)
            now
        }.onFailure {
            settings.setLastSync(settings.current().lastSyncAt, it.userMessage(context))
        }
    }

    suspend fun download(): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = settings.webDavConfig() ?: throw UserMessageException(R.string.errors_webdav_not_configured)
            val bytes = clientFactory(cfg.url, cfg.user, cfg.password).download(FILE_NAME).getOrThrow()
                ?: throw UserMessageException(R.string.errors_no_server_backup)
            val text = decryptRemoteBackup(bytes, cfg.backupPassword, cfg.password)
            parseBackupPayload(json, text)
        }
    }
}

    /** Новые файлы — паролем копии; старые, зашифрованные WebDAV-паролем, читаются фолбэком. */
internal fun decryptRemoteBackup(
        bytes: ByteArray,
        backupPassword: String,
        legacyWebDavPassword: String
    ): String {
        val candidates = listOf(backupPassword, legacyWebDavPassword)
            .filter { it.isNotBlank() }
            .distinct()
        var last: Throwable? = null
        candidates.forEach { password ->
            runCatching { BackupCrypto.decrypt(bytes, password.toCharArray()) }
                .onSuccess { return it }
                .onFailure { last = it }
        }
        throw UserMessageException(R.string.errors_server_decrypt).apply {
            initCause(last)
        }
    }

