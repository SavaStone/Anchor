package app.opora.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<MoodEntity>>

    @Query("SELECT * FROM mood_entries WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: Long, to: Long): Flow<List<MoodEntity>>

    @Query("SELECT * FROM mood_entries WHERE date = :date ORDER BY createdAt ASC")
    suspend fun byDate(date: Long): List<MoodEntity>

    @Query("SELECT * FROM mood_entries WHERE date = :date ORDER BY createdAt ASC")
    fun observeByDate(date: Long): Flow<List<MoodEntity>>

    @Query("SELECT * FROM mood_entries WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): MoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntity): Long

    /** Не перезаписывает существующую запись за ту же дату (демо-данные). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: MoodEntity): Long

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM mood_entries")
    suspend fun getAll(): List<MoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MoodEntity>)

    @Query("DELETE FROM mood_entries")
    suspend fun clear()
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<MedicationEntity?>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): MedicationEntity?

    /**
     * Прекращает приём ровно один раз: повторное подтверждение не переписывает
     * дату и причину. Возвращает число изменённых строк.
     */
    @Query(
        "UPDATE medications SET active = 0, discontinuedAt = :at, " +
            "discontinuedReason = :reason, updatedAt = :now " +
            "WHERE id = :id AND discontinuedAt IS NULL"
    )
    suspend fun discontinue(id: String, at: Long, reason: String, now: Long): Int

    /** Возвращает лекарство в приём; пауза без даты прекращения тоже снимается. */
    @Query(
        "UPDATE medications SET active = 1, discontinuedAt = NULL, " +
            "discontinuedReason = '', updatedAt = :now " +
            "WHERE id = :id AND (active = 0 OR discontinuedAt IS NOT NULL)"
    )
    suspend fun resume(id: String, now: Long): Int

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<MedicationEntity>

    @Upsert
    suspend fun upsert(med: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MedicationEntity>)

    /** Мягкое восстановление: уже существующие записи не перезаписываются. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<MedicationEntity>)

    @Query("DELETE FROM medications")
    suspend fun clear()
}

@Dao
interface DoseDao {
    @Query("SELECT * FROM dose_logs WHERE scheduledDate BETWEEN :from AND :to ORDER BY scheduledAt ASC")
    fun observeRange(from: Long, to: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduledDate BETWEEN :from AND :to ORDER BY scheduledAt ASC")
    suspend fun forRange(from: Long, to: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE scheduledDate = :date ORDER BY scheduledAt ASC")
    fun observeDay(date: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduledDate = :date ORDER BY scheduledAt ASC")
    suspend fun forDay(date: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medId ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeForMedication(medId: String, limit: Int = 60): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medId AND scheduledDate = :date AND scheduledTime = :time LIMIT 1")
    suspend fun find(medId: String, date: Long, time: String): DoseLogEntity?

    @Query(
        "UPDATE medications SET inventoryCount = MAX(inventoryCount - :amount, 0), updatedAt = :now " +
            "WHERE id = :id AND inventoryCount IS NOT NULL"
    )
    suspend fun decrementInventory(id: String, amount: Double, now: Long)

    @Query(
        "UPDATE medications SET inventoryCount = inventoryCount + :amount, updatedAt = :now " +
            "WHERE id = :id AND inventoryCount IS NOT NULL"
    )
    suspend fun incrementInventory(id: String, amount: Double, now: Long)

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Upsert
    suspend fun upsert(log: DoseLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(log: DoseLogEntity): Long

    @Query("UPDATE dose_logs SET status = :status, takenAt = :takenAt, snoozeUntil = NULL, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, takenAt: Long?, now: Long)

    @Query("UPDATE dose_logs SET snoozeUntil = :until, updatedAt = :now WHERE id = :id")
    suspend fun snooze(id: String, until: Long, now: Long)

    /**
     * Пропуск по локальному расписанию: сравниваем пару (дата, время) слота
     * с локальным cutoff, а не с epoch `scheduledAt` (он устаревает при смене
     * часового пояса) и не с «сегодня» (иначе полночь сразу сжигала бы
     * вечернюю дозу вне 4-часового окна).
     */
    @Query(
        "UPDATE dose_logs SET status = 'missed', updatedAt = :now " +
            "WHERE status = 'pending' AND createdAt < :before AND isPrn = 0 " +
            "AND (snoozeUntil IS NULL OR snoozeUntil <= :now) " +
            "AND (scheduledDate < :cutoffDay OR " +
            "(scheduledDate = :cutoffDay AND scheduledTime < :cutoffTime))"
    )
    suspend fun markMissedBefore(cutoffDay: Long, cutoffTime: String, before: Long, now: Long): Int

    @Query("SELECT * FROM dose_logs WHERE status = 'pending' AND scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    suspend fun pendingBetween(from: Long, to: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE status = 'pending' AND snoozeUntil IS NOT NULL AND snoozeUntil > :now")
    suspend fun snoozedAfter(now: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs")
    suspend fun getAll(): List<DoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DoseLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<DoseLogEntity>)

    @Query("DELETE FROM dose_logs")
    suspend fun clear()

    @Query("DELETE FROM dose_logs WHERE medicationId = :medId")
    suspend fun deleteForMedication(medId: String)

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medId AND status = 'pending'")
    suspend fun pendingForMedication(medId: String): List<DoseLogEntity>

    /** Ожидающие дозы с [from] (включительно): прошлое при сохранении не трогаем. */
    @Query(
        "SELECT * FROM dose_logs WHERE medicationId = :medId " +
            "AND status = 'pending' AND scheduledDate >= :from"
    )
    suspend fun pendingForMedicationFrom(medId: String, from: Long): List<DoseLogEntity>

    /** Удаляет только неподтверждённую дозу: принятую запись гонка не заденет. */
    @Query("DELETE FROM dose_logs WHERE id = :id AND status = 'pending'")
    suspend fun deletePendingById(id: String): Int

    @Query("DELETE FROM dose_logs WHERE medicationId = :medId AND status = 'pending'")
    suspend fun deletePendingForMedication(medId: String)

    @Query(
        "DELETE FROM dose_logs WHERE medicationId = :medId " +
            "AND status = 'pending' AND scheduledDate >= :from"
    )
    suspend fun deletePendingForMedicationFrom(medId: String, from: Long)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): JournalEntity?

    /** Редактор настроения поменял уровень или дату — обновляем связанную запись. */
    @Query("UPDATE journal_entries SET mood = :mood, entryDate = :date WHERE moodEntryId = :moodEntryId")
    suspend fun syncLinkedMood(moodEntryId: Long, mood: Int, date: Long)

    /** Отметку удалили в «Настроении» — запись дневника не должна оживить её при сохранении. */
    @Query("UPDATE journal_entries SET mood = NULL, moodEntryId = NULL WHERE moodEntryId = :moodEntryId")
    suspend fun clearLinkedMood(moodEntryId: Long)

    @Upsert
    suspend fun upsert(entry: JournalEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: JournalEntity): Long

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM journal_entries")
    suspend fun getAll(): List<JournalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<JournalEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<JournalEntity>)

    @Query("DELETE FROM journal_entries")
    suspend fun clear()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY startsAt ASC")
    fun observeAll(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments")
    suspend fun getAll(): List<AppointmentEntity>

    @Upsert
    suspend fun upsert(entry: AppointmentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: AppointmentEntity): Long

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AppointmentEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<AppointmentEntity>)

    @Query("DELETE FROM appointments")
    suspend fun clear()
}

