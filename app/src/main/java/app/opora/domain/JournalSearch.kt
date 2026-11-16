package app.opora.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Поиск по записи дневника: текст и подписи меток. Регистр не важен,
 * «ё» и «е» считаются одной буквой, пробелы по краям запроса игнорируются.
 */
fun journalMatchesQuery(
    title: String,
    body: String,
    tagKeys: Collection<String>,
    tagLabels: Map<String, String>,
    query: String,
    normalizedText: String? = null
): Boolean {
    val needle = normalizeForSearch(query.trim())
    if (needle.isEmpty()) return true
    if ((normalizedText ?: normalizeForSearch(title + "\n" + body)).contains(needle)) return true
    return tagKeys.any { key ->
        normalizeForSearch(tagLabels[key] ?: key).contains(needle) ||
            normalizeForSearch(key).contains(needle)
    }
}

fun normalizeForSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        // Strip combining marks so English accents and Russian ё/й match their
        // base letters; search stays case-insensitive and script-agnostic.
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')

private val COMBINING_MARKS = Regex("\\p{M}+")

/**
 * Оставляет по одному варианту каждой метки без учёта регистра.
 * Порядок и написание первого варианта сохраняем: «Тревога, тревога» → «Тревога».
 */
fun distinctJournalTags(tags: Collection<String>): List<String> =
    tags.map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }
