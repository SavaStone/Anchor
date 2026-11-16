package app.opora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.domain.HomeBlock
import app.opora.domain.MOOD_LEVELS
import app.opora.domain.MoodLevel
import app.opora.domain.hhmm
import app.opora.domain.isOverdue
import app.opora.domain.visibleHomeBlocks
import app.opora.domain.weekdayDate
import app.opora.ui.TodayViewModel
import app.opora.ui.text.formatSleep

import app.opora.ui.components.InfoRow
import app.opora.ui.components.MedActionCallbacks
import app.opora.ui.components.MedActionsMenu
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.MoodFace
import app.opora.ui.components.OporaCard
import app.opora.ui.components.OporaSnackbarHost
import app.opora.ui.components.OporaTimePickerDialog
import app.opora.ui.components.SectionHeader
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.theme.fireColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onEditMood: (LocalDate, Long?) -> Unit,
    onOpenMeds: () -> Unit,
    onAddMedication: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenAppointments: () -> Unit,
    onEditMedication: (String) -> Unit = {}
) {
    val state by vm.state.collectAsState()
    if (state.loading) {
        Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var stopping by remember { mutableStateOf<MedicationEntity?>(null) }
    val medsById = remember(state.meds) { state.meds.associateBy { it.id } }
    val undoLabel = stringResource(R.string.today_snackbar_undo)

    fun takeDose(dose: DoseLogEntity) {
        vm.take(dose.id)
        val message = context.getString(R.string.today_snackbar_taken, dose.medicationName)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(dose.id)
        }
    }

    fun skipDose(dose: DoseLogEntity) {
        vm.skip(dose.id)
        val message = context.getString(R.string.today_snackbar_skipped, dose.medicationName)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(dose.id)
        }
    }

    // Возврат к приложению после паузы: сразу перечитываем сегодняшнюю дату.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = LocalDateTime.now()
        vm.refresh()
    }

    // Экран не должен «застывать» на вчерашней дате, если приложение открыто всю ночь.
    LaunchedEffect(Unit) {
        if (state.today != LocalDate.now()) vm.refresh()
        var lastDate = LocalDate.now()
        while (true) {
            delay(60_000)
            val current = LocalDateTime.now()
            if (current.toLocalDate() != lastDate) {
                lastDate = current.toLocalDate()
                now = current
                vm.refresh()
            }
        }
    }

    stopping?.let { med ->
        ConfirmDialog(
            title = stringResource(R.string.today_stop_title),
            text = stringResource(R.string.today_stop_body, med.name),
            confirmLabel = stringResource(R.string.actions_discontinue),
            onConfirm = {
                vm.discontinue(med.id)
                stopping = null
            },
            onDismiss = { stopping = null }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = OporaSpacing.screenGutter,
                end = OporaSpacing.screenGutter,
                top = OporaSpacing.screenTop,
                bottom = OporaSpacing.screenBottom
            ),
            verticalArrangement = Arrangement.spacedBy(OporaSpacing.listGap)
        ) {
            item {
                HeaderDate(date = now.toLocalDate())
            }

            val home = visibleHomeBlocks(
                state.settings.homeBlocks,
                state.settings.hiddenHomeBlocks
            )
            home.forEach { block ->
                when (block) {
                    HomeBlock.MOOD -> {
                        item {
                            MoodCheckInCard(
                        mood = state.mood,
                        onSelect = vm::quickMood,
                        onDetails = { onEditMood(state.today, state.mood?.id) }
                    )
                }
            }

                    HomeBlock.MEDS -> {
                if (state.meds.isEmpty()) {
                item {
                    OporaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column {
                            Text(
                                stringResource(R.string.empty_med_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.today_meds_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = onAddMedication) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(OporaIconSize.inline))
                                Spacer(Modifier.width(OporaSpacing.s))
                                Text(stringResource(R.string.add_medication))
                            }
                        }
                    }
                }
            } else {
                item {
                    OporaCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(
                                    stringResource(R.string.home_block_meds),
                                    Modifier.weight(1f)
                                )
                                val streak = state.doseStreak.current
                                val chipText = if (streak > 0) {
                                    pluralStringResource(R.plurals.days, streak, streak)
                                } else {
                                    stringResource(R.string.streak_none)
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = OporaSpacing.m, vertical = OporaSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.LocalFireDepartment,
                                            contentDescription = null,
                                            tint = fireColor(),
                                            modifier = Modifier.size(OporaIconSize.compact)
                                        )
                                        Text(
                                            chipText,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            if (state.doses.isEmpty()) {
                                Text(
                                    stringResource(R.string.today_no_doses),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = OporaSpacing.s)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 480.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(state.doses, key = { it.id }) { dose ->
                                        DoseRow(
                                            dose = dose,
                                            dosageText = dosageWithForm(
                                                context,
                                                dose.dosage,
                                                medsById[dose.medicationId]?.form
                                            ),
                                            onTake = { takeDose(dose) },
                                            onSkip = { skipDose(dose) },
                                            onUndo = { vm.undo(dose.id) },
                                            onEditTime = { vm.setDoseTime(dose.id, it) },
                                            onDelete = { vm.deleteDose(dose.id) },
                                            medication = medsById[dose.medicationId],
                                            onPause = {
                                                medsById[dose.medicationId]?.let { vm.pause(it.id) }
                                            },
                                            onResume = {
                                                medsById[dose.medicationId]?.let { vm.resume(it.id) }
                                            },
                                            onDiscontinue = {
                                                medsById[dose.medicationId]?.let { stopping = it }
                                            },
                                            onDeleteMedication = {
                                                medsById[dose.medicationId]?.let { vm.deleteMedication(it.id) }
                                            },
                                            onOpenMedication = medsById[dose.medicationId]?.let { med ->
                                                { onEditMedication(med.id) }
                                            }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = onOpenMeds, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(R.string.today_all_meds))
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(OporaIconSize.inline))
                            }
                        }
                    }
                }
            }
            }

                    HomeBlock.APPOINTMENT -> {
                state.nextAppointment?.let { appointment ->
                item {
                    val days = ChronoUnit.DAYS.between(state.today, LocalDate.ofEpochDay(
                        java.time.Instant.ofEpochMilli(appointment.startsAt)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                    ))
                    OporaCard(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onOpenAppointments
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(Modifier.width(OporaSpacing.listGap))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    appointment.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    appointmentSubtitle(appointment.startsAt, appointment.place, days),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
                                )
                            }
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            }

                    HomeBlock.JOURNAL -> {
            item {
                // Вся карточка — одна цель; вложенная кнопка дублировала бы действие и ломала семантику.
                OporaCard(onClick = onOpenJournal) {
                    InfoRow(
                        icon = Icons.Outlined.AutoStories,
                        title = stringResource(R.string.home_block_journal),
                        subtitle = stringResource(R.string.today_journal_subtitle),
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
            }
                    }
                }

        }

        OporaSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HeaderDate(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val text = date.weekdayDate(locale)
    BasicText(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(
            color = MaterialTheme.colorScheme.onBackground
        ),
        // Две строки на всех масштабах: при 1.0 «Пятница, 14 ноября» не
        // помещалась в одну строку и обрезалась по глифам. autoSize включается
        // только когда две строки уже не влезают: кегль уменьшается до
        // читаемого минимума, но месяц остаётся видимым.
        maxLines = 2,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MaterialTheme.typography.bodyLarge.fontSize,
            maxFontSize = MaterialTheme.typography.headlineLarge.fontSize
        ),
        modifier = modifier
    )
}

