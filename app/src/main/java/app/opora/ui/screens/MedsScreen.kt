package app.opora.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.opora.R
import app.opora.AppLanguage
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.domain.DoseDayState
import app.opora.domain.DoseStreak
import app.opora.domain.StopReason
import app.opora.domain.dayMonth
import app.opora.domain.discontinuedDateLabel
import app.opora.domain.formatAmount
import app.opora.domain.hhmm
import app.opora.domain.isScheduledBefore
import app.opora.domain.stopReasonLabel
import app.opora.domain.weekdayDate
import app.opora.domain.weekdayDateShort
import app.opora.domain.weekdayShort
import app.opora.ui.MedsViewModel
import app.opora.ui.theme.onFireColor
import app.opora.ui.theme.OporaIconSize
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.theme.fireColor
import app.opora.ui.components.EmptyState
import app.opora.ui.components.ErrorState
import app.opora.ui.components.IconTextRow
import app.opora.ui.components.MedActionCallbacks
import app.opora.ui.components.MedActionsMenu
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.OporaCard
import app.opora.ui.components.OporaSnackbarHost
import app.opora.ui.components.Pill
import app.opora.ui.components.PrimaryFab
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.ui.text.scheduleSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Прокрутка вкладки переживает поворот: раньше список возвращался к началу. */
@Composable
internal fun rememberMedsListState(tab: Int): LazyListState =
    androidx.compose.runtime.saveable.rememberSaveable(tab, saver = LazyListState.Saver) {
        LazyListState()
    }

