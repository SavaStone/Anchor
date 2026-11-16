package app.opora.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "mood_entries", indices = [Index("date")])
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay() */
    val date: Long,
    /** 1..5 */
    val mood: Int,
    /** 0..10, null = not tracked */
    val anxiety: Int? = null,
    /** 0..10 */
    val energy: Int? = null,
    /** minutes of sleep, null = not tracked */
    val sleepMinutes: Int? = null,
    /** comma separated emotion keys */
    val emotions: String = "",
    /** comma separated symptom keys */
    val symptoms: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String = "",
    /** tablet, capsule, drops, liquid, injection, patch, inhaler, other */
    val form: String = "tablet",
    /** ARGB color of the medication */
    val color: Long = 0xFF4E8C77,
    val purpose: String = "",
    val prescriber: String = "",
    val notes: String = "",
    /** daily, weekly, interval, as_needed */
    val scheduleType: String = "daily",
    /** "08:00,20:00" */
    val timesCsv: String = "09:00",
    /** ISO weekdays, Monday = 1 ... Sunday = 7 */
    val weekdaysCsv: String = "1,2,3,4,5,6,7",
    val intervalDays: Int = 1,
    /** LocalDate.toEpochDay() */
    val startDate: Long,
    val endDate: Long? = null,
    /** Остаток в единицах препарата; дробный, потому что доза бывает 0,5 таблетки. */
    val inventoryCount: Double? = null,
    val refillThreshold: Double? = null,
    val active: Boolean = true,
    /** Когда приём прекращён, epoch millis; null — приём не прекращён. */
    val discontinuedAt: Long? = null,
    /** Ключ причины из [app.opora.domain.StopReason]; пусто — причина не указана. */
    val discontinuedReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "dose_logs",
    indices = [
        Index("medicationId"),
        Index("scheduledDate")
    ]
)
data class DoseLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    /** Snapshot so history survives if the medication is removed */
    val medicationName: String = "",
    val dosage: String = "",
    val color: Long = 0xFF4E8C77,
    /** LocalDate.toEpochDay() */
    val scheduledDate: Long,
    /** "HH:mm" */
    val scheduledTime: String = "09:00",
    /** local epoch millis, for ordering only */
    val scheduledAt: Long = 0L,
    /** pending, taken, skipped, missed */
    val status: String = "pending",
    val takenAt: Long? = null,
    val snoozeUntil: Long? = null,
    val note: String = "",
    val isPrn: Boolean = false,
    /**
     * Сколько единиц запаса реально списано при отметке «принято».
     * null — запись создана до появления поля; при отмене используем дозу.
     * 0.0 — запас не учитывался или его не хватило; возвращать нечего.
     */
    val stockDeducted: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Stable id for a scheduled dose; makes log creation idempotent under races. */
        fun scheduledId(medicationId: String, epochDay: Long, time: String): String =
            UUID.nameUUIDFromBytes("dose|$medicationId|$epochDay|$time".toByteArray()).toString()
    }
}

/**
 * Снимок расписания на день: какие слоты ожидались и по каким правилам.
 * Пишется при генерации доз; прошлые дни больше не зависят от текущего
 * расписания. [assumed] — снимок восстановлен миграцией приблизительно.
 */
@Serializable
@Entity(
    tableName = "schedule_snapshots",
    primaryKeys = ["date", "medicationId"],
    indices = [Index("medicationId")]
)
data class ScheduleSnapshotEntity(
    /** LocalDate.toEpochDay() */
    val date: Long,
    val medicationId: String,
    val scheduleType: String = "daily",
    /** Фактические слоты этого дня, "08:00,20:00" */
    val timesCsv: String = "",
    val weekdaysCsv: String = "",
    val intervalDays: Int = 1,
    val active: Boolean = true,
    val assumed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Интервал паузы приёма; endAt == null — пауза ещё идёт. */
@Serializable
@Entity(tableName = "medication_pauses", indices = [Index("medicationId")])
data class MedicationPauseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    /** epoch millis начала паузы */
    val startAt: Long,
    /** epoch millis конца; null — продолжается */
    val endAt: Long? = null,
    /** История восстановлена миграцией, а не записана приложением. */
    val assumed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "journal_entries", indices = [Index("updatedAt"), Index("searchText")])
data class JournalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    /** Нормализованные title+body для поиска; заполняется при записи. */
    val searchText: String = "",
    /** comma separated tags */
    val tags: String = "",
    /** LocalDate.toEpochDay() of the entry date; null for rows created before the field existed */
    val entryDate: Long? = null,
    /** 1..5 mood snapshot, null if not linked */
    val mood: Int? = null,
    /** mood_entries.id, созданная из этой записи; null у старых записей и записей без настроения */
    val moodEntryId: Long? = null,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "custom_tags")
data class CustomTagEntity(
    @PrimaryKey val key: String,
    val label: String,
    /** emotion | symptom */
    val type: String
)

@Serializable
@Entity(tableName = "positive_entries", indices = [Index("date")])
data class PositiveEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** LocalDate.toEpochDay() */
    val date: Long,
    val text: String,
    /** event | deed */
    val kind: String,
    /** used by deeds only */
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val doctor: String = "",
    val place: String = "",
    val startsAt: Long,
    val durationMinutes: Int = 50,
    val notes: String = "",
    val remindMinutesBefore: Int = 60,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "safety_plan")
data class SafetyPlanEntity(
    @PrimaryKey val id: Int = 1,
    val warningSigns: String = "",
    val copingStrategies: String = "",
    val distractions: String = "",
    val reasonsToLive: String = "",
    val supportPeople: String = "",
    val environmentSafety: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val clinic: String = "",
    val hotline: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "forgiven_dose_days")
data class ForgivenDoseDayEntity(
    /** LocalDate.toEpochDay(). День, ошибочно не отмеченный, не рвёт серию. */
    @PrimaryKey val date: Long
)

/** Дата записи: явная entryDate или день создания у старых записей. */
fun JournalEntity.journalDate(): java.time.LocalDate =
    java.time.LocalDate.ofEpochDay(
        entryDate ?: java.time.Instant.ofEpochMilli(createdAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
    )
