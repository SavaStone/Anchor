package app.opora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.data.db.MoodEntity
import app.opora.domain.dayMonthShort
import app.opora.domain.weekdayShort
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.theme.moodColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val midX = (prev.x + cur.x) / 2f
        path.cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
    }
    return path
}

@Composable
fun LineTrend(
    points: List<Pair<LocalDate, Float>>,
    minValue: Float,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier,
    startDate: LocalDate = points.minOfOrNull { it.first } ?: LocalDate.now(),
    endDate: LocalDate = points.maxOfOrNull { it.first } ?: LocalDate.now(),
    showFill: Boolean = true,
    description: String? = null
) {
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val shape = MaterialTheme.shapes.large
    val sorted = remember(points) { points.sortedBy { it.first } }
    val span = max(1, ChronoUnit.DAYS.between(startDate, endDate).toInt())
    val chartDescription = description
    Spacer(
        modifier
            .fillMaxWidth()
            .height(164.dp)
            .then(
                if (chartDescription != null) {
                    Modifier.semantics { contentDescription = chartDescription }
                } else {
                    Modifier
                }
            )
            .background(background, shape)
            .padding(horizontal = OporaSpacing.m, vertical = OporaSpacing.m)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val range = (maxValue - minValue).coerceAtLeast(0.001f)
                val gridStroke = 1.dp.toPx()
                val offsets = if (sorted.size >= 2) {
                    sorted.map { (date, value) ->
                        val day = ChronoUnit.DAYS.between(startDate, date).toInt().coerceIn(0, span)
                        val x = w * day / span.toFloat()
                        val y = h - (value - minValue) / range * h
                        Offset(x, y.coerceIn(4.dp.toPx(), h - 4.dp.toPx()))
                    }
                } else {
                    emptyList()
                }
                val linePath = if (offsets.size >= 2) smoothPath(offsets) else null
                val fillPath = if (linePath != null && showFill) {
                    smoothPath(offsets).apply {
                        lineTo(offsets.last().x, h)
                        lineTo(offsets.first().x, h)
                        close()
                    }
                } else {
                    null
                }
                val fillBrush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = h
                )
                val lineStroke = 3.dp.toPx()
                val haloRadius = 6.dp.toPx()
                val dotRadius = 3.2.dp.toPx()
                onDrawBehind {
                    repeat(4) { i ->
                        val y = h * i / 3f
                        drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = gridStroke)
                    }
                    if (linePath != null) {
                        if (fillPath != null) drawPath(fillPath, fillBrush)
                        drawPath(
                            linePath,
                            color = color,
                            style = Stroke(
                                width = lineStroke,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        offsets.forEach { point ->
                            drawCircle(color.copy(alpha = 0.18f), radius = haloRadius, center = point)
                            drawCircle(color, radius = dotRadius, center = point)
                        }
                    }
                }
            }
    )
}

