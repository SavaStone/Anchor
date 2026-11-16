package app.opora.domain

import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MedicationPauseEntity
import app.opora.data.db.ScheduleSnapshotEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Состояние одного дня в серии приёма. */
data class DoseDayState(
    val date: LocalDate,
    val expected: Int = 0,
    val taken: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
    val pending: Int = 0,
    val forgiven: Boolean = false,
    val isToday: Boolean = false
) {
    /** Все назначенные на день приёмы отмечены принятыми. */
    val complete: Boolean get() = expected > 0 && (forgiven || taken >= expected)

    /** День уже сломал серию: был пропуск или приём остался неотмеченным. */
    val burned: Boolean
        get() = !forgiven && expected > 0 && taken < expected &&
            (!isToday || missed + skipped > 0)

    /** Приёмов по расписанию в этот день нет — огонёк не положен, серия не рвётся. */
    val restDay: Boolean get() = expected == 0
}

data class DoseStreak(
    val current: Int = 0,
    val days: List<DoseDayState> = emptyList()
) {
    val hasData: Boolean get() = days.any { it.expected > 0 }
}

/**
 * Кэш состояний дней между минутными тиками. Ключ — хэш входных данных;
 * `now` в него не входит, потому что состояние прошедшего дня от часов не зависит.
 */
class DoseStreakCache {
    private var signature: Int = 0
    private var cached: MutableMap<Long, DoseDayState>? = null

    fun statesFor(
        meds: List<MedicationEntity>,
        logs: List<DoseLogEntity>,
        forgivenDays: Set<Long>,
        snapshots: List<ScheduleSnapshotEntity>,
        pauses: List<MedicationPauseEntity>,
        today: LocalDate
    ): MutableMap<Long, DoseDayState> {
        val next = listOf(
            meds.hashCode(), logs.hashCode(), forgivenDays.hashCode(),
            snapshots.hashCode(), pauses.hashCode(), today.toEpochDay()
        ).hashCode()
        if (next != signature || cached == null) {
            signature = next
            cached = HashMap()
        }
        return cached!!
    }
}

/**
 * Считает серию полных дней приёма. День считается полным, если отмечены все
 * приёмы этого дня по расписанию (включая вечерние); день отдыха без приёмов
 * серию не рвёт. Ошибочно не отмеченный день можно простить — [forgivenDays].
 *
 * Сегодняшний незакрытый день серию не сжигает до тех пор, пока в нём нет
 * пропуска: иначе утренняя серия обнулялась бы каждый день до вечерней таблетки.
 */
fun doseStreak(
    meds: List<MedicationEntity>,
    logs: List<DoseLogEntity>,
    forgivenDays: Set<Long>,
    today: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
    windowDays: Int = 14,
    maxLookbackDays: Int = 400,
    snapshots: List<ScheduleSnapshotEntity> = emptyList(),
    pauses: List<MedicationPauseEntity> = emptyList(),
    dayCache: MutableMap<Long, DoseDayState>? = null
): DoseStreak {
    // Прекращённые лекарства учитываются до момента прекращения, а не по
    // текущему `active`: иначе завершение курса стирало бы набранную серию.
    val scheduled = meds.filter { it.scheduleType != "as_needed" }
    val zone = ZoneId.systemDefault()
    val creationDates = scheduled.associate { med ->
        med.id to Instant.ofEpochMilli(med.createdAt).atZone(zone).toLocalDate()
    }
    val stopMoments = scheduled.associate { med ->
        med.id to med.discontinuedAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime()
        }
    }
    val logIndex = logs.asSequence()
        .filterNot { it.isPrn }
        .associateBy { Triple(it.medicationId, it.scheduledDate, it.scheduledTime) }
    val snapshotIndex = snapshots.associateBy { it.date to it.medicationId }
    val pausesByMed = pauses.groupBy { it.medicationId }
    // Страховка для старых копий и снимков, которых ещё нет: времена
    // берём из самих доз, а не из сегодняшнего расписания.
    val logTimes = logs.asSequence()
        .filterNot { it.isPrn }
        .groupBy { it.medicationId to it.scheduledDate }
        .mapValues { (_, rows) -> rows.map { it.scheduledTime }.distinct() }

    fun isPaused(medicationId: String, at: LocalDateTime): Boolean {
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        return pausesByMed[medicationId].orEmpty().any { pause ->
            pause.startAt <= millis && (pause.endAt == null || millis < pause.endAt)
        }
    }

    fun expectedTimes(med: MedicationEntity, date: LocalDate): List<LocalDateTime> {
        val snapshot = snapshotIndex[date.toEpochDay() to med.id]
        val times: List<LocalDateTime> = when {
            snapshot != null -> snapshot.timesCsv.split(',')
                .mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
                .map { date.atTime(it) }
            date == today && med.active -> MedSchedule.occurrencesOn(med, date)
            snapshots.isEmpty() -> {
                // Совместимость со старыми тестами/вызовами без снимков.
                val stop = stopMoments[med.id]
                if (!med.active && stop == null) {
                    emptyList()
                } else {
                    val createdOn = creationDates[med.id] ?: LocalDate.MIN
                    MedSchedule.occurrencesOnIgnoringActive(med, date)
                        .filter { !it.toLocalDate().isBefore(createdOn) }
                        .filter { stop == null || !it.isAfter(stop) }
                }
            }
            else -> logTimes[med.id to date.toEpochDay()].orEmpty().mapNotNull { time ->
                runCatching { date.atTime(LocalTime.parse(time)) }.getOrNull()
            }
        }
        return times.filterNot { isPaused(med.id, it) }
    }

    fun computeDayState(date: LocalDate): DoseDayState {
        val isToday = date == today
        // День считается полным только когда отмечены ВСЕ приёмы дня, включая
        // вечерние. Иначе утренняя доза зажигала бы огонёк, а к вечеру он гас.
        val all = scheduled.flatMap { med ->
            // Пустое расписание — это не «пропуск», а день отдыха/пауза.
            // Снимок дня уже содержит только те слоты, что были реально
            // запланированы; паузы вычитаются из него.
            expectedTimes(med, date).map { med to it }
        }
        var taken = 0
        var skipped = 0
        var missed = 0
        var pending = 0
        all.forEach { (med, occurrence) ->
            val log = logIndex[Triple(
                med.id,
                date.toEpochDay(),
                occurrence.toLocalTime().hhmm()
            )]
            when (log?.status) {
                "taken" -> taken++
                "skipped" -> skipped++
                "missed" -> missed++
                else -> pending++
            }
        }
        return DoseDayState(
            date = date,
            expected = all.size,
            taken = taken,
            skipped = skipped,
            missed = missed,
            pending = pending,
            forgiven = date.toEpochDay() in forgivenDays,
            isToday = isToday
        )
    }

    fun dayState(date: LocalDate): DoseDayState {
        val key = date.toEpochDay()
        return dayCache?.get(key) ?: computeDayState(date).also { state ->
            dayCache?.put(key, state)
        }
    }

    val days = (windowDays - 1 downTo 0).map { offset -> dayState(today.minusDays(offset.toLong())) }
    val todayState = days.lastOrNull() ?: dayState(today)

    var current = 0
    if (!todayState.burned) {
        if (todayState.complete) current++
        var cursor = today.minusDays(1)
        val oldest = today.minusDays(maxLookbackDays.toLong())
        while (!cursor.isBefore(oldest)) {
            val state = dayState(cursor)
            when {
                state.burned -> break
                state.complete -> current++
                state.restDay -> Unit
            }
            cursor = cursor.minusDays(1)
        }
    }
    return DoseStreak(current = current, days = days)
}
