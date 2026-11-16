package app.opora.diagnostics

import java.util.Locale

enum class ReportTheme { SYSTEM, LIGHT, DARK }

/**
 * Технические поля отчёта. Никаких записей дневника, настроения или имён —
 * только версия, настройки отображения, устройство и последняя ошибка.
 */
data class ReportData(
    val appVersion: String,
    val versionCode: Int,
    val languageTag: String,
    val fontScale: Float,
    val theme: ReportTheme,
    val device: String,
    val androidRelease: String,
    val androidSdk: Int,
    val freeBytes: Long,
    val lastError: String? = null,
    val crashFileName: String? = null,
    val recipient: String? = null
)

/**
 * Формирует текст для системного «Поделиться». Чистая функция: одинаковая
 * на устройстве и в JVM-тестах, поэтому формат можно проверить целиком.
 */
object ReportText {

    fun format(data: ReportData): String {
        val ru = data.languageTag.lowercase(Locale.ROOT).startsWith("ru")
        val labels = if (ru) RU else EN
        val lines = buildList {
            add(if (ru) "Опора: сообщение о проблеме" else "Anchor: problem report")
            data.recipient?.takeIf { it.isNotBlank() }?.let { recipient ->
                add("")
                add(labels.notice(recipient))
            }
            add("")
            add(labels.version(data.appVersion, data.versionCode))
            add(labels.language(data.languageTag))
            add(labels.fontScale(decimal(data.fontScale, ru)))
            add(labels.theme(data.theme, ru))
            add(labels.device(data.device))
            add(labels.android(data.androidRelease, data.androidSdk))
            add(labels.freeSpace(bytes(data.freeBytes, ru)))
            DiagnosticRedaction.errorSummary(data.lastError)
                ?.let { add(labels.lastError(it)) }
            data.crashFileName?.takeIf { it.isNotBlank() }?.let { add(labels.crashFile(it)) }
            add("")
            add(if (ru) "Что случилось:" else "What happened:")
        }
        return lines.joinToString("\n")
    }

    private fun decimal(value: Float, ru: Boolean): String {
        val text = String.format(Locale.US, "%.1f", value)
        return if (ru) text.replace('.', ',') else text
    }

    private fun bytes(value: Long, ru: Boolean): String {
        val safe = value.coerceAtLeast(0L)
        val gb = safe / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) {
            val text = String.format(Locale.US, "%.1f", gb)
            return (if (ru) text.replace('.', ',') else text) + " " + if (ru) "ГБ" else "GB"
        }
        val mb = safe / (1024.0 * 1024.0)
        val text = if (mb >= 10) String.format(Locale.US, "%.0f", mb)
        else String.format(Locale.US, "%.1f", mb)
        return (if (ru) text.replace('.', ',') else text) + " " + if (ru) "МБ" else "MB"
    }

    private class Labels(
        val version: (String, Int) -> String,
        val language: (String) -> String,
        val fontScale: (String) -> String,
        val theme: (ReportTheme, Boolean) -> String,
        val device: (String) -> String,
        val android: (String, Int) -> String,
        val freeSpace: (String) -> String,
        val lastError: (String) -> String,
        val crashFile: (String) -> String,
        val notice: (String) -> String
    )

    private val EN = Labels(
        version = { v, code -> "Version: $v ($code)" },
        language = { "App language: $it" },
        fontScale = { "Font scale: $it" },
        theme = { value, _ ->
            "Theme: " + when (value) {
                ReportTheme.SYSTEM -> "system"
                ReportTheme.LIGHT -> "light"
                ReportTheme.DARK -> "dark"
            }
        },
        device = { "Device: $it" },
        android = { release, sdk -> "Android: $release (API $sdk)" },
        freeSpace = { "Free space: $it" },
        lastError = { "Last error: $it" },
        crashFile = { "Crash log: $it" },
        notice = {
            "This report is addressed to $it. Before sending, review the text and the crash file (if attached): personal data may remain in the stack trace."
        }
    )

    private val RU = Labels(
        version = { v, code -> "Версия: $v ($code)" },
        language = { "Язык приложения: $it" },
        fontScale = { "Масштаб шрифта: $it" },
        theme = { value, _ ->
            "Тема: " + when (value) {
                ReportTheme.SYSTEM -> "системная"
                ReportTheme.LIGHT -> "светлая"
                ReportTheme.DARK -> "тёмная"
            }
        },
        device = { "Устройство: $it" },
        android = { release, sdk -> "Android: $release (API $sdk)" },
        freeSpace = { "Свободное место: $it" },
        lastError = { "Последняя ошибка: $it" },
        crashFile = { "Файл падения: $it" },
        notice = {
            "Отчёт адресован $it. Перед отправкой проверьте текст и файл падения (если он приложен): в стектрейсе могут остаться личные данные."
        }
    )
}