/**
 * «сегодня, 10:30 · Клиника» в одной строке; относительный день, время и место
 * приходят готовыми ресурсами.
 */
@Composable
private fun appointmentSubtitle(startsAt: Long, place: String, days: Long): String {
    val whenText = when {
        days <= 0L -> stringResource(R.string.today_appointment_today)
        days == 1L -> stringResource(R.string.today_appointment_tomorrow)
        else -> pluralStringResource(
            R.plurals.today_appointment_in_days,
            days.toInt(),
            days.toInt()
        )
    }
    val time = Instant.ofEpochMilli(startsAt)
        .atZone(ZoneId.systemDefault()).toLocalTime().hhmm()
    return if (place.isNotBlank()) {
        stringResource(R.string.today_appointment_time_place, whenText, time, place)
    } else {
        stringResource(R.string.today_appointment_time, whenText, time)
    }
}

@Composable
private fun MoodCheckInCard(
    mood: app.opora.data.db.MoodEntity?,
    onSelect: (Int) -> Unit,
    onDetails: () -> Unit
) {
    val current = mood?.mood
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val context = LocalContext.current
    OporaCard {
        Column {
            // На крупном шрифте заголовок и действие не делят строку: иначе
            // слово «Настроение» рвётся посередине.
            if (largeFont) {
                SectionHeader(
                    stringResource(R.string.home_block_mood),
                    subtitle = if (current == null) stringResource(R.string.today_mood_question) else null
                )
                TextButton(
                    onClick = onDetails,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        stringResource(
                            if (current == null) R.string.today_add_details else R.string.today_details
                        )
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        stringResource(R.string.home_block_mood),
                        Modifier.weight(1f),
                        subtitle = if (current == null) stringResource(R.string.today_mood_question) else null
                    )
                    TextButton(onClick = onDetails) {
                        Text(
                            stringResource(
                                if (current == null) R.string.today_add_details
                                else R.string.today_details
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (largeFont) 4.dp else 10.dp))
            // При очень крупном шрифте пять колонок ломают подписи посередине слова —
            // те же пять вариантов становятся списком строк с полным названием.
            if (largeFont) {
                Column(
                    Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)
                ) {
                    MOOD_LEVELS.forEach { level ->
                        MoodOptionRow(
                            level = level,
                            selected = current == level.value,
                            onSelect = { onSelect(level.value) }
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MOOD_LEVELS.forEach { level ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MoodFace(
                                level = level.value,
                                selected = current == level.value,
                                onClick = { onSelect(level.value) },
                                size = 48.dp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(level.labelRes),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (current == level.value) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            val sleep = mood?.sleepMinutes?.let { formatSleep(context, it) }
            val anxiety = mood?.anxiety
            if (sleep != null || anxiety != null) {
                Spacer(Modifier.height(8.dp))
                val sleepText = sleep?.let { stringResource(R.string.today_sleep, it) }
                val anxietyText = anxiety?.let { stringResource(R.string.today_anxiety, it) }
                val stats = when {
                    sleepText != null && anxietyText != null ->
                        stringResource(R.string.today_stats_join, sleepText, anxietyText)
                    else -> sleepText ?: anxietyText
                }
                Text(
                    stats.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Вариант шкалы настроения в виде строки: вся строка — одна цель не меньше 48dp. */
@Composable
private fun MoodOptionRow(
    level: MoodLevel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val label = stringResource(level.labelRes)
    val a11yLabel = stringResource(R.string.mood_a11y, label)
    val levelLabel = stringResource(R.string.a11y_level_of_five, level.value)
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
                stateDescription = levelLabel
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
fun DoseRow(
    dose: DoseLogEntity,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onEditTime: (LocalTime) -> Unit,
    onDelete: () -> Unit,
    dosageText: String? = null,
    onOpenMedication: (() -> Unit)? = null,
    medication: MedicationEntity? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onDiscontinue: (() -> Unit)? = null,
    onDeleteMedication: (() -> Unit)? = null
) {
    val actualTime = dose.takenAt?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
    }
    val snoozeTime = dose.snoozeUntil?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
    }
    val overdue = dose.isOverdue()
    val skippedLabel = stringResource(R.string.dose_status_skipped).lowercase()
    val missedLabel = stringResource(R.string.dose_status_missed).lowercase()
    val overdueLabel = stringResource(R.string.dose_status_overdue).lowercase()
    // Дозировка и статус — одна строка: «5 мг · принято в 09:12».
    val dosage = dosageText ?: dose.dosage
    val doseLabel = when (dose.status) {
        "taken" -> {
            val at = actualTime?.hhmm()
            if (at != null && at != dose.scheduledTime) {
                stringResource(R.string.dose_line_taken_at, dosage, at)
            } else {
                stringResource(R.string.dose_line_taken, dosage)
            }
        }
        "skipped" -> stringResource(R.string.dose_line_join, dosage, skippedLabel)
        "missed" -> stringResource(R.string.dose_line_join, dosage, missedLabel)
        else -> when {
            snoozeTime != null ->
                stringResource(R.string.dose_line_snoozed, dosage, snoozeTime.hhmm())
            overdue -> stringResource(R.string.dose_line_join, dosage, overdueLabel)
            else -> dosage
        }
    }
    val statusColor = when (dose.status) {
        "taken" -> MaterialTheme.colorScheme.primary
        "skipped" -> MaterialTheme.colorScheme.outline
        "missed" -> MaterialTheme.colorScheme.error
        else -> if (overdue) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Время — ведущая подпись, название и статус — один блок, который может
    // переноситься. Высота не фиксируется, поэтому при крупном шрифте время
    // остаётся рядом с названием, а не отрывается от него.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            dose.scheduledTime,
            style = MaterialTheme.typography.titleSmall,
            color = statusColor,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 46.dp).padding(end = 10.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                dose.medicationName,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (dose.status == "taken") TextDecoration.LineThrough else null,
                color = if (dose.status == "taken") MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                doseLabel,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }
        DoseStatusAction(
            dose = dose,
            onTake = onTake,
            onUndo = onUndo,
            interactive = medication?.active == true
        )
        DoseOverflowButton(
            dose = dose,
            onSkip = onSkip,
            onUndo = onUndo,
            onEditTime = onEditTime,
            onDelete = onDelete,
            onOpenMedication = onOpenMedication,
            medication = medication,
            onPause = onPause,
            onResume = onResume,
            onDiscontinue = onDiscontinue,
            onDeleteMedication = onDeleteMedication
        )
    }
}

/** Крупный круг «принять»: пустой контур ожидает действия, галочка — уже принято. */
@Composable
fun DoseStatusAction(
    dose: DoseLogEntity,
    onTake: () -> Unit,
    onUndo: () -> Unit,
    interactive: Boolean = true
) {
    val takenUndoA11y = stringResource(R.string.dose_taken_a11y, dose.medicationName)
    val takenA11y = stringResource(R.string.dose_taken_prn_a11y, dose.medicationName)
    val clickAction: (() -> Unit)? = when {
        !interactive -> null
        dose.status == "taken" -> if (dose.isPrn) null else onUndo
        dose.status == "skipped" -> onUndo
        else -> onTake
    }
    val statusA11y = when (dose.status) {
        "taken" -> if (clickAction != null) takenUndoA11y else takenA11y
        "skipped" -> if (clickAction != null) {
            stringResource(R.string.actions_undo)
        } else {
            stringResource(R.string.a11y_dose_named, stringResource(R.string.dose_status_skipped), dose.medicationName)
        }
        "missed" -> if (clickAction != null) {
            stringResource(R.string.dose_take_late_a11y)
        } else {
            stringResource(R.string.a11y_dose_named, stringResource(R.string.dose_status_missed), dose.medicationName)
        }
        else -> if (clickAction != null) stringResource(R.string.dose_take_a11y) else null
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (clickAction != null) {
                    Modifier.clickable(role = Role.Button, onClick = clickAction)
                } else {
                    Modifier
                }
            )
            .then(
                if (statusA11y != null) {
                    Modifier.semantics { contentDescription = statusA11y }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        DoseStatusVisual(dose.status)
    }
}

/**
 * Единственный глиф статуса дозы. Внешний диаметр у taken / pending / missed /
 * skipped одинаков: у Material CheckCircle окружность занимает лишь 20/24
 * вьюпорта, поэтому заливка рисуется отдельным кругом, а не глифом.
 */
@Composable
fun DoseStatusVisual(
    status: String,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        "missed" -> MaterialTheme.colorScheme.error
        "skipped" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier
            .size(38.dp)
            .testTag("dose-status-visual"),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            "taken" -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color, CircleShape)
                )
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.size(OporaIconSize.row)
                )
            }
            "skipped" -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, color, CircleShape)
                )
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(OporaIconSize.inline)
                )
            }
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, color, CircleShape)
            )
        }
    }
}

/** Меню действий приёма, общее для списка дня, карточки лекарства и истории. */
@Composable
fun DoseOverflowButton(
    dose: DoseLogEntity,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onEditTime: (LocalTime) -> Unit,
    onDelete: () -> Unit,
    onOpenMedication: (() -> Unit)? = null,
    medication: MedicationEntity? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onDiscontinue: (() -> Unit)? = null,
    onDeleteMedication: (() -> Unit)? = null
) {
    MedActionsMenu(
        medication = medication,
        dose = dose,
        callbacks = MedActionCallbacks(
            onSkip = onSkip,
            onEditTime = onEditTime,
            onUndo = onUndo,
            onDeleteDose = onDelete,
            onPause = onPause,
            onResume = onResume,
            onDiscontinue = onDiscontinue,
            onEditMedication = onOpenMedication,
            onDeleteMedication = onDeleteMedication
        ),
        contentDescription = stringResource(
            R.string.a11y_dose_overflow,
            dose.medicationName,
            dose.scheduledTime
        )
    )
}
