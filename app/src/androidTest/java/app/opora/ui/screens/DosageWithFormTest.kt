package app.opora.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.AppLanguage
import app.opora.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DecimalFormatSymbols

@RunWith(AndroidJUnit4::class)
class DosageWithFormTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test
    fun addsFormWhenDosageHasNoUnit() {
        val separator =
            DecimalFormatSymbols.getInstance(AppLanguage.locale(context)).decimalSeparator
        assertEquals(
            "0${separator}5 ${context.getString(R.string.med_form_tablet)}",
            dosageWithForm(context, "0.5", "tablet")
        )
        assertEquals(
            "2${separator}5 ${context.getString(R.string.med_form_capsule)}",
            dosageWithForm(context, "2${separator}5", "capsule")
        )
    }

    @Test
    fun keepsExplicitUnit() {
        assertEquals("25 мг", dosageWithForm(context, "25 мг", "tablet"))
        assertEquals("1 капля", dosageWithForm(context, "1 капля", "drops"))
    }

    @Test
    fun blankDosageStaysBlank() {
        assertEquals("", dosageWithForm(context, "  ", "tablet"))
    }
}
