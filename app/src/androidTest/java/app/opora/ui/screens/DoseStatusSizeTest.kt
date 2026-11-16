package app.opora.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * Все состояния дозы рисуются одним визуальным кругом 38dp: проверяем и
 * layout-границы, и реально закрашенные пиксели — глиф CheckCircle занимает
 * лишь 20/24 вьюпорта, поэтому одни bounds его не ловят.
 */
class DoseStatusSizeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun dose(status: String) = DoseLogEntity(
        medicationId = "med",
        medicationName = "Тест",
        scheduledDate = LocalDate.now().toEpochDay(),
        scheduledTime = "09:00",
        status = status,
        takenAt = if (status == "taken") System.currentTimeMillis() else null
    )

    private fun colors(themeMode: String) {
        compose.setContent {
            OporaTheme(themeMode = themeMode) {
                Column {
                    listOf("taken", "pending", "missed", "skipped").forEach { status ->
                        DoseStatusAction(dose(status), onTake = {}, onUndo = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun paintedDiameterDp(index: Int): Dp {
        val node = compose.onAllNodesWithTag("dose-status-visual", useUnmergedTree = true)[index]
        val pixels = node.captureToImage().toPixelMap()
        val background = pixels[0, 0]
        var minX = pixels.width
        var minY = pixels.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                val delta = abs(c.red - background.red) + abs(c.green - background.green) +
                    abs(c.blue - background.blue)
                if (delta > 0.15f) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        assertTrue("status[$index] не нарисован", maxX >= 0 && maxY >= 0)
        val widthPx = maxX - minX + 1
        val heightPx = maxY - minY + 1
        assertEquals(
            "status[$index] не круг: $widthPx x $heightPx px",
            widthPx.toFloat(),
            heightPx.toFloat(),
            1f
        )
        return with(compose.density) { widthPx.toDp() }
    }

    private fun assertStatusSizes(themeMode: String) {
        colors(themeMode)
        val visuals = compose.onAllNodesWithTag("dose-status-visual", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("нужны хотя бы принятое и непринятое состояния", visuals.size >= 2)

        // Layout-границы одинаковы во всех состояниях.
        visuals.indices.forEach { index ->
            val bounds = compose.onAllNodesWithTag(
                "dose-status-visual",
                useUnmergedTree = true
            )[index].getUnclippedBoundsInRoot()
            assertEquals("bounds width[$index] $themeMode", 38f, (bounds.right - bounds.left).value, 1f)
            assertEquals("bounds height[$index] $themeMode", 38f, (bounds.bottom - bounds.top).value, 1f)
        }

        // Реальный диаметр закрашенного глифа — тоже 38dp во всех состояниях.
        val diameters = visuals.indices.map { paintedDiameterDp(it) }
        diameters.forEachIndexed { index, diameter ->
            assertEquals("painted diameter[$index] $themeMode", 38f, diameter.value, 1f)
        }
        assertEquals("должны быть все четыре состояния", 4, visuals.size)
    }

    @Test
    fun allStatusVisualsHaveSameDiameterLight() = assertStatusSizes("light")

    @Test
    fun allStatusVisualsHaveSameDiameterDark() = assertStatusSizes("dark")

    @Test
    fun tapTargetsRemainAtLeast48dp() {
        colors("light")
        val targets = compose.onAllNodesWithContentDescription(TestStrings.get(R.string.dose_take_a11y))
            .fetchSemanticsNodes()
        assertEquals(2, targets.size)
        targets.forEach { node ->
            assertTrue(
                "tap target ${node.boundsInRoot.width} x ${node.boundsInRoot.height}",
                node.boundsInRoot.width >= with(compose.density) { 48.dp.toPx() } &&
                    node.boundsInRoot.height >= with(compose.density) { 48.dp.toPx() }
            )
        }
        listOf(
            TestStrings.get(R.string.dose_taken_a11y, "Тест"),
            TestStrings.get(R.string.actions_undo)
        ).forEach { description ->
            val node = compose.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().single()
            assertTrue(
                "$description ${node.boundsInRoot.width} x ${node.boundsInRoot.height}",
                node.boundsInRoot.width >= with(compose.density) { 48.dp.toPx() } &&
                    node.boundsInRoot.height >= with(compose.density) { 48.dp.toPx() }
            )
        }
    }
}