@Composable
fun MedsScreen(
    vm: MedsViewModel,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit,
    leftHanded: Boolean = false
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // У каждой вкладки своя прокрутка: заголовок и карточка серии приёма
    // не должны оставаться сдвинутыми с прошлой вкладки.
    val listState = rememberMedsListState(tab)
    var showResolved by rememberSaveable { mutableStateOf(false) }
    // Для подписи «просрочено» и обновления статуса без перезапуска экрана.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Возврат к приложению после паузы: проверяем, не сменилась ли дата.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        nowMillis = System.currentTimeMillis()
        vm.refresh()
    }

    // После полуночи список приёмов и подпись «сегодня» должны обновиться.
    LaunchedEffect(Unit) {
        if (state.today != LocalDate.now()) vm.refresh()
        var lastDate = LocalDate.now()
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
            val now = LocalDate.now()
            if (now != lastDate) {
                lastDate = now
                vm.refresh()
            }
        }
    }

    val medsById = remember(state.meds) { state.meds.associateBy { it.id } }
    val scheduledToday = state.todayDoses.filterNot { it.isPrn }
    val pendingToday = scheduledToday
        .filter { it.status == "pending" || it.status == "missed" }
        .sortedWith(compareBy({ it.scheduledTime }, { it.medicationName }))
    val resolvedToday = scheduledToday
        .filter { it.status == "taken" || it.status == "skipped" }
        .sortedWith(compareBy({ it.scheduledTime }, { it.medicationName }))
    val nextPendingDose = pendingToday.firstOrNull()
    val nextDoseText = if (nextPendingDose != null) {
        val label = context.getString(
            R.string.meds_dose_today_at,
            nextPendingDose.medicationName,
            nextPendingDose.scheduledTime
        )
        when {
            nextPendingDose.status == "missed" -> context.getString(R.string.dose_next_missed, label)
            nextPendingDose.isScheduledBefore(nowMillis) -> context.getString(R.string.dose_next_waiting, label)
            else -> context.getString(R.string.dose_next, label)
        }
    } else state.nextDose?.let { (med, time) ->
        val days = ChronoUnit.DAYS.between(state.today, time.toLocalDate())
        val dayLabel = when {
            days <= 0L -> context.getString(R.string.meds_day_today)
            days == 1L -> context.getString(R.string.meds_day_tomorrow)
            else -> time.toLocalDate().weekdayShort(locale)
                .replaceFirstChar { it.uppercase(locale) }
        }
        context.getString(R.string.meds_next_dose_line, med.name, dayLabel, time.hhmm())
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun takeDose(dose: DoseLogEntity) {
        vm.take(dose.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.meds_snack_taken, dose.medicationName),
                actionLabel = context.getString(R.string.meds_snack_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(dose.id)
        }
    }

    fun skipDose(dose: DoseLogEntity) {
        vm.skip(dose.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.meds_snack_skipped, dose.medicationName),
                actionLabel = context.getString(R.string.meds_snack_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(dose.id)
        }
    }

    // Прекращение приёма подтверждается отдельно: это не удаление и не одно
    // пропущенное принятие, отменить действие можно из снекбара.
    var stopping by remember { mutableStateOf<MedicationEntity?>(null) }

    fun stopMedication(med: MedicationEntity, reason: String) {
        // «Вернуть» уместно только для активного лекарства: у приостановленного
        // отмена вернула бы его в приём, а не в паузу.
        val wasActive = med.active
        vm.discontinue(med.id, reason)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.meds_snack_stopped, med.name),
                actionLabel = if (wasActive) context.getString(R.string.meds_snack_restore) else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.resume(med.id)
        }
    }

    fun resumeMedication(med: MedicationEntity) {
        vm.resume(med.id)
        scope.launch {
            val courseOver = med.endDate?.let { it < LocalDate.now().toEpochDay() } == true
            val result = snackbarHostState.showSnackbar(
                message = if (courseOver) {
                    context.getString(R.string.meds_snack_course_over, med.name)
                } else {
                    context.getString(R.string.meds_snack_resumed, med.name)
                },
                actionLabel = if (courseOver) context.getString(R.string.action_edit) else null
            )
            if (result == SnackbarResult.ActionPerformed) onEditMedication(med.id)
        }
    }

    val fabExpanded by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }


    Box(Modifier.fillMaxSize()) {
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
        item {
            ScreenTitle(title = stringResource(R.string.tab_meds))
        }

        if (state.loading) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.error) {
            item {
                ErrorState(
                    icon = Icons.Outlined.Warning,
                    title = stringResource(R.string.meds_error_title),
                    body = stringResource(R.string.meds_error_body),
                    actionLabel = stringResource(R.string.meds_retry),
                    onAction = vm::retry
                )
            }
        } else if (state.meds.isEmpty() && state.history.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Medication,
                    title = stringResource(R.string.meds_empty_title),
                    body = stringResource(R.string.meds_empty_body)
                )
            }
        } else {
            if (tab == 0) {
            item {
                FlameStreakCard(
                    streak = state.doseStreak,
                    nextDoseText = nextDoseText,
                    onForgive = vm::forgiveDay,
                    onUnforgive = vm::unforgiveDay
                )
            }

            item {
                val low = state.meds.filter { med ->
                    val stock = med.inventoryCount
                    val threshold = med.refillThreshold
                    med.active && stock != null && threshold != null && stock <= threshold
                }
                if (low.isNotEmpty()) {
                    OporaCard(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)) {
                        Column {
                            IconTextRow(
                                icon = Icons.Rounded.WarningAmber,
                                title = stringResource(R.string.stock_low_title),
                                subtitle = stringResource(R.string.stock_low_hint),
                                titleStyle = MaterialTheme.typography.titleSmall,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                titleColor = MaterialTheme.colorScheme.onErrorContainer,
                                subtitleColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(OporaSpacing.s))
                            low.forEach { med ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable(role = Role.Button) { onEditMedication(med.id) }
                                        .heightIn(min = 48.dp)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.stock_item_line,
                                            med.name,
                                            med.inventoryCount?.let { formatAmount(it, locale) }
                                                ?: stringResource(R.string.common_dash),
                                            unit(context, med)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        stringResource(R.string.action_edit),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Icon(
                                        Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(OporaIconSize.inline)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

            stickyHeader(key = "tabs") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Три равные колонки при крупном шрифте не вмещают подписи:
                    // «Сегодня» и «История» обрезались многоточием. Тогда показываем
                    // короткие подписи и прокручиваемый ряд: текст не обрезается,
                    // а полный смысл вкладки остаётся в описании для TalkBack.
                    val largeFont = LocalDensity.current.fontScale >= 1.5f
                    val labels = if (largeFont) {
                        listOf(
                            stringResource(R.string.meds_tab_day),
                            stringResource(R.string.meds_tab_list),
                            stringResource(R.string.meds_tab_archive)
                        )
                    } else {
                        listOf(
                            stringResource(R.string.tab_today),
                            stringResource(R.string.meds_tab_list),
                            stringResource(R.string.meds_tab_history)
                        )
                    }
                    val descriptions = listOf(
                        stringResource(R.string.meds_tab_today_desc),
                        stringResource(R.string.meds_tab_list_desc),
                        stringResource(R.string.meds_tab_history_desc)
                    )
                    val tabItems: @Composable () -> Unit = {
                        labels.forEachIndexed { index, title ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Text(
                                        title,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = if (largeFont) {
                                            Modifier.clearAndSetSemantics {
                                                contentDescription = descriptions[index]
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                            )
                        }
                    }
                    if (largeFont) {
                        ScrollableTabRow(
                            selectedTabIndex = tab,
                            modifier = Modifier.clip(MaterialTheme.shapes.extraLarge),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            edgePadding = 0.dp,
                            divider = {},
                            tabs = tabItems
                        )
                    } else {
                        TabRow(
                            selectedTabIndex = tab,
                            modifier = Modifier.clip(MaterialTheme.shapes.extraLarge),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            divider = {},
                            tabs = tabItems
                        )
                    }
                }
            }

            when (tab) {
                0 -> {
                    val prn = state.meds.filter { it.active && it.scheduleType == "as_needed" }
                    val takenCount = resolvedToday.count { it.status == "taken" }
                    item {
                        val headerLargeFont = LocalDensity.current.fontScale >= 1.5f
                        val title: @Composable () -> Unit = {
                            Column {
                                Text(
                                    stringResource(R.string.meds_today_header),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    state.today.weekdayDateShort(locale),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val counter: @Composable () -> Unit = {
                            if (scheduledToday.isNotEmpty()) {
                                Pill(
                                    stringResource(
                                        R.string.meds_taken_of,
                                        takenCount,
                                        scheduledToday.size
                                    ),
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = context.getString(
                                            R.string.meds_taken_of_a11y,
                                            takenCount,
                                            scheduledToday.size
                                        )
                                    }
                                )
                            }
                        }
                        if (headerLargeFont) {
                            // Крупный шрифт: счётчик уходит под заголовок, иначе
                            // его закрывает плавающая кнопка справа.
                            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                title()
                                Spacer(Modifier.height(OporaSpacing.s))
                                counter()
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Box(Modifier.weight(1f)) { title() }
                                counter()
                            }
                        }
                    }

                    if (scheduledToday.isEmpty()) {
                        item {
                            NoDosesTodayCard(
                                nextDoseText = nextDoseText,
                                hasPrnMeds = prn.isNotEmpty()
                            )
                        }
                    } else {
                        // Как в референсе: время вынесено над карточками и общее
                        // для всех препаратов, назначенных на этот слот.
                        pendingToday.groupBy { it.scheduledTime }.toSortedMap()
                            .forEach { (time, doses) ->
                                item(key = "time-$time") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = OporaSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            time,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (doses.any {
                                                    it.status == "missed" || it.isScheduledBefore(nowMillis)
                                                }
                                            ) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.weight(1f))
                                        if (doses.size > 1) {
                                            Text(
                                                pluralStringResource(
                                                    R.plurals.medications,
                                                    doses.size,
                                                    doses.size
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                items(doses.size, key = { "dose-${doses[it].id}" }) { index ->
                                    val dose = doses[index]
                                    TodayDoseCard(
                                        dose = dose,
                                        med = medsById[dose.medicationId],
                                        overdue = dose.isScheduledBefore(nowMillis),
                                        onTake = { takeDose(dose) },
                                        onSkip = { skipDose(dose) },
                                        onUndo = { vm.undo(dose.id) },
                                        onEditTime = { vm.setDoseTime(dose.id, it) },
                                        onDelete = { vm.deleteDose(dose.id) },
                                        onOpenMedication = medsById[dose.medicationId]?.let { med ->
                                            { onEditMedication(med.id) }
                                        },
                                        onPause = { medsById[dose.medicationId]?.let { vm.pause(it.id) } },
                                        onResume = { medsById[dose.medicationId]?.let { vm.resume(it.id) } },
                                        onDiscontinue = {
                                            medsById[dose.medicationId]?.let { stopping = it }
                                        },
                                        onDeleteMedication = {
                                            medsById[dose.medicationId]?.let { vm.deleteMedication(it.id) }
                                        }
                                    )
                                }
                            }

                        if (resolvedToday.isNotEmpty()) {
                            item(key = "resolved-header") {
                                ResolvedDosesHeader(
                                    count = resolvedToday.size,
                                    expanded = showResolved,
                                    onToggle = { showResolved = !showResolved }
                                )
                            }
                            if (showResolved) {
                                item(key = "resolved-list") {
                                    OporaCard(contentPadding = 12.dp) {
                                        Column {
                                            resolvedToday.forEachIndexed { index, dose ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        Modifier.padding(vertical = OporaSpacing.xs)
                                                    )
                                                }
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
                                                    onOpenMedication = medsById[dose.medicationId]?.let { med ->
                                                        { onEditMedication(med.id) }
                                                    },
                                                    medication = medsById[dose.medicationId],
                                                    onPause = { medsById[dose.medicationId]?.let { vm.pause(it.id) } },
                                                    onResume = { medsById[dose.medicationId]?.let { vm.resume(it.id) } },
                                                    onDiscontinue = {
                                                        medsById[dose.medicationId]?.let { stopping = it }
                                                    },
                                                    onDeleteMedication = {
                                                        medsById[dose.medicationId]?.let { vm.deleteMedication(it.id) }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (prn.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.meds_schedule_as_needed)) }
                        items(prn.size, key = { "prn-${prn[it].id}" }) { index ->
                            val med = prn[index]
                            val prnToday = state.todayDoses.filter {
                                it.isPrn && it.medicationId == med.id
                            }
                            PrnMedicationCard(
                                med = med,
                                todayLogs = prnToday,
                                onLog = {
                                    vm.prn(med.id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                R.string.meds_snack_prn_logged,
                                                med.name,
                                                LocalTime.now().hhmm()
                                            )
                                        )
                                    }
                                },
                                onClick = { onEditMedication(med.id) }
                            )
                        }
                    }
                }

                1 -> {
                    val active = state.meds.filter { it.active }
                    val stopped = state.meds.filter { !it.active && it.discontinuedAt != null }
                    val paused = state.meds.filter { !it.active && it.discontinuedAt == null }
                    val hasInactive = stopped.isNotEmpty() || paused.isNotEmpty()
                    if (active.isEmpty() && !hasInactive) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.Medication,
                                title = stringResource(R.string.meds_empty_active_title),
                                body = stringResource(R.string.meds_empty_active_body),
                                actionLabel = stringResource(R.string.add_medication),
                                onAction = onAddMedication
                            )
                        }
                    } else {
                        items(active.size, key = { "med-${active[it].id}" }) { index ->
                            val med = active[index]
                            val isPrn = med.scheduleType == "as_needed"
                            MedicationCard(
                                med = med,
                                todayDoses = if (isPrn) emptyList()
                                else state.todayDoses.filter { it.medicationId == med.id },
                                onTake = { logId ->
                                    val dose = state.todayDoses.firstOrNull { it.id == logId }
                                    if (dose != null) takeDose(dose) else vm.take(logId)
                                },
                                onSkip = vm::skip,
                                onUndo = vm::undo,
                                onEditTime = vm::setDoseTime,
                                onPause = { vm.pause(med.id) },
                                onDeleteMedication = { vm.deleteMedication(med.id) },
                                onLogPrn = if (isPrn) {
                                    {
                                        vm.prn(med.id)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    R.string.meds_snack_prn_logged,
                                                    med.name,
                                                    LocalTime.now().hhmm()
                                                )
                                            )
                                        }
                                    }
                                } else null,
                                onDiscontinue = { stopping = med },
                                onClick = { onEditMedication(med.id) }
                            )
                        }
                    }
                    if (stopped.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.meds_section_stopped)) }
                        items(stopped.size, key = { "stopped-${stopped[it].id}" }) { index ->
                            val med = stopped[index]
                            InactiveMedicationCard(
                                med = med,
                                status = buildString {
                                    append(
                                        context.getString(
                                            R.string.meds_status_stopped,
                                            discontinuedDateLabel(
                                                med.discontinuedAt ?: 0L,
                                                locale = locale
                                            )
                                        )
                                    )
                                    val reason = stopReasonLabel(med.discontinuedReason, context)
                                    if (reason.isNotBlank()) append(" · ").append(reason)
                                },
                                onDelete = { vm.deleteMedication(med.id) },
                                onClick = { onEditMedication(med.id) }
                            )
                        }
                    }
                    if (paused.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.meds_section_paused)) }
                        items(paused.size, key = { "paused-${paused[it].id}" }) { index ->
                            val med = paused[index]
                            InactiveMedicationCard(
                                med = med,
                                status = stringResource(R.string.meds_status_paused),
                                onResume = { resumeMedication(med) },
                                onDiscontinue = { stopping = med },
                                onClick = { onEditMedication(med.id) }
                            )
                        }
                    }
                    item {
                        StockCard(
                            meds = state.meds.filter { it.active },
                            onEditMedication = onEditMedication,
                            onAdjustStock = vm::adjustStock
                        )
                    }
                }

                else -> {
                    val grouped = state.history.groupBy { it.scheduledDate }
                        .toSortedMap(compareByDescending { it })
                    if (grouped.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Outlined.Schedule,
                                title = stringResource(R.string.meds_history_empty_title),
                                body = stringResource(R.string.history_empty_body)
                            )
                        }
                    } else {
                        item(key = "history-caption") {
                            Text(
                                stringResource(R.string.meds_history_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        grouped.forEach { (epochDay, doses) ->
                            item(key = "date-$epochDay") {
                                Text(
                                    dayLabel(epochDay, locale, context),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(doses.size, key = { "dose-${doses[it].id}" }) { index ->
                                val dose = doses[index]
                                OporaCard(contentPadding = 12.dp) {
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
                                        onOpenMedication = medsById[dose.medicationId]?.let {
                                            { onEditMedication(it.id) }
                                        },
                                        medication = medsById[dose.medicationId],
                                        onPause = { medsById[dose.medicationId]?.let { vm.pause(it.id) } },
                                        onResume = { medsById[dose.medicationId]?.let { vm.resume(it.id) } },
                                        onDiscontinue = {
                                            medsById[dose.medicationId]?.let { stopping = it }
                                        },
                                        onDeleteMedication = {
                                            medsById[dose.medicationId]?.let { vm.deleteMedication(it.id) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        OporaSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            aboveFab = true
        )
        PrimaryFab(
            text = stringResource(R.string.meds_fab_add),
            contentDescription = stringResource(R.string.add_medication),
            onClick = onAddMedication,
            expanded = fabExpanded,
            modifier = Modifier
                .align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(OporaSpacing.l)
        )
    }

    stopping?.let { med ->
        DiscontinueMedicationDialog(
            med = med,
            onConfirm = { reason ->
                stopping = null
                stopMedication(med, reason)
            },
            onDismiss = { stopping = null }
        )
    }
}

/**
 * Копирайт исходит из сценария «врач сказал больше не принимать»: сначала
 * последствие для напоминаний и истории, причина — необязательный шаг.
 */
@Composable
private fun DiscontinueMedicationDialog(
    med: MedicationEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember(med.id) { mutableStateOf<String?>(null) }
    var reasonMenu by remember(med.id) { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Block, contentDescription = null) },
        title = { Text(stringResource(R.string.meds_stop_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.meds_stop_body, medLabel(med)))
                Spacer(Modifier.height(OporaSpacing.l))
                Text(
                    stringResource(R.string.meds_stop_reason),
                    style = MaterialTheme.typography.titleSmall
                )
                Box {
                    TextButton(onClick = { reasonMenu = true }) {
                        Text(
                            reason?.let { stopReasonLabel(it, context) }
                                ?: stringResource(R.string.meds_stop_reason_none)
                        )
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = reasonMenu,
                        onDismissRequest = { reasonMenu = false }
                    ) {
                        StopReason.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(context.getString(option.labelRes)) },
                                trailingIcon = if (reason == option.key) {
                                    {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = stringResource(R.string.meds_selected)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    reason = option.key
                                    reasonMenu = false
                                }
                            )
                        }
                        if (reason != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.meds_stop_reason_clear)) },
                                onClick = {
                                    reason = null
                                    reasonMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason ?: "") }) {
                Text(stringResource(R.string.meds_stop_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.meds_stop_cancel)) }
        }
    )
}

@Composable
private fun NoDosesTodayCard(
    nextDoseText: String?,
    hasPrnMeds: Boolean
) {
    OporaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(OporaSpacing.m))
            Column {
                Text(
                    stringResource(R.string.meds_no_doses_today),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(OporaSpacing.xs))
                val plannedText = nextDoseText
                    ?: stringResource(R.string.meds_no_doses_planned)
                Text(
                    if (hasPrnMeds) {
                        stringResource(R.string.meds_no_doses_both, plannedText)
                    } else {
                        plannedText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Карточка приёма как в референсе: название, доза с единицами, круговая кнопка. */
@Composable
private fun TodayDoseCard(
    dose: DoseLogEntity,
    med: MedicationEntity?,
    overdue: Boolean,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onEditTime: (LocalTime) -> Unit,
    onDelete: () -> Unit,
    onOpenMedication: (() -> Unit)?,
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
    val statusText = when (dose.status) {
        "taken" -> actualTime?.let { stringResource(R.string.med_dose_taken_at, it.hhmm()) }
            ?: stringResource(R.string.med_dose_taken)
        "skipped" -> stringResource(R.string.dose_status_skipped)
        "missed" -> stringResource(R.string.dose_status_missed)
        else -> when {
            snoozeTime != null -> stringResource(R.string.med_dose_snoozed_until, snoozeTime.hhmm())
            overdue -> stringResource(R.string.dose_status_overdue)
            else -> null
        }
    }
    val statusColor = when (dose.status) {
        "taken" -> MaterialTheme.colorScheme.primary
        "skipped" -> MaterialTheme.colorScheme.outline
        "missed" -> MaterialTheme.colorScheme.error
        else -> if (overdue) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val context = LocalContext.current
    OporaCard(contentPadding = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = OporaSpacing.l, end = OporaSpacing.xs, top = OporaSpacing.m, bottom = OporaSpacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(med?.color ?: dose.color))
            )
            Spacer(Modifier.width(OporaSpacing.s))
            Column(Modifier.weight(1f)) {
                Text(
                    dose.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val dosage = dosageWithForm(context, dose.dosage, med?.form)
                if (dosage.isNotBlank()) {
                    Spacer(Modifier.height(OporaSpacing.xs))
                    Text(
                        dosage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (statusText != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
            DoseStatusAction(
                dose = dose,
                onTake = onTake,
                onUndo = onUndo,
                interactive = med?.active == true
            )
            DoseOverflowButton(
                dose = dose,
                onSkip = onSkip,
                onUndo = onUndo,
                onEditTime = onEditTime,
                onDelete = onDelete,
                onOpenMedication = onOpenMedication,
                medication = med,
                onPause = onPause,
                onResume = onResume,
                onDiscontinue = onDiscontinue,
                onDeleteMedication = onDeleteMedication
            )
        }
    }
}

@Composable
private fun ResolvedDosesHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onToggle)
            .heightIn(min = 48.dp)
            .semantics {
                stateDescription = context.getString(
                    if (expanded) R.string.meds_expanded else R.string.meds_collapsed
                )
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.resolved_doses),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(OporaSpacing.s))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = stringResource(
                if (expanded) R.string.meds_collapse else R.string.meds_expand
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrnMedicationCard(
    med: MedicationEntity,
    todayLogs: List<DoseLogEntity>,
    onLog: () -> Unit,
    onClick: () -> Unit
) {
    val last = todayLogs.maxByOrNull { it.takenAt ?: it.createdAt }
    val context = LocalContext.current
    OporaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(OporaSpacing.xs))
                Text(
                    dosageWithForm(context, med.dosage, med.form)
                        .ifBlank { formLabel(context, med.form) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (last != null) {
                    val at = Instant.ofEpochMilli(last.takenAt ?: last.createdAt)
                        .atZone(ZoneId.systemDefault()).toLocalTime()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.meds_prn_today_line, todayLogs.size, at.hhmm()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onLog) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(OporaIconSize.inline)
                )
                Spacer(Modifier.width(OporaSpacing.s))
                Text(stringResource(R.string.med_dose_log))
            }
        }
    }
}

@Composable
private fun StockCard(
    meds: List<MedicationEntity>,
    onEditMedication: (String) -> Unit,
    onAdjustStock: (String, Int) -> Unit
) {
    if (meds.isEmpty()) return
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    OporaCard {
        Column {
            SectionHeader(
                stringResource(R.string.stock_card_title),
                subtitle = stringResource(R.string.stock_card_subtitle)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(meds, key = { _, med -> med.id }) { index, med ->
                    if (index > 0) {
                        HorizontalDivider(Modifier.padding(vertical = OporaSpacing.xs))
                    }
                    val stock = med.inventoryCount
                    val threshold = med.refillThreshold
                    val low = stock != null && threshold != null && stock <= threshold
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button) { onEditMedication(med.id) }
                            .heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                med.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    stock == null -> stringResource(R.string.stock_not_set)
                                    low -> stringResource(
                                        R.string.stock_low_short,
                                        formatAmount(stock, locale),
                                        unit(context, med)
                                    )
                                    else -> stringResource(
                                        R.string.stock_line,
                                        formatAmount(stock, locale),
                                        unit(context, med)
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (low) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (stock != null) {
                            IconButton(
                                onClick = { onAdjustStock(med.id, -1) },
                                enabled = stock > 0
                            ) {
                                Icon(
                                    Icons.Outlined.Remove,
                                    contentDescription = stringResource(
                                        R.string.stock_remove_one,
                                        med.name
                                    )
                                )
                            }
                            Text(
                                formatAmount(stock, locale),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.widthIn(min = 32.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = { onAdjustStock(med.id, 1) }) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = stringResource(
                                        R.string.stock_add_one,
                                        med.name
                                    )
                                )
                            }
                        } else {
                            TextButton(onClick = { onEditMedication(med.id) }) {
                                Text(stringResource(R.string.stock_set))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** «0,5» без единиц ничего не говорит: добавляем форму, если её нет в тексте дозы. */
fun dosageWithForm(context: Context, dosage: String, form: String?): String {
    val trimmed = dosage.trim()
    if (trimmed.isEmpty()) return ""
    val locale = AppLanguage.locale(context)
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val normalized = if (separator == ',') {
        Regex("(\\d)\\.(\\d)").replace(trimmed, "$1,$2")
    } else {
        trimmed
    }
    return if (normalized.any { it.isLetter() }) normalized
    else "$normalized ${formLabel(context, form)}"
}

@Composable
internal fun FlameStreakCard(
    streak: DoseStreak,
    nextDoseText: String?,
    onForgive: (java.time.LocalDate) -> Unit,
    onUnforgive: (java.time.LocalDate) -> Unit
) {
    var selected by remember { mutableStateOf<DoseDayState?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val windowDays = streak.days
    OporaCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.meds_streak_title), Modifier.weight(1f))
                if (streak.current > 0) {
                    Pill(
                        pluralStringResource(R.plurals.days, streak.current, streak.current),
                        icon = Icons.Rounded.LocalFireDepartment,
                        color = fireColor(),
                        contentColor = onFireColor()
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    streak.current > 0 -> stringResource(
                        R.string.meds_streak_line,
                        streak.current,
                        pluralStringResource(
                            R.plurals.days,
                            streak.days.size,
                            streak.days.size
                        )
                    )
                    streak.hasData -> stringResource(R.string.streak_empty)
                    else -> stringResource(R.string.streak_no_data)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(OporaSpacing.s))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OporaSpacing.xs)
            ) {
                windowDays.forEach { day ->
                    FlameDay(
                        day = day,
                        modifier = Modifier.minimumInteractiveComponentSize().width(48.dp),
                        onClick = {
                            if (day.burned || day.forgiven) selected = day
                        }
                    )
                }
            }
            Spacer(Modifier.height(OporaSpacing.s))
            Text(
                stringResource(R.string.streak_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (nextDoseText != null) {
                Spacer(Modifier.height(OporaSpacing.s))
                IconTextRow(
                    icon = Icons.Outlined.Schedule,
                    title = nextDoseText,
                    titleStyle = MaterialTheme.typography.bodySmall,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    gap = OporaSpacing.xs
                )
            }
        }
    }

    selected?.let { day ->
        ConfirmDialog(
            title = if (day.forgiven) stringResource(R.string.forgiven_day_title)
            else stringResource(R.string.restore_day_title),
            text = if (day.forgiven) stringResource(R.string.forgiven_day_body)
            else stringResource(R.string.restore_day_body, day.date.dayMonth(locale)),
            confirmLabel = if (day.forgiven) stringResource(R.string.forgiven_day_action)
            else stringResource(R.string.restore_day_action),
            destructive = day.forgiven,
            onConfirm = {
                if (day.forgiven) onUnforgive(day.date) else onForgive(day.date)
            },
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun FlameDay(
    day: DoseDayState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lit = day.complete
    val tint = when {
        day.forgiven -> fireColor().copy(alpha = 0.82f)
        lit -> fireColor()
        day.burned -> MaterialTheme.colorScheme.error
        // Неподписанный день всё равно должен читаться: 45% давали ~2,2:1.
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stateLabel = when {
        day.forgiven -> stringResource(R.string.day_forgiven)
        day.complete -> stringResource(R.string.day_full)
        day.burned -> stringResource(R.string.meds_day_missed)
        day.restDay -> stringResource(R.string.meds_day_rest)
        else -> stringResource(R.string.day_incomplete)
    }
    val locale = LocalConfiguration.current.locales[0]
    val dayA11y = stringResource(
        R.string.meds_day_a11y,
        day.date.weekdayDate(locale),
        stateLabel
    )
    // English three-letter weekdays are clipped in a 7-column strip at 2.0x.
    // Two letters stay unambiguous (Su/Mo/Tu/We/Th/Fr/Sa) and keep the cell.
    val compactWeekday = LocalDensity.current.fontScale >= 1.5f
    val weekdayLabel = day.date.weekdayShort(locale).let {
        if (compactWeekday && it.length > 2) it.take(2) else it
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = day.burned || day.forgiven, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = dayA11y
            }
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (lit || day.forgiven) Icons.Rounded.LocalFireDepartment
            else Icons.Outlined.LocalFireDepartment,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(OporaIconSize.regular)
        )
        Text(
            weekdayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

@Composable
internal fun MedicationCard(
    med: MedicationEntity,
    todayDoses: List<DoseLogEntity>,
    onTake: (String) -> Unit,
    onSkip: (String) -> Unit,
    onUndo: (String) -> Unit,
    onEditTime: (String, LocalTime) -> Unit,
    onLogPrn: (() -> Unit)?,
    onPause: () -> Unit,
    onDiscontinue: () -> Unit,
    onDeleteMedication: () -> Unit,
    onClick: () -> Unit
) {
    // Пропущенный приём тоже можно закрыть: человек мог принять лекарство позже.
    val pending = todayDoses.filter { it.status == "pending" || it.status == "missed" }
        .sortedBy { it.scheduledTime }
    val taken = todayDoses.any { it.status == "taken" }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    OporaCard(onClick = onClick) {
        Column {
            IconTextRow(
                title = med.name,
                subtitle = dosageWithForm(context, med.dosage, med.form).ifBlank { null },
                leadingSize = 12.dp,
                leading = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(med.color))
                    )
                },
                titleStyle = MaterialTheme.typography.titleMedium,
                trailing = {
                    val menuDose = pending.firstOrNull() ?: todayDoses
                        .maxByOrNull { it.createdAt }
                    MedActionsMenu(
                        medication = med,
                        dose = menuDose,
                        callbacks = MedActionCallbacks(
                            onSkip = menuDose?.let { dose -> { onSkip(dose.id) } },
                            onEditTime = menuDose?.let { dose -> { time -> onEditTime(dose.id, time) } },
                            onUndo = menuDose?.let { dose -> { onUndo(dose.id) } },
                            onPause = onPause,
                            onDiscontinue = onDiscontinue,
                            onEditMedication = onClick,
                            onDeleteMedication = onDeleteMedication
                        ),
                        contentDescription = medMenuDescription(med)
                    )
                },
                trailingFirstLine = true,
                trailingSize = 48.dp
            )
            // Бейдж отдельной строкой: рядом с названием он отбирал ширину
            // и при крупном шрифте сжимал название до нескольких букв.
            val badge = when {
                med.scheduleType == "as_needed" ->
                    stringResource(R.string.meds_schedule_as_needed) to
                        MaterialTheme.colorScheme.tertiaryContainer
                med.endDate?.let { it < LocalDate.now().toEpochDay() } == true ->
                    // Курс закончился, но запись активна: приёмов не будет,
                    // пока не изменить дату окончания.
                    stringResource(R.string.stop_course) to MaterialTheme.colorScheme.surfaceVariant
                else -> null
            }
            if (badge != null) {
                Spacer(Modifier.height(OporaSpacing.s))
                Pill(badge.first, color = badge.second)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                scheduleSummary(context, med),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (med.purpose.isNotBlank()) {
                Spacer(Modifier.height(OporaSpacing.xs))
                Text(
                    med.purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(OporaSpacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                med.inventoryCount?.let { count ->
                    val threshold = med.refillThreshold
                    val low = threshold != null && count <= threshold
                    val amount = formatAmount(count, locale)
                    val stockA11y = if (low) {
                        stringResource(R.string.stock_low_line, amount, unit(context, med))
                    } else {
                        stringResource(R.string.stock_line, amount, unit(context, med))
                    }
                    Pill(
                        stringResource(R.string.stock_line, amount, unit(context, med)),
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = stockA11y
                        },
                        icon = Icons.Outlined.Inventory2,
                        color = if (low) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (low) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.action_edit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(OporaIconSize.inline)
                )
            }
            if (onLogPrn != null) {
                Spacer(Modifier.height(OporaSpacing.s))
                FilledTonalButton(onClick = onLogPrn) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(OporaIconSize.inline)
                    )
                    Spacer(Modifier.width(OporaSpacing.s))
                    Text(stringResource(R.string.med_dose_log_action))
                }
            }
            if (pending.isNotEmpty()) {
                Spacer(Modifier.height(OporaSpacing.s))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pending.forEach { dose ->
                        FilledTonalButton(
                            onClick = { onTake(dose.id) },
                            contentPadding = PaddingValues(horizontal = OporaSpacing.l)
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(OporaIconSize.inline)
                            )
                            Spacer(Modifier.width(OporaSpacing.s))
                            Text(stringResource(R.string.med_dose_take_at, dose.scheduledTime))
                        }
                    }
                }
            } else if (taken) {
                Spacer(Modifier.height(OporaSpacing.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OporaIconSize.inline)
                    )
                    Spacer(Modifier.width(OporaSpacing.s))
                    Text(
                        stringResource(R.string.med_dose_taken_today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * «Ещё» без дозы не различить у двух карточек с одинаковым названием:
 * TalkBack должен назвать именно эту.
 */
@Composable
private fun medMenuDescription(med: MedicationEntity): String =
    stringResource(R.string.meds_more_a11y, medLabel(med))

/** Название с дозой: различает две карточки с одинаковым названием. */
private fun medLabel(med: MedicationEntity): String =
    listOf(med.name, med.dosage.trim())
        .filter { it.isNotBlank() }
        .joinToString(", ")

/**
 * Лекарство вне приёма: строка состояния вместо кнопок «принять» — приём
 * уже не идёт, а вернуть или прекратить его можно из меню.
 */
@Composable
private fun InactiveMedicationCard(
    med: MedicationEntity,
    status: String,
    onResume: (() -> Unit)? = null,
    onDiscontinue: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    OporaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    med.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dosageWithForm(context, med.dosage, med.form)
                        .ifBlank { formLabel(context, med.form) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            MedActionsMenu(
                medication = med,
                dose = null,
                callbacks = MedActionCallbacks(
                    onResume = onResume,
                    onDiscontinue = onDiscontinue,
                    onDeleteMedication = onDelete
                ),
                contentDescription = medMenuDescription(med)
            )
        }
    }
}

fun formUnit(context: Context, form: String?): String = when (form) {
    "tablet" -> context.getString(R.string.med_unit_tablet)
    "capsule" -> context.getString(R.string.med_unit_capsule)
    "drops" -> context.getString(R.string.med_unit_drops)
    "syrup" -> context.getString(R.string.med_unit_syrup)
    "injection" -> context.getString(R.string.med_unit_injection)
    "spray" -> context.getString(R.string.med_unit_spray)
    "patch" -> context.getString(R.string.med_unit_patch)
    "other" -> context.getString(R.string.med_unit_other)
    else -> context.getString(R.string.med_unit_tablet)
}

fun unit(context: Context, med: MedicationEntity): String = formUnit(context, med.form)

fun formLabel(context: Context, form: String?): String = when (form) {
    "capsule" -> context.getString(R.string.med_form_capsule)
    "drops" -> context.getString(R.string.med_form_drops)
    "syrup" -> context.getString(R.string.med_form_syrup)
    "injection" -> context.getString(R.string.med_form_injection)
    "spray" -> context.getString(R.string.med_form_spray)
    "patch" -> context.getString(R.string.med_form_patch)
    "other" -> context.getString(R.string.med_form_other)
    else -> context.getString(R.string.med_form_tablet)
}

private fun dayLabel(epochDay: Long, locale: Locale, context: Context): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when {
        date == today -> context.getString(R.string.meds_day_today_label)
        date == today.minusDays(1) -> context.getString(R.string.meds_day_yesterday_label)
        else -> date.weekdayDate(locale)
    }
}
