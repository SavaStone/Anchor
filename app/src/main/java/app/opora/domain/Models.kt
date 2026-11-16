package app.opora.domain

import androidx.annotation.StringRes
import app.opora.R
import app.opora.data.db.AppointmentEntity
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

data class MoodLevel(val value: Int, @StringRes val labelRes: Int)

val MOOD_LEVELS = listOf(
    MoodLevel(1, R.string.mood_1),
    MoodLevel(2, R.string.mood_2),
    MoodLevel(3, R.string.mood_3),
    MoodLevel(4, R.string.mood_4),
    MoodLevel(5, R.string.mood_5)
)

fun moodLevel(value: Int): MoodLevel =
    MOOD_LEVELS.firstOrNull { it.value == value } ?: MOOD_LEVELS[2]

data class TagDef(val key: String, val label: String, @StringRes val labelRes: Int = 0)

/**
 * Блоки, которые можно показывать на «Сегодня». Дата, помощь и нижняя
 * навигация закреплены: главный экран остаётся коротким, а дополнительные
 * блоки включаются в настройках.
 */
enum class HomeBlock(val key: String, @StringRes val labelRes: Int) {
    MOOD("mood", R.string.home_block_mood),
    MEDS("meds", R.string.home_block_meds),
    JOURNAL("journal", R.string.home_block_journal),
    APPOINTMENT("appointment", R.string.home_block_appointment)
}

val DEFAULT_HOME_ORDER: String = HomeBlock.entries.joinToString(",") { it.key }

/** По умолчанию главная состоит из трёх ежедневных действий. */
val DEFAULT_HIDDEN_HOME_BLOCKS: String = HomeBlock.APPOINTMENT.key

/** Меняет блок местами с соседним; неизвестный ключ и край списка ничего не делают. */
fun moveHomeBlockInOrder(orderCsv: String, key: String, delta: Int): String {
    val blocks = parseHomeBlocks(orderCsv).toMutableList()
    val from = blocks.indexOfFirst { it.key == key }
    if (from < 0) return blocks.joinToString(",") { it.key }
    val to = (from + delta).coerceIn(0, blocks.lastIndex)
    if (to == from) return blocks.joinToString(",") { it.key }
    blocks.add(to, blocks.removeAt(from))
    return blocks.joinToString(",") { it.key }
}

/** Порядок из настроек; новые блоки, появившиеся после сохранения, добавляются в конец. */
fun parseHomeBlocks(orderCsv: String): List<HomeBlock> {
    val known = orderCsv.split(',').map { it.trim() }
        .mapNotNull { key -> HomeBlock.entries.firstOrNull { it.key == key } }
    return (known + HomeBlock.entries).distinct()
}

fun visibleHomeBlocks(orderCsv: String, hiddenCsv: String): List<HomeBlock> {
    val hidden = hiddenCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return parseHomeBlocks(orderCsv).filterNot { it.key in hidden }
}

fun csvWithout(csv: String, key: String): String =
    csv.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != key }.joinToString(",")

fun csvWith(csv: String, key: String): String {
    val parts = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    if (key !in parts) parts += key
    return parts.joinToString(",")
}

const val TAG_TYPE_EMOTION = "emotion"
const val TAG_TYPE_SYMPTOM = "symptom"

const val POSITIVE_EVENT = "event"
const val POSITIVE_DEED = "deed"

private fun tagCollator(): Collator = Collator.getInstance(Locale("ru"))

/** Алфавитный порядок по видимой подписи, а не по техническому ключу. */
fun sortTags(tags: List<TagDef>): List<TagDef> {
    val collator = tagCollator()
    return tags.sortedWith(compareBy(collator) { it.label })
}

/** Встроенные и пользовательские пункты в одном алфавитном списке. */
fun mergeTags(base: List<TagDef>, custom: List<TagDef>): List<TagDef> = sortTags(base + custom)

fun tagLabelMap(vararg groups: List<TagDef>): Map<String, String> = buildMap {
    groups.forEach { group -> group.forEach { put(it.key, it.label) } }
}

fun sortedTagKeys(keys: Collection<String>, labels: Map<String, String>): List<String> {
    val collator = tagCollator()
    // Метки сравниваем без учёта регистра: «Тревога» и «тревога» — одна метка.
    return keys.distinctBy { it.lowercase(Locale.ROOT) }
        .sortedWith(compareBy(collator) { labels[it] ?: it })
}

