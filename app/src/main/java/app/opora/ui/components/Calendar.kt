package app.opora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opora.ui.theme.OporaSpacing
import app.opora.R
import app.opora.data.db.MoodEntity
import app.opora.domain.MOOD_LEVELS
import app.opora.domain.monthYear
import app.opora.domain.moodLevel
import app.opora.domain.weekdayDate
import app.opora.ui.theme.moodColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun MoodCalendar(
    month: YearMonth,
    entries: Map<Long, List<MoodEntity>>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null
) {
    // today не читается в композиции напрямую: экран может передать своё значение,
    // иначе фиксируем дату на входе в композицию.
    val rememberedToday = remember { LocalDate.now() }
    val currentToday = today ?: rememberedToday
    val locale = LocalConfiguration.current.locales[0]
    val weekdayLabels = remember(locale) {
        (1..7).map { day ->
            DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
                .replaceFirstChar { it.titlecase(locale) }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                month.atDay(1).monthYear(locale),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            if (month != YearMonth.from(currentToday)) {
                TextButton(onClick = {
                    val diff = (currentToday.year - month.year) * 12 +
                        (currentToday.monthValue - month.monthValue)
                    onMonthChange(diff)
                }) { Text(stringResource(R.string.tab_today)) }
            } else {
                Spacer(Modifier.height(1.dp))
            }
            IconButton(onClick = { onMonthChange(-1) }) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_prev_month)
                )
            }
            IconButton(onClick = { onMonthChange(1) }) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(OporaSpacing.s))
        AnimatedContent(
            targetState = month,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "month"
        ) { animatedMonth ->
            val animatedFirst = animatedMonth.atDay(1)
            val animatedLead = animatedFirst.dayOfWeek.value - 1
            val animatedDays = animatedMonth.lengthOfMonth()
            val animatedCells = ((animatedLead + animatedDays + 6) / 7) * 7
            // Уходящий слой ещё видим во время fade, но не должен принимать нажатия
            // и попадать в дерево TalkBack.
            val isCurrentLayer = animatedMonth == month
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (isCurrentLayer) Modifier else Modifier.clearAndSetSemantics { })
            ) {
                for (row in 0 until animatedCells / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val index = row * 7 + col
                            val dayNumber = index - animatedLead + 1
                            if (dayNumber in 1..animatedDays) {
                                val date = animatedMonth.atDay(dayNumber)
                                CalendarCell(
                                    date = date,
                                    entries = entries[date.toEpochDay()].orEmpty(),
                                    isSelected = date == selected,
                                    isToday = date == currentToday,
                                    isFuture = date.isAfter(currentToday),
                                    enabled = isCurrentLayer,
                                    onClick = { if (isCurrentLayer) onSelect(date) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f).height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    date: LocalDate,
    entries: List<MoodEntity>,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Несколько отметок за день — несколько цветов в ячейке. Уровни без повторов:
    // две отметки «хорошо» не должны делить день на две одинаковые полосы.
    val moods = remember(entries) {
        entries.mapNotNull { it.mood }.distinct().sorted()
    }
    // Штрих под номером показывает уровень последней отметки.
    val latestMood = remember(entries) {
        entries.maxByOrNull { it.updatedAt }?.mood
    }
    val cellShape = MaterialTheme.shapes.large
    val moodFills = moods.map { moodColor(it) }
    val bg = when {
        isFuture -> Color.Transparent
        moodFills.isNotEmpty() -> moodFills.first()
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }
    // Жёсткие цветовые остановки делят ячейку на полосы, а не смешивают цвета
    // в градиент: иначе два настроения читались бы как третье, промежуточное.
    val moodBrush = if (moodFills.size > 1) {
        val stops = buildList {
            moodFills.forEachIndexed { index, color ->
                add(index.toFloat() / moodFills.size to color)
                add((index + 1).toFloat() / moodFills.size to color)
            }
        }
        Brush.horizontalGradient(*stops.toTypedArray())
    } else {
        null
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        isFuture -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val interactive = enabled && !isFuture
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val description = remember(date, moods, isToday, isSelected, isFuture, locale, context) {
        val base = "${date.weekdayDate(locale)} ${date.year}"
        val dayText = when {
            isToday -> context.getString(R.string.a11y_calendar_today, base)
            isFuture -> context.getString(R.string.a11y_calendar_future, base)
            else -> base
        }
        val moodText = when {
            isFuture -> dayText
            moods.size == 1 -> context.getString(
                R.string.a11y_calendar_mood,
                dayText,
                moods.single(),
                context.getString(moodLevel(moods.single()).labelRes).lowercase()
            )
            moods.isNotEmpty() -> context.getString(
                R.string.a11y_calendar_moods,
                dayText,
                moods.joinToString(", ") { context.getString(moodLevel(it).labelRes).lowercase() }
            )
            else -> context.getString(R.string.a11y_calendar_no_mood, dayText)
        }
        if (isSelected) context.getString(R.string.a11y_calendar_selected, moodText) else moodText
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(cellShape)
            .then(
                if (moodBrush != null) Modifier.background(moodBrush)
                else Modifier.background(bg)
            )
            .border(if (isSelected) 2.dp else 1.dp, borderColor, cellShape)
            .selectable(
                selected = isSelected,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                // Номер дня всегда onSurface: цвет не единственный носитель смысла,
                // а контраст не зависит от оттенка настроения. Для будущих дней —
                // приглушённый onSurfaceVariant вместо 38% onSurface: 2.45:1 не читалось.
                color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurface
            )
            if (latestMood != null && !isFuture) {
                Spacer(Modifier.height(3.dp))
                // Длина штриха повторяет уровень настроения — подсказка не только цветом.
                // Цвет штриха — onSurface, чтобы он читался независимо от оттенка.
                Box(
                    Modifier
                        .width((6 + (latestMood.coerceIn(1, 5) - 1) * 4).dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f))
                )
            } else {
                // Держим одинаковую высоту ячейки, чтобы номера дней не «прыгали».
                Spacer(Modifier.height(OporaSpacing.s))
            }
        }
    }
}

@Composable
fun MoodLegend(modifier: Modifier = Modifier) {
    val scaleLabel = stringResource(R.string.calendar_mood_scale_a11y)
    FlowRow(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = scaleLabel },
        horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MOOD_LEVELS.forEach { level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodDot(level.value, 7.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(level.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
