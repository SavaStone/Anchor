package app.opora.domain

import java.util.Locale

/**
 * Сколько единиц запаса списывается за один приём.
 *
 * Дозировка — свободный текст: «0,5 капсулы», «2 таблетки», «25 мг».
 * Считать дозу в миллиграммах за единицу упаковки нельзя, поэтому явное
 * количество учитывается только для единиц счёта: таблетки, капсулы, капли,
 * миллилитры, штуки, дозы. Для «25 мг» списывается одна таблетка.
 */
fun doseUnitsFor(dosage: String): Double {
    val trimmed = dosage.trim()
    val fraction = FRACTION_PREFIX.find(trimmed)
    val decimal = if (fraction == null) NUMERIC_PREFIX.find(trimmed) else null
    val amount = when {
        fraction != null -> {
            val numerator = fraction.groupValues[1].toDoubleOrNull() ?: return 1.0
            val denominator = fraction.groupValues[2].toDoubleOrNull() ?: return 1.0
            if (denominator == 0.0) return 1.0
            numerator / denominator
        }
        decimal != null -> decimal.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 1.0
        else -> return 1.0
    }
    if (amount <= 0.0 || amount > 10_000.0) return 1.0
    val consumed = fraction?.range?.last ?: decimal?.range?.last ?: return 1.0
    val tail = trimmed.substring(consumed + 1).lowercase(Locale.ROOT)
    if (tail.isBlank()) return amount
    return if (COUNT_UNITS.containsMatchIn(tail)) amount else 1.0
}

/** Принимает и «18,5», и «18.5»; пустая строка — null. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

private val NUMERIC_PREFIX = Regex("""^(\d+(?:[.,]\d+)?)""")
private val FRACTION_PREFIX = Regex("""^(\d+)\s*/\s*(\d+)""")

private val COUNT_UNITS = Regex(
    "таблет|капсул|капл|капел|шт|доз|мл|инъекц|ампул|пакет|саше|горош|" +
        "tablet|capsul|drop|pill|dose|ml|inject|ampou|packet|sachet|grain|puff"
)
