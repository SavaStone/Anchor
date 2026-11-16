@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.opora.ui.screens

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opora.data.db.JournalEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.journalDate
import app.opora.domain.EMOTIONS
import app.opora.domain.SYMPTOMS
import app.opora.domain.parseKeys
import app.opora.domain.dayMonthShort
import app.opora.domain.journalDateLabel
import app.opora.ui.JournalViewModel
import app.opora.ui.components.EmptyState
import app.opora.R
import app.opora.ui.components.LoadingState
import app.opora.ui.components.MoodDot
import app.opora.ui.components.OporaCard
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.PrimaryFab
import app.opora.ui.components.Pill
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.ui.text.displayLabel
import java.time.LocalDate

/**
 * Встроенные эмоции и симптомы живут в ресурсах, свои метки остаются текстом
 * пользователя. Разрешаем их один раз, чтобы карточки получали готовые подписи.
 */
internal fun resolvedTagLabels(context: Context, labels: Map<String, String>): Map<String, String> {
    val builtin = (EMOTIONS + SYMPTOMS).associate { it.key to it.displayLabel(context) }
    return labels.mapValues { (key, raw) -> builtin[key] ?: raw.ifBlank { key } }
}

@Composable
fun JournalScreen(
    vm: JournalViewModel,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
    leftHanded: Boolean = false
) {
    val state by vm.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val filtering = state.query.isNotBlank() || state.activeTag != null
    val labels = remember(state.tagLabels, context) {
        resolvedTagLabels(context, state.tagLabels)
    }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OporaSpacing.screenGutter,
                end = OporaSpacing.screenGutter,
                top = OporaSpacing.screenTop,
                bottom = OporaSpacing.fabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)
        ) {
            item { ScreenTitle(title = stringResource(R.string.journal_title)) }
            item {
                TextField(
                    value = state.query,
                    onValueChange = vm::search,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.journal_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.search("") }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.journal_search_clear)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }
            if (state.loading) {
                item { LoadingState() }
            }
            if (state.tags.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.activeTag == null,
                            onClick = { vm.filterTag(null) },
                            label = { Text(stringResource(R.string.journal_filter_all)) }
                        )
                        state.tags.forEach { tag ->
                            FilterChip(
                                selected = state.activeTag == tag,
                                onClick = { vm.filterTag(if (state.activeTag == tag) null else tag) },
                                label = { Text(labels[tag] ?: tag) }
                            )
                        }
                    }
                }
            }
            if (filtering && !state.loading) {
                item {
                    Text(
                        stringResource(
                            R.string.journal_results,
                            pluralStringResource(
                                R.plurals.entries,
                                state.entries.size,
                                state.entries.size
                            )
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            if (state.entries.isEmpty() && !state.loading) {
                item {
                    if (filtering) {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(R.string.journal_empty_search_title),
                            body = stringResource(R.string.journal_empty_search_body),
                            actionLabel = stringResource(R.string.journal_reset_filters),
                            onAction = {
                                vm.search("")
                                vm.filterTag(null)
                            }
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.AutoStories,
                            title = stringResource(R.string.journal_empty_title),
                            body = stringResource(R.string.journal_empty_body),
                            actionLabel = stringResource(R.string.journal_first_entry),
                            onAction = onNew
                        )
                    }
                }
            } else {
                val pinned = state.entries.filter { it.pinned }
                val others = state.entries.filterNot { it.pinned }
                if (pinned.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.journal_pinned)) }
                    items(pinned, key = { "pin-${it.id}" }) { entry ->
                        JournalCard(
                            entry = entry,
                            labels = labels,
                            dayMoods = state.moodsByDate[entry.journalDate().toEpochDay()].orEmpty(),
                            // У закреплённых нет заголовка даты: показываем её в карточке.
                            dateLabel = entry.journalDate().journalDateLabel(today, locale),
                            onClick = { onOpen(entry.id) }
                        )
                    }
                }
                val groups = others.groupBy { it.journalDate() }
                    .toSortedMap(compareByDescending { it })
                groups.forEach { (date, dayEntries) ->
                    item(key = "date-${date.toEpochDay()}") {
                        DateHeader(date)
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        JournalCard(
                            entry = entry,
                            labels = labels,
                            dayMoods = state.moodsByDate[entry.journalDate().toEpochDay()].orEmpty(),
                            onClick = { onOpen(entry.id) }
                        )
                    }
                }
            }
        }

        PrimaryFab(
            text = stringResource(R.string.journal_new_short),
            contentDescription = stringResource(R.string.journal_new_a11y),
            onClick = onNew,
            expanded = fabExpanded,
            modifier = Modifier
                .align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(OporaSpacing.l)
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        date.journalDateLabel(LocalDate.now(), locale),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = OporaSpacing.s, start = OporaSpacing.xs)
    )
}

/** Карточка заметки в духе Google Keep; лента — одна колонка по датам. */
@Composable
private fun JournalCard(
    entry: JournalEntity,
    labels: Map<String, String>,
    dayMoods: List<MoodEntity>,
    dateLabel: String? = null,
    onClick: () -> Unit
) {
    val tags = parseKeys(entry.tags)
    val pinnedState = stringResource(R.string.journal_pinned_state)
    OporaCard(
        onClick = onClick,
        color = noteColor(entry),
        modifier = Modifier.semantics {
            if (entry.pinned) stateDescription = pinnedState
        }
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Точки настроения за день: запись остаётся текстом, но
                // читается в контексте состояния. Правится оно в «Настроении».
                dayMoods.take(5).forEach { mark ->
                    MoodDot(mark.mood, 8.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.weight(1f))
                if (dateLabel != null) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(OporaSpacing.s))
                }
                if (entry.pinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(OporaIconSize.compact),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(OporaSpacing.s))
            Text(
                entry.title.ifBlank { stringResource(R.string.journal_untitled_note) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(OporaSpacing.s))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.take(4).forEach { tag ->
                        Pill(
                            labels[tag] ?: tag,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (tags.size > 4) {
                        Text(
                            stringResource(R.string.journal_more_tags, tags.size - 4),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun noteColor(entry: JournalEntity): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
    )
    return palette[Math.floorMod(entry.id.hashCode(), palette.size)]
}

/** Small inline preview used on the Today screen. */
@Composable
fun JournalPreviewCard(entry: JournalEntity, onClick: () -> Unit) {
    val date = entry.journalDate()
    val locale = LocalConfiguration.current.locales[0]
    OporaCard(onClick = onClick) {
        Column {
            Text(
                date.dayMonthShort(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                entry.title.ifBlank { stringResource(R.string.journal_untitled_note) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                entry.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
