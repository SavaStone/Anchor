package app.opora.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Единое правило `IconTextRow`: центр иконки совпадает с центром первой строки
 * заголовка (или с центром блока для `firstLine = false`) при 1.0/1.3/2.0.
 */
@RunWith(AndroidJUnit4::class)
class IconTextRowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        scale: Float,
        title: String,
        subtitle: String?,
        firstLine: Boolean,
        lineStyle: TextStyle
    ) {
        compose.setContent {
            OporaTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                    IconTextRow(
                        icon = Icons.Outlined.Schedule,
                        title = title,
                        subtitle = subtitle,
                        titleStyle = lineStyle,
                        firstLine = firstLine,
                        modifier = Modifier.width(280.dp)
                    )
                }
            }
        }
    }

    private fun center(tag: String): Dp {
        val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return (b.top + b.bottom) / 2
    }

    private fun assertAligned(scale: Float, mode: String) {
        compose.waitForIdle()
        val delta = abs((center(IconTextRowTags.LEADING) - center(IconTextRowTags.TITLE)).value)
        assertTrue("$mode scale=$scale delta=$delta", delta <= 2f)
    }

    @Test
    fun singleLineIsCenteredAtAllFontScales() {
        val style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
        show(1.0f, "Арипипразол", null, firstLine = true, lineStyle = style)
        assertAligned(1.0f, "single")
        compose.onNodeWithTag(IconTextRowTags.TITLE).assertExists()
        // Повторная композиция с другим масштабом в том же тесте невозможна:
        // setContent можно вызвать один раз. Проверяем масштабы отдельными тестами.
    }

    @Test
    fun singleLineCenteredAt13() {
        val style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
        show(1.3f, "Арипипразол", null, firstLine = true, lineStyle = style)
        assertAligned(1.3f, "single")
    }

    @Test
    fun singleLineCenteredAt20() {
        val style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
        show(2.0f, "Арипипразол", null, firstLine = true, lineStyle = style)
        assertAligned(2.0f, "single")
    }

    @Test
    fun bodySmallSingleLineCenteredAt10() {
        val style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
        show(1.0f, "Далее: 19:00", null, firstLine = true, lineStyle = style)
        assertAligned(1.0f, "bodySmall single")
    }

    @Test
    fun bodySmallSingleLineCenteredAt13() {
        val style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
        show(1.3f, "Далее: 19:00", null, firstLine = true, lineStyle = style)
        assertAligned(1.3f, "bodySmall single")
    }

    @Test
    fun bodySmallSingleLineCenteredAt20() {
        val style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
        show(2.0f, "Далее: 19:00", null, firstLine = true, lineStyle = style)
        assertAligned(2.0f, "bodySmall single")
    }

    @Test
    fun bodySmallTwoLinesAlignToFirstLineAt20() {
        val style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
        show(
            2.0f,
            "Далее: Арипипразол пролонгированного действия, сегодня в 19:00",
            null,
            firstLine = true,
            lineStyle = style
        )
        compose.waitForIdle()
        val title = compose.onNodeWithTag(IconTextRowTags.TITLE).getUnclippedBoundsInRoot()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(IconTextRowTags.TITLE).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) { it(layouts) }
        val layout = layouts.first()
        assertTrue("ожидали перенос", layout.lineCount >= 2)
        val firstLineCenter = with(compose.density) {
            title.top + ((layout.getLineTop(0) + layout.getLineBottom(0)) / 2f).toDp()
        }
        val delta = abs((center(IconTextRowTags.LEADING) - firstLineCenter).value)
        assertTrue("bodySmall multi scale=2.0 delta=$delta", delta <= 2f)
    }

    @Test
    fun multiLineAlignsToFirstLineAt20() {
        val style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
        // Длинный заголовок гарантированно переносится на две строки.
        show(
            2.0f,
            "Арипипразол пролонгированного действия 400 мг",
            "принимать вечером",
            firstLine = true,
            lineStyle = style
        )
        compose.waitForIdle()
        val title = compose.onNodeWithTag(IconTextRowTags.TITLE).getUnclippedBoundsInRoot()
        val leading = compose.onNodeWithTag(IconTextRowTags.LEADING).getUnclippedBoundsInRoot()
        // Центр первой строки берём из TextLayoutResult: шрифтовые метрики
        // добавляют к lineHeight собственную погрешность.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(IconTextRowTags.TITLE).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) { it(layouts) }
        val layout = layouts.first()
        val firstLineCenter = with(compose.density) {
            title.top + ((layout.getLineTop(0) + layout.getLineBottom(0)) / 2f).toDp()
        }
        val delta = abs((center(IconTextRowTags.LEADING) - firstLineCenter).value)
        assertTrue(
            "multi firstLine scale=2.0 delta=$delta leading=$leading title=$title " +
                "firstLineCenter=$firstLineCenter",
            delta <= 2f
        )
    }

    @Test
    fun trailingActionAlignsToFirstLineAt20() {
        val style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
        compose.setContent {
            OporaTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, 2.0f)) {
                    IconTextRow(
                        icon = Icons.Outlined.Schedule,
                        title = "Арипипразол пролонгированного действия 400 мг",
                        subtitle = "принимать вечером",
                        titleStyle = style,
                        modifier = Modifier.width(280.dp),
                        trailing = {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray)
                            )
                        },
                        trailingFirstLine = true,
                        trailingSize = 48.dp
                    )
                }
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithTag(IconTextRowTags.TITLE).getUnclippedBoundsInRoot()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(IconTextRowTags.TITLE).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) { it(layouts) }
        val firstLineCenter = with(compose.density) {
            title.top + ((layouts.first().getLineTop(0) + layouts.first().getLineBottom(0)) / 2f).toDp()
        }
        val delta = abs(
            (center(IconTextRowTags.TRAILING) - firstLineCenter).value
        )
        assertTrue("trailing scale=2.0 delta=$delta", delta <= 2f)
    }

    @Test
    fun badgeModeCentersOnTheWholeBlock() {
        val style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
        show(
            2.0f,
            "Дневник",
            "Записи, мысли и то, что хочется сохранить",
            firstLine = false,
            lineStyle = style
        )
        val delta = abs(
            (center(IconTextRowTags.LEADING) - center(IconTextRowTags.BLOCK)).value
        )
        assertTrue("badge scale=2.0 delta=$delta", delta <= 2f)
    }
}
