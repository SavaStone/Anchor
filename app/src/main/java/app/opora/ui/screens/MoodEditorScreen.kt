package app.opora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opora.ui.theme.OporaIconSize
import app.opora.domain.MOOD_LEVELS
import app.opora.domain.POSITIVE_DEED
import app.opora.domain.POSITIVE_EVENT
import app.opora.domain.MoodLevel
import app.opora.domain.TAG_TYPE_EMOTION
import app.opora.domain.TAG_TYPE_SYMPTOM
import app.opora.domain.hhmm
import app.opora.domain.parseKeys
import app.opora.data.db.PositiveEntity
import app.opora.domain.dayMonth
import app.opora.ui.MoodViewModel
import app.opora.R
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.ImeBackHandler
import app.opora.ui.components.FormDialog
import app.opora.ui.components.rememberKeyboardHider
import app.opora.ui.components.formDialogField
import app.opora.ui.components.AddTagDialog
import app.opora.ui.components.BodyMetricsCard
import app.opora.ui.components.DateChip
import app.opora.ui.components.EmotionsCard
import app.opora.ui.components.MoodScaleCard
import app.opora.ui.components.SymptomsCard
import app.opora.ui.components.ImeAwareBox
import app.opora.ui.components.MoodFace
import app.opora.ui.components.OporaCard
import app.opora.ui.components.OporaTimePickerDialog
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