@Dao
interface SafetyPlanDao {
    @Query("SELECT * FROM safety_plan WHERE id = 1")
    fun observe(): Flow<SafetyPlanEntity?>

    @Query("SELECT * FROM safety_plan WHERE id = 1")
    suspend fun get(): SafetyPlanEntity?

    @Upsert
    suspend fun upsert(plan: SafetyPlanEntity)

    @Query("DELETE FROM safety_plan")
    suspend fun clear()
}

@Dao
interface CustomTagDao {
    @Query("SELECT * FROM custom_tags")
    fun observeAll(): Flow<List<CustomTagEntity>>

    @Query("SELECT * FROM custom_tags")
    suspend fun getAll(): List<CustomTagEntity>

    @Upsert
    suspend fun upsert(tag: CustomTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CustomTagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<CustomTagEntity>)

    @Query("DELETE FROM custom_tags")
    suspend fun clear()
}

@Dao
interface PositiveDao {
    @Query("SELECT * FROM positive_entries WHERE date = :date ORDER BY createdAt ASC")
    fun observeForDate(date: Long): Flow<List<PositiveEntity>>

    @Query("SELECT * FROM positive_entries WHERE date = :date ORDER BY createdAt ASC")
    suspend fun forDate(date: Long): List<PositiveEntity>

    @Query("SELECT * FROM positive_entries WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PositiveEntity?

    @Query("SELECT * FROM positive_entries")
    suspend fun getAll(): List<PositiveEntity>

    @Upsert
    suspend fun upsert(entry: PositiveEntity)

    @Query("DELETE FROM positive_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PositiveEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<PositiveEntity>)

    @Query("DELETE FROM positive_entries")
    suspend fun clear()
}

@Dao
interface ForgivenDoseDayDao {
    @Query("SELECT * FROM forgiven_dose_days ORDER BY date ASC")
    fun observeAll(): Flow<List<ForgivenDoseDayEntity>>

    @Query("SELECT * FROM forgiven_dose_days")
    suspend fun getAll(): List<ForgivenDoseDayEntity>

    @Upsert
    suspend fun upsert(day: ForgivenDoseDayEntity)

    @Query("DELETE FROM forgiven_dose_days WHERE date = :date")
    suspend fun delete(date: Long)

    @Query("DELETE FROM forgiven_dose_days")
    suspend fun clear()
}


@Dao
interface ScheduleSnapshotDao {
    @Query("SELECT * FROM schedule_snapshots")
    fun observeAll(): Flow<List<ScheduleSnapshotEntity>>

    @Query("SELECT * FROM schedule_snapshots")
    suspend fun getAll(): List<ScheduleSnapshotEntity>

    @Query("SELECT * FROM schedule_snapshots WHERE date = :date")
    suspend fun forDay(date: Long): List<ScheduleSnapshotEntity>

    @Upsert
    suspend fun upsertAll(items: List<ScheduleSnapshotEntity>)

    @Query("DELETE FROM schedule_snapshots")
    suspend fun clear()
}

@Dao
interface MedicationPauseDao {
    @Query("SELECT * FROM medication_pauses")
    fun observeAll(): Flow<List<MedicationPauseEntity>>

    @Query("SELECT * FROM medication_pauses")
    suspend fun getAll(): List<MedicationPauseEntity>

    @Query(
        "SELECT * FROM medication_pauses WHERE medicationId = :medicationId " +
            "AND endAt IS NULL ORDER BY startAt DESC LIMIT 1"
    )
    suspend fun openFor(medicationId: String): MedicationPauseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pause: MedicationPauseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MedicationPauseEntity>)

    @Query(
        "UPDATE medication_pauses SET endAt = :endAt, updatedAt = :now " +
            "WHERE medicationId = :medicationId AND endAt IS NULL"
    )
    suspend fun closeOpen(medicationId: String, endAt: Long, now: Long): Int

    @Query("DELETE FROM medication_pauses")
    suspend fun clear()
}
