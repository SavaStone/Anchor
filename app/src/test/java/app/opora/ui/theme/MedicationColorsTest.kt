package app.opora.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationColorsTest {

    @Test
    fun swatchInkKeepsContrastAtLeast45() {
        MedicationSwatches.forEach { swatch ->
            val ink = medicationOnColor(swatch.value)
            val background = Color(swatch.value).luminance()
            val foreground = ink.luminance()
            val ratio = (maxOf(background, foreground) + 0.05f) /
                (minOf(background, foreground) + 0.05f)
            assertTrue(
                "${swatch.value.toString(16)}: contrast ${"%.2f".format(ratio)} < 4.5",
                ratio >= 4.5f
            )
        }
    }
}