/** Порог пополнения по умолчанию: неделя ежедневного приёма. */
const val DEFAULT_REFILL_THRESHOLD = 7.0

/** Момент, когда WorkManager должен показать напоминание о визите. */
fun AppointmentEntity.reminderAt(): Long =
    startsAt - remindMinutesBefore * 60_000L

/**
 * Напоминание ещё можно поставить. Если его момент уже прошёл, обещать
 * «Напомним за час» нельзя: задача не будет запланирована.
 */
fun AppointmentEntity.reminderIsScheduled(now: Long = System.currentTimeMillis()): Boolean =
    !done && reminderAt() > now && startsAt > now

val EMOTIONS: List<TagDef> = sortTags(listOf(
    TagDef("joy", "радость", R.string.tag_joy),
    TagDef("calm", "спокойствие", R.string.tag_calm),
    TagDef("interest", "интерес", R.string.tag_interest),
    TagDef("gratitude", "благодарность", R.string.tag_gratitude),
    TagDef("hope", "надежда", R.string.tag_hope),
    TagDef("pride", "гордость", R.string.tag_pride),
    TagDef("love", "любовь", R.string.tag_love),
    TagDef("anxiety", "тревога", R.string.tag_anxiety),
    TagDef("fear", "страх", R.string.tag_fear),
    TagDef("panic", "паника", R.string.tag_panic),
    TagDef("sadness", "грусть", R.string.tag_sadness),
    TagDef("longing", "тоска", R.string.tag_longing),
    TagDef("anger", "злость", R.string.tag_anger),
    TagDef("irritation", "раздражение", R.string.tag_irritation),
    TagDef("shame", "стыд", R.string.tag_shame),
    TagDef("guilt", "вина", R.string.tag_guilt),
    TagDef("loneliness", "одиночество", R.string.tag_loneliness),
    TagDef("apathy", "апатия", R.string.tag_apathy),
    TagDef("fatigue", "усталость", R.string.tag_fatigue),
    TagDef("confusion", "растерянность", R.string.tag_confusion)
))

val SYMPTOMS: List<TagDef> = sortTags(listOf(
    TagDef("headache", "головная боль", R.string.tag_headache),
    TagDef("nausea", "тошнота", R.string.tag_nausea),
    TagDef("dizziness", "головокружение", R.string.tag_dizziness),
    TagDef("tremor", "тремор", R.string.tag_tremor),
    TagDef("drowsiness", "сонливость", R.string.tag_drowsiness),
    TagDef("insomnia", "бессонница", R.string.tag_insomnia),
    TagDef("dry_mouth", "сухость во рту", R.string.tag_dry_mouth),
    TagDef("sweating", "потливость", R.string.tag_sweating),
    TagDef("palpitations", "сердцебиение", R.string.tag_palpitations),
    TagDef("weight_gain", "набор веса", R.string.tag_weight_gain),
    TagDef("appetite_loss", "нет аппетита", R.string.tag_appetite_loss),
    TagDef("appetite_gain", "повышен аппетит", R.string.tag_appetite_gain),
    TagDef("low_libido", "снижение либидо", R.string.tag_low_libido),
    TagDef("slowness", "заторможенность", R.string.tag_slowness),
    TagDef("restless_legs", "беспокойные ноги", R.string.tag_restless_legs),
    TagDef("constipation", "запор", R.string.tag_constipation),
    TagDef("diarrhea", "диарея", R.string.tag_diarrhea),
    TagDef("muscle_pain", "мышечная боль", R.string.tag_muscle_pain)
))

fun tagLabel(list: List<TagDef>, key: String): String =
    list.firstOrNull { it.key == key }?.label ?: key

fun parseKeys(csv: String): List<String> =
    csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun joinKeys(keys: Collection<String>): String = keys.distinct().joinToString(",")

/**
 * Время хранится и разбирается как «08:05». Формат обязан быть ASCII:
 * в локалях с национальными цифрами `%02d` даёт «٠٨:٠٥», а `LocalTime.parse`
 * молча не находит приём — напоминания и дозы пропадают.
 */
fun LocalTime.hhmm(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

fun LocalDateTime.hhmm(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

fun LocalDate.toEpochDayLong(): Long = toEpochDay()

fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
