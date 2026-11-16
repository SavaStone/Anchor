package app.opora.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.domain.MedSchedule
import app.opora.domain.discontinuedDateLabel
import app.opora.domain.doseUnitsFor
import app.opora.domain.formatAmount
import app.opora.domain.parseAmount
import app.opora.domain.hhmm
import app.opora.domain.DEFAULT_REFILL_THRESHOLD
import app.opora.domain.weekdayDate
import app.opora.notifications.MedicationAlarms
import app.opora.ui.LocalAppContainer
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.ImeBackHandler
import app.opora.ui.components.ImeAwareBox
import app.opora.ui.components.OporaCard
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.ui.theme.DefaultMedicationColor
import app.opora.ui.theme.MedicationSwatches
import app.opora.ui.theme.medicationOnColor
import app.opora.ui.text.weekdayShort
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

private const val MIN_INTERVAL_DAYS = 1
private const val MAX_INTERVAL_DAYS = 90

private val LocalDateSaver = listSaver<LocalDate, Long>(
    save = { listOf(it.toEpochDay()) },
    restore = { LocalDate.ofEpochDay(it.first()) }
)

internal fun stockValidationError(
    context: Context,
    enabled: Boolean,
    countText: String,
    thresholdText: String
): String? {
    if (!enabled) return null
    val count = parseAmount(countText)
    val threshold = parseAmount(thresholdText)
    if (countText.isBlank() && thresholdText.isBlank()) {
        return context.getString(R.string.stock_error_empty)
    }
    if (countText.isNotBlank() && count == null) {
        return context.getString(R.string.stock_error_count_number)
    }
    if (thresholdText.isNotBlank() && threshold == null) {
        return context.getString(R.string.stock_error_threshold_number)
    }
    if (threshold != null && count == null) {
        return context.getString(R.string.stock_error_threshold_first)
    }
    if (count != null && threshold != null && threshold > count) {
        return context.getString(R.string.stock_error_threshold_above)
    }
    return null
}

/** Ввод остатка: цифры и один десятичный разделитель, не больше двух знаков после запятой. */
private fun amountInput(value: String): String {
    val filtered = value.replace('.', ',').filter { it.isDigit() || it == ',' }
    val parts = filtered.split(',')
    return if (parts.size == 1) parts[0].take(5)
    else parts[0].take(5) + "," + parts[1].take(2)
}

