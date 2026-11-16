package app.opora.data

import app.opora.R

import android.content.Context
import androidx.room.withTransaction
import app.opora.data.db.AppointmentEntity
import app.opora.data.db.CustomTagEntity
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.ForgivenDoseDayEntity
import app.opora.data.db.JournalEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MedicationPauseEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.ScheduleSnapshotEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.db.PositiveEntity
import app.opora.data.db.SafetyPlanEntity
import app.opora.data.db.journalDate
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.EMOTIONS
import app.opora.domain.MedSchedule
import app.opora.domain.MoodStats
import app.opora.domain.SYMPTOMS
import app.opora.domain.TAG_TYPE_EMOTION
import app.opora.domain.TAG_TYPE_SYMPTOM
import app.opora.domain.DEFAULT_REFILL_THRESHOLD
import app.opora.domain.distinctJournalTags
import app.opora.domain.normalizeForSearch
import app.opora.domain.doseUnitsFor
import app.opora.domain.hhmm
import app.opora.domain.joinKeys
import app.opora.domain.parseKeys
import app.opora.notifications.MedicationAlarms
import app.opora.notifications.Notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class OporaRepository(
    private val context: Context,
    val db: OporaDatabase,
    val settings: SettingsRepository,
    private val workScheduler: app.opora.work.WorkScheduler? = null
) {
    /** Application context for locale-aware labels; never for Activity UI. */
    val appContext: Context get() = context

    private val moodDao = db.moodDao()
    private val snapshotDao = db.scheduleSnapshotDao()
    private val pauseDao = db.medicationPauseDao()
    private val medDao = db.medicationDao()
    private val doseDao = db.doseDao()
    private val journalDao = db.journalDao()
    private val customTagDao = db.customTagDao()
    private val positiveDao = db.positiveDao()
    private val appointmentDao = db.appointmentDao()
    private val safetyDao = db.safetyPlanDao()
    private val forgivenDao = db.forgivenDoseDayDao()
    private val alarms = MedicationAlarms(context, db)
    private val ensureMutex = Mutex()
    /** Смена статуса дозы — read-modify-write: параллельные тапы не должны списать запас дважды. */
    private val doseStatusMutex = Mutex()

    // ---------- Mood ----------

    fun moodEntries(): Flow<List<MoodEntity>> = moodDao.observeAll()

    fun moodRange(from: LocalDate, to: LocalDate): Flow<List<MoodEntity>> =
        moodDao.observeRange(from.toEpochDay(), to.toEpochDay())

    suspend fun moodById(id: Long): MoodEntity? = moodDao.byId(id)

    suspend fun saveMood(
        date: LocalDate,
        mood: Int,
        anxiety: Int?,
        energy: Int?,
        sleepMinutes: Int?,
        emotions: Collection<String>,
        symptoms: Collection<String>,
        note: String,
        entryId: Long? = null,
        occurredAt: Long? = null
    ): Long {
        val existing = entryId?.let { moodDao.byId(it) }
        val now = System.currentTimeMillis()
        val entity = MoodEntity(
            id = existing?.id ?: 0,
            date = date.toEpochDay(),
            mood = mood.coerceIn(1, 5),
            anxiety = anxiety?.coerceIn(0, 10),
            energy = energy?.coerceIn(0, 10),
            sleepMinutes = sleepMinutes?.coerceIn(0, 24 * 60),
            emotions = joinKeys(emotions),
            symptoms = joinKeys(symptoms),
            note = note.trim(),
            // Для новых отметок createdAt — выбранные дата и время, а не
            // момент нажатия «Сохранить». Существующую отметку без явного
            // времени не двигаем.
            createdAt = occurredAt ?: existing?.createdAt ?: now,
            updatedAt = now
        )
        return db.withTransaction {
            val id = moodDao.insert(entity)
            // Если отметка создана из дневника, редактор настроения — тоже
            // источник правды: без этого следующее сохранение записи вернуло
            // бы старый уровень и дату.
            if (existing != null) {
                journalDao.syncLinkedMood(id, entity.mood, entity.date)
            }
            id
        }
    }

    suspend fun deleteMood(id: Long) {
        db.withTransaction {
            // Сначала разрываем связь, иначе запись дневника при следующем
            // автосохранении создаст удалённую отметку заново.
            journalDao.clearLinkedMood(id)
            moodDao.deleteById(id)
        }
    }

    fun moodStats(entries: List<MoodEntity>, today: LocalDate = LocalDate.now()): MoodStats {
        if (entries.isEmpty()) return MoodStats()
        val sorted = entries.sortedBy { it.date }
        val moodValues = sorted.map { it.mood }
        val anxietyValues = sorted.mapNotNull { it.anxiety }
        val energyValues = sorted.mapNotNull { it.energy }
        val sleepValues = sorted.mapNotNull { it.sleepMinutes }
        val distribution = (1..5).associateWith { level -> sorted.count { it.mood == level } }
        val emotions = sorted.flatMap { parseKeys(it.emotions) }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        val symptoms = sorted.flatMap { parseKeys(it.symptoms) }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

        var streak = 0
        var cursor = today
        val dates = sorted.map { it.date }.toSet()
        if (cursor.toEpochDay() !in dates) cursor = cursor.minusDays(1)
        while (cursor.toEpochDay() in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return MoodStats(
            count = sorted.size,
            avgMood = moodValues.average(),
            minMood = moodValues.minOrNull(),
            maxMood = moodValues.maxOrNull(),
            avgAnxiety = anxietyValues.takeIf { it.isNotEmpty() }?.average(),
            avgEnergy = energyValues.takeIf { it.isNotEmpty() }?.average(),
            avgSleepMinutes = sleepValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            streak = streak,
            distribution = distribution,
            topEmotions = emotions.take(8),
            topSymptoms = symptoms.take(8)
        )
    }

    // ---------- Medication ----------

    fun medications(): Flow<List<MedicationEntity>> = medDao.observeAll()

    fun scheduleSnapshots(): Flow<List<ScheduleSnapshotEntity>> = snapshotDao.observeAll()

    fun medicationPauses(): Flow<List<MedicationPauseEntity>> = pauseDao.observeAll()

    fun activeMedications(): Flow<List<MedicationEntity>> = medDao.observeActive()

    fun medication(id: String): Flow<MedicationEntity?> = medDao.observeById(id)

    suspend fun medicationOnce(id: String): MedicationEntity? = medDao.byId(id)

    fun todayDoses(date: LocalDate = LocalDate.now()): Flow<List<DoseLogEntity>> =
        doseDao.observeDay(date.toEpochDay())

    fun doseHistory(days: Int = 30, today: LocalDate = LocalDate.now()): Flow<List<DoseLogEntity>> =
        doseDao.observeRange(
            // Ровно [days] календарных дней включая сегодня.
            today.minusDays((days - 1).coerceAtLeast(0).toLong()).toEpochDay(),
            today.toEpochDay()
        )

    fun dosesForMedication(id: String): Flow<List<DoseLogEntity>> =
        doseDao.observeForMedication(id)

    suspend fun ensureTodayLogs(date: LocalDate = LocalDate.now()) = ensureMutex.withLock {
        val existing = doseDao.forDay(date.toEpochDay())
            .filter { !it.isPrn }
            .associateBy { it.medicationId to it.scheduledTime }
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val snapshots = mutableListOf<ScheduleSnapshotEntity>()
        for (med in medDao.getAll().filter { it.active }) {
            val createdAt = Instant.ofEpochMilli(med.createdAt).atZone(zone).toLocalDateTime()
            val expectedTimes = mutableListOf<String>()
            for (occurrence in MedSchedule.occurrencesOn(med, date)) {
                // Нельзя пропустить приём лекарства, которое ещё не было добавлено —
                // но приём сегодняшнего дня остаётся доступным даже после своего времени:
                // иначе препарат, добавленный днём с расписанием на утро, вообще нельзя
                // отметить принятым до следующего дня.
                if (occurrence.toLocalDate() < createdAt.toLocalDate()) continue
                val time = occurrence.toLocalTime().hhmm()
                expectedTimes += time
                if (existing[med.id to time] == null) {
                    doseDao.insertIgnore(
                        DoseLogEntity(
                            id = DoseLogEntity.scheduledId(med.id, date.toEpochDay(), time),
                            medicationId = med.id,
                            medicationName = med.name,
                            dosage = med.dosage,
                            color = med.color,
                            scheduledDate = date.toEpochDay(),
                            scheduledTime = time,
                            scheduledAt = occurrence.atZone(zone).toInstant().toEpochMilli(),
                            status = "pending",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            // Снимок фиксирует, что именно ожидалось в этот день. Правка
            // расписания завтра не должна переписывать прошлые дни.
            snapshots += ScheduleSnapshotEntity(
                date = date.toEpochDay(),
                medicationId = med.id,
                scheduleType = med.scheduleType,
                timesCsv = expectedTimes.distinct().sorted().joinToString(","),
                weekdaysCsv = med.weekdaysCsv,
                intervalDays = med.intervalDays,
                active = true,
                assumed = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        if (snapshots.isNotEmpty()) snapshotDao.upsertAll(snapshots)
        if (date == LocalDate.now()) markMissedOlderThan(now, 240)
    }

    suspend fun markMissedOlderThan(now: LocalDateTime = LocalDateTime.now(), minutes: Int = 240) {
        val zone = ZoneId.systemDefault()
        val cutoff = now.minusMinutes(minutes.toLong())
        doseDao.markMissedBefore(
            cutoffDay = cutoff.toLocalDate().toEpochDay(),
            cutoffTime = cutoff.toLocalTime().hhmm(),
            before = cutoff.atZone(zone).toInstant().toEpochMilli(),
            now = System.currentTimeMillis()
        )
    }

    /**
     * [expectedInventory] — остаток, который редактор показал при открытии.
     * Если за время редактирования запас изменился (приём из уведомления),
     * а поле не правили, сохраняем актуальное значение вместо устаревшего.
     */
    suspend fun saveMedication(
        med: MedicationEntity,
        expectedInventory: Double? = null,
        inventoryEdited: Boolean = expectedInventory == null || med.inventoryCount != expectedInventory
    ) {
        val times = med.timesCsv.split(',')
            .mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            .distinct().sorted()
        val now = System.currentTimeMillis()
        val current = medDao.byId(med.id)
        val inventory = when {
            current == null -> med.inventoryCount
            med.inventoryCount == null -> null
            // Поле не трогали — сохраняем актуальный запас, даже если он
            // изменился, пока редактор был открыт.
            !inventoryEdited -> current.inventoryCount
            // Пользователь явно ввёл значение, в том числе равное загруженному:
            // его нельзя отбрасывать как «неизменённое».
            else -> med.inventoryCount
        }
        val clean = med.copy(
            timesCsv = times.joinToString(",") { it.hhmm() }.ifBlank { "09:00" },
            startDate = if (med.startDate <= 0) LocalDate.now().toEpochDay() else med.startDate,
            inventoryCount = inventory,
            // Активное лекарство не может оставаться прекращённым: тумблер
            // в редакторе скорее снимает паузу, чем отменяет приём.
            discontinuedAt = if (med.active) null else med.discontinuedAt,
            discontinuedReason = if (med.active) "" else med.discontinuedReason,
            updatedAt = now
        )
        // Пересобираем только сегодняшние ожидающие дозы. Вчерашняя
        // неотмеченная доза — история, её удаление теряло запись.
        val todayEpoch = LocalDate.now().toEpochDay()
        val stale = doseDao.pendingForMedicationFrom(clean.id, todayEpoch)
        db.withTransaction {
            medDao.upsert(clean)
            doseDao.deletePendingForMedicationFrom(clean.id, todayEpoch)
            recordPauseTransition(current, clean, now)
        }
        stale.forEach {
            Notify.cancel(context, it.id)
            alarms.cancelSnooze(it.id)
        }
        ensureTodayLogs()
        alarms.scheduleAll()
        val stock = clean.inventoryCount
        val threshold = clean.refillThreshold ?: DEFAULT_REFILL_THRESHOLD
        if (stock != null && stock > threshold) Notify.cancelRefill(context, clean.id)
    }

    private suspend fun recordPauseTransition(
        before: MedicationEntity?,
        after: MedicationEntity,
        now: Long
    ) {
        if (before == null) return
        val open = pauseDao.openFor(after.id)
        val startedPause = before.active && !after.active && after.discontinuedAt == null
        val endedPause = !before.active && after.active
        val stoppedWhilePaused =
            !before.active && !after.active && after.discontinuedAt != null
        when {
            startedPause && open == null -> pauseDao.insert(
                MedicationPauseEntity(
                    medicationId = after.id,
                    startAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            (endedPause || stoppedWhilePaused) && open != null ->
                pauseDao.closeOpen(after.id, now, now)
        }
    }

    suspend fun deleteMedication(id: String) {
        // Cancel before the row disappears: scheduleAll() can only see existing meds.
        alarms.cancelMedication(id)
        // Показанное напоминание пережило бы удаление лекарства.
        doseDao.pendingForMedication(id).forEach { Notify.cancel(context, it.id) }
        doseDao.deletePendingForMedication(id)
        medDao.delete(id)
        Notify.cancelRefill(context, id)
        alarms.scheduleAll()
    }

    /**
     * Прекращает приём: напоминания и ожидающие дозы убираются, принятая
     * история и запас остаются, лекарство переезжает в «Прекращённые».
     * Повторный вызов ничего не меняет — дата первого прекращения сохраняется.
     *
     * Под [ensureMutex] вместе с [ensureTodayLogs]: иначе фоновая проверка
     * успела прочитать `active = true` и заново создала сегодняшнюю дозу
     * уже после очистки.
     */
    suspend fun discontinueMedication(id: String, reason: String = ""): Boolean =
        ensureMutex.withLock {
            val now = System.currentTimeMillis()
            // Дата прекращения и удаление ожидающих доз коммитятся вместе:
            // снаружи не должно быть состояния «приём прекращён, но доза ждёт».
            val pending = db.withTransaction {
                if (medDao.discontinue(id, at = now, reason = reason, now = now) == 0) {
                    return@withTransaction null
                }
                val rows = doseDao.pendingForMedication(id)
                doseDao.deletePendingForMedication(id)
                pauseDao.closeOpen(id, now, now)
                rows
            } ?: return@withLock false
            alarms.cancelMedication(id)
            pending.forEach {
                Notify.cancel(context, it.id)
                alarms.cancelSnooze(it.id)
            }
            Notify.cancelRefill(context, id)
            alarms.scheduleAll()
            true
        }

    /**
     * Возвращает прекращённое или приостановленное лекарство в приём и снова
     * создаёт сегодняшние дозы. Курс с прошедшей датой окончания не трогаем:
     * дату назначает врач, а не приложение.
     */
    /** Ставит приём на паузу; история паузы открывается в saveMedication. */
    suspend fun pauseMedication(id: String): Boolean {
        val current = medDao.byId(id) ?: return false
        if (!current.active || current.discontinuedAt != null) return false
        saveMedication(
            current.copy(
                active = false,
                discontinuedAt = null,
                discontinuedReason = ""
            )
        )
        return true
    }

    suspend fun resumeMedication(id: String): Boolean {
        // Под тем же мьютексом, что и прекращение: отмена из снекбара не должна
        // обогнать ещё не закоммиченный stop и молча ничего не сделать.
        val changed = ensureMutex.withLock {
            medDao.resume(id, now = System.currentTimeMillis()) > 0
        }
        if (!changed) return false
        pauseDao.closeOpen(id, System.currentTimeMillis(), System.currentTimeMillis())
        ensureTodayLogs()
        alarms.scheduleAll()
        return true
    }

    /** Напоминание о пополнении уважает выключенный тумблер в настройках. */
    private suspend fun notifyRefill(med: MedicationEntity) {
        if (!med.active) return
        if (settings.current().refillReminders) Notify.refill(context, med)
    }

    /** Быстрая правка остатка без открытия редактора: единицы препарата, не упаковки. */
    suspend fun adjustInventory(id: String, delta: Double) {
        if (delta == 0.0) return
        val med = medDao.byId(id) ?: return
        val before = med.inventoryCount ?: return
        val now = System.currentTimeMillis()
        // Атомарный UPDATE, а не read-modify-write: параллельный приём
        // не должен затирать ручную правку запаса.
        if (delta > 0) {
            doseDao.incrementInventory(id, delta, now)
        } else {
            doseDao.decrementInventory(id, -delta, now)
        }
        val updated = medDao.byId(id) ?: return
        val after = updated.inventoryCount ?: return
        val threshold = updated.refillThreshold ?: DEFAULT_REFILL_THRESHOLD
        // Уведомляем только при пересечении порога, а не на каждом списании.
        if (delta < 0 && before > threshold && after <= threshold) {
            notifyRefill(updated)
        } else if (delta > 0 && after > threshold) {
            Notify.cancelRefill(context, updated.id)
        }
    }

    fun forgivenDoseDays(): Flow<Set<Long>> = forgivenDao.observeAll().map { list ->
        list.mapTo(mutableSetOf()) { it.date }
    }

    /** День, ошибочно оставшийся без отметок, снова засчитывается в серию. */
    suspend fun forgiveDoseDay(date: LocalDate) {
        forgivenDao.upsert(ForgivenDoseDayEntity(date.toEpochDay()))
    }

    suspend fun unforgiveDoseDay(date: LocalDate) {
        forgivenDao.delete(date.toEpochDay())
    }

    suspend fun setDoseStatus(logId: String, status: String, note: String = "") =
        doseStatusMutex.withLock {
            val log = doseDao.byId(logId) ?: return@withLock
            val med = medDao.byId(log.medicationId)
            // Прекращённое или удалённое лекарство не должно снова получить
            // ожидающую дозу: «Вернуть в ожидание» иначе воскрешало бы её.
            if (status == "pending" && (med == null || med.discontinuedAt != null)) {
                return@withLock
            }
            val wasTaken = log.status == "taken"
            val nowTaken = status == "taken"
            val now = System.currentTimeMillis()
            var deducted = log.stockDeducted
            if (wasTaken != nowTaken) {
                if (nowTaken) {
                    deducted = if (med?.inventoryCount != null) {
                        // Списываем по снимку дозы в записи приёма: если лекарство
                        // отредактировали позже, старый приём всё равно был на прежнюю дозу.
                        val before = med.inventoryCount
                        val amount = doseUnitsFor(log.dosage.ifBlank { med.dosage })
                        doseDao.decrementInventory(med.id, amount, now)
                        val updated = medDao.byId(med.id) ?: med
                        val after = updated.inventoryCount ?: before
                        val threshold = updated.refillThreshold ?: DEFAULT_REFILL_THRESHOLD
                        // Повторные приёмы при уже низком запасе не должны слать
                        // уведомление каждый раз — только момент пересечения порога.
                        if (before > threshold && after <= threshold) notifyRefill(updated)
                        (before - after).coerceAtLeast(0.0)
                    } else {
                        // Запас не учитывается — при отмене возвращать нечего.
                        0.0
                    }
                } else {
                    if (med?.inventoryCount != null) {
                        // Возвращаем ровно то, что было списано. Если запас был
                        // меньше дозы, списание упёрлось в ноль — полная доза
                        // создала бы запас из воздуха. null — запись старого формата.
                        val restore = log.stockDeducted
                            ?: doseUnitsFor(log.dosage.ifBlank { med.dosage })
                        if (restore > 0.0) doseDao.incrementInventory(med.id, restore, now)
                        val updated = medDao.byId(med.id) ?: med
                        val threshold = updated.refillThreshold ?: DEFAULT_REFILL_THRESHOLD
                        if ((updated.inventoryCount ?: 0.0) > threshold) {
                            Notify.cancelRefill(context, updated.id)
                        }
                    }
                    deducted = 0.0
                }
            }
            doseDao.upsert(
                log.copy(
                    status = status,
                    takenAt = if (nowTaken) now else null,
                    snoozeUntil = null,
                    note = note.ifBlank { log.note },
                    stockDeducted = deducted,
                    updatedAt = now
                )
            )
            if (status != "pending") {
                Notify.cancel(context, log.id)
                alarms.cancelSnooze(log.id)
            }
        }

    suspend fun undoDose(logId: String) = setDoseStatus(logId, "pending")

    /**
     * Выключение напоминаний о приёме: убираем уже показанные уведомления
     * и отложенные будильники, иначе они придут вопреки настройке.
     */
    suspend fun clearMedicationReminders() {
        doseDao.getAll().forEach { log ->
            Notify.cancel(context, log.id)
            alarms.cancelSnooze(log.id)
        }
    }

    suspend fun snoozeDose(logId: String, minutes: Int = 10) {
        val log = doseDao.byId(logId) ?: return
        val until = System.currentTimeMillis() + minutes * 60_000L
        doseDao.snooze(logId, until, System.currentTimeMillis())
        Notify.cancel(context, log.id)
        alarms.scheduleSnooze(log.id, until)
    }

    suspend fun logPrn(medicationId: String, note: String = "", at: LocalDateTime = LocalDateTime.now()) {
        val med = medDao.byId(medicationId) ?: return
        val now = System.currentTimeMillis()
        val before = med.inventoryCount
        var deducted = 0.0
        db.withTransaction {
            if (before != null) {
                val amount = doseUnitsFor(med.dosage)
                doseDao.decrementInventory(med.id, amount, now)
                val after = medDao.byId(med.id)?.inventoryCount ?: before
                deducted = (before - after).coerceAtLeast(0.0)
            }
            // Идемпотентность плановых доз обеспечивает детерминированный id,
            // поэтому несколько PRN-приёмов в одну минуту — это разные записи.
            doseDao.insertIgnore(
                DoseLogEntity(
                    medicationId = med.id,
                    medicationName = med.name,
                    dosage = med.dosage,
                    color = med.color,
                    scheduledDate = at.toLocalDate().toEpochDay(),
                    scheduledTime = at.toLocalTime().hhmm(),
                    scheduledAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    status = "taken",
                    takenAt = now,
                    note = note,
                    isPrn = true,
                    stockDeducted = deducted,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        if (before != null) {
            val updated = medDao.byId(med.id)
            val after = updated?.inventoryCount ?: before
            val threshold = updated?.refillThreshold ?: DEFAULT_REFILL_THRESHOLD
            if (before > threshold && after <= threshold) {
                updated?.let { notifyRefill(it) }
            }
        }
    }

    /** Меняет фактическое время уже записанного приёма. */
    suspend fun updateDoseTime(logId: String, time: LocalTime) {
        val log = doseDao.byId(logId) ?: return
        if (log.status != "taken") return
        val now = System.currentTimeMillis()
        val date = LocalDate.ofEpochDay(log.scheduledDate)
        val at = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        doseDao.upsert(
            log.copy(
                // Для PRN время приёма — это и есть слот в истории.
                scheduledTime = if (log.isPrn) time.hhmm() else log.scheduledTime,
                scheduledAt = if (log.isPrn) at else log.scheduledAt,
                takenAt = at,
                updatedAt = now
            )
        )
    }

    /** Удаляет ошибочно записанный PRN-приём и возвращает единицу запаса. */
    suspend fun deletePrnDose(logId: String) {
        val log = doseDao.byId(logId) ?: return
        if (!log.isPrn) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            // Удаляем первыми: при двойном нажатии второй вызов получит 0
            // и не вернёт запас повторно.
            val deleted = doseDao.deleteById(logId)
            if (deleted > 0 && log.status == "taken") {
                val fallback = medDao.byId(log.medicationId)?.dosage.orEmpty()
                // Ровно списанное, а не полная доза: иначе пустой запас
                // превратился бы в целую таблетку. null — запись старого формата.
                val restore = log.stockDeducted
                    ?: doseUnitsFor(log.dosage.ifBlank { fallback })
                if (restore > 0.0) {
                    doseDao.incrementInventory(log.medicationId, restore, now)
                }
            }
        }
        if (log.status == "taken") {
            val med = medDao.byId(log.medicationId)
            if (med?.inventoryCount != null && med.refillThreshold != null &&
                med.inventoryCount > med.refillThreshold
            ) {
                Notify.cancelRefill(context, med.id)
            }
        }
        Notify.cancel(context, logId)
        alarms.cancelSnooze(logId)
    }

    suspend fun nextDose(): Pair<MedicationEntity, LocalDateTime>? {
        val now = LocalDateTime.now()
        return medDao.getAll().filter { it.active && it.scheduleType != "as_needed" }
            .mapNotNull { med -> MedSchedule.nextOccurrence(med, now)?.let { med to it } }
            .minByOrNull { it.second }
    }

    // ---------- Positive entries ----------

    fun positives(date: LocalDate): Flow<List<PositiveEntity>> =
        positiveDao.observeForDate(date.toEpochDay())

    suspend fun addPositive(date: LocalDate, text: String, kind: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val entry = PositiveEntity(
            date = date.toEpochDay(),
            text = trimmed,
            kind = kind
        )
        positiveDao.upsert(entry)
        return entry.id
    }

    suspend fun setPositiveDone(id: String, done: Boolean) {
        positiveDao.byId(id)?.let { positiveDao.upsert(it.copy(done = done)) }
    }

    suspend fun deletePositive(id: String) {
        positiveDao.delete(id)
    }

    // ---------- Custom tags ----------

    fun customTags(): Flow<List<CustomTagEntity>> = customTagDao.observeAll()

    /**
     * Добавляет свою эмоцию или симптом. Возвращает ключ существующего пункта,
     * если такая подпись уже есть среди встроенных или пользовательских.
     */
    suspend fun addCustomTag(type: String, label: String): String? {
        require(type == TAG_TYPE_EMOTION || type == TAG_TYPE_SYMPTOM)
        val normalized = label.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null
        val builtIn = (if (type == TAG_TYPE_EMOTION) EMOTIONS else SYMPTOMS)
            .firstOrNull {
                it.label.equals(normalized, ignoreCase = true) ||
                    (it.labelRes != 0 &&
                        context.getString(it.labelRes).equals(normalized, ignoreCase = true))
            }
        if (builtIn != null) return builtIn.key
        val existing = customTagDao.getAll()
            .firstOrNull { it.type == type && it.label.equals(normalized, ignoreCase = true) }
        if (existing != null) return existing.key
        val tag = CustomTagEntity(
            key = "custom_${UUID.randomUUID()}",
            label = normalized.lowercase(),
            type = type
        )
        customTagDao.upsert(tag)
        return tag.key
    }

    // ---------- Journal ----------

    fun journalEntries(): Flow<List<JournalEntity>> = journalDao.observeAll()

    suspend fun journalEntry(id: String): JournalEntity? = journalDao.byId(id)

    suspend fun saveJournal(entry: JournalEntity): JournalEntity = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val existing = journalDao.byId(id)
        val entryDate = entry.entryDate ?: entry.journalDate().toEpochDay()
        // Настроение из дневника — та же запись, что и в разделе «Настроение»:
        // создаём её один раз и обновляем при изменении, чтобы не копить дубли.
        val moodEntryId = syncJournalMood(entry.mood, entry.moodEntryId, entryDate, now)
        val savedTitle = entry.title.trim()
        val savedBody = entry.body.trimEnd()
        val saved = entry.copy(
            id = id,
            title = savedTitle,
            body = savedBody,
            searchText = normalizeForSearch(savedTitle + "\n" + savedBody),
            tags = joinKeys(distinctJournalTags(parseKeys(entry.tags))),
            entryDate = entryDate,
            moodEntryId = moodEntryId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        journalDao.upsert(saved)
        journalDao.byId(id) ?: saved
    }

    /**
     * Сохраняет текст записи, не трогая связанную отметку настроения.
     *
     * Редактор дневника больше не редактирует состояние, поэтому связь берётся
     * из базы: пока экран был открыт, отметку могли изменить или удалить в
     * «Настроении». Локальный снимок редактора в этом случае оживил бы
     * удалённую отметку или откатил её дату.
     */
    suspend fun saveJournalText(entry: JournalEntity): JournalEntity = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val existing = journalDao.byId(id)
        val savedTitle = entry.title.trim()
        val savedBody = entry.body.trimEnd()
        val saved = entry.copy(
            id = id,
            title = savedTitle,
            body = savedBody,
            searchText = normalizeForSearch(savedTitle + "\n" + savedBody),
            tags = joinKeys(distinctJournalTags(parseKeys(entry.tags))),
            entryDate = entry.entryDate ?: entry.journalDate().toEpochDay(),
            mood = existing?.mood,
            moodEntryId = existing?.moodEntryId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        journalDao.upsert(saved)
        journalDao.byId(id) ?: saved
    }

    /**
     * Синхронизирует отметку настроения, созданную из записи дневника.
     * Другие поля отметки (тревога, сон, метки, заметка) не трогаем: их могли
     * заполнить в разделе «Настроение» уже после сохранения дневника.
     */
    private suspend fun syncJournalMood(
        mood: Int?,
        linkedId: Long?,
        entryDate: Long,
        now: Long
    ): Long? {
        val linked = linkedId?.let { moodDao.byId(it) }
        if (mood == null) {
            releaseJournalMood(linked)
            return null
        }
        val level = mood.coerceIn(1, 5)
        if (linked == null) {
            return moodDao.insert(
                MoodEntity(date = entryDate, mood = level, createdAt = now, updatedAt = now)
            )
        }
        if (linked.mood != level || linked.date != entryDate) {
            moodDao.insert(linked.copy(mood = level, date = entryDate, updatedAt = now))
        }
        return linked.id
    }

    /**
     * Дневник больше не ссылается на отметку. Удаляем её, только если это
     * пустая проекция; тревогу, сон, метки или заметку могли заполнить
     * в «Настроении» — такие данные переживают удаление записи.
     */
    private suspend fun releaseJournalMood(mood: MoodEntity?) {
        if (mood == null) return
        val bare = mood.anxiety == null && mood.energy == null && mood.sleepMinutes == null &&
            mood.emotions.isBlank() && mood.symptoms.isBlank() && mood.note.isBlank()
        if (bare) moodDao.deleteById(mood.id)
    }

    suspend fun deleteJournal(id: String) {
        db.withTransaction {
            val entry = journalDao.byId(id)
            journalDao.delete(id)
            // Пустая отметка уходит вместе с записью, а заполненная в
            // «Настроении» остаётся — иначе удаление заметки теряло бы данные.
            releaseJournalMood(entry?.moodEntryId?.let { moodDao.byId(it) })
        }
    }

    // ---------- Appointments ----------

    fun appointments(): Flow<List<AppointmentEntity>> = appointmentDao.observeAll()

    suspend fun saveAppointment(entry: AppointmentEntity): String {
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val saved = entry.copy(id = id, updatedAt = now)
        appointmentDao.upsert(saved)
        when {
            saved.done -> workScheduler?.cancelAppointment(id)
            else -> workScheduler?.scheduleAppointment(saved)
        }
        return id
    }

    suspend fun deleteAppointment(id: String) {
        appointmentDao.delete(id)
        workScheduler?.cancelAppointment(id)
    }

    // ---------- Safety plan ----------

    fun safetyPlan(): Flow<SafetyPlanEntity> = safetyDao.observe().map { it ?: SafetyPlanEntity() }

    suspend fun saveSafetyPlan(plan: SafetyPlanEntity) =
        safetyDao.upsert(plan.copy(updatedAt = System.currentTimeMillis()))

    // ---------- Data ----------

    suspend fun clearAllData() {
        // Сначала отменяем всё запланированное, пока данные ещё в БД:
        // alarms.cancelAll() перебирает лекарства, а отмена WorkManager —
        // приёмы, иначе после очистки останутся «фантомные» напоминания.
        workScheduler?.cancelAllScheduled()
        withContext(Dispatchers.IO) {
            runCatching { alarms.cancelAll() }
            doseDao.getAll().forEach { log -> runCatching { alarms.cancelSnooze(log.id) } }
            appointmentDao.getAll().forEach { appt ->
                runCatching { workScheduler?.cancelAppointment(appt.id) }
            }
        }
        Notify.cancelAll(context)

        doseDao.clear()
        medDao.clear()
        moodDao.clear()
        journalDao.clear()
        customTagDao.clear()
        positiveDao.clear()
        appointmentDao.clear()
        safetyDao.clear()
        forgivenDao.clear()
    }

    /** Удаляет локальные PDF-отчёты из приватного хранилища приложения. */
    suspend fun deleteReports(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "reports")
        if (!dir.isDirectory) return@withContext 0
        var deleted = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension.equals("pdf", ignoreCase = true) &&
                runCatching { file.delete() }.getOrDefault(false)
            ) deleted++
        }
        deleted
    }

    suspend fun seedDemoData() {
        fun demo(id: Int): String = context.getString(id)
        val rnd = Random(7)
        val today = LocalDate.now()
        val now = System.currentTimeMillis()

        // Стабильные id: повторный запуск демо обновляет те же строки, а не плодит дубликаты.
        val sertraline = MedicationEntity(
            id = "demo-sertraline",
            name = demo(R.string.demo_med_sertraline), dosage = demo(R.string.demo_dosage_50), form = "tablet", color = 0xFF4F7CD1,
            purpose = demo(R.string.demo_purpose_depression), scheduleType = "daily", timesCsv = "08:30",
            startDate = today.minusDays(74).toEpochDay(), inventoryCount = 42.0, refillThreshold = 14.0,
            createdAt = today.minusDays(74).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            updatedAt = now
        )
        val lamotrigine = MedicationEntity(
            id = "demo-lamotrigine",
            name = demo(R.string.demo_med_lamotrigine), dosage = demo(R.string.demo_dosage_25), form = "tablet", color = 0xFF8E6FC7,
            purpose = demo(R.string.demo_purpose_mood_stabilizer), scheduleType = "daily", timesCsv = "09:00,21:00",
            startDate = today.minusDays(60).toEpochDay(), inventoryCount = 56.0, refillThreshold = 16.0,
            createdAt = today.minusDays(60).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            updatedAt = now
        )
        val quetiapine = MedicationEntity(
            id = "demo-quetiapine",
            name = demo(R.string.demo_med_quetiapine), dosage = demo(R.string.demo_dosage_25), form = "tablet", color = 0xFFC77B4F,
            purpose = demo(R.string.demo_purpose_sleep), scheduleType = "daily", timesCsv = "22:30",
            startDate = today.minusDays(40).toEpochDay(), inventoryCount = 18.0, refillThreshold = 8.0,
            createdAt = today.minusDays(40).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            updatedAt = now
        )
        val atarax = MedicationEntity(
            id = "demo-atarax",
            name = demo(R.string.demo_med_atarax), dosage = demo(R.string.demo_dosage_25), form = "tablet", color = 0xFF4E8C77,
            purpose = demo(R.string.demo_purpose_anxiety_prn), scheduleType = "as_needed", timesCsv = "12:00",
            startDate = today.minusDays(50).toEpochDay(), inventoryCount = 12.0, refillThreshold = 4.0,
            createdAt = today.minusDays(50).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            updatedAt = now
        )
        listOf(sertraline, lamotrigine, quetiapine, atarax).forEach { medDao.upsert(it) }

        for (offset in 74 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val progress = (74 - offset) / 74.0
            if (rnd.nextInt(100) < 9) continue
            val base = 2.35 + progress * 1.15 + rnd.nextDouble(-0.75, 0.75)
            val mood = base.roundToInt().coerceIn(1, 5)
            val anxiety = ((5.8 - progress * 2.4) + rnd.nextDouble(-1.6, 1.6)).roundToInt().coerceIn(0, 10)
            val energy = ((4.2 + progress * 1.8) + rnd.nextDouble(-1.5, 1.5)).roundToInt().coerceIn(0, 10)
            val sleep = (420 + rnd.nextInt(-80, 70)).coerceIn(240, 600)
            val emotions = buildList {
                when {
                    mood >= 4 -> { add("calm"); if (rnd.nextBoolean()) add("joy"); if (rnd.nextBoolean()) add("hope") }
                    mood == 3 -> { add("calm"); if (rnd.nextBoolean()) add("fatigue") }
                    else -> { add("anxiety"); if (rnd.nextBoolean()) add("sadness"); if (rnd.nextBoolean()) add("apathy") }
                }
                if (rnd.nextInt(100) < 25) add("irritation")
            }
            val symptoms = if (rnd.nextInt(100) < 22) {
                listOf("dry_mouth", "drowsiness", "headache").shuffled(rnd).take(1)
            } else emptyList()
            val note = when {
                offset == 2 -> demo(R.string.demo_note_hard_evening)
                offset == 5 -> demo(R.string.demo_note_first_walk)
                offset == 9 -> demo(R.string.demo_note_before_visit)
                mood >= 4 && rnd.nextInt(100) < 30 -> demo(R.string.demo_note_calm_day)
                mood <= 2 && rnd.nextInt(100) < 40 -> demo(R.string.demo_note_low_energy)
                else -> ""
            }
            // IGNORE: не перезаписываем настоящие записи пользователя за ту же дату.
            moodDao.insertIgnore(
                MoodEntity(
                    date = date.toEpochDay(), mood = mood, anxiety = anxiety, energy = energy,
                    sleepMinutes = sleep, emotions = joinKeys(emotions), symptoms = joinKeys(symptoms),
                    note = note, createdAt = now - offset * 86_400_000L, updatedAt = now - offset * 86_400_000L
                )
            )
        }

        val startDay = today.minusDays(29)
        var day = startDay
        while (!day.isAfter(today)) {
            for (med in listOf(sertraline, lamotrigine, quetiapine)) {
                for (time in MedSchedule.parseTimes(med)) {
                    val dt = day.atTime(time)
                    if (dt.isAfter(LocalDateTime.now())) continue
                    val roll = rnd.nextInt(100)
                    val status = when {
                        roll < 84 -> "taken"
                        roll < 93 -> "skipped"
                        else -> "missed"
                    }
                    doseDao.insertIgnore(
                        DoseLogEntity(
                            id = DoseLogEntity.scheduledId(med.id, day.toEpochDay(), time.hhmm()),
                            medicationId = med.id, medicationName = med.name, dosage = med.dosage,
                            color = med.color, scheduledDate = day.toEpochDay(),
                            scheduledTime = time.hhmm(),
                            scheduledAt = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            status = status,
                            takenAt = if (status == "taken") dt.plusMinutes(rnd.nextLong(0, 40))
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null,
                            createdAt = now, updatedAt = now
                        )
                    )
                }
            }
            day = day.plusDays(1)
        }

        val journalSamples = listOf(
            Triple(demo(R.string.demo_journal_1_title), demo(R.string.demo_journal_1_body), demo(R.string.demo_journal_1_tags)),
            Triple(demo(R.string.demo_journal_2_title), demo(R.string.demo_journal_2_body), demo(R.string.demo_journal_2_tags)),
            Triple(demo(R.string.demo_journal_3_title), demo(R.string.demo_journal_3_body), demo(R.string.demo_journal_3_tags)),
            Triple(demo(R.string.demo_journal_4_title), demo(R.string.demo_journal_4_body), demo(R.string.demo_journal_4_tags)),
            Triple(demo(R.string.demo_journal_5_title), demo(R.string.demo_journal_5_body), demo(R.string.demo_journal_5_tags)),
            Triple(demo(R.string.demo_journal_6_title), demo(R.string.demo_journal_6_body), demo(R.string.demo_journal_6_tags))
        )
        journalSamples.forEachIndexed { index, (title, body, tags) ->
            journalDao.insertIgnore(
                JournalEntity(
                    id = "demo-journal-${index + 1}",
                    title = title, body = body, tags = tags,
                    mood = listOf(3, 3, 2, 4, 3, 3)[index],
                    pinned = index == 0,
                    createdAt = now - (index + 1) * 86_400_000L * 2,
                    updatedAt = now - (index + 1) * 86_400_000L * 2
                )
            )
        }
        // Демо-записи создаются в обход редактора, поэтому связь с «Настроением»
        // проставляем тем же помощником, что и для старых копий.
        linkJournalMoods(journalDao, moodDao)

        appointmentDao.insertIgnore(
            AppointmentEntity(
                id = "demo-appointment-psychiatrist",
                title = demo(R.string.demo_appt_psychiatrist), doctor = demo(R.string.demo_doctor_elena), place = demo(R.string.demo_place_dispensary),
                startsAt = LocalDate.now().plusDays(3).atTime(11, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                durationMinutes = 50, remindMinutesBefore = 120,
                notes = demo(R.string.demo_appt_psychiatrist_notes), createdAt = now, updatedAt = now
            )
        )
        appointmentDao.insertIgnore(
            AppointmentEntity(
                id = "demo-appointment-therapy",
                title = demo(R.string.demo_appt_therapy), doctor = demo(R.string.demo_doctor_anna), place = demo(R.string.demo_place_online),
                startsAt = LocalDate.now().plusDays(6).atTime(18, 30)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                durationMinutes = 50, remindMinutesBefore = 30,
                notes = demo(R.string.demo_appt_therapy_notes), createdAt = now, updatedAt = now
            )
        )

        ensureTodayLogs()
        alarms.scheduleAll()
    }
}
