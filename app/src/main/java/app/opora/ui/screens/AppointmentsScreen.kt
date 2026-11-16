@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.opora.ui.screens

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.opora.ui.theme.OporaIconSize
import app.opora.data.db.AppointmentEntity
import app.opora.ui.AppointmentsViewModel
import app.opora.ui.components.ConfirmDialog
import app.opora.R
import app.opora.domain.CalendarEvent
import app.opora.domain.toCalendarEvent
import app.opora.domain.calendarEvent
import app.opora.ui.copyCalendarEvent
import app.opora.ui.openCalendarInsert
import app.opora.ui.components.EmptyState
import app.opora.ui.components.FormDialog
import app.opora.ui.components.rememberKeyboardHider
import app.opora.ui.components.formDialogField
import app.opora.ui.components.LoadingState
import app.opora.ui.components.OporaCard
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.Pill
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.domain.dayMonth
import app.opora.domain.dayMonthShort
import app.opora.domain.reminderIsScheduled
import app.opora.domain.hhmm
import app.opora.ui.text.formatDuration
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun AppointmentsScreen(
    vm: AppointmentsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appointmentsState by vm.appointments.collectAsState(initial = null)
    if (appointmentsState == null) {
        Box(Modifier.fillMaxSize().padding(top = 120.dp), contentAlignment = Alignment.Center) {
            LoadingState()
        }
        return
    }
    val appointments = appointmentsState!!
    val now = System.currentTimeMillis()
    val upcoming = appointments.filter { !it.done && it.startsAt + it.durationMinutes * 60_000L >= now }
        .sortedBy { it.startsAt }
    val past = appointments.filter { it.done || it.startsAt + it.durationMinutes * 60_000L < now }
        .sortedByDescending { it.startsAt }

    var editor by remember { mutableStateOf<AppointmentEntity?>(null) }
    var editorId by rememberSaveable { mutableStateOf("") }
    var editorCreatedAt by rememberSaveable { mutableStateOf(0L) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AppointmentEntity?>(null) }
    var calendarFallback by remember { mutableStateOf<CalendarEvent?>(null) }
    val screenContext = LocalContext.current

    fun openCalendar(event: CalendarEvent) {
        if (!screenContext.openCalendarInsert(event)) calendarFallback = event
    }

    fun openEditor(appointment: AppointmentEntity?) {
        editor = appointment
        editorId = appointment?.id.orEmpty()
        editorCreatedAt = appointment?.createdAt ?: 0L
        showEditor = true
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = OporaSpacing.screenGutter,
            end = OporaSpacing.screenGutter,
            top = OporaSpacing.screenTop,
            bottom = OporaSpacing.screenBottom
        ),
        verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)
    ) {
        item {
            ScreenTitle(
                title = stringResource(R.string.appointments_title),
                onBack = onBack,
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            openEditor(null)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.appointments_add)
                        )
                    }
                }
            )
        }
        if (appointments.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.appointments_empty_title),
                    body = stringResource(R.string.appointments_empty_body),
                    actionLabel = stringResource(R.string.appointments_add),
                    onAction = {
                        openEditor(null)
                    }
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.appointments_upcoming),
                    subtitle = pluralStringResource(
                        R.plurals.appointments_count,
                        upcoming.size,
                        upcoming.size
                    )
                )
            }
            items(upcoming, key = { it.id }) { appointment ->
                AppointmentCard(
                    appointment = appointment,
                    onClick = {
                        openEditor(appointment)
                    },
                    onDelete = { deleteTarget = appointment },
                    onAddToCalendar = { openCalendar(appointment.toCalendarEvent()) }
                )
            }
        }
        if (past.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.appointments_past)) }
            items(past, key = { it.id }) { appointment ->
                AppointmentCard(
                    appointment = appointment,
                    past = true,
                    onClick = {
                        openEditor(appointment)
                    },
                    onDelete = { deleteTarget = appointment },
                    onAddToCalendar = { openCalendar(appointment.toCalendarEvent()) }
                )
            }
        }
    }

    if (showEditor) {
        AppointmentEditorDialog(
            initial = editor ?: editorId.takeIf { it.isNotBlank() }
                ?.let { id -> appointments.firstOrNull { it.id == id } },
            editingId = editorId.takeIf { it.isNotBlank() },
            editingCreatedAt = editorCreatedAt.takeIf { it > 0L },
            onDismiss = {
                showEditor = false
                editor = null
                editorId = ""
                editorCreatedAt = 0L
            },
            onSave = { entry ->
                vm.save(entry)
                Unit
            },
            onAddToCalendar = ::openCalendar
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.appointments_delete_title),
            text = stringResource(R.string.appointments_delete_body, target.title),
            onConfirm = { vm.delete(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }

    calendarFallback?.let { event ->
        ConfirmDialog(
            title = stringResource(R.string.appointments_calendar_missing_title),
            text = stringResource(R.string.appointments_calendar_missing_body),
            confirmLabel = stringResource(R.string.appointments_calendar_copy),
            dismissLabel = stringResource(R.string.action_close),
            destructive = false,
            onConfirm = {
                screenContext.copyCalendarEvent(event)
                Toast.makeText(
                    screenContext,
                    R.string.appointments_calendar_copied,
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { calendarFallback = null }
        )
    }
}

@Composable
private fun AppointmentCard(
    appointment: AppointmentEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAddToCalendar: () -> Unit,
    past: Boolean = false
) {
    val date = Instant.ofEpochMilli(appointment.startsAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    // «ноя» / «Nov»: the day number is shown separately in the same tile.
    val monthLabel = date.toLocalDate().dayMonthShort(locale)
        .replace(date.dayOfMonth.toString(), "")
        .trim()
    OporaCard(
        color = if (past) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(56.dp)
                    .heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        if (past) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    appointment.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val subtitle = listOfNotNull(
                    date.toLocalTime().hhmm(),
                    appointment.doctor.takeIf { it.isNotBlank() },
                    appointment.place.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(OporaSpacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
                    // Прошедшее время — не подтверждённый визит: «Завершён»
                    // ставим только когда его действительно отметили.
                    if (appointment.done) {
                        Pill(
                            stringResource(R.string.appointments_status_done),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else if (!past) {
                        if (appointment.reminderIsScheduled()) {
                            Pill(
                                stringResource(
                                    R.string.appointments_reminder_before,
                                    formatDuration(context, appointment.remindMinutesBefore)
                                ),
                                icon = Icons.Outlined.NotificationsActive,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            )
                        } else {
                            Pill(
                                stringResource(R.string.appointments_no_reminder),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onAddToCalendar) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = stringResource(R.string.appointments_add_to_calendar),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(
                        R.string.appointments_delete_a11y,
                        appointment.title
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppointmentPickerField(
    label: String,
    value: String,
    actionDescription: String,
    fieldDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            trailingIcon = {
                IconButton(onClick = onClick) {
                    Icon(icon, contentDescription = actionDescription)
                }
            }
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    role = Role.Button,
                    onClickLabel = actionDescription
                ) { onClick() }
                .semantics { contentDescription = fieldDescription }
        )
    }
}

@Composable
internal fun AppointmentEditorDialog(
    initial: AppointmentEntity?,
    editingId: String?,
    editingCreatedAt: Long?,
    onDismiss: () -> Unit,
    onSave: suspend (AppointmentEntity) -> Unit,
    onAddToCalendar: (CalendarEvent) -> Unit,
    /** Test seam: proves the keyboard Done action reached the form. */
    onImeDone: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val entryId by rememberSaveable {
        mutableStateOf(editingId ?: UUID.randomUUID().toString())
    }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var doctor by rememberSaveable { mutableStateOf(initial?.doctor.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(initial?.place.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var duration by rememberSaveable { mutableStateOf(initial?.durationMinutes ?: 50) }
    var remind by rememberSaveable { mutableStateOf(initial?.remindMinutesBefore ?: 60) }
    var done by rememberSaveable { mutableStateOf(initial?.done ?: false) }
    var startsAtMillis by rememberSaveable {
        mutableStateOf(
            initial?.startsAt ?: LocalDateTime.now().plusDays(1)
                .withHour(10).withMinute(0).withSecond(0).withNano(0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
    }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val nameRequired = stringResource(R.string.appt_name_required)
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val hideKeyboard = rememberKeyboardHider()

    val dateTime = Instant.ofEpochMilli(startsAtMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    val isPast = startsAtMillis < System.currentTimeMillis()

    fun dismissOrConfirm() {
        if (saving) return
        if (dirty) showDiscard = true else onDismiss()
    }

    fun save() {
                    if (saving) return
                    if (title.isBlank()) {
                        error = nameRequired
                        return
                    }
                    if (editingId == null && startsAtMillis < System.currentTimeMillis()) {
                        error = context.getString(R.string.appointments_past_error)
                        return
                    }
                    error = ""
                    saving = true
                    val entry = AppointmentEntity(
                        id = entryId,
                        title = title.trim(),
                        doctor = doctor.trim(),
                        place = place.trim(),
                        startsAt = startsAtMillis,
                        durationMinutes = duration,
                        notes = notes,
                        remindMinutesBefore = remind,
                        done = done,
                        createdAt = editingCreatedAt ?: initial?.createdAt
                            ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    scope.launch {
                        runCatching { onSave(entry) }
                            .onSuccess {
                                saving = false
                                onDismiss()
                            }
                            .onFailure { failure ->
                                saving = false
                                error = context.getString(R.string.appointments_save_error)
                            }
                    }
    }

    FormDialog(
        title = stringResource(
            if (editingId == null) R.string.appointments_new_title
            else R.string.appointments_edit_title
        ),
        onDismissRequest = { dismissOrConfirm() },
        confirmLabel = stringResource(
            if (saving) R.string.appointments_saving
            else R.string.appointments_save
        ),
        confirmEnabled = !saving,
        dismissLabel = stringResource(R.string.appointments_cancel),
        onConfirm = ::save
    ) {
        Column(Modifier.fillMaxWidth()) {                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.appointments_field_title)) },
                    placeholder = {
                        Text(stringResource(R.string.appointments_field_title_hint))
                    },
                    modifier = Modifier.fillMaxWidth().formDialogField(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = doctor,
                    onValueChange = {
                        doctor = it
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.appointments_field_doctor)) },
                    modifier = Modifier.fillMaxWidth().formDialogField(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = place,
                    onValueChange = {
                        place = it
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.appointments_field_place)) },
                    modifier = Modifier.fillMaxWidth().formDialogField(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(Modifier.height(OporaSpacing.s))
                val largeFont = LocalDensity.current.fontScale >= 1.5f
                val locale = LocalConfiguration.current.locales[0]
                val dateValue = dateTime.toLocalDate().dayMonth(locale)
                val timeValue = dateTime.toLocalTime().hhmm()
                val dateField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    AppointmentPickerField(
                        label = stringResource(R.string.appointments_field_date),
                        value = dateValue,
                        actionDescription = stringResource(R.string.appointments_choose_date),
                        fieldDescription = stringResource(
                            R.string.appointments_date_a11y,
                            dateValue
                        ),
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = { dateDialog = true },
                        modifier = fieldModifier
                    )
                }
                val timeField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    AppointmentPickerField(
                        label = stringResource(R.string.appointments_field_time),
                        value = timeValue,
                        actionDescription = stringResource(R.string.appointments_choose_time),
                        fieldDescription = stringResource(
                            R.string.appointments_time_a11y,
                            timeValue
                        ),
                        icon = Icons.Outlined.Schedule,
                        onClick = { timeDialog = true },
                        modifier = fieldModifier
                    )
                }
                if (largeFont) {
                    // При крупном шрифте дата и время не делят строку:
                    // «13 ноября» и «10:00» переносились и обрезались.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dateField(Modifier.fillMaxWidth())
                        timeField(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dateField(Modifier.weight(1f))
                        timeField(Modifier.weight(0.7f))
                    }
                }
                if (isPast) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.appointments_past_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(OporaSpacing.s))
                Text(
                    stringResource(R.string.appointments_duration),
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
                    listOf(30, 50, 60, 90).forEach { value ->
                        FilterChip(
                            selected = duration == value,
                            onClick = {
                                duration = value
                                dirty = true
                            },
                            label = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.duration_minutes,
                                        value,
                                        value
                                    )
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.appointments_remind_lead),
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(OporaSpacing.s)) {
                    listOf(15, 30, 60, 120, 1440).forEach { value ->
                        FilterChip(
                            selected = remind == value,
                            onClick = {
                                remind = value
                                dirty = true
                            },
                            label = { Text(formatDuration(context, value)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        dirty = true
                    },
                    label = { Text(stringResource(R.string.appointments_notes)) },
                    modifier = Modifier.fillMaxWidth().formDialogField(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        hideKeyboard()
                        onImeDone()
                    })
                )
                Spacer(Modifier.height(OporaSpacing.s))
                OutlinedButton(
                    onClick = {
                        onAddToCalendar(
                            calendarEvent(
                                title = title,
                                doctor = doctor,
                                place = place,
                                notes = notes,
                                beginMillis = startsAtMillis,
                                durationMinutes = duration
                            )
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(OporaIconSize.compact)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.appointments_add_to_calendar))
                }
                if (editingId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = done,
                            onClick = {
                                done = !done
                                dirty = true
                            },
                            label = { Text(stringResource(R.string.appointments_status_done)) },
                            leadingIcon = if (done) {
                                {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(OporaIconSize.compact)
                                    )
                                }
                            } else null
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.appointments_discard_title),
            text = stringResource(R.string.appointments_discard_body),
            confirmLabel = stringResource(R.string.action_close),
            dismissLabel = stringResource(R.string.appointments_continue),
            destructive = false,
            onConfirm = {
                showDiscard = false
                onDismiss()
            },
            onDismiss = { showDiscard = false }
        )
    }

    if (dateDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        startsAtMillis = picked.atTime(dateTime.toLocalTime())
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        dirty = true
                    }
                    dateDialog = false
                }) { Text(stringResource(R.string.action_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { dateDialog = false }) {
                    Text(stringResource(R.string.appointments_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (timeDialog) {
        val state = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    startsAtMillis = dateTime.withHour(state.hour).withMinute(state.minute)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    dirty = true
                    timeDialog = false
                }) { Text(stringResource(R.string.action_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { timeDialog = false }) {
                    Text(stringResource(R.string.appointments_cancel))
                }
            },
            title = { Text(stringResource(R.string.appointments_time_title)) },
            text = { TimePicker(state = state) }
        )
    }
}
