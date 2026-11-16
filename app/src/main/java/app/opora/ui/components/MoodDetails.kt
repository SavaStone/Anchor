package app.opora.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opora.ui.theme.OporaIconSize
import app.opora.R
import app.opora.domain.MOOD_LEVELS
import app.opora.domain.MoodLevel
import app.opora.domain.TagDef
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.text.displayLabel
import app.opora.ui.text.formatSleep
import kotlin.math.roundToInt

/**
 * Шкала настроения, общая для отметки дня и записи дневника. Состояние
 * остаётся у вызывающего экрана, здесь только одинаковый вид и семантика.
 */
@Composable
fun MoodScaleCard(
    mood: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val cardTitle = title ?: stringResource(R.string.mood_scale_title)
    OporaCard(modifier) {
        Column {
            Text(cardTitle, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(OporaSpacing.listGap))
            // При крупном шрифте пять узких колонок рвут подписи посередине
            // слова. Те же пять вариантов становятся строками на всю ширину.
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(
                    Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)
                ) {
                    MOOD_LEVELS.forEach { level ->
                        MoodScaleRow(
                            level = level,
                            selected = mood == level.value,
                            onSelect = { onSelect(level.value) }
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(OporaSpacing.xs)
                ) {
                    MOOD_LEVELS.forEach { level ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MoodFace(
                                level = level.value,
                                selected = mood == level.value,
                                onClick = { onSelect(level.value) },
                                size = 46.dp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(level.labelRes),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mood == level.value) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodScaleRow(
    level: MoodLevel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(level.labelRes)
    val a11yLabel = stringResource(R.string.mood_a11y, label)
    val levelOfFive = stringResource(R.string.mood_level_of_five, level.value)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .semantics {
                contentDescription = a11yLabel
                stateDescription = levelOfFive
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoodFace(level = level.value, selected = selected, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BodyMetricsCard(
    anxietyEnabled: Boolean,
    anxiety: Float,
    onAnxietyEnabled: (Boolean) -> Unit,
    onAnxiety: (Float) -> Unit,
    energyEnabled: Boolean,
    energy: Float,
    onEnergyEnabled: (Boolean) -> Unit,
    onEnergy: (Float) -> Unit,
    sleepEnabled: Boolean,
    sleepMinutes: Float,
    onSleepEnabled: (Boolean) -> Unit,
    onSleep: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val anxietyTitle = stringResource(R.string.mood_anxiety_title)
    val energyTitle = stringResource(R.string.mood_energy_title)
    val sleepTitle = stringResource(R.string.mood_sleep_title)
    OporaCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(
                stringResource(R.string.mood_body_metrics_title),
                subtitle = stringResource(R.string.mood_optional)
            )
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = anxietyTitle,
                checked = anxietyEnabled,
                onCheckedChange = onAnxietyEnabled
            )
            if (anxietyEnabled) {
                ValueSlider(
                    value = anxiety,
                    onChange = onAnxiety,
                    valueText = "${anxiety.roundToInt()} / 10",
                    label = anxietyTitle
                )
            }
            SwitchRow(
                title = energyTitle,
                checked = energyEnabled,
                onCheckedChange = onEnergyEnabled
            )
            if (energyEnabled) {
                ValueSlider(
                    value = energy,
                    onChange = onEnergy,
                    valueText = "${energy.roundToInt()} / 10",
                    label = energyTitle
                )
            }
            SwitchRow(
                title = sleepTitle,
                checked = sleepEnabled,
                onCheckedChange = onSleepEnabled
            )
            if (sleepEnabled) {
                ValueSlider(
                    value = sleepMinutes,
                    onChange = onSleep,
                    valueRange = 0f..720f,
                    steps = 23,
                    valueText = formatSleep(context, sleepMinutes.roundToInt()),
                    label = sleepTitle
                )
            }
        }
    }
}

@Composable
fun EmotionsCard(
    tags: List<TagDef>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onAddCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    OporaCard(modifier) {
        Column {
            SectionHeader(stringResource(R.string.mood_emotions_title))
            Spacer(Modifier.height(OporaSpacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
                verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.key in selected,
                        onClick = { onToggle(tag.key) },
                        label = { Text(tag.displayLabel(context)) }
                    )
                }
            }
            TextButton(onClick = onAddCustom) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(OporaIconSize.inline))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.mood_custom_emotion))
            }
        }
    }
}

@Composable
fun SymptomsCard(
    tags: List<TagDef>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onAddCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    // Выбранный симптом не должен прятаться за «Показать все»: иначе
    // отметка сохраняется, но человек её больше не видит.
    LaunchedEffect(tags, selected) {
        val collapsed = tags.take(8).map { it.key }.toSet()
        if (selected.any { it !in collapsed }) showAll = true
    }
    val context = LocalContext.current
    OporaCard(modifier) {
        Column {
            SectionHeader(stringResource(R.string.mood_symptoms_title))
            Spacer(Modifier.height(OporaSpacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
                verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)
            ) {
                val visible = if (showAll) tags else tags.take(8)
                visible.forEach { tag ->
                    FilterChip(
                        selected = tag.key in selected,
                        onClick = { onToggle(tag.key) },
                        label = { Text(tag.displayLabel(context)) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tags.size > 8) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) stringResource(R.string.mood_collapse)
                            else stringResource(R.string.mood_show_all)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAddCustom) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(OporaIconSize.inline))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.mood_custom_symptom))
                }
            }
        }
    }
}

/** Общий диалог для своей эмоции и своего симптома. */
@Composable
fun AddTagDialog(
    title: String,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val hideKeyboard = rememberKeyboardHider()
    FormDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.mood_add),
        confirmEnabled = text.isNotBlank(),
        dismissLabel = stringResource(R.string.mood_cancel),
        onConfirm = { onAdd(text.trim()) }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().formDialogField(),
            label = { Text(stringResource(R.string.mood_tag_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { hideKeyboard() })
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ValueSlider(
    value: Float,
    onChange: (Float) -> Unit,
    valueText: String,
    label: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 9
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label }
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = OporaSpacing.s)
        )
    }
}
