package app.opora.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.domain.hhmm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Вторичные действия над лекарством и его приёмом. Набор один для Today,
 * карточки лекарства и истории; недоступные по состоянию пункты скрываются,
 * а первичные действия (принять, отложить) остаются на своих кнопках.
 */
data class MedActionCallbacks(
    val onSkip: (() -> Unit)? = null,
    val onEditTime: ((LocalTime) -> Unit)? = null,
    val onUndo: (() -> Unit)? = null,
    val onDeleteDose: (() -> Unit)? = null,
    val onPause: (() -> Unit)? = null,
    val onResume: (() -> Unit)? = null,
    val onDiscontinue: (() -> Unit)? = null,
    val onEditMedication: (() -> Unit)? = null,
    val onDeleteMedication: (() -> Unit)? = null
)

@Composable
fun MedActionsMenu(
    medication: MedicationEntity?,
    dose: DoseLogEntity?,
    callbacks: MedActionCallbacks,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var deleteDose by remember { mutableStateOf(false) }
    var deleteMedication by remember { mutableStateOf(false) }

    val active = medication?.active == true
    val paused = medication != null && !medication.active && medication.discontinuedAt == null
    val discontinued = medication?.discontinuedAt != null

    val doseActions = buildList {
        // У приостановленного и прекращённого лекарства меню только про само
        // лекарство: прошлые приёмы не должны предлагать действия, которые
        // вернут его в расписание.
        if (dose != null && (medication == null || active)) {
            if ((dose.status == "pending" || dose.status == "missed") && !dose.isPrn &&
                callbacks.onSkip != null
            ) add("skip")
            // Меняем только фактическое время уже принятой дозы; перенос
            // плановой дозы — отдельная операция расписания.
            if (dose.status == "taken" && callbacks.onEditTime != null) add("time")
            if (dose.status != "pending" && !dose.isPrn && medication != null &&
                callbacks.onUndo != null
            ) add("undo")
            if (dose.isPrn && callbacks.onDeleteDose != null) add("deleteDose")
        }
    }

    Box(modifier) {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = contentDescription)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            doseActions.forEach { action ->
                when (action) {
                    "skip" -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.dose_skip_action)) },
                        leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                        onClick = { menu = false; callbacks.onSkip?.invoke() }
                    )
                    "time" -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.actions_edit_time)) },
                        leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                        onClick = { menu = false; timePicker = true }
                    )
                    "undo" -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.actions_undo)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null) },
                        onClick = { menu = false; callbacks.onUndo?.invoke() }
                    )
                    "deleteDose" -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.actions_delete_dose)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = { menu = false; deleteDose = true }
                    )
                }
            }
            if (doseActions.isNotEmpty() && (active || paused || discontinued)) {
                HorizontalDivider()
            }
            if (medication != null && active) {
                if (callbacks.onPause != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_pause)) },
                    leadingIcon = { Icon(Icons.Outlined.Pause, contentDescription = null) },
                    onClick = { menu = false; callbacks.onPause?.invoke() }
                )
                if (callbacks.onDiscontinue != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_discontinue)) },
                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                    onClick = { menu = false; callbacks.onDiscontinue?.invoke() }
                )
                if (callbacks.onEditMedication != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_edit_medication)) },
                    leadingIcon = { Icon(Icons.Outlined.Medication, contentDescription = null) },
                    onClick = { menu = false; callbacks.onEditMedication?.invoke() }
                )
            }
            if (medication != null && paused) {
                if (callbacks.onResume != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_resume)) },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    onClick = { menu = false; callbacks.onResume?.invoke() }
                )
                if (callbacks.onDiscontinue != null) DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_discontinue)) },
                    leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                    onClick = { menu = false; callbacks.onDiscontinue?.invoke() }
                )
            }
            if (medication != null && discontinued && callbacks.onDeleteMedication != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.actions_delete_medication)) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    onClick = { menu = false; deleteMedication = true }
                )
            }
        }
    }

    if (timePicker && dose != null && callbacks.onEditTime != null) {
        val initial = dose.takenAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: runCatching { LocalTime.parse(dose.scheduledTime) }
            .getOrDefault(LocalTime.of(9, 0))
        OporaTimePickerDialog(
            initial = initial,
            onDismiss = { timePicker = false },
            onSelect = {
                timePicker = false
                callbacks.onEditTime?.invoke(it)
            }
        )
    }
    if (deleteDose && dose != null) {
        val time = dose.takenAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        }?.hhmm() ?: dose.scheduledTime
        ConfirmDialog(
            title = stringResource(R.string.actions_delete_dose_title),
            text = stringResource(R.string.actions_delete_dose_body, dose.medicationName, time),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteDose = false
                callbacks.onDeleteDose?.invoke()
            },
            onDismiss = { deleteDose = false }
        )
    }
    if (deleteMedication && medication != null) {
        ConfirmDialog(
            title = stringResource(R.string.actions_delete_med_title),
            text = stringResource(R.string.actions_delete_med_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteMedication = false
                callbacks.onDeleteMedication?.invoke()
            },
            onDismiss = { deleteMedication = false }
        )
    }
}
