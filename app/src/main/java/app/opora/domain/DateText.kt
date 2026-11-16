package app.opora.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Date and number text lives here, never in a hardcoded month table.
 * Russian uses the genitive month form ("5 ноября") because java.time asks
 * CLDR for the format-wide name; English uses "Nov 5".
 */

private fun Locale.isRussian(): Boolean = language == "ru"

private fun patternFor(locale: Locale, russian: String, english: String): String =
    if (locale.isRussian()) russian else english

/** «5 ноября» / «Nov 5». */
fun LocalDate.dayMonth(locale: Locale): String =
    format(DateTimeFormatter.ofPattern(patternFor(locale, "d MMMM", "MMM d"), locale))

/** «5 ноя» / «Nov 5». CLDR adds a dot to the Russian abbreviation; drop it. */
fun LocalDate.dayMonthShort(locale: Locale): String =
    format(DateTimeFormatter.ofPattern(patternFor(locale, "d MMM", "MMM d"), locale))
        .replace(".", "")

/** «Ноябрь 2026» / «November 2026». */
fun LocalDate.monthYear(locale: Locale): String =
    format(DateTimeFormatter.ofPattern("LLLL y", locale))
        .replaceFirstChar { it.titlecase(locale) }

/** «5 ноября 2026» / «Nov 5, 2026». */
fun LocalDate.dateWithYear(locale: Locale): String =
    format(DateTimeFormatter.ofPattern(patternFor(locale, "d MMMM y", "MMM d, y"), locale))

/** «среда» / «Wednesday». */
fun LocalDate.weekday(locale: Locale): String =
    dayOfWeek.getDisplayName(TextStyle.FULL, locale)

/** «ср» / «Wed». */
fun LocalDate.weekdayShort(locale: Locale): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

/** «Среда, 5 ноября» / «Wednesday, Nov 5». */
fun LocalDate.weekdayDate(locale: Locale): String =
    weekday(locale).replaceFirstChar { it.titlecase(locale) } + ", " + dayMonth(locale)

/** «ср, 5 ноя» / «Wed, Nov 5». */
fun LocalDate.weekdayDateShort(locale: Locale): String =
    weekdayShort(locale) + ", " + dayMonthShort(locale)

/**
 * Journal date: the year is omitted only for the current year, otherwise
 * entries from different years read as the same day.
 */
fun LocalDate.journalDateLabel(today: LocalDate, locale: Locale): String =
    if (year == today.year) dayMonth(locale) else dateWithYear(locale)

/** «5 ноября 2026, 22:30» / «Nov 5, 2026, 22:30». */
fun LocalDateTime.dateTime(locale: Locale): String =
    toLocalDate().dateWithYear(locale) + ", " + hhmm()

fun Long.dateTime(locale: Locale): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime().dateTime(locale)

fun Long.millisToLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Decimal comma in Russian, decimal point in English; one separator everywhere. */
fun formatDecimal(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)

/** Amount of medication: no grouping, at most two fraction digits. */
fun formatAmount(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 4
    }.format(value)
