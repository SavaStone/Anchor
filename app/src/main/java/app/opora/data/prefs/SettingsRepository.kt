package app.opora.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.opora.data.backup.AutoBackupPasswordBox
import app.opora.domain.DEFAULT_HIDDEN_HOME_BLOCKS
import app.opora.domain.DEFAULT_HOME_ORDER
import app.opora.domain.parseHomeBlocks
import app.opora.security.KeystoreSecretBox
import app.opora.security.SecretBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "opora_settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val userName: String = "",
    val themeMode: String = "system", // system | light | dark
    val dynamicColor: Boolean = false,
    val leftHanded: Boolean = false,
    val homeBlocks: String = DEFAULT_HOME_ORDER,
    val hiddenHomeBlocks: String = DEFAULT_HIDDEN_HOME_BLOCKS,
    val hiddenTabs: String = "",
    val appLock: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val secureScreen: Boolean = false,
    val medReminders: Boolean = true,
    val moodReminder: Boolean = false,
    val moodReminderTime: String = "21:00",
    val refillReminders: Boolean = true,
    val lastBackupAt: Long = 0L,
    /** SAF-папка для автобэкапа; пустая строка — папка не выбрана. */
    val autoBackupFolderUri: String = "",
    val autoBackupFolderLabel: String = "",
    val autoBackupPasswordSet: Boolean = false,
    /** Конверт есть, но ключ Keystore его уже не открывает. */
    val autoBackupPasswordLost: Boolean = false,
    /** Время последней успешной копии в папку. */
    val lastAutoBackupAt: Long = 0L,
    /** Время последней попытки — по нему работает часовой throttle. */
    val lastAutoBackupAttemptAt: Long = 0L,
    /** Код последней ошибки автобэкапа (см. AutoBackupFailure). */
    val autoBackupError: String = "",
    val restorePromptDismissed: Boolean = false,
    val webDavUrl: String = "",
    val webDavUser: String = "",
    val webDavBackupPasswordSet: Boolean = false,
    val webDavKeyWarning: Boolean = false,
    val webDavEnabled: Boolean = false,
    val autoSync: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncError: String = "",
    val disclaimerAccepted: Boolean = false,
    /** BCP 47 language tag; empty means "follow the device language". */
    val language: String = ""
)

data class WebDavConfig(
    val url: String,
    val user: String,
    val password: String,
    /** Отдельный пароль для шифрования файлов; WebDAV-пароль — только транспорт. */
    val backupPassword: String = ""
)