private val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodEditorScreen(
    initialDate: LocalDate,
    entryId: Long? = null,
    vm: MoodViewModel,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var addTagType by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    // Маршрут может прийти из дневника с будущей датой: отметка настроения
    // для ещё не наступившего дня не имеет смысла.
    var dateEpoch by rememberSaveable(initialDate) {
        mutableStateOf(minOf(initialDate, today).toEpochDay())
    }
    val date = LocalDate.ofEpochDay(dateEpoch)
    // Время отметки — не момент сохранения. Для сегодняшнего дня стартуем
    // с текущего времени, для прошлого пользователь может выбрать любое.
    var timeMinutes by rememberSaveable(initialDate) {
        mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute })
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var currentId by rememberSaveable(initialDate, entryId) { mutableStateOf(entryId) }
    var seeded by rememberSaveable(initialDate, entryId) { mutableStateOf(false) }
    var lastSaved by rememberSaveable(initialDate) { mutableStateOf<String?>(null) }
    var saveTick by rememberSaveable(initialDate) { mutableIntStateOf(0) }
    var hasEntry by rememberSaveable(initialDate) { mutableStateOf(entryId != null) }
    var loading by remember { mutableStateOf(entryId != null) }
    var mood by rememberSaveable(initialDate) { mutableStateOf<Int?>(null) }
    var anxietyEnabled by rememberSaveable(initialDate) { mutableStateOf(false) }
    var anxiety by rememberSaveable(initialDate) { mutableStateOf(5f) }
    var energyEnabled by rememberSaveable(initialDate) { mutableStateOf(false) }
    var energy by rememberSaveable(initialDate) { mutableStateOf(5f) }
    var sleepEnabled by rememberSaveable(initialDate) { mutableStateOf(false) }
    var sleepMinutes by rememberSaveable(initialDate) { mutableStateOf(420f) }
    var emotions by rememberSaveable(initialDate, stateSaver = StringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var symptoms by rememberSaveable(initialDate, stateSaver = StringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var note by rememberSaveable(initialDate) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMoodRequired by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val persistMutex = remember { Mutex() }
    val listState = rememberLazyListState()
    // Заголовок + дата + шкала; при ошибке загрузки между ними появляется ещё пункт.
    val moodItemIndex = if (error == null) 2 else 3

    fun snapshot(): String = listOf(
        dateEpoch,
        timeMinutes,
        mood,
        anxietyEnabled,
        anxiety,
        energyEnabled,
        energy,
        sleepEnabled,
        sleepMinutes,
        emotions.sorted().joinToString(","),
        symptoms.sorted().joinToString(","),
        note
    ).joinToString("|")

    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    LaunchedEffect(entryId) {
        if (seeded) {
            loading = false
            return@LaunchedEffect
        }
        loading = entryId != null
        error = null
        if (entryId != null) {
            runCatching { vm.entry(entryId) }
                .onSuccess { existing ->
                    if (existing != null) {
                        currentId = existing.id
                        hasEntry = true
                        dateEpoch = existing.date
                        timeMinutes = Instant.ofEpochMilli(existing.createdAt)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                            .let { it.hour * 60 + it.minute }
                        mood = existing.mood
                        anxietyEnabled = existing.anxiety != null
                        anxiety = (existing.anxiety ?: 5).toFloat()
                        energyEnabled = existing.energy != null
                        energy = (existing.energy ?: 5).toFloat()
                        sleepEnabled = existing.sleepMinutes != null
                        sleepMinutes = (existing.sleepMinutes ?: 420).toFloat()
                        emotions = parseKeys(existing.emotions).toSet()
                        symptoms = parseKeys(existing.symptoms).toSet()
                        note = existing.note
                    } else {
                        error = context.getString(R.string.mood_error_not_found)
                    }
                }
                .onFailure {
                    error = context.getString(R.string.mood_error_load)
                }
        }
        seeded = true
        lastSaved = snapshot()
        loading = false
    }

    /**
     * Отметка сохраняется сама: выбор настроения — уже полноценная запись,
     * поэтому выход назад не должен спрашивать «выйти без сохранения?».
     * Повторный проход нужен, если пользователь успел что-то поменять,
     * пока шло сохранение.
     */
    suspend fun persist(): Boolean {
        if (deleted) return true
        return persistMutex.withLock {
            if (deleted || lastSaved == snapshot()) return@withLock true
            var ok = true
            do {
                val snap = snapshot()
                val level = mood ?: return@withLock true
                saving = true
                error = null
                runCatching {
                    vm.save(
                        date = LocalDate.ofEpochDay(dateEpoch),
                        mood = level,
                        anxiety = if (anxietyEnabled) anxiety.roundToInt() else null,
                        energy = if (energyEnabled) energy.roundToInt() else null,
                        sleepMinutes = if (sleepEnabled) sleepMinutes.roundToInt() else null,
                        emotions = emotions,
                        symptoms = symptoms,
                        note = note,
                        entryId = currentId,
                        occurredAt = date.atTime(
                            timeMinutes / 60,
                            timeMinutes % 60
                        ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                }.onSuccess { savedId ->
                    currentId = savedId
                    hasEntry = true
                }.onFailure {
                    error = context.getString(R.string.mood_error_save)
                    ok = false
                }
                saving = false
                if (ok) {
                    lastSaved = snap
                    saveTick++
                }
            } while (ok && snapshot() != lastSaved)
            ok
        }
    }

    /**
     * Детали без уровня не станут отметкой. Не выходим молча: просим выбрать
     * настроение. Если настроение выбрано, выход обычный и без диалога.
     */
    fun hasDetailsWithoutMood(): Boolean =
        mood == null && (anxietyEnabled || energyEnabled || sleepEnabled ||
            emotions.isNotEmpty() || symptoms.isNotEmpty() || note.isNotBlank())

    fun requestClose() {
        if (closing) return
        if (hasDetailsWithoutMood()) {
            showMoodRequired = true
            return
        }
        closing = true
        scope.launch {
            if (deleted || mood == null || lastSaved == snapshot()) {
                onClose()
                return@launch
            }
            // persist() ждёт текущее автосохранение через Mutex, поэтому
            // «Назад» во время записи не теряется и не закрывает экран дважды.
            if (persist()) onClose() else closing = false
        }
    }

    ImeBackHandler { requestClose() }

    // Пауза перед автосохранением: ползунки тревоги и энергии не должны
    // писать в базу на каждый пиксель движения.
    LaunchedEffect(
        dateEpoch, mood, anxietyEnabled, anxiety, energyEnabled, energy,
        sleepEnabled, sleepMinutes, emotions, symptoms, note, seeded
    ) {
        if (!seeded) return@LaunchedEffect
        if (lastSaved == null || lastSaved == snapshot() || mood == null) return@LaunchedEffect
        delay(700)
        // Сохранение запускается в scope экрана, а не в корутине эффекта:
        // новая правка отменяет задержку, но не уже начатую запись.
        if (!deleted) scope.launch { persist() }
    }

    fun deleteEntry() {
        if (deleting) return
        val id = currentId
        if (id == null) {
            onClose()
            return
        }
        deleting = true
        deleteError = null
        scope.launch {
            // Мутекс не даёт автосохранению, начатому до удаления,
            // записать отметку заново после delete.
            persistMutex.withLock {
                if (deleted) return@withLock
                runCatching { vm.deleteNow(id) }
                    .onSuccess {
                        deleted = true
                        showDelete = false
                        onClose()
                    }
                    .onFailure {
                        deleteError = context.getString(R.string.mood_error_delete)
                    }
            }
            deleting = false
        }
    }

    val datePickerState = key(date) {
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayUtcMillis

                override fun isSelectableYear(year: Int): Boolean =
                    year in (today.year - 5)..today.year
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        dateEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.mood_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.mood_cancel))
                }
            }
        ) {
            DatePicker(datePickerState, showModeToggle = false)
        }
    }

    if (showTimePicker) {
        OporaTimePickerDialog(
            initial = LocalTime.of(timeMinutes / 60, timeMinutes % 60),
            onDismiss = { showTimePicker = false },
            onSelect = {
                timeMinutes = it.hour * 60 + it.minute
                showTimePicker = false
            }
        )
    }

    if (showMoodRequired) {
        ConfirmDialog(
            title = stringResource(R.string.mood_need_mood_title),
            text = stringResource(R.string.mood_need_mood_body),
            confirmLabel = stringResource(R.string.mood_exit_without_saving),
            dismissLabel = stringResource(R.string.mood_choose_mood),
            destructive = true,
            dismissOnConfirm = false,
            onConfirm = {
                showMoodRequired = false
                onClose()
            },
            onDismiss = {
                showMoodRequired = false
                // Возвращаем к шкале: пользователь уже мог уйти вниз к симптомам.
                scope.launch { listState.animateScrollToItem(moodItemIndex) }
            }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.mood_delete_title),
            text = stringResource(
                R.string.mood_delete_body,
                date.dayMonth(locale)
            ),
            confirmLabel = if (deleting) {
                stringResource(R.string.mood_deleting)
            } else {
                stringResource(R.string.action_delete)
            },
            confirmEnabled = !deleting,
            dismissOnConfirm = false,
            errorMessage = deleteError,
            onConfirm = { deleteEntry() },
            onDismiss = {
                if (!deleting) {
                    showDelete = false
                    deleteError = null
                }
            }
        )
    }

    addTagType?.let { type ->
        AddTagDialog(
            title = if (type == TAG_TYPE_EMOTION) {
                stringResource(R.string.mood_custom_emotion)
            } else {
                stringResource(R.string.mood_custom_symptom)
            },
            onDismiss = { addTagType = null },
            onAdd = { label ->
                scope.launch {
                    runCatching { vm.addCustomTag(type, label) }
                        .onSuccess { key ->
                            if (key != null) {
                                if (type == TAG_TYPE_EMOTION) {
                                    emotions = emotions + key
                                } else {
                                    symptoms = symptoms + key
                                }
                            }
                            addTagType = null
                        }
                        .onFailure {
                            addTagType = null
                            error = context.getString(R.string.mood_error_add_tag)
                        }
                }
            }
        )
    }

    if (loading) {
        Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    ImeAwareBox {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = OporaSpacing.screenGutter,
            end = OporaSpacing.screenGutter,
            top = OporaSpacing.screenTop,
            bottom = OporaSpacing.screenBottom
        ),
        verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)
    ) {
        item {
            val largeFont = LocalDensity.current.fontScale >= 1.5f
            ScreenTitle(
                title = when {
                    largeFont -> stringResource(R.string.mood_editor_title_short)
                    entryId != null -> stringResource(R.string.mood_editor_title_edit)
                    else -> stringResource(R.string.mood_editor_title_new)
                },
                onBack = { requestClose() },
                actions = {
                    if (saving) {
                        Text(
                            stringResource(R.string.mood_saving),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else if (saveTick > 0) {
                        Text(
                            stringResource(R.string.mood_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (hasEntry) {
                        IconButton(
                            onClick = { deleteError = null; showDelete = true },
                            enabled = !saving,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.mood_delete_a11y)
                            )
                        }
                    }
                }
            )
        }

        error?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        item {
            DateChip(date = date, onClick = { showDatePicker = true })
        }

        item {
            val time = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(OporaIconSize.inline)
                )
                Spacer(Modifier.width(OporaSpacing.s))
                Text(stringResource(R.string.mood_time_chip, time.hhmm()))
            }
        }

        item {
            MoodScaleCard(
                mood = mood,
                onSelect = { mood = it }
            )
        }

        item {
            BodyMetricsCard(
                anxietyEnabled = anxietyEnabled,
                anxiety = anxiety,
                onAnxietyEnabled = { anxietyEnabled = it },
                onAnxiety = { anxiety = it },
                energyEnabled = energyEnabled,
                energy = energy,
                onEnergyEnabled = { energyEnabled = it },
                onEnergy = { energy = it },
                sleepEnabled = sleepEnabled,
                sleepMinutes = sleepMinutes,
                onSleepEnabled = { sleepEnabled = it },
                onSleep = { sleepMinutes = it }
            )
        }

        item {
            EmotionsCard(
                tags = state.emotionTags,
                selected = emotions,
                onToggle = { key ->
                    emotions = if (key in emotions) emotions - key else emotions + key
                },
                onAddCustom = { addTagType = TAG_TYPE_EMOTION }
            )
        }

        item {
            SymptomsCard(
                tags = state.symptomTags,
                selected = symptoms,
                onToggle = { key ->
                    symptoms = if (key in symptoms) symptoms - key else symptoms + key
                },
                onAddCustom = { addTagType = TAG_TYPE_SYMPTOM }
            )
        }

        item {
            val positives by remember(dateEpoch) { vm.positives(date) }
                .collectAsState(initial = emptyList())
            PositivesCard(
                events = positives.filter { it.kind == POSITIVE_EVENT },
                deeds = positives.filter { it.kind == POSITIVE_DEED },
                onAdd = { text, kind -> vm.addPositive(date, text, kind) },
                onDone = vm::setPositiveDone,
                onDelete = vm::deletePositive
            )
        }

        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.mood_note_label)) },
                placeholder = { Text(stringResource(R.string.mood_note_placeholder)) },
                minLines = 3,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }

        item {
            Button(
                onClick = { requestClose() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (saving) stringResource(R.string.mood_saving)
                    else stringResource(R.string.mood_done),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    }
}


/**
 * Хорошее за день — часть отметки настроения, а не отдельный блок главной.
 * События просто фиксируются, у дел есть галочка «сделано».
 */
@Composable
private fun PositivesCard(
    events: List<PositiveEntity>,
    deeds: List<PositiveEntity>,
    onAdd: (String, String) -> Unit,
    onDone: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var addKind by remember { mutableStateOf<String?>(null) }
    OporaCard {
        Column {
            SectionHeader(stringResource(R.string.mood_positives_title))
            Spacer(Modifier.height(4.dp))
            PositiveGroup(
                title = stringResource(R.string.mood_positives_events),
                emptyText = stringResource(R.string.mood_positives_events_empty),
                entries = events,
                showDone = false,
                onAdd = { addKind = POSITIVE_EVENT },
                onDone = onDone,
                onDelete = onDelete
            )
            Spacer(Modifier.height(OporaSpacing.s))
            PositiveGroup(
                title = stringResource(R.string.mood_positives_deeds),
                emptyText = stringResource(R.string.mood_positives_deeds_empty),
                entries = deeds,
                showDone = true,
                onAdd = { addKind = POSITIVE_DEED },
                onDone = onDone,
                onDelete = onDelete
            )
        }
    }

    addKind?.let { kind ->
        AddPositiveDialog(
            title = if (kind == POSITIVE_EVENT) {
                stringResource(R.string.mood_positive_event_title)
            } else {
                stringResource(R.string.mood_positive_deed_title)
            },
            onDismiss = { addKind = null },
            onAdd = { text ->
                onAdd(text, kind)
                addKind = null
            }
        )
    }
}

@Composable
private fun PositiveGroup(
    title: String,
    emptyText: String,
    entries: List<PositiveEntity>,
    showDone: Boolean,
    onAdd: () -> Unit,
    onDone: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.mood_positive_add_a11y, title)
            )
        }
    }
    if (entries.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    } else {
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = OporaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDone) {
                    Checkbox(
                        checked = entry.done,
                        onCheckedChange = { onDone(entry.id, it) }
                    )
                }
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                    color = if (entry.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onDelete(entry.id) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(
                            R.string.mood_positive_delete_a11y,
                            entry.text
                        ),
                        modifier = Modifier.size(OporaIconSize.inline),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPositiveDialog(
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
            label = { Text(stringResource(R.string.mood_positive_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { hideKeyboard() })
        )
    }
}
