package app.opora.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun fireInkIsReadableInBothThemes() {
        assertTrue(contrast(OnFireColorLight, FireColorLight) >= 4.5)
        assertTrue(contrast(OnFireColorDark, FireColorDark) >= 4.5)
    }

    @Test
    fun fireIconHasNonTextContrastOnLightContainers() {
        listOf(
            LightColors.primaryContainer,
            LightColors.secondaryContainer,
            LightColors.surfaceContainerLow
        ).forEach { background ->
            assertTrue(
                "contrast on $background",
                contrast(FireColorLight, background) >= 3.0
            )
        }
    }

    @Test
    fun fireIconHasNonTextContrastOnDarkContainers() {
        listOf(
            DarkColors.primaryContainer,
            DarkColors.secondaryContainer,
            DarkColors.surfaceContainerLow
        ).forEach { background ->
            assertTrue(
                "contrast on $background",
                contrast(FireColorDark, background) >= 3.0
            )
        }
    }
}
