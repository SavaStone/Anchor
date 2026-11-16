package app.opora.domain

import app.opora.data.db.MedicationEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Pure medication schedule engine. All times are local. */
object MedSchedule {

    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTimes(med: MedicationEntity): List<LocalTime> =
        med.timesCsv.split(',')
            .mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            .distinct()
            .sorted()

    fun parseWeekdays(med: MedicationEntity): Set<Int> =
        med.weekdaysCsv.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    fun isScheduledOn(med: MedicationEntity, date: LocalDate): Boolean =
        med.active && matchesSchedule(med, date)

    /** Расписание без учёта `active`: история и серия видят прекращённые приёмы. */
    fun occurrencesOnIgnoringActive(
        med: MedicationEntity,
        date: LocalDate
    ): List<LocalDateTime> =
        if (matchesSchedule(med, date)) parseTimes(med).map { date.atTime(it) }
        else emptyList()

    fun occurrencesOn(med: MedicationEntity, date: LocalDate): List<LocalDateTime> {
        if (!isScheduledOn(med, date)) return emptyList()
        return parseTimes(med).map { date.atTime(it) }
    }

    private fun matchesSchedule(med: MedicationEntity, date: LocalDate): Boolean {
        val start = LocalDate.ofEpochDay(med.startDate)
        if (date.isBefore(start)) return false
        med.endDate?.let { if (date.isAfter(LocalDate.ofEpochDay(it))) return false }
        return when (med.scheduleType) {
            "daily" -> true
            "weekly" -> date.dayOfWeek.value in parseWeekdays(med)
            "interval" -> {
                val every = med.intervalDays.coerceAtLeast(1)
                val diff = ChronoUnit.DAYS.between(start, date)
                diff >= 0 && diff % every == 0L
            }
            "as_needed" -> false
            else -> true
        }
    }

    fun nextOccurrence(med: MedicationEntity, after: LocalDateTime): LocalDateTime? {
        val start = after.toLocalDate()
        // Интервал в редакторе — до 90 дней; 60-дневного окна не хватало,
        // и подсказка «далее» пропадала у редких схем.
        for (offset in 0..120L) {
            val day = start.plusDays(offset)
            val candidate = occurrencesOn(med, day).firstOrNull { it.isAfter(after) }
            if (candidate != null) return candidate
        }
        return null
    }

    /** Next occurrence of one specific time slot (e.g. the 21:00 dose). */
    fun nextForTime(med: MedicationEntity, time: LocalTime, after: LocalDateTime): LocalDateTime? {
        val start = after.toLocalDate()
        for (offset in 0..120L) {
            val day = start.plusDays(offset)
            if (!isScheduledOn(med, day)) continue
            val candidate = day.atTime(time)
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }

    fun nextOccurrenceToday(med: MedicationEntity, now: LocalDateTime): LocalDateTime? =
        occurrencesOn(med, now.toLocalDate()).firstOrNull { it.isAfter(now) }

    /** Приёмы по расписанию с [from] по [now] включительно. */
    fun expectedDoses(med: MedicationEntity, from: LocalDate, now: LocalDateTime): List<LocalDateTime> {
        if (med.scheduleType == "as_needed") return emptyList()
        val out = mutableListOf<LocalDateTime>()
        var day = from
        val end = now.toLocalDate()
        while (!day.isAfter(end)) {
            occurrencesOn(med, day).forEach { if (!it.isAfter(now)) out += it }
            day = day.plusDays(1)
        }
        return out
    }

}
