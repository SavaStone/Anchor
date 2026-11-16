package app.opora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.opora.R
import app.opora.data.db.MoodEntity
import app.opora.domain.TagDef
import app.opora.domain.hhmm
import app.opora.domain.formatDecimal
import app.opora.domain.moodLevel
import app.opora.domain.parseKeys
import app.opora.domain.weekdayDate
import app.opora.ui.InsightNote
import app.opora.ui.MoodInsights
import app.opora.ui.MoodViewModel
import app.opora.ui.components.AnxietyTrendChart
import app.opora.ui.components.EmptyState
import app.opora.ui.components.MoodCalendar
import app.opora.ui.components.MoodDistribution
import app.opora.ui.components.MoodFace
import app.opora.ui.components.MoodLegend
import app.opora.ui.components.MoodTrendChart
import app.opora.ui.components.OporaCard
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.Pill
import app.opora.ui.components.PrimaryFab
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.ui.theme.moodColor
import app.opora.ui.text.displayLabel
import app.opora.ui.text.formatSleep
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

@Composable
fun MoodScreen(
    vm: MoodViewModel,
    onEditMood: (LocalDate, Long?) -> Unit,
    onEditEntry: (Long) -> Unit,
    leftHanded: Boolean = false
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val entriesByDate = remember(state.monthEntries) {
        state.monthEntries.groupBy { it.date }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) {
        if (state.today != LocalDate.now()) vm.refresh()
        var lastDate = LocalDate.now()
        while (true) {
            delay(60_000)
            val now = LocalDate.now()
            if (now != lastDate) {
                lastDate = now
                vm.refresh()
            }
        }
    }

    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // Горизонтальные поля задаются у элементов: календарь должен получить все 336dp,
            // чтобы семь ячеек оставались не меньше 48dp по ширине. Нижний отступ
            // оставляет место FAB, чтобы он не перекрывал последнюю карточку.
            contentPadding = PaddingValues(
                start = OporaSpacing.screenGutter,
                end = OporaSpacing.screenGutter,
                top = OporaSpacing.screenTop,
                bottom = OporaSpacing.fabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)
        ) {
            item {
                MoodSection {
                    ScreenTitle(title = stringResource(R.string.mood_title))
                }
            }

            if (state.loading) {
                item {
                    MoodSection {
                        Box(
                            Modifier.fillMaxWidth().height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else if (!state.hasEntries) {
                item {
                    MoodSection {
                        EmptyState(
                            icon = Icons.Outlined.SelfImprovement,
                            title = stringResource(R.string.mood_empty_title),
                            body = stringResource(R.string.mood_empty_body)
                        )
                    }
                }
            } else {
                item {
                    // Полноширинная карточка: 360dp − 2×12dp = 336dp на семь ячеек.
                    OporaCard(contentPadding = 12.dp) {
                        Column {
                            MoodCalendar(
                                month = state.month,
                                entries = entriesByDate,
                                selected = state.selectedDate,
                                today = state.today,
                                onSelect = { date ->
                                    vm.select(date)
                                    // Календарь — способ посмотреть прошлые дни, а не
                                    // редактор. Открываем его только для сегодняшней
                                    // даты (можно добавить ещё отметку) и для вчерашней,
                                    // если она ещё пустая. Старые дни остаются
                                    // просмотром; отметить их можно кнопкой в карточке.
                                    val hasEntries = entriesByDate[date.toEpochDay()]
                                        ?.isNotEmpty() == true
                                    if (shouldOpenMoodEditor(date, state.today, hasEntries)) {
                                        onEditMood(
                                            date,
                                            entriesByDate[date.toEpochDay()]
                                                ?.maxByOrNull { it.createdAt }?.id
                                        )
                                    }
                                },
                                onMonthChange = vm::changeMonth
                            )
                            Spacer(Modifier.height(OporaSpacing.s))
                            MoodLegend()
                        }
                    }
                }

                item {
                    MoodSection {
                        SelectedDayCard(
                            date = state.selectedDate,
                            today = state.today,
                            entries = state.selectedEntries,
                            emotionTags = state.emotionTags,
                            onAdd = { onEditMood(state.selectedDate, null) },
                            onEditEntry = onEditEntry
                        )
                    }
                }

                item {
                    MoodSection {
                        SectionHeader(stringResource(R.string.mood_period_30_days))
                    }
                }

                item {
                    MoodSection {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            // Четыре узкие плитки не помещают подписи при крупном шрифте.
                            val columns = if (LocalDensity.current.fontScale >= 1.3f) 2 else 4
                            val gap = 8.dp
                            // −1dp: при дробной ширине четыре плитки округляются вверх
                            // по пикселям и FlowRow переносит четвёртую на новую строку.
                            val tileWidth =
                                ((maxWidth - gap * (columns - 1).toFloat()) / columns.toFloat()) - 1.dp
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                val dash = stringResource(R.string.common_dash)
                                MetricTile(
                                    value = state.stats30.avgMood
                                        ?.let { formatDecimal(it, locale) } ?: dash,
                                    label = stringResource(R.string.mood_metric_mood),
                                    modifier = Modifier.width(tileWidth)
                                )
                                MetricTile(
                                    value = state.stats30.avgAnxiety
                                        ?.let { formatDecimal(it, locale) } ?: dash,
                                    label = stringResource(R.string.mood_metric_anxiety),
                                    modifier = Modifier.width(tileWidth)
                                )
                                MetricTile(
                                    value = state.stats30.avgSleepMinutes
                                        ?.let { formatSleep(context, it) } ?: dash,
                                    label = stringResource(R.string.mood_metric_sleep),
                                    modifier = Modifier.width(tileWidth)
                                )
                                MetricTile(
                                    value = state.stats30.count.toString(),
                                    label = pluralStringResource(
                                        R.plurals.mood_marks,
                                        state.stats30.count,
                                        state.stats30.count
                                    ),
                                    modifier = Modifier.width(tileWidth)
                                )
                            }
                        }
                    }
                }

                item {
                    MoodSection {
                        OporaCard {
                            Column {
                                SectionHeader(
                                    stringResource(R.string.mood_title),
                                    subtitle = stringResource(R.string.mood_scale_hint)
                                )
                                Spacer(Modifier.height(8.dp))
                                MoodTrendChart(state.allEntries, days = 30)
                            }
                        }
                    }
                }

                item {
                    MoodSection {
                        OporaCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.WaterDrop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(OporaIconSize.inline)
                                    )
                                    Spacer(Modifier.width(OporaSpacing.s))
                                    SectionHeader(
                                        stringResource(R.string.mood_anxiety_title),
                                        subtitle = stringResource(R.string.mood_anxiety_hint)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                AnxietyTrendChart(state.allEntries, days = 30)
                            }
                        }
                    }
                }

                item {
                    MoodSection {
                        OporaCard {
                            Column {
                                SectionHeader(stringResource(R.string.mood_distribution))
                                Spacer(Modifier.height(OporaSpacing.listGap))
                                MoodDistribution(state.stats30.distribution)
                            }
                        }
                    }
                }

                if (state.stats30.topEmotions.isNotEmpty() || state.stats30.topSymptoms.isNotEmpty()) {
                    item {
                        MoodSection {
                            OporaCard {
                                Column {
                                    if (state.stats30.topEmotions.isNotEmpty()) {
                                        SectionHeader(stringResource(R.string.mood_top_emotions))
                                        Spacer(Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
                                            verticalArrangement = Arrangement.spacedBy(OporaSpacing.s)
                                        ) {
                                            state.stats30.topEmotions.forEach { (key, count) ->
                                                Pill(
                                                    stringResource(
                                                        R.string.mood_tag_count,
                                                        state.emotionTags.displayLabel(context, key),
                                                        count
                                                    ),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                )
                                            }
                                        }
                                    }
                                    if (state.stats30.topSymptoms.isNotEmpty()) {
                                        Spacer(Modifier.height(OporaSpacing.listGap))
                                        SectionHeader(stringResource(R.string.mood_top_symptoms))
                                        Spacer(Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
                                            verticalArrangement = Arrangement.spacedBy(OporaSpacing.s)
                                        ) {
                                            state.stats30.topSymptoms.forEach { (key, count) ->
                                                Pill(
                                                    stringResource(
                                                        R.string.mood_tag_count,
                                                        state.symptomTags.displayLabel(context, key),
                                                        count
                                                    ),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    MoodSection {
                        InsightCard(state.insights, state.emotionTags)
                    }
                }
            }
        }
        PrimaryFab(
            text = stringResource(R.string.mood_mark_action),
            contentDescription = stringResource(R.string.mood_mark_a11y),
            onClick = { onEditMood(state.selectedDate, null) },
            expanded = fabExpanded,
            modifier = Modifier
                .align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(OporaSpacing.l)
        )
    }
}

/** Единые горизонтальные поля для всех блоков, кроме полноширинного календаря. */
@Composable
private fun MoodSection(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        content()
    }
}

/**
 * Календарь открывает редактор только там, где это ожидаемо:
 * сегодня — всегда (можно добавить ещё одну отметку),
 * вчера — только если день ещё не заполнен.
 * Для более старых дат клик только выбирает день: пользователь хочет
 * посмотреть отметки, а не изменить их.
 */
internal fun shouldOpenMoodEditor(date: LocalDate, today: LocalDate, hasEntries: Boolean): Boolean =
    date == today || (date == today.minusDays(1) && !hasEntries)

@Composable
private fun SelectedDayCard(
    date: LocalDate,
    today: LocalDate,
    entries: List<MoodEntity>,
    emotionTags: List<TagDef>,
    onAdd: () -> Unit,
    onEditEntry: (Long) -> Unit
) {
    val future = date.isAfter(today)
    val latest = entries.maxByOrNull { it.updatedAt }
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val dateText = if (date.year != today.year) {
        stringResource(R.string.mood_date_with_year, date.weekdayDate(locale), date.year)
    } else {
        date.weekdayDate(locale)
    }
    OporaCard(
        color = if (latest != null) moodColor(latest.mood).copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        dateText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when {
                            future -> stringResource(R.string.mood_future_day)
                            entries.isEmpty() -> stringResource(R.string.mood_no_marks)
                            else -> pluralStringResource(
                                R.plurals.mood_marks,
                                entries.size,
                                entries.size
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!future && entries.isEmpty()) {
                    FilledTonalButton(onClick = onAdd) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(OporaIconSize.inline))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.mood_mark_action))
                    }
                }
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    Spacer(Modifier.height(OporaSpacing.s))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(OporaSpacing.s))
                } else {
                    Spacer(Modifier.height(OporaSpacing.s))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoodFace(
                        level = entry.mood,
                        selected = true,
                        size = 42.dp,
                        onClick = { onEditEntry(entry.id) }
                    )
                    Spacer(Modifier.width(OporaSpacing.s))
                    val moodLabel = stringResource(moodLevel(entry.mood).labelRes)
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildString {
                                append(moodLabel)
                                append(" · ")
                                append(
                                    Instant.ofEpochMilli(entry.createdAt)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalTime()
                                        .hhmm()
                                )
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        val metrics = listOfNotNull(
                            entry.anxiety?.let { context.getString(R.string.mood_metric_anxiety_value, it) },
                            entry.energy?.let { context.getString(R.string.mood_metric_energy_value, it) },
                            entry.sleepMinutes?.let {
                                context.getString(
                                    R.string.mood_metric_sleep_value,
                                    formatSleep(context, it)
                                )
                            }
                        )
                        if (metrics.isNotEmpty()) {
                            Text(
                                metrics.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val emotions = parseKeys(entry.emotions)
                        if (emotions.isNotEmpty()) {
                            Text(
                                emotions.joinToString(", ") { emotionTags.displayLabel(context, it) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (entry.note.isNotBlank()) {
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { onEditEntry(entry.id) }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.mood_edit_a11y),
                            modifier = Modifier.size(OporaIconSize.inline)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    OporaCard(modifier = modifier, contentPadding = OporaSpacing.m) {
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Итог наблюдений: спокойная карточка вместо цветного блока на всю ширину.
 * Заголовок с иконкой задаёт раздел, фраза-вывод читается первой, а факты
 * идут строками «подпись — значение» и не обрезаются при крупном шрифте.
 */
@Composable
private fun InsightCard(insights: MoodInsights, emotionTags: List<TagDef>) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val bestDayLabel = stringResource(R.string.insight_best_day)
    val mostFrequentLabel = stringResource(R.string.insight_most_frequent)
    val topEmotion = insights.topEmotionKey?.let { emotionTags.displayLabel(context, it) }
    val bestWeekday = insights.bestWeekday?.getDisplayName(TextStyle.FULL, locale)
        ?.replaceFirstChar { char -> char.titlecase(locale) }
    val facts = buildList {
        topEmotion?.let { add(mostFrequentLabel to it) }
        bestWeekday?.let { add(bestDayLabel to it) }
    }
    OporaCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(OporaIconSize.row)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.insight_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.insight_window),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(OporaSpacing.listGap))
            val noteText = when (insights.note) {
                InsightNote.NEED_MORE -> stringResource(R.string.insight_need_more)
                InsightNote.BETTER -> stringResource(
                    R.string.insight_better,
                    insights.deltaPercent ?: 0
                )
                InsightNote.LOWER -> stringResource(
                    R.string.insight_lower,
                    -(insights.deltaPercent ?: 0)
                )
                InsightNote.STABLE -> stringResource(R.string.insight_stable)
                null -> stringResource(R.string.insight_empty)
            }
            Text(
                noteText,
                style = MaterialTheme.typography.bodyLarge
            )
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                facts.forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            value,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
