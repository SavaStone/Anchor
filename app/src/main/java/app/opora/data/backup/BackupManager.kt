package app.opora.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.room.withTransaction
import app.opora.R
import app.opora.data.UserMessageException
import app.opora.data.db.OporaDatabase
import app.opora.data.db.SafetyPlanEntity
import app.opora.data.linkJournalMoods
import app.opora.data.prefs.SettingsRepository
import app.opora.security.BackupCrypto
import app.opora.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom

class BackupManager(
    private val context: Context,
    private val db: OporaDatabase,
    private val settings: SettingsRepository,
    private val autoPrefsName: String = "opora_auto"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun createPayload(): BackupPayload = withContext(Dispatchers.IO) {
        BackupPayload(
            exportedAt = System.currentTimeMillis(),
            moods = db.moodDao().getAll(),
            medications = db.medicationDao().getAll(),
            doses = db.doseDao().getAll(),
            journal = db.journalDao().getAll(),
            customTags = db.customTagDao().getAll(),
            positives = db.positiveDao().getAll(),
            appointments = db.appointmentDao().getAll(),
            safetyPlan = db.safetyPlanDao().get(),
            forgivenDoseDays = db.forgivenDoseDayDao().getAll(),
            scheduleSnapshots = db.scheduleSnapshotDao().getAll(),
            medicationPauses = db.medicationPauseDao().getAll()
        )
    }

    suspend fun exportEncrypted(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val payload = createPayload()
        val bytes = BackupCrypto.encrypt(json.encodeToString(payload), passphrase)
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw UserMessageException(R.string.errors_open_file_write)
        settings.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val payload = createPayload()
        val bytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw UserMessageException(R.string.errors_open_file_write)
        settings.setLastBackupAt(System.currentTimeMillis())
    }

    /** Зашифрованные байты готовой копии — для записи в SAF-папку. */
    suspend fun encryptedBytes(payload: BackupPayload, passphrase: CharArray): ByteArray =
        withContext(Dispatchers.IO) {
            BackupCrypto.encrypt(json.encodeToString(payload), passphrase)
        }

    /** Reads and decrypts a backup. Passphrase is required only for encrypted files. */
    suspend fun readBackup(uri: Uri, passphrase: CharArray?): BackupPayload =
        withContext(Dispatchers.IO) {
            val bytes = readLimited(uri)
            val text = if (BackupCrypto.isEncrypted(bytes)) {
                if (passphrase == null) throw UserMessageException(R.string.errors_passphrase_required)
                runCatching { BackupCrypto.decrypt(bytes, passphrase) }
                    .getOrElse { throw UserMessageException(R.string.errors_wrong_passphrase) }
            } else {
                String(bytes, Charsets.UTF_8)
            }
            parseBackupPayload(json, text)
        }

    suspend fun restore(payload: BackupPayload) = withContext(Dispatchers.IO) {
        if (payload.formatVersion > 3) throw UserMessageException(R.string.errors_newer_version)
        db.withTransaction {
            db.doseDao().clear()
            db.medicationDao().clear()
            db.moodDao().clear()
            db.journalDao().clear()
            db.customTagDao().clear()
            db.positiveDao().clear()
            db.appointmentDao().clear()
            db.safetyPlanDao().clear()
            db.forgivenDoseDayDao().clear()
            db.scheduleSnapshotDao().clear()
            db.medicationPauseDao().clear()

            if (payload.moods.isNotEmpty()) db.moodDao().insertAll(payload.moods)
            if (payload.medications.isNotEmpty()) db.medicationDao().insertAll(payload.medications)
            if (payload.doses.isNotEmpty()) db.doseDao().insertAll(payload.doses)
            if (payload.journal.isNotEmpty()) db.journalDao().insertAll(payload.journal)
            // Копии до версии со связью не содержат moodEntryId — создаём отметки.
            linkJournalMoods(db.journalDao(), db.moodDao())
            if (payload.customTags.isNotEmpty()) db.customTagDao().insertAll(payload.customTags)
            if (payload.positives.isNotEmpty()) db.positiveDao().insertAll(payload.positives)
            if (payload.appointments.isNotEmpty()) db.appointmentDao().insertAll(payload.appointments)
            payload.safetyPlan?.let { db.safetyPlanDao().upsert(it) }
            if (payload.forgivenDoseDays.isNotEmpty()) {
                payload.forgivenDoseDays.forEach { db.forgivenDoseDayDao().upsert(it) }
            }
            if (payload.scheduleSnapshots.isNotEmpty()) {
                db.scheduleSnapshotDao().upsertAll(payload.scheduleSnapshots)
            }
            if (payload.medicationPauses.isNotEmpty()) {
                db.medicationPauseDao().insertAll(payload.medicationPauses)
            }
        }
    }

    suspend fun readFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) { readLimited(uri) }

    suspend fun writeFile(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw UserMessageException(R.string.errors_write_file)
    }

    /**
     * Читает файл порциями и не даёт загрузить в память больше [MAX_IMPORT_BYTES].
     * Проверка размера выполняется до чтения, а также во время чтения для потоков,
     * у которых размер заранее неизвестен.
     */
    private fun readLimited(uri: Uri, max: Long = MAX_IMPORT_BYTES): ByteArray {
        val declared = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (declared > max) throw UserMessageException(R.string.errors_file_too_large, MAX_IMPORT_BYTES / (1024 * 1024))

        val input = context.contentResolver.openInputStream(uri) ?: throw UserMessageException(R.string.errors_read_file)
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > max) throw UserMessageException(R.string.errors_file_too_large, MAX_IMPORT_BYTES / (1024 * 1024))
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    /**
     * Удаляет автокопии прежних версий из приватного хранилища: они всё равно
     * не переживают переустановку. Отсутствие папки не ошибка.
     */
    suspend fun deleteAllLocalBackups(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups")
        if (!dir.isDirectory) return@withContext 0
        var deleted = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("auto-") &&
                file.extension.equals("opora", ignoreCase = true) &&
                runCatching { file.delete() }.getOrDefault(false)
            ) deleted++
        }
        deleted
    }


    // ---------- Локальная точка восстановления после «Удалить все данные» ----------

    private fun restorePointFile(): File = File(context.filesDir, "restore/restore.opora")

    /** Относительный путь для UI: абсолютный приватный путь пользователю ничего не даёт. */
    fun restorePointDisplayPath(): String = "files/restore/restore.opora"

    /** Снимок перед удалением. Хранится в приватном каталоге и шифруется тем же ключом, что автокопии. */
    suspend fun createRestorePoint(userName: String): RestorePointInfo = withContext(Dispatchers.IO) {
        val file = restorePointFile()
        val existing = if (file.isFile) {
            runCatching { readRestorePoint(file) }.getOrNull()
        } else null
        // Повторное «удалить всё» на пустых данных не должно затирать
        // предыдущий снимок: иначе восстановить уже будет нечего.
        val currentPayload = createPayload()
        if (currentPayload.totalRecords == 0) {
            existing?.let { return@withContext RestorePointInfo(it.savedAt, it.payload.totalRecords, it.userName) }
        }
        // Если между удалениями человек успел что-то добавить, второй снимок
        // не должен потерять первый: храним объединение удалённых поколений.
        val payload = existing?.let { mergePayloads(it.payload, currentPayload) } ?: currentPayload
        val point = LocalRestorePoint(
            savedAt = System.currentTimeMillis(),
            userName = userName.ifBlank { existing?.userName.orEmpty() },
            payload = payload
        )
        file.parentFile?.mkdirs()
        file.writeBytes(BackupCrypto.encrypt(json.encodeToString(point), autoPassphrase()))
        RestorePointInfo(point.savedAt, payload.totalRecords, point.userName)
    }

    suspend fun restorePointInfo(): RestorePointInfo? = withContext(Dispatchers.IO) {
        val file = restorePointFile()
        if (!file.isFile) return@withContext null
        runCatching { readRestorePoint(file) }
            .map { RestorePointInfo(it.savedAt, it.payload.totalRecords, it.userName) }
            .getOrNull()
    }

    /**
     * Возвращает удалённые записи, не стирая то, что появилось после удаления:
     * снимок добавляется к текущим данным, существующие строки не перезаписываются.
     */
    suspend fun restorePoint(): RestoreReport = withContext(Dispatchers.IO) {
        val file = restorePointFile()
        if (!file.isFile) throw UserMessageException(R.string.errors_restore_point_missing)
        val point = runCatching { readRestorePoint(file) }
            .getOrElse { throw UserMessageException(R.string.errors_restore_point_read) }
        if (point.payload.formatVersion > 3) {
            throw UserMessageException(R.string.errors_restore_point_newer)
        }
        db.withTransaction {
            // Отметки вставляются с новыми id, поэтому ссылки из дневника
            // переводим на них; иначе moodEntryId указывал бы в пустоту.
            val moodIds = HashMap<Long, Long>()
            point.payload.moods.forEach { mood ->
                moodIds[mood.id] = db.moodDao().insert(mood.copy(id = 0))
            }
            if (point.payload.medications.isNotEmpty()) {
                db.medicationDao().insertIgnoreAll(point.payload.medications)
            }
            if (point.payload.doses.isNotEmpty()) {
                db.doseDao().insertIgnoreAll(point.payload.doses)
            }
            if (point.payload.journal.isNotEmpty()) {
                val journal = point.payload.journal.map { entry ->
                    entry.copy(moodEntryId = entry.moodEntryId?.let { moodIds[it] })
                }
                db.journalDao().insertIgnoreAll(journal)
            }
            linkJournalMoods(db.journalDao(), db.moodDao())
            if (point.payload.customTags.isNotEmpty()) {
                db.customTagDao().insertIgnoreAll(point.payload.customTags)
            }
            if (point.payload.positives.isNotEmpty()) {
                db.positiveDao().insertIgnoreAll(point.payload.positives)
            }
            if (point.payload.appointments.isNotEmpty()) {
                db.appointmentDao().insertIgnoreAll(point.payload.appointments)
            }
            point.payload.forgivenDoseDays.forEach { db.forgivenDoseDayDao().upsert(it) }
            if (db.safetyPlanDao().get()?.isBlank() != false) {
                point.payload.safetyPlan?.let { db.safetyPlanDao().upsert(it) }
            }
        }
        // Имя профиля восстанавливаем, только если после удаления его ещё не задали.
        if (settings.current().userName.isBlank() && point.userName.isNotBlank()) {
            settings.setUserName(point.userName)
        }
        runCatching { file.delete() }
        RestoreReport(point.payload)
    }

    suspend fun discardRestorePoint() = withContext(Dispatchers.IO) {
        runCatching { restorePointFile().delete() }
        Unit
    }

    /**
     * Объединяет снимки по устойчивым идентификаторам. Текущие (вторые)
     * записи важнее старых, поэтому ставятся первыми.
     */
    private fun mergePayloads(old: BackupPayload, current: BackupPayload): BackupPayload =
        BackupPayload(
            formatVersion = maxOf(old.formatVersion, current.formatVersion),
            appVersion = current.appVersion,
            exportedAt = System.currentTimeMillis(),
            moods = (current.moods + old.moods).distinctBy { it.id },
            medications = (current.medications + old.medications).distinctBy { it.id },
            doses = (current.doses + old.doses).distinctBy { it.id },
            journal = (current.journal + old.journal).distinctBy { it.id },
            customTags = (current.customTags + old.customTags).distinctBy { it.key },
            positives = (current.positives + old.positives).distinctBy { it.id },
            appointments = (current.appointments + old.appointments).distinctBy { it.id },
            safetyPlan = current.safetyPlan?.takeUnless { it.isBlank() } ?: old.safetyPlan,
            forgivenDoseDays = (current.forgivenDoseDays + old.forgivenDoseDays).distinctBy { it.date },
            scheduleSnapshots = (current.scheduleSnapshots + old.scheduleSnapshots)
                .distinctBy { it.date to it.medicationId },
            medicationPauses = (current.medicationPauses + old.medicationPauses)
                .distinctBy { it.id }
        )

    private fun readRestorePoint(file: File): LocalRestorePoint {
        val text = BackupCrypto.decrypt(file.readBytes(), autoPassphrase())
        return json.decodeFromString<LocalRestorePoint>(text)
    }

    private companion object {
        /** Верхний предел для импортируемого файла: 50 МБ. */
        const val MAX_IMPORT_BYTES = 50L * 1024 * 1024
        const val KEY_CHANGED = "key_changed"
        /** Один замок на процесс: иначе два параллельных авто-бэкапа
         *  сгенерируют два ключа и перетрут SharedPreferences. */
        val AUTO_KEY_LOCK = Any()
    }

    /** Флаг ставится, когда сохранённый ключ больше не расшифровывается. */
    fun keyWarning(): String? {
        val prefs = context.getSharedPreferences(autoPrefsName, Context.MODE_PRIVATE)
        val stored = prefs.getString("key", null)
        val invalid = !stored.isNullOrBlank() && SecretStore.decrypt(stored) == null
        if (invalid) prefs.edit { putBoolean(KEY_CHANGED, true) }
        return if (invalid || prefs.getBoolean(KEY_CHANGED, false)) {
            context.getString(R.string.errors_key_changed)
        } else {
            null
        }
    }

    fun clearKeyWarning() {
        context.getSharedPreferences(autoPrefsName, Context.MODE_PRIVATE)
            .edit { remove(KEY_CHANGED) }
    }

    private fun markKeyChanged(prefs: android.content.SharedPreferences) {
        prefs.edit { putBoolean(KEY_CHANGED, true) }
    }

    private fun autoPassphrase(): CharArray = synchronized(AUTO_KEY_LOCK) {
        val prefs = context.getSharedPreferences(autoPrefsName, Context.MODE_PRIVATE)
        val stored = prefs.getString("key", null)
        if (!stored.isNullOrBlank()) {
            val decrypted = SecretStore.decrypt(stored)
            if (decrypted != null && decrypted.isNotBlank()) return decrypted.toCharArray()
            // Ключ keystore инвалидирован: молча генерировать новый нельзя —
            // старые файлы уже не откроются, пользователю нужен явный сигнал.
            markKeyChanged(prefs)
        }
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val pass = Base64.encodeToString(random, Base64.NO_WRAP)
        val encrypted = SecretStore.encrypt(pass)
        if (encrypted == null) markKeyChanged(prefs)
        prefs.edit { putString("key", encrypted ?: "") }
        pass.toCharArray()
    }
}

@Serializable
private data class LocalRestorePoint(
    val savedAt: Long,
    val userName: String = "",
    val payload: BackupPayload
)

data class RestorePointInfo(
    val savedAt: Long,
    val records: Int,
    val userName: String = ""
)

data class RestoreReport(val payload: BackupPayload)

private fun SafetyPlanEntity.isBlank(): Boolean = copy(updatedAt = 0L) == SafetyPlanEntity()