private val FORMS = listOf(
    "tablet" to R.string.med_editor_form_tablet,
    "capsule" to R.string.med_editor_form_capsule,
    "drops" to R.string.med_editor_form_drops,
    "syrup" to R.string.med_editor_form_syrup,
    "injection" to R.string.med_editor_form_injection,
    "spray" to R.string.med_editor_form_spray,
    "patch" to R.string.med_editor_form_patch,
    "other" to R.string.med_editor_form_other
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedEditorScreen(
    medId: String?,
    onClose: () -> Unit,
    repository: OporaRepository = LocalAppContainer.current.repository,
    db: OporaDatabase = LocalAppContainer.current.db
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(medId != null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var existing by remember { mutableStateOf<MedicationEntity?>(null) }
    val medEntityId = rememberSaveable(medId) { medId ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf("") }
    var dosage by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf("tablet") }
    var color by rememberSaveable { mutableStateOf(DefaultMedicationColor) }
    var purpose by rememberSaveable { mutableStateOf("") }
    var prescriber by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var scheduleType by rememberSaveable { mutableStateOf("daily") }
    val times = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf("09:00") }
    var weekdays by rememberSaveable(
        stateSaver = listSaver<Set<Int>, Int>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(setOf(1, 3, 5)) }
    var intervalDays by rememberSaveable { mutableStateOf(2) }
    var startDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(LocalDate.now())
    }
    var hasEndDate by rememberSaveable { mutableStateOf(false) }
    var endDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(LocalDate.now().plusMonths(1))
    }
    var inventoryEnabled by rememberSaveable { mutableStateOf(false) }
    var inventoryCount by rememberSaveable { mutableStateOf("30") }
    var inventoryTouched by rememberSaveable { mutableStateOf(false) }
    var refillThreshold by rememberSaveable {
        mutableStateOf(formatAmount(DEFAULT_REFILL_THRESHOLD, locale))
    }
    var active by rememberSaveable { mutableStateOf(true) }
    // Трогал ли пользователь тумблер: после возврата из другой вкладки
    // сохранённое значение не должно перебивать реальное состояние записи.
    var activeTouched by rememberSaveable { mutableStateOf(false) }
    var formExpanded by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var seededFor by rememberSaveable { mutableStateOf("") }
    var baseline by rememberSaveable { mutableStateOf("") }

    fun snapshot(): String = listOf(
        name, dosage, form, color.toString(), purpose, prescriber, notes,
        scheduleType, times.joinToString(","), weekdays.sorted().joinToString(","),
        intervalDays.toString(), startDate.toEpochDay().toString(),
        hasEndDate.toString(), endDate.toEpochDay().toString(),
        inventoryEnabled.toString(), inventoryCount, refillThreshold,
        active.toString()
    ).joinToString("|")

    LaunchedEffect(medId, loadAttempt) {
        val key = medId ?: "new"
        loadFailed = false
        if (medId != null) {
            loading = true
            val loaded = runCatching { repository.medicationOnce(medId) }
                .getOrElse {
                    loadFailed = true
                    loading = false
                    return@LaunchedEffect
                }
            if (loaded == null) {
                // Запись удалили или база недоступна: пустая форма выглядела бы
                // как новое лекарство с чужим id.
                loadFailed = true
                loading = false
                return@LaunchedEffect
            }
            existing = loaded
            if (seededFor != key) {
                name = loaded.name
                dosage = loaded.dosage
                form = loaded.form
                // Не подменяем сохранённый цвет ближайшим из палитры: иначе
                // обычное сохранение перекрашивало бы старую запись.
                color = loaded.color
                purpose = loaded.purpose
                prescriber = loaded.prescriber
                notes = loaded.notes
                scheduleType = loaded.scheduleType
                times.clear()
                times.addAll(MedSchedule.parseTimes(loaded).map { it.hhmm() })
                weekdays = MedSchedule.parseWeekdays(loaded).toSet()
                intervalDays = loaded.intervalDays.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
                startDate = LocalDate.ofEpochDay(loaded.startDate)
                hasEndDate = loaded.endDate != null
                endDate = loaded.endDate?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusMonths(1)
                inventoryEnabled = loaded.inventoryCount != null
                inventoryCount = loaded.inventoryCount?.let { formatAmount(it, locale) } ?: "30"
                inventoryTouched = false
                refillThreshold = loaded.refillThreshold?.let { formatAmount(it, locale) }
                    ?: formatAmount(DEFAULT_REFILL_THRESHOLD, locale)
                active = loaded.active
                activeTouched = false
            } else if (!activeTouched) {
                // Лекарство прекратили, пока редактор был в фоне: показываем
                // фактическое состояние, иначе «Сохранить» тихо вернёт приём.
                active = loaded.active
            }
        }
        if (seededFor != key) seededFor = key
        if (baseline.isEmpty()) baseline = snapshot()
        loading = false
    }

    val dirty = baseline.isNotEmpty() && snapshot() != baseline

    fun requestClose() {
        if (loading || saving) return
        if (dirty) showDiscard = true else onClose()
    }

    ImeBackHandler { requestClose() }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.med_editor_delete_title),
            text = stringResource(R.string.med_editor_delete_text),
            confirmLabel = if (deleting) stringResource(R.string.med_editor_deleting)
            else stringResource(R.string.action_delete),
            confirmEnabled = !deleting,
            dismissOnConfirm = false,
            errorMessage = deleteError,
            onConfirm = {
                if (deleting) return@ConfirmDialog
                val target = existing ?: return@ConfirmDialog
                deleting = true
                deleteError = null
                scope.launch {
                    runCatching {
                        MedicationAlarms(context, db).cancelMedication(target.id)
                        repository.deleteMedication(target.id)
                    }.onSuccess {
                        deleting = false
                        showDelete = false
                        onClose()
                    }.onFailure { failure ->
                        deleting = false
                        deleteError = context.getString(R.string.med_editor_delete_error)
                    }
                }
            },
            onDismiss = {
                if (!deleting) {
                    showDelete = false
                    deleteError = null
                }
            }
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.med_editor_discard_title),
            text = stringResource(R.string.med_editor_discard_text),
            confirmLabel = stringResource(R.string.med_editor_discard_confirm),
            dismissLabel = stringResource(R.string.med_editor_discard_stay),
            destructive = false,
            onConfirm = {
                showDiscard = false
                onClose()
            },
            onDismiss = { showDiscard = false }
        )
    }

    if (showStartPicker) {
        DatePickerSheet(
            initial = startDate,
            onDismiss = { showStartPicker = false },
            onPick = {
                startDate = it
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        DatePickerSheet(
            initial = endDate,
            onDismiss = { showEndPicker = false },
            onPick = {
                endDate = it
                showEndPicker = false
            }
        )
    }

    if (loading) {
        Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    if (loadFailed) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(OporaSpacing.screenGutter),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.med_editor_load_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(OporaSpacing.s))
            Text(
                stringResource(R.string.med_editor_load_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(OporaSpacing.l))
            Button(onClick = { loadAttempt++ }) {
                Text(stringResource(R.string.meds_retry))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.meds_back)) }
        }
        return
    }

    val scheduleValid = !hasEndDate || !endDate.isBefore(startDate)
    val stockIssue = stockValidationError(context, inventoryEnabled, inventoryCount, refillThreshold)
    val canSave = name.isNotBlank() &&
        (scheduleType == "as_needed" || times.isNotEmpty()) &&
        (scheduleType != "weekly" || weekdays.isNotEmpty()) &&
        scheduleValid &&
        stockIssue == null

    val largeFont = LocalDensity.current.fontScale >= 1.5f
    ImeAwareBox {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = OporaSpacing.screenGutter,
            end = OporaSpacing.screenGutter,
            top = OporaSpacing.screenTop,
            bottom = OporaSpacing.editorBottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)
    ) {
        item {
            ScreenTitle(
                title = when {
                    largeFont -> stringResource(R.string.med_editor_title_short)
                    medId == null -> stringResource(R.string.med_editor_title_new)
                    else -> stringResource(R.string.med_editor_title_edit)
                },
                subtitle = when {
                    largeFont && medId == null -> stringResource(R.string.med_editor_subtitle_new_short)
                    largeFont -> stringResource(R.string.med_editor_subtitle_schedule_short)
                    else -> stringResource(R.string.med_editor_subtitle)
                },
                onBack = { requestClose() },
                actions = {
                    if (medId != null) {
                        IconButton(
                            onClick = { showDelete = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.med_editor_delete_a11y)
                            )
                        }
                    }
                }
            )
        }

        item {
            OporaCard {
                Column(verticalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
                    SectionHeader(stringResource(R.string.med_editor_section_main))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text(stringResource(R.string.med_editor_name_label)) },
                        placeholder = { Text(stringResource(R.string.med_editor_name_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text(stringResource(R.string.med_editor_dosage_label)) },
                        placeholder = { Text(stringResource(R.string.med_editor_dosage_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenuBox(
                        expanded = formExpanded,
                        onExpandedChange = { formExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = FORMS.firstOrNull { it.first == form }?.second
                                ?.let { stringResource(it) }
                                ?: stringResource(R.string.med_editor_form_other),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.med_editor_form_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(formExpanded)
                            },
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = formExpanded,
                            onDismissRequest = { formExpanded = false }
                        ) {
                            FORMS.forEach { (key, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = { form = key; formExpanded = false }
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.med_editor_color),
                        style = MaterialTheme.typography.labelLarge
                    )
                    val swatches = MedicationSwatches
                    Column(verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)) {
                        swatches.chunked(5).forEach { rowSwatches ->
                            Row(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.xs)) {
                                rowSwatches.forEach { swatch ->
                                    val selected = color == swatch.value
                                    val swatchLabel = stringResource(swatch.labelRes)
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .selectable(
                                                selected = selected,
                                                role = Role.RadioButton,
                                                onClick = { color = swatch.value }
                                            )
                                            .semantics { contentDescription = swatchLabel },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(swatch.value))
                                                .border(
                                                    width = if (selected) 3.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = medicationOnColor(swatch.value),
                                                    modifier = Modifier.size(OporaIconSize.inline)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(OporaSpacing.s))
                    OutlinedTextField(
                        value = purpose,
                        onValueChange = { purpose = it },
                        label = { Text(stringResource(R.string.med_editor_purpose_label)) },
                        placeholder = { Text(stringResource(R.string.med_editor_purpose_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = prescriber,
                        onValueChange = { prescriber = it },
                        label = { Text(stringResource(R.string.med_editor_prescriber_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            OporaCard {
                Column {
                    SectionHeader(stringResource(R.string.med_editor_section_schedule))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
                        listOf(
                            "daily" to R.string.meds_schedule_daily,
                            "weekly" to R.string.schedule_weekdays,
                            "interval" to R.string.schedule_interval_days,
                            "as_needed" to R.string.meds_schedule_as_needed
                        ).forEach { (key, labelRes) ->
                            FilterChip(
                                selected = scheduleType == key,
                                onClick = { scheduleType = key },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(OporaSpacing.s))

                    if (scheduleType == "weekly") {
                        Text(
                            stringResource(R.string.schedule_weekdays_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..7).forEach { day ->
                                FilterChip(
                                    selected = day in weekdays,
                                    onClick = {
                                        weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                    },
                                    label = {
                                        Text(weekdayShort(DayOfWeek.of(day), context))
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (scheduleType == "interval") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.schedule_interval),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    intervalDays = (intervalDays - 1).coerceAtLeast(MIN_INTERVAL_DAYS)
                                },
                                enabled = intervalDays > MIN_INTERVAL_DAYS
                            ) {
                                Icon(
                                    Icons.Outlined.Remove,
                                    contentDescription = stringResource(R.string.schedule_interval_dec)
                                )
                            }
                            Text(
                                pluralStringResource(
                                    R.plurals.duration_days,
                                    intervalDays,
                                    intervalDays
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = {
                                    intervalDays = (intervalDays + 1).coerceAtMost(MAX_INTERVAL_DAYS)
                                },
                                enabled = intervalDays < MAX_INTERVAL_DAYS
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.schedule_interval_inc)
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.schedule_interval_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (scheduleType != "as_needed") {
                        Spacer(Modifier.height(OporaSpacing.s))
                        Text(
                            stringResource(R.string.schedule_times_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(OporaSpacing.s))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s),
                            verticalArrangement = Arrangement.spacedBy(OporaSpacing.s)
                        ) {
                            times.forEach { time ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(OporaIconSize.compact)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(time, style = MaterialTheme.typography.labelLarge)
                                        IconButton(
                                            onClick = { times.remove(time) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = stringResource(
                                                    R.string.schedule_remove_time,
                                                    time
                                                ),
                                                modifier = Modifier.size(OporaIconSize.compact)
                                            )
                                        }
                                    }
                                }
                            }
                            AssistChip(
                                onClick = {
                                    val (h, m) = times.lastOrNull()?.split(":")
                                        ?.let { (it.getOrNull(0)?.toIntOrNull() ?: 9) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
                                        ?: (9 to 0)
                                    TimePickerDialog(context, { _, hour, minute ->
                                        val value = LocalTime.of(hour, minute).hhmm()
                                        if (value !in times) times.add(value)
                                        times.sort()
                                    }, h, m, true).show()
                                },
                                label = { Text(stringResource(R.string.schedule_add_time)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(OporaIconSize.inline)
                                    )
                                }
                            )
                        }
                        if (times.isEmpty()) {
                            Text(
                                stringResource(R.string.schedule_times_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.schedule_prn_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    DateRow(
                        label = stringResource(R.string.schedule_start),
                        date = startDate,
                        onClick = { showStartPicker = true }
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = hasEndDate,
                                role = Role.Switch,
                                onValueChange = { hasEndDate = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.schedule_has_end),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = hasEndDate, onCheckedChange = null)
                    }
                    if (hasEndDate) {
                        DateRow(
                            label = stringResource(R.string.schedule_end),
                            date = endDate,
                            onClick = { showEndPicker = true }
                        )
                        if (!scheduleValid) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.schedule_end_before_start),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            OporaCard {
                Column {
                    SectionHeader(
                        stringResource(R.string.stock_section),
                        subtitle = stringResource(R.string.stock_optional)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = inventoryEnabled,
                                role = Role.Switch,
                                onValueChange = {
                                    inventoryEnabled = it
                                    inventoryTouched = true
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.stock_track),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = inventoryEnabled, onCheckedChange = null)
                    }
                    if (inventoryEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
                            OutlinedTextField(
                                value = inventoryCount,
                                onValueChange = {
                                    inventoryCount = amountInput(it)
                                    inventoryTouched = true
                                },
                                label = { Text(stringResource(R.string.stock_remaining)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = refillThreshold,
                                onValueChange = { refillThreshold = amountInput(it) },
                                label = { Text(stringResource(R.string.stock_threshold)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (stockIssue != null) {
                            Text(
                                stockIssue,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                stringResource(
                                    R.string.stock_dose_hint,
                                    formatAmount(doseUnitsFor(dosage), locale),
                                    formUnit(context, form)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            OporaCard {
                Column {
                    SectionHeader(stringResource(R.string.med_editor_notes_title))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text(stringResource(R.string.med_editor_notes_hint)) },
                        minLines = 3,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    context.getString(R.string.med_editor_notes_a11y)
                            }
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(Modifier.padding(vertical = OporaSpacing.s))
                    val stoppedAt = existing?.discontinuedAt
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = active,
                                role = Role.Switch,
                                onValueChange = {
                                    activeTouched = true
                                    active = it
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    !active && stoppedAt != null ->
                                        stringResource(R.string.med_editor_status_stopped)
                                    !active -> stringResource(R.string.med_editor_status_paused)
                                    else -> stringResource(R.string.med_editor_status_active)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val courseOver = active && hasEndDate && endDate.isBefore(LocalDate.now())
                            Text(
                                when {
                                    active && courseOver -> stringResource(
                                        R.string.med_editor_course_over,
                                        endDate.weekdayDate(locale)
                                    )
                                    active -> stringResource(R.string.med_editor_reminders_on)
                                    stoppedAt != null -> stringResource(
                                        R.string.med_editor_stopped_body,
                                        discontinuedDateLabel(stoppedAt, locale = locale)
                                    )
                                    else -> stringResource(R.string.med_editor_reminders_off)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = active, onCheckedChange = null)
                    }
                }
            }
        }

    }

    // Сохранение всегда под рукой: форма длинная, а «Сохранить» в конце
    // списка не видно на первом экране.
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(horizontal = OporaSpacing.l, vertical = OporaSpacing.m)) {
            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(OporaSpacing.s))
            }
            Button(
                onClick = {
                    if (saving) return@Button
                    if (!canSave) {
                        error = when {
                            name.isBlank() -> context.getString(R.string.med_editor_error_name)
                            scheduleType != "as_needed" && times.isEmpty() ->
                                context.getString(R.string.med_editor_error_time)
                            scheduleType == "weekly" && weekdays.isEmpty() ->
                                context.getString(R.string.med_editor_error_weekday)
                            !scheduleValid -> context.getString(R.string.med_editor_error_dates)
                            stockIssue != null -> stockIssue
                            else -> context.getString(R.string.med_editor_error_check)
                        }
                        return@Button
                    }
                    error = null
                    saving = true
                    val now = System.currentTimeMillis()
                    // Сохраняем поверх загруженной записи: поля, которых нет
                    // в форме (дата и причина прекращения), не должны теряться
                    // при обычном редактировании заметки.
                    val med = (existing ?: MedicationEntity(
                        id = medEntityId,
                        name = name.trim(),
                        startDate = startDate.toEpochDay()
                    )).copy(
                        id = medEntityId,
                        name = name.trim(),
                        dosage = dosage.trim(),
                        form = form,
                        color = color,
                        purpose = purpose.trim(),
                        prescriber = prescriber.trim(),
                        notes = notes.trim(),
                        scheduleType = scheduleType,
                        timesCsv = times.sorted().joinToString(","),
                        weekdaysCsv = weekdays.sorted().joinToString(","),
                        intervalDays = intervalDays.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS),
                        startDate = startDate.toEpochDay(),
                        endDate = if (hasEndDate) endDate.toEpochDay() else null,
                        inventoryCount = if (inventoryEnabled) parseAmount(inventoryCount) else null,
                        refillThreshold = if (inventoryEnabled) parseAmount(refillThreshold) else null,
                        active = active,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                    scope.launch {
                        runCatching {
                            repository.saveMedication(
                                med,
                                expectedInventory = existing?.inventoryCount,
                                inventoryEdited = inventoryTouched
                            )
                        }
                            .onSuccess {
                                saving = false
                                onClose()
                            }
                            .onFailure { failure ->
                                saving = false
                                error = context.getString(R.string.med_editor_save_error)
                            }
                    }
                },
                // Ошибку объясняем по нажатию: выключенная кнопка молчит,
                // а подсказки уже написаны в обработчике ниже.
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (saving) stringResource(R.string.med_editor_saving)
                    else stringResource(R.string.med_editor_save),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    }
}

@Composable
private fun DateRow(label: String, date: LocalDate, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = OporaSpacing.l, vertical = OporaSpacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(OporaIconSize.inline))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(date.weekdayDate(locale), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.action_choose)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.med_editor_cancel)) }
        }
    ) {
        DatePicker(state, showModeToggle = false)
    }
}
