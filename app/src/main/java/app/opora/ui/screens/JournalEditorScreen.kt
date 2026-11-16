@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.opora.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import app.opora.R
import app.opora.data.db.JournalEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.journalDate
import app.opora.domain.hhmm
import app.opora.domain.moodLevel
import app.opora.domain.parseKeys
import app.opora.ui.text.formatSleep
import app.opora.ui.JournalViewModel
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.ImeBackHandler
import app.opora.ui.components.DateChip
import app.opora.ui.components.Divider
import app.opora.ui.components.FormDialog
import app.opora.ui.components.rememberKeyboardHider
import app.opora.ui.components.LoadingState
import app.opora.ui.components.formDialogField
import app.opora.ui.components.MoodFace
import app.opora.ui.components.OporaCard
import app.opora.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Дневник — это текст, а не второй редактор настроения.
 *
 * Структурированные данные (уровень, тревога, энергия, сон, эмоции, симптомы)
 * остаются в «Настроении»: там их удобно отмечать и сравнивать. Здесь — мысли,
 * события и метки; состояние дня показывается только как контекст со ссылкой
 * в редактор отметки. Так одна и та же отметка не создаётся и не правится
 * в двух местах.
 */
@Composable
fun JournalEditorScreen(
    entryId: String?,
    vm: JournalViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMood: (LocalDate, Long?) -> Unit = { _, _ -> }
) {
    var currentId by rememberSaveable(entryId) { mutableStateOf(entryId) }
    var title by rememberSaveable(entryId) { mutableStateOf("") }
    var body by rememberSaveable(entryId) { mutableStateOf("") }
    var tagsText by rememberSaveable(entryId) { mutableStateOf("") }
    var pinned by rememberSaveable(entryId) { mutableStateOf(false) }
    var entryDate by rememberSaveable(entryId) { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }
    var createdAt by rememberSaveable(entryId) { mutableStateOf(System.currentTimeMillis()) }
    var loaded by rememberSaveable(entryId) { mutableStateOf(entryId == null) }
    var seeded by rememberSaveable(entryId) { mutableStateOf(false) }
    var lastSaved by rememberSaveable(entryId) { mutableStateOf<String?>(null) }
    var saveTick by rememberSaveable(entryId) { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    val hideKeyboard = rememberKeyboardHider()
    var deleting by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val persistMutex = remember { Mutex() }
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    fun snapshot(): String = "$entryDate|$title|$body|$tagsText|$pinned"

    // Закрепление само по себе — не содержание: пустая заметка не должна
    // создаваться только из-за нажатого пина.
    fun formEmpty(): Boolean =
        title.isBlank() && body.isBlank() && tagsText.isBlank()

    fun nothingToSave(): Boolean = currentId == null && formEmpty()

    LaunchedEffect(entryId) {
        if (seeded) {
            loaded = true
            return@LaunchedEffect
        }
        if (entryId != null) {
            runCatching { vm.load(entryId) }
                .onSuccess { entry ->
                    if (entry != null) {
                        title = entry.title
                        body = entry.body
                        tagsText = parseKeys(entry.tags).joinToString(", ")
                        entryDate = entry.entryDate ?: entry.journalDate().toEpochDay()
                        pinned = entry.pinned
                        createdAt = entry.createdAt
                    }
                }
                .onFailure {
                    error = context.getString(R.string.journal_error_load)
                }
        }
        loaded = true
        seeded = true
        lastSaved = snapshot()
    }

    suspend fun persist(): Boolean = persistMutex.withLock {
        if (deleted) return@withLock true
        // Новую запись очистили до пустого состояния: пустая карточка в ленте
        // не нужна. Существующую запись так не удаляем — её мог открыть
        // пользователь осознанно, и с ней может быть связана старая отметка.
        if (entryId == null && formEmpty()) {
            val id = currentId
            if (id != null) {
                val removed = runCatching { vm.deleteNow(id) }
                    .onFailure {
                        error = context.getString(R.string.journal_error_remove_empty)
                    }
                    .isSuccess
                if (!removed) return@withLock false
                currentId = null
            }
            lastSaved = snapshot()
            return@withLock true
        }
        if (lastSaved == snapshot() || nothingToSave()) return@withLock true
        var ok = true
        do {
            val snap = snapshot()
            val entry = JournalEntity(
                id = currentId.orEmpty(),
                title = title,
                body = body,
                tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    .joinToString(","),
                entryDate = entryDate,
                pinned = pinned,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis()
            )
            runCatching { vm.save(entry) }
                .onSuccess { saved ->
                    currentId = saved.id
                    error = null
                }
                .onFailure {
                    error = context.getString(R.string.journal_error_save)
                    ok = false
                }
            if (ok) {
                lastSaved = snap
                saveTick++
            }
        } while (ok && snapshot() != lastSaved)
        ok
    }

    fun requestClose() {
        if (closing) return
        closing = true
        if (deleted) {
            onClose()
            return
        }
        scope.launch {
            if (!loaded) {
                onClose()
                return@launch
            }
            if (nothingToSave() || lastSaved == snapshot()) {
                onClose()
                return@launch
            }
            saving = true
            // persist() ждёт автосохранение через Mutex: «Назад» во время
            // записи не игнорируется и не закрывает экран дважды.
            val ok = persist()
            saving = false
            if (ok) onClose() else closing = false
        }
    }

    ImeBackHandler { requestClose() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && loaded && seeded && !deleted &&
                lastSaved != snapshot()
            ) {
                scope.launch { persist() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(title, body, tagsText, pinned, entryDate, loaded) {
        if (!loaded) return@LaunchedEffect
        val snap = snapshot()
        if (lastSaved == null) {
            lastSaved = snap
            return@LaunchedEffect
        }
        if (snap == lastSaved) return@LaunchedEffect
        delay(900)
        if (deleted) return@LaunchedEffect
        scope.launch { persist() }
    }

    fun deleteEntry() {
        val id = currentId ?: return
        if (deleting) return
        deleting = true
        deleteError = null
        scope.launch {
            persistMutex.withLock {
                if (deleted) return@withLock
                runCatching { vm.deleteNow(id) }
                    .onSuccess {
                        deleted = true
                        showDelete = false
                        onClose()
                    }
                    .onFailure {
                        deleteError = context.getString(R.string.journal_error_delete)
                    }
            }
            deleting = false
        }
    }

    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()

    fun updateTags(items: Collection<String>) {
        tagsText = items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            .joinToString(", ")
    }

    if (!loaded) {
        LoadingState(modifier.fillMaxWidth().padding(top = 120.dp))
        return
    }

    val entryDay = LocalDate.ofEpochDay(entryDate)
    val dayMoods = state.moodsByDate[entryDate].orEmpty()

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = OporaSpacing.screenGutter,
                end = OporaSpacing.screenGutter,
                top = OporaSpacing.screenTop,
                bottom = OporaSpacing.screenBottom
            )
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { requestClose() }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.journal_back)
                )
            }
            Text(
                when {
                    saving -> stringResource(R.string.journal_saving)
                    saveTick > 0 -> stringResource(R.string.journal_saved)
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { pinned = !pinned }) {
                Icon(
                    if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) {
                        stringResource(R.string.journal_unpin)
                    } else {
                        stringResource(R.string.journal_pin)
                    },
                    tint = if (pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (currentId != null) {
                IconButton(onClick = {
                    deleteError = null
                    showDelete = true
                }) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.journal_delete_a11y),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = { requestClose() }) {
                Text(stringResource(R.string.journal_done))
            }
        }

        error?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = OporaSpacing.xs, vertical = OporaSpacing.xs)
            )
        }

        val titleA11y = stringResource(R.string.journal_title_field_a11y)
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { contentDescription = titleA11y },
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (title.isEmpty()) {
                        Text(
                            stringResource(R.string.journal_title_placeholder),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        DateChip(
            date = entryDay,
            onClick = { showDatePicker = true }
        )

        Spacer(Modifier.height(16.dp))
        val bodyA11y = stringResource(R.string.journal_body_field_a11y)
        BasicTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .semantics { contentDescription = bodyA11y },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (body.isEmpty()) {
                        Text(
                            stringResource(R.string.journal_body_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.journal_tags_title))
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEach { tag ->
                val removeLabel = stringResource(R.string.journal_tag_remove_a11y, tag)
                InputChip(
                    selected = true,
                    onClick = { updateTags(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(OporaIconSize.compact)
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = removeLabel
                    }
                )
            }
            AssistChip(
                onClick = {
                    tagDraft = ""
                    showTagDialog = true
                },
                label = { Text(stringResource(R.string.journal_add_tag)) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(OporaIconSize.inline)
                    )
                }
            )
        }

        Spacer(Modifier.height(OporaSpacing.listGap))
        DayStateCard(
            date = entryDay,
            entries = dayMoods,
            labels = state.tagLabels,
            onAdd = { onOpenMood(entryDay, null) },
            onEdit = { onOpenMood(entryDay, it) }
        )
    }

    if (showTagDialog) {
        FormDialog(
            title = stringResource(R.string.journal_add_tag),
            onDismissRequest = { showTagDialog = false },
            confirmLabel = stringResource(R.string.journal_add),
            confirmEnabled = tagDraft.isNotBlank(),
            dismissLabel = stringResource(R.string.journal_cancel),
            onConfirm = {
                val additions = tagDraft.split(",").map { it.trim() }
                    .filter { it.isNotBlank() }
                if (additions.isNotEmpty()) updateTags(tags + additions)
                showTagDialog = false
            }
        ) {
            OutlinedTextField(
                value = tagDraft,
                onValueChange = { tagDraft = it },
                modifier = Modifier.fillMaxWidth().formDialogField(),
                label = { Text(stringResource(R.string.journal_tag_label)) },
                placeholder = { Text(stringResource(R.string.journal_tag_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { hideKeyboard() })
            )
        }
    }

    if (showDatePicker) {
        val pickerState = key(entryDay) {
            rememberDatePickerState(
                initialSelectedDateMillis = entryDay
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        entryDate = Instant.ofEpochMilli(it)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.journal_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.journal_cancel))
                }
            }
        ) {
            DatePicker(pickerState, showModeToggle = false)
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.journal_delete_title),
            text = stringResource(R.string.journal_delete_body),
            confirmLabel = if (deleting) {
                stringResource(R.string.journal_deleting)
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
}

/**
 * Состояние дня только для чтения. Дневник не создаёт и не меняет отметку:
 * переход ведёт в единственный редактор настроения.
 */
@Composable
private fun DayStateCard(
    date: LocalDate,
    entries: List<MoodEntity>,
    labels: Map<String, String>,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    OporaCard {
        Column {
            val canEdit = !date.isAfter(LocalDate.now())
            val context = LocalContext.current
            val resolvedLabels = remember(labels, context) { resolvedTagLabels(context, labels) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    stringResource(R.string.journal_day_state),
                    subtitle = stringResource(R.string.journal_day_state_hint),
                    modifier = Modifier.weight(1f)
                )
                if (entries.isEmpty() && canEdit) {
                    FilledTonalButton(onClick = onAdd) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(OporaIconSize.inline)
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(stringResource(R.string.mood_mark_action))
                    }
                }
            }
            if (entries.isEmpty()) {
                Spacer(Modifier.height(OporaSpacing.s))
                Text(
                    if (canEdit) {
                        stringResource(R.string.journal_day_state_empty)
                    } else {
                        stringResource(R.string.journal_day_state_future)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .then(
                                if (canEdit) Modifier.clickable { onEdit(entry.id) }
                                else Modifier
                            )
                            .padding(vertical = OporaSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MoodFace(
                            level = entry.mood,
                            selected = true,
                            size = 40.dp
                        )
                        Spacer(Modifier.size(OporaSpacing.s))
                        Column(Modifier.weight(1f)) {
                            val moodLabel = stringResource(moodLevel(entry.mood).labelRes)
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
                                entry.anxiety?.let {
                                    context.getString(R.string.mood_metric_anxiety_value, it)
                                },
                                entry.energy?.let {
                                    context.getString(R.string.mood_metric_energy_value, it)
                                },
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
                                    emotions.joinToString(", ") { resolvedLabels[it] ?: it },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (canEdit) {
                    TextButton(onClick = onAdd) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(OporaIconSize.inline)
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(stringResource(R.string.journal_add_mark))
                    }
                }
            }
        }
    }
}
