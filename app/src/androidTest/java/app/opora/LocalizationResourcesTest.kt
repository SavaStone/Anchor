package app.opora

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {
    private fun localized(locale: Locale): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    @Test
    fun russianIsTheBaselineAndEnglishIsTranslated() {
        val ru = localized(Locale.forLanguageTag("ru"))
        val en = localized(Locale.ENGLISH)

        assertEquals("Сегодня", ru.getString(R.string.tab_today))
        assertEquals("Today", en.getString(R.string.tab_today))
        assertEquals("Опора", ru.getString(R.string.app_name))
        assertEquals("Anchor", en.getString(R.string.app_name))
        assertEquals("Приём: Тест", ru.getString(R.string.notif_med_title, "Тест"))
        assertEquals("Dose: Test", en.getString(R.string.notif_med_title, "Test"))
        assertEquals("Очень тяжело", ru.getString(R.string.mood_1))
        assertEquals("Very difficult", en.getString(R.string.mood_1))
        assertEquals("Без приёма", ru.getString(R.string.dose_status_skipped))
        assertEquals("Skipped", en.getString(R.string.dose_status_skipped))
    }

    @Test
    fun russianHasThreePluralFormsAndEnglishTwo() {
        val ru = localized(Locale.forLanguageTag("ru")).resources
        val en = localized(Locale.ENGLISH).resources

        assertEquals("1 день", ru.getQuantityString(R.plurals.days, 1, 1))
        assertEquals("2 дня", ru.getQuantityString(R.plurals.days, 2, 2))
        assertEquals("5 дней", ru.getQuantityString(R.plurals.days, 5, 5))
        assertEquals("11 дней", ru.getQuantityString(R.plurals.days, 11, 11))
        assertEquals("21 день", ru.getQuantityString(R.plurals.days, 21, 21))

        assertEquals("1 day", en.getQuantityString(R.plurals.days, 1, 1))
        assertEquals("2 days", en.getQuantityString(R.plurals.days, 2, 2))
        assertEquals("5 days", en.getQuantityString(R.plurals.days, 5, 5))

        assertEquals("1 лекарство", ru.getQuantityString(R.plurals.medications, 1, 1))
        assertEquals("3 лекарства", ru.getQuantityString(R.plurals.medications, 3, 3))
        assertEquals("8 лекарств", ru.getQuantityString(R.plurals.medications, 8, 8))
        assertEquals("1 medication", en.getQuantityString(R.plurals.medications, 1, 1))
        assertEquals("8 medications", en.getQuantityString(R.plurals.medications, 8, 8))

        assertEquals("1 приём", ru.getQuantityString(R.plurals.doses, 1, 1))
        assertEquals("2 приёма", ru.getQuantityString(R.plurals.doses, 2, 2))
        assertEquals("5 приёмов", ru.getQuantityString(R.plurals.doses, 5, 5))
        assertEquals("1 dose", en.getQuantityString(R.plurals.doses, 1, 1))
        assertEquals("5 doses", en.getQuantityString(R.plurals.doses, 5, 5))

        assertEquals("1 запись", ru.getQuantityString(R.plurals.entries, 1, 1))
        assertEquals("2 записи", ru.getQuantityString(R.plurals.entries, 2, 2))
        assertEquals("5 записей", ru.getQuantityString(R.plurals.entries, 5, 5))
        assertEquals("1 entry", en.getQuantityString(R.plurals.entries, 1, 1))
        assertEquals("5 entries", en.getQuantityString(R.plurals.entries, 5, 5))
    }
}