class SettingsRepository(
    private val context: Context,
    private val secretBox: SecretBox = KeystoreSecretBox
) {
    private val autoPassword = AutoBackupPasswordBox(secretBox)

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val userName = stringPreferencesKey("user_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val leftHanded = booleanPreferencesKey("left_handed")
        val homeBlocks = stringPreferencesKey("home_blocks")
        val hiddenHomeBlocks = stringPreferencesKey("hidden_home_blocks")
        val hiddenTabs = stringPreferencesKey("hidden_tabs")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val medReminders = booleanPreferencesKey("med_reminders")
        val moodReminder = booleanPreferencesKey("mood_reminder")
        val moodReminderTime = stringPreferencesKey("mood_reminder_time")
        val refillReminders = booleanPreferencesKey("refill_reminders")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val autoBackupFolder = stringPreferencesKey("auto_backup_folder")
        val autoBackupFolderLabel = stringPreferencesKey("auto_backup_folder_label")
        val autoBackupPassword = stringPreferencesKey("auto_backup_password")
        val lastAutoBackupAt = longPreferencesKey("last_auto_backup_at")
        val lastAutoBackupAttemptAt = longPreferencesKey("last_auto_backup_attempt_at")
        val autoBackupError = stringPreferencesKey("auto_backup_error")
        val restorePromptDismissed = booleanPreferencesKey("auto_restore_prompt_dismissed")
        val webDavUrl = stringPreferencesKey("webdav_url")
        val webDavUser = stringPreferencesKey("webdav_user")
        val webDavPassword = stringPreferencesKey("webdav_password")
        val webDavBackupPassword = stringPreferencesKey("webdav_backup_password")
        val webDavEnabled = booleanPreferencesKey("webdav_enabled")
        val autoSync = booleanPreferencesKey("auto_sync")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val disclaimerAccepted = booleanPreferencesKey("disclaimer_accepted")
        val language = stringPreferencesKey("language")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            userName = p[Keys.userName] ?: "",
            themeMode = p[Keys.themeMode] ?: "system",
            dynamicColor = p[Keys.dynamicColor] ?: false,
            leftHanded = p[Keys.leftHanded] ?: false,
            homeBlocks = parseHomeBlocks(p[Keys.homeBlocks] ?: DEFAULT_HOME_ORDER)
                .joinToString(",") { it.key },
            hiddenHomeBlocks = p[Keys.hiddenHomeBlocks] ?: DEFAULT_HIDDEN_HOME_BLOCKS,
            hiddenTabs = p[Keys.hiddenTabs] ?: "",
            appLock = p[Keys.appLock] ?: false,
            lockTimeoutSeconds = p[Keys.lockTimeout] ?: 60,
            secureScreen = p[Keys.secureScreen] ?: false,
            medReminders = p[Keys.medReminders] ?: true,
            moodReminder = p[Keys.moodReminder] ?: false,
            moodReminderTime = p[Keys.moodReminderTime] ?: "21:00",
            refillReminders = p[Keys.refillReminders] ?: true,
            lastBackupAt = p[Keys.lastBackupAt] ?: 0L,
            autoBackupFolderUri = p[Keys.autoBackupFolder] ?: "",
            autoBackupFolderLabel = p[Keys.autoBackupFolderLabel] ?: "",
            autoBackupPasswordSet = !p[Keys.autoBackupPassword].isNullOrBlank(),
            autoBackupPasswordLost = autoPassword.isLost(p[Keys.autoBackupPassword]),
            lastAutoBackupAt = p[Keys.lastAutoBackupAt] ?: 0L,
            lastAutoBackupAttemptAt = p[Keys.lastAutoBackupAttemptAt] ?: 0L,
            autoBackupError = p[Keys.autoBackupError] ?: "",
            restorePromptDismissed = p[Keys.restorePromptDismissed] ?: false,
            webDavUrl = p[Keys.webDavUrl] ?: "",
            webDavUser = p[Keys.webDavUser] ?: "",
            webDavBackupPasswordSet = !p[Keys.webDavBackupPassword].isNullOrBlank(),
            webDavKeyWarning = listOf(
                p[Keys.webDavPassword],
                p[Keys.webDavBackupPassword]
            ).any { raw -> !raw.isNullOrBlank() && secretBox.decrypt(raw) == null },
            webDavEnabled = p[Keys.webDavEnabled] ?: false,
            autoSync = p[Keys.autoSync] ?: false,
            lastSyncAt = p[Keys.lastSyncAt] ?: 0L,
            lastSyncError = p[Keys.lastSyncError] ?: "",
            disclaimerAccepted = p[Keys.disclaimerAccepted] ?: false,
            language = p[Keys.language] ?: ""
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(value: Boolean) = context.dataStore.edit {
        it[Keys.onboardingDone] = value
    }

    suspend fun setUserName(value: String) = context.dataStore.edit {
        it[Keys.userName] = value.trim()
    }

    suspend fun clearUserName() = context.dataStore.edit {
        it[Keys.userName] = ""
    }

    suspend fun setThemeMode(value: String) = context.dataStore.edit {
        it[Keys.themeMode] = value
    }

    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit {
        it[Keys.dynamicColor] = value
    }

    suspend fun setLeftHanded(value: Boolean) = context.dataStore.edit {
        it[Keys.leftHanded] = value
    }

    suspend fun setHomeBlocks(value: String) = context.dataStore.edit {
        it[Keys.homeBlocks] = value
    }

    suspend fun setHiddenHomeBlocks(value: String) = context.dataStore.edit {
        it[Keys.hiddenHomeBlocks] = value
    }

    suspend fun setHiddenTabs(value: String) = context.dataStore.edit {
        it[Keys.hiddenTabs] = value
    }

    suspend fun setAppLock(value: Boolean) = context.dataStore.edit {
        it[Keys.appLock] = value
    }

    /** Таймаут блокировки; 0 — блокировать сразу после ухода в фон. */
    suspend fun setLockTimeoutSeconds(value: Int) = context.dataStore.edit {
        it[Keys.lockTimeout] = value.coerceAtLeast(0)
    }

    suspend fun setLockTimeout(seconds: Int) = context.dataStore.edit {
        it[Keys.lockTimeout] = seconds
    }

    suspend fun setSecureScreen(value: Boolean) = context.dataStore.edit {
        it[Keys.secureScreen] = value
    }

    suspend fun setMedReminders(value: Boolean) = context.dataStore.edit {
        it[Keys.medReminders] = value
    }

    suspend fun setMoodReminder(enabled: Boolean, time: String? = null) = context.dataStore.edit {
        it[Keys.moodReminder] = enabled
        if (time != null) it[Keys.moodReminderTime] = time
    }

    suspend fun setRefillReminders(value: Boolean) = context.dataStore.edit {
        it[Keys.refillReminders] = value
    }

    suspend fun setAutoBackupFolder(uri: String, label: String) = context.dataStore.edit {
        it[Keys.autoBackupFolder] = uri
        it[Keys.autoBackupFolderLabel] = label
        it.remove(Keys.autoBackupError)
    }

    suspend fun clearAutoBackupFolder() = context.dataStore.edit {
        it.remove(Keys.autoBackupFolder)
        it.remove(Keys.autoBackupFolderLabel)
        it.remove(Keys.autoBackupError)
    }

    /**
     * Пароль автобэкапа хранится только AES/GCM-конвертом от Keystore.
     * false — ключ недоступен, сохранять открытый текст нельзя.
     */
    suspend fun setAutoBackupPassword(password: CharArray): Boolean {
        val sealed = autoPassword.seal(password) ?: return false
        context.dataStore.edit {
            it[Keys.autoBackupPassword] = sealed
            it.remove(Keys.autoBackupError)
        }
        return true
    }

    suspend fun clearAutoBackupPassword() = context.dataStore.edit {
        it.remove(Keys.autoBackupPassword)
    }

    suspend fun autoBackupPassword(): CharArray? =
        autoPassword.open(context.dataStore.data.first()[Keys.autoBackupPassword])

    suspend fun setLastBackupAt(value: Long) = context.dataStore.edit {
        it[Keys.lastBackupAt] = value
    }

    suspend fun setLastAutoBackupAt(value: Long) = context.dataStore.edit {
        it[Keys.lastAutoBackupAt] = value
    }

    suspend fun recordAutoBackupAttempt(at: Long) = context.dataStore.edit {
        it[Keys.lastAutoBackupAttemptAt] = at
    }

    suspend fun recordAutoBackupSuccess(at: Long) = context.dataStore.edit {
        it[Keys.lastAutoBackupAttemptAt] = at
        it[Keys.lastAutoBackupAt] = at
        it.remove(Keys.autoBackupError)
    }

    suspend fun recordAutoBackupError(code: String, at: Long) = context.dataStore.edit {
        it[Keys.lastAutoBackupAttemptAt] = at
        it[Keys.autoBackupError] = code
    }

    suspend fun setRestorePromptDismissed(value: Boolean) = context.dataStore.edit {
        it[Keys.restorePromptDismissed] = value
    }

    /**
     * Сбрасывает отметку о ручном экспорте после удаления файлов. Состояние
     * автобэкапа не трогаем: оно относится к папке и переживает очистку данных,
     * а сброс throttle сразу после удаления записал бы пустую копию.
     */
    suspend fun clearBackupTimestamps() = context.dataStore.edit {
        it.remove(Keys.lastBackupAt)
    }

    suspend fun setWebDav(
        url: String,
        user: String,
        password: String?,
        backupPassword: String? = null
    ) = context.dataStore.edit {
        it[Keys.webDavUrl] = url.trim()
        it[Keys.webDavUser] = user.trim()
        if (password != null) {
            it[Keys.webDavPassword] = secretBox.encrypt(password) ?: ""
        }
        if (backupPassword != null) {
            it[Keys.webDavBackupPassword] = secretBox.encrypt(backupPassword) ?: ""
        }
    }

    suspend fun setWebDavEnabled(value: Boolean) = context.dataStore.edit {
        it[Keys.webDavEnabled] = value
    }

    suspend fun setAutoSync(value: Boolean) = context.dataStore.edit {
        it[Keys.autoSync] = value
    }

    suspend fun setLastSync(at: Long, error: String = "") = context.dataStore.edit {
        it[Keys.lastSyncAt] = at
        it[Keys.lastSyncError] = error
    }

    suspend fun setDisclaimerAccepted(value: Boolean) = context.dataStore.edit {
        it[Keys.disclaimerAccepted] = value
    }

    suspend fun setLanguage(value: String) = context.dataStore.edit {
        it[Keys.language] = value
    }

    /** Read once at process start, before Compose exists. */
    suspend fun languageTag(): String = context.dataStore.data.first()[Keys.language] ?: ""

    suspend fun webDavConfig(): WebDavConfig? {
        val s = current()
        if (s.webDavUrl.isBlank()) return null
        val prefs = context.dataStore.data.first()
        val pwd = prefs[Keys.webDavPassword] ?: ""
        val backup = prefs[Keys.webDavBackupPassword] ?: ""
        return WebDavConfig(
            s.webDavUrl,
            s.webDavUser,
            secretBox.decrypt(pwd) ?: "",
            secretBox.decrypt(backup) ?: ""
        )
    }

    suspend fun clearWebDavPassword() = context.dataStore.edit {
        it[Keys.webDavPassword] = ""
    }

    /** Полностью забывает настройки сервера: адрес, логин, пароль и автосинхронизацию. */
    suspend fun clearWebDav() = context.dataStore.edit {
        it[Keys.webDavUrl] = ""
        it[Keys.webDavUser] = ""
        it[Keys.webDavPassword] = ""
        it[Keys.webDavBackupPassword] = ""
        it[Keys.webDavEnabled] = false
        it[Keys.autoSync] = false
        it[Keys.lastSyncAt] = 0L
        it[Keys.lastSyncError] = ""
    }
}
