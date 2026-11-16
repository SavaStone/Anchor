package app.opora.data.backup

import app.opora.R
import app.opora.data.UserMessageException
import app.opora.data.db.AppointmentEntity
import app.opora.data.db.CustomTagEntity
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.ForgivenDoseDayEntity
import app.opora.data.db.JournalEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MedicationPauseEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.ScheduleSnapshotEntity
import app.opora.data.db.PositiveEntity
import app.opora.data.db.SafetyPlanEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class BackupPayload(
    val formatVersion: Int = 3,
    /**
     * Метка формата. Без неё любой JSON декодировался бы в пустую копию,
     * а восстановление очищает базу.
     */
    val app: String = APP_MARKER,
    val appVersion: String = "1.0.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val moods: List<MoodEntity> = emptyList(),
    val medications: List<MedicationEntity> = emptyList(),
    val doses: List<DoseLogEntity> = emptyList(),
    val journal: List<JournalEntity> = emptyList(),
    val customTags: List<CustomTagEntity> = emptyList(),
    val positives: List<PositiveEntity> = emptyList(),
    val appointments: List<AppointmentEntity> = emptyList(),
    val safetyPlan: SafetyPlanEntity? = null,
    val forgivenDoseDays: List<ForgivenDoseDayEntity> = emptyList(),
    val scheduleSnapshots: List<ScheduleSnapshotEntity> = emptyList(),
    val medicationPauses: List<MedicationPauseEntity> = emptyList()
) {
    val totalRecords: Int
        get() = moods.size + medications.size + doses.size + journal.size +
            customTags.size + positives.size + appointments.size +
            forgivenDoseDays.size + scheduleSnapshots.size + medicationPauses.size +
            if (safetyPlan != null) 1 else 0

    companion object {
        const val APP_MARKER = "opora"
    }
}

/**
 * Разбирает копию и проверяет, что это действительно копия «Опоры»:
 * восстановление заменяет все записи, поэтому чужой JSON недопустим.
 * Копии без метки (созданные до её появления) узнаём по обязательным полям.
 */
fun parseBackupPayload(json: Json, text: String): BackupPayload {
    val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw UserMessageException(R.string.errors_not_a_backup)
    val marked = (root["app"] as? JsonPrimitive)?.contentOrNull == BackupPayload.APP_MARKER
    val legacy = root["formatVersion"] != null &&
        root["exportedAt"] != null &&
        root["appVersion"] != null
    if (!marked && !legacy) throw UserMessageException(R.string.errors_not_a_backup)
    return runCatching { json.decodeFromString<BackupPayload>(text) }
        .getOrElse { throw UserMessageException(R.string.errors_not_a_backup) }
}