@Composable
fun ChartFooter(start: LocalDate?, end: LocalDate?) {
    if (start == null || end == null) return
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            start.dayMonthShort(locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            end.dayMonthShort(locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MoodTrendChart(
    entries: List<MoodEntity>,
    modifier: Modifier = Modifier,
    days: Int = 30,
    endDate: LocalDate? = null
) {
    val rememberedToday = remember { LocalDate.now() }
    val today = endDate ?: rememberedToday
    val start = today.minusDays(days.toLong() - 1)
    val points = remember(entries, days, today) {
        entries.filter { it.date >= start.toEpochDay() && it.date <= today.toEpochDay() }
            .map { LocalDate.ofEpochDay(it.date) to it.mood.toFloat() }
    }
    val context = LocalContext.current
    val description = remember(points, days, context) {
        if (points.size < 2) {
            context.getString(R.string.chart_mood_empty_a11y)
        } else {
            val values = points.map { it.second.toInt() }
            val last = points.maxByOrNull { it.first }!!.second.toInt()
            context.getString(
                R.string.chart_mood_a11y,
                points.size,
                values.minOrNull() ?: 0,
                values.maxOrNull() ?: 0,
                last
            )
        }
    }
    Column(modifier) {
        if (points.size < 2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.chart_need_two),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LineTrend(
                points = points,
                minValue = 1f,
                maxValue = 5f,
                color = moodColor(5),
                startDate = start,
                endDate = today,
                description = description
            )
            ChartFooter(start, today)
        }
    }
}

@Composable
fun AnxietyTrendChart(
    entries: List<MoodEntity>,
    modifier: Modifier = Modifier,
    days: Int = 30,
    endDate: LocalDate? = null
) {
    val rememberedToday = remember { LocalDate.now() }
    val today = endDate ?: rememberedToday
    val start = today.minusDays(days.toLong() - 1)
    val points = remember(entries, days, today) {
        entries.filter { it.date >= start.toEpochDay() && it.anxiety != null }
            .map { LocalDate.ofEpochDay(it.date) to it.anxiety!!.toFloat() }
    }
    val context = LocalContext.current
    val description = remember(points, days, context) {
        if (points.size < 2) {
            context.getString(R.string.chart_anxiety_empty_a11y)
        } else {
            val values = points.map { it.second.toInt() }
            context.getString(
                R.string.chart_anxiety_a11y,
                points.size,
                values.minOrNull() ?: 0,
                values.maxOrNull() ?: 0
            )
        }
    }
    Column(modifier) {
        if (points.size < 2) {
            Text(
                stringResource(R.string.chart_anxiety_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LineTrend(
                points = points,
                minValue = 0f,
                maxValue = 10f,
                color = MaterialTheme.colorScheme.tertiary,
                startDate = start,
                endDate = today,
                description = description
            )
            ChartFooter(start, today)
        }
    }
}

@Composable
fun MoodDistribution(
    distribution: Map<Int, Int>,
    modifier: Modifier = Modifier
) {
    val max = max(1, distribution.values.maxOrNull() ?: 1)
    val total = distribution.values.sum()
    val context = LocalContext.current
    val description = remember(distribution, context) {
        if (total == 0) {
            context.getString(R.string.chart_distribution_empty_a11y)
        } else {
            val items = (1..5).joinToString(", ") { level ->
                context.getString(R.string.chart_distribution_item, level, distribution[level] ?: 0)
            }
            context.getString(R.string.chart_distribution_a11y, total, items)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (1..5).forEach { level ->
            val count = distribution[level] ?: 0
            val fraction = count.toFloat() / max
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (count > 0) count.toString() else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((10 + 54 * fraction).dp)
                        .background(
                            moodColor(level).copy(alpha = if (count == 0) 0.18f else 0.85f),
                            MaterialTheme.shapes.small
                        )
                )
                Spacer(Modifier.height(OporaSpacing.s))
                Text(
                    when (level) {
                        1 -> "1"
                        2 -> "2"
                        3 -> "3"
                        4 -> "4"
                        else -> "5"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun MiniBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(6.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    fraction.coerceIn(0f, 1f),
                    0f..1f
                )
            }
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
fun WeekRhythm(
    points: List<Pair<LocalDate, Float>>,
    modifier: Modifier = Modifier
) {
    val last = remember(points) { points.takeLast(7) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val description = remember(last, context, locale) {
        if (last.isEmpty()) {
            context.getString(R.string.chart_week_rhythm_empty_a11y)
        } else {
            val items = last.joinToString(", ") { (date, value) ->
                context.getString(
                    R.string.chart_week_rhythm_item,
                    date.weekdayShort(locale),
                    value.toInt().coerceIn(1, 5)
                )
            }
            context.getString(R.string.chart_week_rhythm_a11y, items)
        }
    }
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
            last.forEach { (date, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((18 + 46 * (value / 5f)).dp)
                            .background(
                                moodColor(value.toInt().coerceIn(1, 5)).copy(alpha = 0.8f),
                                MaterialTheme.shapes.small
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
