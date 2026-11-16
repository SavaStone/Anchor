package app.opora.ui.screens

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.opora.R
import app.opora.ui.theme.OporaIconSize
import app.opora.reports.ReportGenerator
import app.opora.data.OporaRepository
import app.opora.data.db.journalDate
import app.opora.data.prefs.SettingsRepository
import app.opora.ui.LocalAppContainer
import app.opora.ui.components.IconTextRow
import app.opora.ui.components.EmptyState
import app.opora.ui.components.OporaCard
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.domain.dateTime
import app.opora.domain.formatDecimal
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val ReportFileSaver = Saver<File?, String>(
    save = { it?.absolutePath.orEmpty() },
    restore = { path -> path.takeIf { it.isNotBlank() }?.let(::File) }
)

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repository: OporaRepository = LocalAppContainer.current.repository,
    settings: SettingsRepository = LocalAppContainer.current.settings
) {
    val context = LocalContext.current
    val appName = context.getString(R.string.app_name)
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val moodsFlow = remember { repository.moodEntries() }
    val allMoods by moodsFlow.collectAsState(initial = emptyList())
    val journalFlow = remember { repository.journalEntries() }
    val allJournal by journalFlow.collectAsState(initial = emptyList())
    var days by rememberSaveable { mutableStateOf(30) }
    var includeJournal by rememberSaveable { mutableStateOf(false) }
    var includeSymptoms by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var shareError by remember { mutableStateOf("") }
    var file by rememberSaveable(stateSaver = ReportFileSaver) { mutableStateOf<File?>(null) }

    val dosesFlow = remember(days) { repository.doseHistory(days) }
    val periodDoses by dosesFlow.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val from = today.minusDays(days.toLong() - 1)
    val periodMoods = allMoods.filter { it.date >= from.toEpochDay() }
    val periodJournal = allJournal.filter {
        val date = it.journalDate()
        date >= from && date <= today
    }
    val average = periodMoods.map { it.mood }.average().takeIf { !it.isNaN() }
    val hasData = periodMoods.isNotEmpty() || periodDoses.isNotEmpty() ||
        periodJournal.isNotEmpty()
    // Записи дневника — единственные данные за период: без них отчёт пуст,
    // поэтому включаем их сразу. Пользователь может выключить тумблер.
    LaunchedEffect(days, periodMoods.size, periodDoses.size, periodJournal.size) {
        if (periodMoods.isEmpty() && periodDoses.isEmpty() && periodJournal.isNotEmpty()) {
            includeJournal = true
        }
    }

    val allTimeDays = allMoods.minOfOrNull { it.date }?.let { earliest ->
        (ChronoUnit.DAYS.between(LocalDate.ofEpochDay(earliest), LocalDate.now()) + 1)
            .toInt()
            .coerceAtLeast(1)
    }
    val allTimeTarget = allTimeDays?.takeIf { it > 30 && it != 90 && it != 180 }

    fun discardGeneratedReport() {
        val previous = file
        file = null
        error = ""
        shareError = ""
        if (previous != null) runCatching { previous.delete() }
    }

    fun share(report: File) {
        if (!report.exists()) {
            file = null
            error = context.getString(R.string.report_file_missing)
            return
        }
        shareError = ""
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(
                    context.getString(R.string.report_share_label, appName),
                    uri
                )
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    context.getString(R.string.report_share_subject, appName)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.report_share_chooser)
                )
            )
        }.onFailure {
            shareError = context.getString(R.string.report_share_error)
        }
    }

    fun deleteReport(report: File) {
        if (runCatching { report.delete() }.getOrDefault(false)) {
            file = null
            shareError = ""
        } else {
            shareError = context.getString(R.string.report_delete_error)
        }
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
                title = stringResource(R.string.report_title),
                onBack = onBack
            )
        }
        item {
            OporaCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                IconTextRow(
                    icon = Icons.Outlined.PictureAsPdf,
                    title = stringResource(R.string.report_intro),
                    titleStyle = MaterialTheme.typography.bodySmall,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    gap = OporaSpacing.s
                )
            }
        }
        item { SectionHeader(stringResource(R.string.report_period)) }
        item {
            val periodOptions = listOfNotNull(
                30 to pluralStringResource(R.plurals.days, 30, 30),
                90 to pluralStringResource(R.plurals.days, 90, 90),
                180 to pluralStringResource(R.plurals.days, 180, 180),
                allTimeTarget?.let { it to stringResource(R.string.report_period_all) }
            )
            // Ровная сетка 2×2: одиночная «сирота» в FlowRow выглядела случайной.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                periodOptions.chunked(2).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = days == value,
                                onClick = {
                                    if (days != value) {
                                        days = value
                                        discardGeneratedReport()
                                    }
                                },
                                label = {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingIcon = if (days == value) {
                                    {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(OporaIconSize.inline)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (hasData) {
            item {
                OporaCard {
                    Column {
                        Text(
                            stringResource(R.string.report_mood_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            stringResource(
                                R.string.report_mood_summary,
                                pluralStringResource(
                                    R.plurals.mood_marks,
                                    periodMoods.size,
                                    periodMoods.size
                                ),
                                average?.let { formatDecimal(it, locale) }
                                    ?: stringResource(R.string.common_dash)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(OporaSpacing.s))
                        Text(
                            stringResource(R.string.report_doses_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            stringResource(
                                R.string.report_doses_summary,
                                pluralStringResource(
                                    R.plurals.doses,
                                    periodDoses.size,
                                    periodDoses.size
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(OporaSpacing.listGap))
                        Text(
                            stringResource(R.string.report_include_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        SettingSwitch(
                            title = stringResource(R.string.report_include_journal),
                            subtitle = stringResource(R.string.report_include_journal_hint),
                            checked = includeJournal,
                            onChange = {
                                includeJournal = it
                                discardGeneratedReport()
                            }
                        )
                        SettingSwitch(
                            title = stringResource(R.string.report_include_symptoms),
                            subtitle = stringResource(R.string.report_include_symptoms_hint),
                            checked = includeSymptoms,
                            onChange = {
                                includeSymptoms = it
                                discardGeneratedReport()
                            }
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = generate@{
                            if (busy) return@generate
                            busy = true
                            error = ""
                            val previous = file
                            val genDays = days
                            val genJournal = includeJournal
                            val genSymptoms = includeSymptoms
                            scope.launch {
                                runCatching {
                                    ReportGenerator(context, repository, settings)
                                        .generate(genDays, genJournal, genSymptoms)
                                }.onSuccess { generated ->
                                    if (generated != previous) {
                                        if (previous != null) runCatching { previous.delete() }
                                    }
                                    if (genDays == days && genJournal == includeJournal && genSymptoms == includeSymptoms) {
                                        file = generated
                                    } else {
                                        runCatching { generated.delete() }
                                    }
                                }.onFailure {
                                    error = context.getString(R.string.report_generate_error)
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(OporaSpacing.s))
                        Text(stringResource(R.string.report_generate))
                    }
                    if (busy) {
                        ReportProgressRow(stringResource(R.string.report_generating))
                    }
                    if (error.isNotBlank()) {
                        IconTextRow(
                            icon = Icons.Rounded.Warning,
                            title = error,
                            titleStyle = MaterialTheme.typography.bodySmall,
                            tint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            gap = OporaSpacing.s,
                            modifier = Modifier
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
            item {
                val report = file
                if (report != null && !busy) {
                    OporaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column {
                            Text(
                                stringResource(R.string.report_ready),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                report.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(
                                    R.string.report_created,
                                    report.lastModified().dateTime(locale)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(OporaSpacing.s))
                            Button(
                                onClick = { share(report) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text(stringResource(R.string.report_share))
                            }
                            if (shareError.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    shareError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { deleteReport(report) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text(stringResource(R.string.report_delete_file))
                            }
                        }
                    }
                }
            }
        } else {
            item {
                OporaCard(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    EmptyState(
                        icon = Icons.Outlined.PictureAsPdf,
                        title = stringResource(R.string.report_empty_title),
                        body = if (allMoods.isEmpty()) {
                            stringResource(R.string.report_empty_no_data)
                        } else {
                            stringResource(R.string.report_empty_period)
                        },
                        actionLabel = if (allTimeTarget != null) {
                            stringResource(R.string.report_period_all_action)
                        } else null,
                        onAction = allTimeTarget?.let { target ->
                            {
                                days = target
                                discardGeneratedReport()
                            }
                        }
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.report_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportProgressRow(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(OporaIconSize.regular),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(OporaSpacing.s))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

