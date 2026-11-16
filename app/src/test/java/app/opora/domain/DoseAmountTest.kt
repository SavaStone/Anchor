package app.opora.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DoseAmountTest {
    @Test
    fun fractionalDoseIsSubtractedAsFraction() {
        assertEquals(0.5, doseUnitsFor("0,5 капсулы"), 0.0001)
        assertEquals(0.5, doseUnitsFor("0.5"), 0.0001)
        assertEquals(1.5, doseUnitsFor("1,5 таблетки"), 0.0001)
        assertEquals(2.0, doseUnitsFor("2 таблетки"), 0.0001)
        assertEquals(0.5, doseUnitsFor("1/2 таблетки"), 0.0001)
    }

    @Test
    fun strengthInMilligramsDoesNotBecomeStockAmount() {
        assertEquals(1.0, doseUnitsFor("25 мг"), 0.0001)
        assertEquals(1.0, doseUnitsFor("50мг"), 0.0001)
        assertEquals(1.0, doseUnitsFor(""), 0.0001)
    }

    @Test
    fun countUnitsAreSubtracted() {
        assertEquals(1.0, doseUnitsFor("1 капля"), 0.0001)
        assertEquals(10.0, doseUnitsFor("10 капель"), 0.0001)
        assertEquals(5.0, doseUnitsFor("5 мл"), 0.0001)
    }

    @Test
    fun amountFormattingUsesRussianComma() {
        assertEquals("19", formatAmount(19.0, Locale.forLanguageTag("ru")))
        assertEquals("19", formatAmount(19.0, Locale.ENGLISH))
        assertEquals("18,5", formatAmount(18.5, Locale.forLanguageTag("ru")))
        assertEquals("18.5", formatAmount(18.5, Locale.ENGLISH))
        assertEquals("18,25", formatAmount(18.25, Locale.forLanguageTag("ru")))
        assertEquals("0", formatAmount(0.0, Locale.forLanguageTag("ru")))
    }

    @Test
    fun inputAcceptsBothSeparators() {
        assertEquals(18.5, parseAmount("18,5")!!, 0.0001)
        assertEquals(18.5, parseAmount("18.5")!!, 0.0001)
        assertEquals(null, parseAmount(""))
    }

    @Test
    fun englishCountUnitsAreRecognized() {
        assertEquals(2.0, doseUnitsFor("2 tablets"), 0.0001)
        assertEquals(0.5, doseUnitsFor("0.5 capsule"), 0.0001)
        assertEquals(1.0, doseUnitsFor("25 mg"), 0.0001)
    }
}
