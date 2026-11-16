package app.opora.ui.text

import android.content.Context
import app.opora.AppLanguage
import app.opora.domain.TagDef
import app.opora.domain.parseKeys
import java.text.Collator

/**
 * Alphabetical order by the label the user actually sees, in the app language.
 * The domain keeps a stable Russian fallback for storage and deduplication;
 * every displayed list is re-sorted here.
 */
fun List<TagDef>.sortedByDisplayLabel(context: Context): List<TagDef> {
    // wrap() re-applies the in-app choice on Android 12 and below, where the
    // application context keeps the locale it had at process start.
    val localized = AppLanguage.wrap(context)
    val collator = Collator.getInstance(AppLanguage.locale(localized))
    return sortedWith(compareBy(collator) { it.displayLabel(localized) })
}

fun mergeTagsForDisplay(
    base: List<TagDef>,
    custom: List<TagDef>,
    context: Context
): List<TagDef> = (base + custom)
    .distinctBy { it.key }
    .sortedByDisplayLabel(context)

fun tagLabelsForDisplay(
    context: Context,
    vararg groups: List<TagDef>
): Map<String, String> = buildMap {
    val localized = AppLanguage.wrap(context)
    groups.forEach { group ->
        group.forEach { put(it.key, it.displayLabel(localized)) }
    }
}

fun sortedTagKeysForDisplay(
    keys: Collection<String>,
    labels: Map<String, String>,
    context: Context
): List<String> {
    val localized = AppLanguage.wrap(context)
    val collator = Collator.getInstance(AppLanguage.locale(localized))
    return keys.distinctBy { it.lowercase(java.util.Locale.ROOT) }
        .sortedWith(compareBy(collator) { labels[it] ?: it })
}
