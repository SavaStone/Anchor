package app.opora.data.backup

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import app.opora.R
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.AppSettings
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Причина последней неудачи автобэкапа. Код хранится в DataStore, текст — в ресурсах. */
enum class AutoBackupFailure(val code: String, @StringRes val messageRes: Int) {
    FOLDER_UNAVAILABLE("folder", R.string.backup_auto_error_folder),
    PASSWORD_MISSING("password_missing", R.string.backup_auto_error_password_missing),
    PASSWORD_LOST("password_lost", R.string.backup_auto_error_password_lost),
    WRITE_FAILED("write", R.string.backup_auto_error_write);

    companion object {
        fun fromCode(code: String): AutoBackupFailure? =
            entries.firstOrNull { it.code == code }
    }
}

sealed interface AutoBackupOutcome {
    /** Нет папки или пароля — автобэкап честно выключен. */
    data object NotConfigured : AutoBackupOutcome

    /** Настройки есть, но час с прошлой попытки ещё не прошёл. */
    data object Skipped : AutoBackupOutcome

    data class Saved(val name: String) : AutoBackupOutcome

    data class Failed(val reason: AutoBackupFailure) : AutoBackupOutcome
}

/** Файл копии в выбранной папке или в «Загрузках». */
data class AutoBackupEntry(
    val uri: Uri,
    val name: String,
    val savedAt: Long,
    val size: Long
)

/** Откуда предложено восстановление на чистой установке. */
enum class RestoreOfferKind {
    /** Из выбранной папки автобэкапа: URI и дата известны. */
    FOLDER,

    /** Из «Загрузок»: MediaStore нашёл файл (работает не на всех версиях Android). */
    DOWNLOADS,

    /** Файл не виден системе: предлагаем выбрать его вручную. */
    PICK_FILE
}

/**
 * Что предложить на чистой установке. После удаления приложения настройки
 * стираются, а scoped storage не даёт перечислить «Загрузки» — поэтому
 * остаётся честное предложение выбрать файл самому.
 */
data class RestoreOffer(
    val kind: RestoreOfferKind,
    val name: String,
    val savedAt: Long,
    val knownUri: Uri?
)

/**
 * Автобэкап в папку, выбранную человеком через SAF. Файл переживает удаление
 * приложения, поэтому именно он — страховка при смене подписи/канала.
 *
 * Запись идёт при старте и после изменений, но не чаще раза в час; раз в сутки
 * её дополнительно будит WorkManager. Хранятся 7 последних файлов.
 */
class AutoBackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val backup: BackupManager,
    private val db: OporaDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun isConfigured(): Boolean {
        val s = settings.current()
        return s.autoBackupFolderUri.isNotBlank() &&
            s.autoBackupPasswordSet &&
            !s.autoBackupPasswordLost
    }

    /**
     * @param force игнорировать часовой throttle (кнопка «Сохранить сейчас»).
     */
    suspend fun run(force: Boolean = false, now: Long = clock()): AutoBackupOutcome =
        withContext(Dispatchers.IO) {
            val s = settings.current()
            val treeUri = s.treeUri() ?: return@withContext AutoBackupOutcome.NotConfigured
            if (!force && !AutoBackupPolicy.isDue(s.lastAutoBackupAttemptAt, now)) {
                return@withContext AutoBackupOutcome.Skipped
            }
            val password = settings.autoBackupPassword()
            if (password == null) {
                val reason = if (s.autoBackupPasswordSet) {
                    AutoBackupFailure.PASSWORD_LOST
                } else {
                    AutoBackupFailure.PASSWORD_MISSING
                }
                return@withContext failure(reason, now)
            }
            try {
                if (!hasPersistedWrite(treeUri)) {
                    return@withContext failure(AutoBackupFailure.FOLDER_UNAVAILABLE, now)
                }
                val dir = DocumentFile.fromTreeUri(context, treeUri)
                    ?.takeIf { it.isDirectory && it.canWrite() }
                    ?: return@withContext failure(AutoBackupFailure.FOLDER_UNAVAILABLE, now)

                val name = AutoBackupPolicy.fileName(
                    Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
                )
                // Пустая база не нужна в папке: после «Удалить все данные»
                // такая копия только вытеснила бы полезные файлы.
                val payload = backup.createPayload()
                if (payload.totalRecords == 0) return@withContext AutoBackupOutcome.Skipped
                // В одну минуту — один файл. Повторную запись не делаем,
                // чтобы не затирать уже сохранённую копию.
                if (dir.findFile(name) != null) {
                    return@withContext AutoBackupOutcome.Skipped
                }
                val bytes = backup.encryptedBytes(payload, password)
                val file = dir.createFile(MIME_TYPE, name)
                    ?: return@withContext failure(AutoBackupFailure.WRITE_FAILED, now)
                val written = runCatching {
                    val stream = context.contentResolver.openOutputStream(file.uri, "wt")
                        ?: return@runCatching false
                    stream.use { it.write(bytes) }
                    true
                }.getOrDefault(false)
                if (written == false) {
                    runCatching { file.delete() }
                    return@withContext failure(AutoBackupFailure.WRITE_FAILED, now)
                }
                prune(dir)
                settings.recordAutoBackupSuccess(now)
                AutoBackupOutcome.Saved(name)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                failure(AutoBackupFailure.WRITE_FAILED, now)
            } finally {
                password.fill('\u0000')
            }
        }

    /** Копии в выбранной папке, самые новые первыми. */
    suspend fun listBackups(): List<AutoBackupEntry> = withContext(Dispatchers.IO) {
        val s = settings.current()
        val treeUri = s.treeUri() ?: return@withContext emptyList()
        if (!hasPersistedWrite(treeUri)) return@withContext emptyList()
        val dir = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return@withContext emptyList()
        dir.listFiles()
            .asSequence()
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                if (!AutoBackupPolicy.isBackupName(name)) return@mapNotNull null
                AutoBackupEntry(
                    uri = file.uri,
                    name = name,
                    savedAt = AutoBackupPolicy.savedAt(name) ?: file.lastModified(),
                    size = file.length()
                )
            }
            .sortedByDescending { it.name }
            .toList()
    }

    /**
     * Копии `*.opora` из «Загрузок». После переустановки настройки приложения
     * стёрты, поэтому это единственный способ самому предложить восстановление.
     * Доступ ограничен scoped storage: если записей не видно, список пуст —
     * ручной импорт из файла всё равно доступен.
     */
    suspend fun downloadsBackups(): List<AutoBackupEntry> = withContext(Dispatchers.IO) {
        runCatching { queryDownloads() }.getOrDefault(emptyList())
            .sortedByDescending { it.savedAt }
    }

    /** Пустая база: ни лекарств, ни отметок, ни записей дневника, ни доз. */
    suspend fun isDatabaseEmpty(): Boolean = withContext(Dispatchers.IO) {
        db.medicationDao().getAll().isEmpty() &&
            db.moodDao().getAll().isEmpty() &&
            db.journalDao().getAll().isEmpty() &&
            db.doseDao().getAll().isEmpty()
    }

    /**
     * Что показать при первом запуске. Папка важнее «Загрузок»: в ней лежат
     * копии с известным URI, их можно открыть сразу после ввода пароля.
     * Если ничего не видно (обычный случай после переустановки), предлагаем
     * выбрать файл вручную — молчать нельзя, иначе данные легко потерять.
     */
    suspend fun restoreOffer(): RestoreOffer? = withContext(Dispatchers.IO) {
        if (!isDatabaseEmpty()) return@withContext null
        listBackups().firstOrNull()?.let {
            return@withContext RestoreOffer(RestoreOfferKind.FOLDER, it.name, it.savedAt, it.uri)
        }
        downloadsBackups().firstOrNull()?.let {
            return@withContext RestoreOffer(
                RestoreOfferKind.DOWNLOADS,
                it.name,
                it.savedAt,
                it.uri
            )
        }
        RestoreOffer(RestoreOfferKind.PICK_FILE, "", 0L, null)
    }

    /** Человекочитаемый путь папки: «Download/Opora» вместо content://… */
    fun folderLabel(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val path = id?.substringAfter(':', "")?.takeIf { it.isNotBlank() }
        if (path != null) return path.trim('/')
        val name = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        return name ?: uri.lastPathSegment.orEmpty()
    }

    fun hasPersistedWrite(uri: Uri): Boolean = context.contentResolver.persistedUriPermissions
        .any { it.uri == uri && it.isWritePermission }

    fun persistPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private suspend fun failure(reason: AutoBackupFailure, now: Long): AutoBackupOutcome {
        settings.recordAutoBackupError(reason.code, now)
        return AutoBackupOutcome.Failed(reason)
    }

    private fun prune(dir: DocumentFile) {
        val files = dir.listFiles().filter { it.isFile }
        val names = files.mapNotNull { it.name }
        val stale = AutoBackupPolicy.staleNames(names).toSet()
        if (stale.isEmpty()) return
        files.forEach { file ->
            if (file.name in stale) runCatching { file.delete() }
        }
    }

    private fun queryDownloads(): List<AutoBackupEntry> {
        val collection: Uri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            collection = MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$SUFFIX")
        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    if (!name.endsWith(SUFFIX, ignoreCase = true)) continue
                    val id = it.getLong(0)
                    add(
                        AutoBackupEntry(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = name,
                            savedAt = AutoBackupPolicy.savedAt(name)
                                ?: it.getLong(2) * 1000L,
                            size = -1L
                        )
                    )
                }
            }
        }
    }

    private fun AppSettings.treeUri(): Uri? =
        autoBackupFolderUri.takeIf { it.isNotBlank() }?.let(Uri::parse)

    private companion object {
        /** Неизвестное расширение не даёт DocumentsProvider дописать «.bin». */
        const val MIME_TYPE = "application/octet-stream"
        const val SUFFIX = AutoBackupPolicy.SUFFIX
    }
}
