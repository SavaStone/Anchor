package app.opora.diagnostics

/**
 * Чистая очистка технических строк от типовых секретов перед показом человеку
 * и отправкой отчёта. Убирает URL (вместе с userinfo), адреса почты и значения
 * заголовков/полей с паролями и токенами, но сохраняет классы, методы и номера
 * строк стектрейса.
 *
 * Это не гарантия отсутствия произвольных персональных данных: отчёт всё равно
 * нужно просмотреть перед отправкой. Записи дневника, настроения и базу helper
 * не читает.
 */
object DiagnosticRedaction {
    const val MAX_ERROR_CHARS = 500
    const val MAX_TRACE_CHARS = 64_000
    const val MAX_TRACE_INPUT_BYTES = 128 * 1024

    private val WHITESPACE = Regex("\\s+")
    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val AUTH_HEADER =
        Regex("""(?i)\b(authorization|proxy-authorization)\b\s*[:=]\s*[^\r\n]+""")
    private val BEARER =
        Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val SECRET = Regex(
        """(?i)\b(password|passwd|pwd|secret|client[_-]?secret|token|api[_-]?key|api[_-]?token|access[_-]?token|refresh[_-]?token|auth[_-]?token)("?)(\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s,;]+)"""
    )

    /**
     * Многострочная очистка (стектрейсы, старые файлы падений). Входные данные
     * ограничиваются [MAX_TRACE_CHARS], чтобы регулярные выражения не работали
     * с неограниченным текстом.
     */
    fun scrubTrace(raw: String): String = scrub(raw)

    /** Очищает произвольный текст; переводы строк сохраняются. */
    fun scrub(raw: String): String {
        var text = if (raw.length <= MAX_TRACE_CHARS) raw else raw.take(MAX_TRACE_CHARS)
        text = URL.replace(text, "[redacted-url]")
        text = AUTH_HEADER.replace(text) { match ->
            "${match.groupValues[1]}=[redacted]"
        }
        text = BEARER.replace(text) { match ->
            "${match.groupValues[1]} [redacted]"
        }
        text = SECRET.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}[redacted]"
        }
        text = EMAIL.replace(text, "[redacted-email]")
        return if (text.length <= MAX_TRACE_CHARS) text else text.take(MAX_TRACE_CHARS)
    }

    /**
     * Однострочная сводка последней ошибки: очищает секреты, убирает переводы
     * строк и управляющие символы, ограничивает длину [MAX_ERROR_CHARS].
     * Возвращает null для пустой строки.
     */
    fun errorSummary(raw: String?): String? {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return null
        val flat = scrub(source.take(MAX_ERROR_CHARS * 4))
            .replace(WHITESPACE, " ")
            .filterNot { it.isISOControl() }
            .trim()
        if (flat.isEmpty()) return null
        if (flat.length <= MAX_ERROR_CHARS) return flat
        return flat.take(MAX_ERROR_CHARS - 1) + "\u2026"
    }
}
