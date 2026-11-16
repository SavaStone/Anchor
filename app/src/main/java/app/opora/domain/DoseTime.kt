package app.opora.domain

import app.opora.data.db.DoseLogEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Локальное время приёма по снимку даты и «HH:mm», а не по сохранённому
 * epoch. После смены часового пояса старый `scheduledAt` показывает другое
 * фактическое время; расписание остаётся местным и не должно «съезжать».
 */
fun DoseLogEntity.scheduledDateTime(): LocalDateTime =
    LocalDate.ofEpochDay(scheduledDate)
        .atTime(runCatching { LocalTime.parse(scheduledTime) }.getOrDefault(LocalTime.MIDNIGHT))

fun DoseLogEntity.scheduledEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    scheduledDateTime().atZone(zone).toInstant().toEpochMilli()

fun DoseLogEntity.isScheduledBefore(
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Boolean = scheduledEpochMillis(zone) < nowMillis

fun DoseLogEntity.isOverdue(
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): Boolean = status == "pending" && isScheduledBefore(nowMillis, zone)

/**
 * Отложить можно только уже наступивший плановый приём. Иначе кнопка
 * создавала бы второй будильник раньше расписания.
 */
fun DoseLogEntity.snoozeAvailable(now: LocalDateTime = LocalDateTime.now()): Boolean =
    status == "pending" && !isPrn && !now.isBefore(scheduledDateTime())
