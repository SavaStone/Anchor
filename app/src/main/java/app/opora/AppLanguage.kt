package app.opora

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import app.opora.data.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * One place that decides which locale the app resources use.
 *
 * The choice is persisted in DataStore together with the other settings.
 * Below Android 13 [wrap] replaces the configuration in `attachBaseContext`;
 * on Android 13+ the framework's per-app locale is the primary source (it also
 * renames the launcher icon), and [wrap] is a harmless no-op when it matches.
 */
object AppLanguage {
    const val SYSTEM = ""
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"

    val options = listOf(SYSTEM, RUSSIAN, ENGLISH)

    @Volatile
    private var tag: String = SYSTEM

    fun currentTag(): String = tag

    fun normalize(value: String?): String =
        if (value != null && value in options) value else SYSTEM

    fun setTag(value: String?) {
        tag = normalize(value)
    }

    fun locale(context: Context): Locale =
        context.resources.configuration.locales[0] ?: Locale.getDefault()

    /**
     * Resolves the language before any activity exists. A DataStore read is a
     * few milliseconds; it runs once per process so the first frame is already
     * in the right language and notifications never use a stale locale.
     */
    fun resolveStartupTag(base: Context): String {
        val stored = runCatching {
            runBlocking { SettingsRepository(base.applicationContext).languageTag() }
        }.getOrDefault(SYSTEM)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return normalize(stored)
        val manager = base.getSystemService(LocaleManager::class.java)
        val systemTag = manager?.applicationLocales
            ?.takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
        // The system's per-app language wins: it may have been changed from
        // Settings, where the app cannot observe the change as a DataStore edit.
        return normalize(systemTag ?: stored)
    }

    fun wrap(base: Context): Context {
        val value = tag
        if (value.isEmpty()) return base
        return base.withLocale(Locale.forLanguageTag(value))
    }

    fun Context.withLocale(locale: Locale): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return createConfigurationContext(config)
    }

    /** Mirrors the choice into the platform so the launcher label follows it. */
    fun applyToSystem(context: Context, value: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val locales = if (value.isEmpty()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(value)
        }
        if (manager.applicationLocales != locales) {
            manager.applicationLocales = locales
        }
    }
}

/** Context may be wrapped by the theme/insets machinery; the activity is behind it. */
tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
