@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.opora.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.opora.ui.theme.OporaSpacing
import app.opora.R
import app.opora.data.backup.AutoBackupEntry
import app.opora.data.backup.AutoBackupFailure
import app.opora.data.backup.AutoBackupOutcome
import app.opora.data.backup.BackupPayload
import app.opora.data.backup.RestoreStage
import app.opora.data.backup.RestoreState
import app.opora.data.prefs.AppSettings
import app.opora.notifications.MedicationAlarms
import app.opora.security.BackupCrypto
import app.opora.ui.LocalAppContainer
import app.opora.ui.components.IconTextRow
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.FormDialog
import app.opora.ui.components.rememberKeyboardHider
import app.opora.ui.components.formDialogField
import app.opora.ui.components.OporaCard
import app.opora.ui.components.OporaSnackbarHost
import app.opora.ui.components.ScreenTitle
import app.opora.ui.components.SectionHeader
import app.opora.data.UserMessageException
import app.opora.data.userMessage
import app.opora.domain.dateTime
import app.opora.domain.formatDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val backupProgressLabel = stringResource(R.string.backup_progress_a11y)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var pendingPass by remember { mutableStateOf<CharArray?>(null) }
    var passDialog by remember { mutableStateOf(false) }
    var showImportPass by remember { mutableStateOf(false) }
    var importPassError by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var restoreConfirm by remember { mutableStateOf<BackupPayload?>(null) }
    var autoEntries by remember { mutableStateOf<List<AutoBackupEntry>>(emptyList()) }
    var autoEntryToRestore by remember { mutableStateOf<AutoBackupEntry?>(null) }
    var autoRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var autoRestorePassError by remember { mutableStateOf("") }
    var showAutoPassword by remember { mutableStateOf(false) }
    var showAutoRestorePass by remember { mutableStateOf(false) }
    var showChannel by remember { mutableStateOf(false) }
    var showWebDav by remember { mutableStateOf(false) }
    var disableWebDavConfirm by remember { mutableStateOf(false) }
    var jsonExportConfirm by remember { mutableStateOf(false) }
    var webDavUrl by remember(settings.webDavUrl) { mutableStateOf(settings.webDavUrl) }
    var webDavUser by remember(settings.webDavUser) { mutableStateOf(settings.webDavUser) }
    var webDavPassword by remember { mutableStateOf("") }
    var webDavBackupPassword by remember { mutableStateOf("") }
    var webDavUrlError by remember { mutableStateOf("") }
    var webDavUserError by remember { mutableStateOf("") }
    var webDavPassError by remember { mutableStateOf("") }
    var webDavBackupPassError by remember { mutableStateOf("") }
    var showWebDavPass by remember { mutableStateOf(false) }
    var showWebDavBackupPass by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val restoreState by container.restore.state.collectAsState()
    var localKeyWarning by remember { mutableStateOf(container.backup.keyWarning()) }
    val restoreRunning = restoreState is RestoreState.Running
    val webDavConfigured = settings.webDavEnabled && settings.webDavUrl.isNotBlank()
    val webDavBackupReady = settings.webDavBackupPasswordSet
    val autoConfigured = settings.autoBackupFolderUri.isNotBlank() &&
        settings.autoBackupPasswordSet && !settings.autoBackupPasswordLost

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        localKeyWarning = container.backup.keyWarning()
        scope.launch { autoEntries = container.autoBackup.listBackups() }
    }
    LaunchedEffect(settings.autoBackupFolderUri) {
        autoEntries = container.autoBackup.listBackups()
    }

    suspend fun refreshAuto() {
        autoEntries = container.autoBackup.listBackups()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            container.autoBackup.persistPermission(uri)
            busy = true
            scope.launch {
                val label = withContext(Dispatchers.IO) { container.autoBackup.folderLabel(uri) }
                container.settings.setAutoBackupFolder(uri.toString(), label)
                container.workScheduler.scheduleAutoBackup()
                error = ""
                message = context.getString(R.string.backup_auto_folder_saved)
                if (container.autoBackup.isConfigured()) {
                    when (val outcome = container.autoBackup.run(force = true)) {
                        is AutoBackupOutcome.Saved ->
                            message = context.getString(R.string.backup_auto_saved_now)
                        is AutoBackupOutcome.Failed ->
                            error = outcome.reason.text(context)
                        else -> Unit
                    }
                }
                refreshAuto()
                busy = false
            }
        }
    }

    fun exportEncrypted(uri: Uri) {
        val pass = pendingPass ?: return
        if (busy) {
            pendingPass = null
            return
        }
        busy = true
        scope.launch {
            runCatching { container.backup.exportEncrypted(uri, pass) }
                .onSuccess { message = context.getString(R.string.backup_export_done) }
                .onFailure { error = it.screenMessage(context, R.string.backup_error_save) }
            busy = false
            pendingPass = null
        }
    }

    val exportEncryptedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            pendingPass = null
        } else {
            exportEncrypted(uri)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                runCatching { container.backup.exportJson(uri) }
                    .onSuccess { message = context.getString(R.string.backup_json_export_done) }
                    .onFailure { error = it.screenMessage(context, R.string.backup_error_save) }
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                runCatching {
                    val bytes = container.backup.readFile(uri)
                    if (BackupCrypto.isEncrypted(bytes)) {
                        pendingImport = uri
                        importPassError = ""
                        showImportPass = true
                    } else {
                        val payload = container.backup.readBackup(uri, null)
                        restoreConfirm = payload
                    }
                }.onFailure { error = it.screenMessage(context, R.string.backup_error_read_file) }
                busy = false
            }
        }
    }

    fun importEncrypted(pass: CharArray) {
        val uri = pendingImport ?: return
        if (busy) return
        busy = true
        scope.launch {
            runCatching { container.backup.readBackup(uri, pass) }
                .onSuccess {
                    restoreConfirm = it
                    pendingImport = null
                    importPassError = ""
                }
                .onFailure {
                    // Файл уже выбран: не заставляем проходить пикер заново.
                    importPassError = it.screenMessage(context, R.string.backup_error_decrypt)
                    showImportPass = true
                }
            busy = false
        }
    }

    LaunchedEffect(error) {
        if (error.isNotBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = context.getString(R.string.backup_ack),
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                error = ""
            }
        }
    }
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            message = ""
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .imePadding(),
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
                    title = stringResource(R.string.backup_title),
                    onBack = onBack
                )
            }
            if (localKeyWarning != null) {
                item {
                    OporaCard(color = MaterialTheme.colorScheme.errorContainer) {
                        Column {
                            Text(
                                localKeyWarning!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = {
                                    container.backup.clearKeyWarning()
                                    localKeyWarning = null
                                }
                            ) { Text(stringResource(R.string.backup_ack)) }
                        }
                    }
                }
            }
            if (settings.webDavKeyWarning) {
                item {
                    OporaCard(color = MaterialTheme.colorScheme.errorContainer) {
                        Column {
                            Text(
                                stringResource(R.string.webdav_key_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { showWebDav = true }) {
                                Text(stringResource(R.string.webdav_open_settings))
                            }
                        }
                    }
                }
            }
            item {
                when (val state = restoreState) {
                    is RestoreState.Running -> OporaCard(
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.restore_progress_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(
                                    when (state.stage) {
                                        RestoreStage.SNAPSHOT -> R.string.restore_stage_snapshot
                                        RestoreStage.WRITING -> R.string.restore_stage_writing
                                        RestoreStage.REARMING -> R.string.restore_stage_rearming
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { container.restore.cancel() }) {
                                Text(stringResource(R.string.restore_cancel))
                            }
                        }
                    }
                    is RestoreState.Success -> OporaCard(
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            stringResource(
                                R.string.restore_success,
                                pluralStringResource(R.plurals.entries, state.records, state.records)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is RestoreState.Failed -> OporaCard(
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    RestoreState.Cancelled -> OporaCard(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            stringResource(R.string.restore_cancelled),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    RestoreState.Idle -> Unit
                }
            }
            item {
                OporaCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    IconTextRow(
                        icon = Icons.Outlined.Security,
                        title = stringResource(R.string.backup_encryption_hint),
                        titleStyle = MaterialTheme.typography.bodySmall,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        titleColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(R.string.backup_section_device))
            }
            item {
                OporaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.backup_export_import_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.backup_export_import_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                if (busy) return@Button
                                error = ""
                                passDialog = true
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.backup_export_with_password))
                        }
                        OutlinedButton(
                            onClick = {
                                if (busy || restoreRunning) return@OutlinedButton
                                error = ""
                                importLauncher.launch(arrayOf("application/octet-stream", "application/json"))
                            },
                            enabled = !busy && !restoreRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.backup_import_from_file))
                        }
                        OutlinedButton(
                            onClick = {
                                if (busy) return@OutlinedButton
                                error = ""
                                jsonExportConfirm = true
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.backup_export_json))
                        }
                        Text(
                            stringResource(R.string.backup_json_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OporaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.backup_auto_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.backup_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            stringResource(R.string.backup_auto_folder_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            settings.autoBackupFolderLabel.ifBlank {
                                stringResource(R.string.backup_auto_folder_none)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.autoBackupFolderUri.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!busy) folderLauncher.launch(null)
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text(
                                    stringResource(
                                        if (settings.autoBackupFolderUri.isBlank()) {
                                            R.string.backup_auto_folder_choose
                                        } else {
                                            R.string.backup_auto_folder_change
                                        }
                                    )
                                )
                            }
                            if (settings.autoBackupFolderUri.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        if (busy) return@TextButton
                                        scope.launch {
                                            busy = true
                                            container.settings.clearAutoBackupFolder()
                                            message = context.getString(R.string.backup_auto_folder_removed)
                                            refreshAuto()
                                            busy = false
                                        }
                                    },
                                    enabled = !busy
                                ) {
                                    Text(
                                        stringResource(R.string.backup_auto_folder_remove),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Text(
                            stringResource(R.string.backup_auto_password_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(
                                when {
                                    settings.autoBackupPasswordLost ->
                                        R.string.backup_auto_password_state_lost
                                    settings.autoBackupPasswordSet ->
                                        R.string.backup_auto_password_state_set
                                    else -> R.string.backup_auto_password_state_missing
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                settings.autoBackupPasswordLost -> MaterialTheme.colorScheme.error
                                settings.autoBackupPasswordSet -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        OutlinedButton(
                            onClick = { if (!busy) showAutoPassword = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                stringResource(
                                    if (settings.autoBackupPasswordSet) {
                                        R.string.backup_auto_password_change
                                    } else {
                                        R.string.backup_auto_password_set_action
                                    }
                                )
                            )
                        }

                        if (!autoConfigured) {
                            Text(
                                stringResource(R.string.backup_auto_not_configured),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.backup_auto_not_configured_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            if (settings.lastAutoBackupAt > 0) {
                                stringResource(
                                    R.string.backup_auto_status_last,
                                    settings.lastAutoBackupAt.dateTime(locale)
                                )
                            } else {
                                stringResource(R.string.backup_auto_status_never)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (settings.autoBackupError.isNotBlank()) {
                            Text(
                                stringResource(
                                    R.string.backup_auto_status_error,
                                    autoErrorText(context, settings.autoBackupError)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                if (busy) return@FilledTonalButton
                                busy = true
                                scope.launch {
                                    when (val outcome = container.autoBackup.run(force = true)) {
                                        is AutoBackupOutcome.Saved -> {
                                            message = context.getString(R.string.backup_auto_saved_now)
                                            error = ""
                                        }
                                        is AutoBackupOutcome.Failed -> {
                                            error = context.getString(
                                                R.string.backup_auto_status_error,
                                                outcome.reason.text(context)
                                            )
                                        }
                                        AutoBackupOutcome.NotConfigured,
                                        AutoBackupOutcome.Skipped -> Unit
                                    }
                                    refreshAuto()
                                    busy = false
                                }
                            },
                            enabled = !busy && autoConfigured,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(stringResource(R.string.backup_auto_create_now))
                        }
                        Text(
                            stringResource(R.string.backup_auto_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (autoEntries.isEmpty()) {
                            Text(
                                stringResource(R.string.backup_auto_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                stringResource(R.string.backup_auto_files_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            autoEntries.take(7).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            if (entry.size >= 0) {
                                                stringResource(
                                                    R.string.backup_file_meta,
                                                    entry.savedAt.dateTime(locale),
                                                    formatSize(context, entry.size, locale)
                                                )
                                            } else {
                                                entry.savedAt.dateTime(locale)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            if (!busy && !restoreRunning) {
                                                autoEntryToRestore = entry
                                            }
                                        },
                                        enabled = !busy && !restoreRunning,
                                        modifier = Modifier.semantics {
                                            contentDescription = context.getString(
                                                R.string.backup_restore_a11y,
                                                entry.savedAt.dateTime(locale)
                                            )
                                        }
                                    ) { Text(stringResource(R.string.backup_restore_action)) }
                                }
                            }
                            Text(
                                stringResource(R.string.backup_auto_restore_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.webdav_section_title),
                    subtitle = stringResource(R.string.webdav_section_subtitle),
                    action = {
                        TextButton(onClick = { showWebDav = !showWebDav }, enabled = !busy) {
                            Icon(
                                if (showWebDav) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null
                            )
                            Text(
                                stringResource(
                                    if (showWebDav) R.string.webdav_collapse else R.string.webdav_configure
                                )
                            )
                        }
                    }
                )
            }
            if (showWebDav) {
                item {
                    OporaCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.webdav_optional_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.webDavKeyWarning) {
                                Text(
                                    stringResource(R.string.webdav_key_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedTextField(
                                value = webDavUrl,
                                onValueChange = {
                                    webDavUrl = it
                                    webDavUrlError = ""
                                },
                                label = { Text(stringResource(R.string.webdav_url_label)) },
                                placeholder = { Text("https://cloud.example.com/remote.php/dav/files/me/opora") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !busy,
                                isError = webDavUrlError.isNotBlank(),
                                supportingText = if (webDavUrlError.isNotBlank()) {
                                    { Text(webDavUrlError) }
                                } else null,
                                shape = MaterialTheme.shapes.large
                            )
                            OutlinedTextField(
                                value = webDavUser,
                                onValueChange = {
                                    webDavUser = it
                                    webDavUserError = ""
                                },
                                label = { Text(stringResource(R.string.webdav_user_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !busy,
                                isError = webDavUserError.isNotBlank(),
                                supportingText = if (webDavUserError.isNotBlank()) {
                                    { Text(webDavUserError) }
                                } else null,
                                shape = MaterialTheme.shapes.large
                            )
                            OutlinedTextField(
                                value = webDavPassword,
                                onValueChange = {
                                    webDavPassword = it
                                    webDavPassError = ""
                                },
                                label = { Text(stringResource(R.string.webdav_password_label)) },
                                placeholder = {
                                    Text(
                                        stringResource(
                                            if (settings.webDavUrl.isNotBlank()) {
                                                R.string.webdav_keep_empty
                                            } else {
                                                R.string.webdav_app_password
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false
                                ),
                                visualTransformation = if (showWebDavPass) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showWebDavPass = !showWebDavPass },
                                        enabled = !busy
                                    ) {
                                        Icon(
                                            if (showWebDavPass) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = stringResource(
                                                if (showWebDavPass) {
                                                    R.string.webdav_hide_password
                                                } else {
                                                    R.string.webdav_show_password
                                                }
                                            )
                                        )
                                    }
                                },
                                isError = webDavPassError.isNotBlank(),
                                supportingText = if (webDavPassError.isNotBlank()) {
                                    { Text(webDavPassError) }
                                } else null,
                                shape = MaterialTheme.shapes.large
                            )
                            OutlinedTextField(
                                value = webDavBackupPassword,
                                onValueChange = {
                                    webDavBackupPassword = it
                                    webDavBackupPassError = ""
                                },
                                label = { Text(stringResource(R.string.webdav_backup_password_label)) },
                                placeholder = {
                                    Text(
                                        stringResource(
                                            if (settings.webDavBackupPasswordSet) {
                                                R.string.webdav_keep_empty
                                            } else {
                                                R.string.webdav_backup_password_new
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false
                                ),
                                visualTransformation = if (showWebDavBackupPass) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showWebDavBackupPass = !showWebDavBackupPass },
                                        enabled = !busy
                                    ) {
                                        Icon(
                                            if (showWebDavBackupPass) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = stringResource(
                                                if (showWebDavBackupPass) {
                                                    R.string.webdav_hide_backup_password
                                                } else {
                                                    R.string.webdav_show_backup_password
                                                }
                                            )
                                        )
                                    }
                                },
                                isError = webDavBackupPassError.isNotBlank(),
                                supportingText = if (webDavBackupPassError.isNotBlank()) {
                                    { Text(webDavBackupPassError) }
                                } else null,
                                shape = MaterialTheme.shapes.large
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (busy) return@Button
                                        val url = webDavUrl.trim()
                                        val user = webDavUser.trim()
                                        val passwordRequired =
                                            context.getString(R.string.webdav_error_password_required)
                                        val backupPasswordRequired =
                                            context.getString(R.string.webdav_error_backup_password_required)
                                        webDavUrlError = when {
                                            url.isBlank() ->
                                                context.getString(R.string.webdav_error_url_required)
                                            !url.startsWith("https://", ignoreCase = true) ->
                                                context.getString(R.string.webdav_error_https)
                                            else -> ""
                                        }
                                        webDavUserError = if (user.isBlank()) {
                                            context.getString(R.string.webdav_error_user_required)
                                        } else {
                                            ""
                                        }
                                        webDavPassError = if (webDavPassword.isBlank() && settings.webDavUrl.isBlank()) {
                                            passwordRequired
                                        } else {
                                            ""
                                        }
                                        webDavBackupPassError =
                                            if (webDavBackupPassword.isBlank() && !settings.webDavBackupPasswordSet) {
                                                backupPasswordRequired
                                            } else {
                                                ""
                                            }
                                        if (webDavUrlError.isNotBlank() ||
                                            webDavUserError.isNotBlank() ||
                                            webDavPassError.isNotBlank() ||
                                            webDavBackupPassError.isNotBlank()
                                        ) {
                                            return@Button
                                        }
                                        busy = true
                                        scope.launch {
                                            runCatching {
                                                val saved = container.settings.webDavConfig()
                                                val effective = webDavPassword.ifBlank { saved?.password.orEmpty() }
                                                require(effective.isNotBlank()) { passwordRequired }
                                                val effectiveBackup =
                                                    webDavBackupPassword.ifBlank { saved?.backupPassword.orEmpty() }
                                                require(effectiveBackup.isNotBlank()) { backupPasswordRequired }
                                                // Сначала проверяем сервер и только потом сохраняем настройки.
                                                container.sync.test(url, user, effective).getOrThrow()
                                                container.settings.setWebDav(
                                                    url,
                                                    user,
                                                    webDavPassword.ifBlank { null },
                                                    webDavBackupPassword.ifBlank { null }
                                                )
                                                container.settings.setWebDavEnabled(true)
                                                container.workScheduler.scheduleAutoSync()
                                            }.onSuccess {
                                                message = context.getString(R.string.webdav_saved)
                                                error = ""
                                                webDavUrlError = ""
                                                webDavUserError = ""
                                                webDavPassError = ""
                                                webDavBackupPassError = ""
                                                webDavPassword = ""
                                                webDavBackupPassword = ""
                                                showWebDavPass = false
                                                showWebDavBackupPass = false
                                            }.onFailure {
                                                val text = it.screenMessage(
                                                    context,
                                                    R.string.webdav_error_connect
                                                )
                                                error = text
                                                if (text == passwordRequired) {
                                                    webDavPassError = text
                                                } else if (text == backupPasswordRequired) {
                                                    webDavBackupPassError = text
                                                }
                                            }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.webdav_save)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (busy || !webDavConfigured) return@OutlinedButton
                                        busy = true
                                        scope.launch {
                                            container.sync.upload()
                                                .onSuccess { message = context.getString(R.string.webdav_upload_done) }
                                                .onFailure {
                                                    error = it.screenMessage(
                                                        context,
                                                        R.string.webdav_error_upload
                                                    )
                                                }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy && webDavConfigured && webDavBackupReady,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 3.dp))
                                    Text(stringResource(R.string.backup_upload))
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (busy || !webDavConfigured) return@OutlinedButton
                                        busy = true
                                        scope.launch {
                                            container.sync.download()
                                                .onSuccess { restoreConfirm = it }
                                                .onFailure {
                                                    error = it.screenMessage(
                                                        context,
                                                        R.string.webdav_error_download
                                                    )
                                                }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy && webDavConfigured,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 3.dp))
                                    Text(stringResource(R.string.backup_download))
                                }
                            }
                            if (!webDavConfigured) {
                                Text(
                                    stringResource(R.string.webdav_need_save_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (!webDavBackupReady) {
                                Text(
                                    stringResource(R.string.webdav_need_backup_pass_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .toggleable(
                                        value = settings.autoSync,
                                        enabled = !busy && webDavConfigured,
                                        role = Role.Switch,
                                        onValueChange = { enabled ->
                                            if (!busy && webDavConfigured) {
                                                scope.launch {
                                                    container.settings.setAutoSync(enabled)
                                                    if (enabled) {
                                                        container.workScheduler.scheduleAutoSync()
                                                    } else {
                                                        container.workScheduler.cancelAutoSync()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    .padding(vertical = OporaSpacing.s),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.webdav_auto_sync),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        stringResource(R.string.webdav_auto_sync_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(OporaSpacing.s))
                                Switch(
                                    checked = settings.autoSync,
                                    enabled = !busy && webDavConfigured,
                                    onCheckedChange = null
                                )
                            }
                            if (settings.lastSyncAt > 0) {
                                Text(
                                    stringResource(
                                        R.string.webdav_last_sync,
                                        settings.lastSyncAt.dateTime(locale)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (settings.lastSyncError.isNotBlank()) {
                                Text(
                                    settings.lastSyncError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                stringResource(R.string.webdav_encryption_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.webDavEnabled || settings.webDavUrl.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        if (!busy) disableWebDavConfirm = true
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        stringResource(R.string.webdav_disable),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                OporaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.backup_channel_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.backup_channel_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showChannel = !showChannel },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (showChannel) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null
                            )
                            Text(
                                stringResource(
                                    if (showChannel) {
                                        R.string.backup_channel_collapse
                                    } else {
                                        R.string.backup_channel_expand
                                    }
                                )
                            )
                        }
                        if (showChannel) {
                            Text(
                                stringResource(R.string.backup_channel_step1),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(R.string.backup_channel_step2),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(R.string.backup_channel_step3),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(R.string.backup_channel_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.restore_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .semantics { contentDescription = backupProgressLabel }
            )
        }
        OporaSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        )
    }

    if (passDialog) {
        PassphraseDialog(
            title = stringResource(R.string.backup_passphrase_title),
            description = stringResource(R.string.backup_passphrase_desc),
            requireConfirm = true,
            onDismiss = { passDialog = false },
            onConfirm = { pass ->
                passDialog = false
                pendingPass = pass
                exportEncryptedLauncher.launch("opora-backup-${dateStamp()}.opora")
            }
        )
    }
    if (showImportPass) {
        PassphraseDialog(
            title = stringResource(R.string.backup_import_pass_title),
            description = stringResource(R.string.backup_import_pass_desc),
            requireConfirm = false,
            initialError = importPassError,
            onDismiss = {
                showImportPass = false
                pendingImport = null
                importPassError = ""
            },
            onConfirm = { pass ->
                showImportPass = false
                importEncrypted(pass)
            }
        )
    }

    if (jsonExportConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.backup_json_confirm_title),
            text = stringResource(R.string.backup_json_confirm_body),
            confirmLabel = stringResource(R.string.backup_json_confirm_action),
            destructive = true,
            icon = Icons.Rounded.Warning,
            onConfirm = {
                exportJsonLauncher.launch("opora-export-${dateStamp()}.json")
            },
            onDismiss = { jsonExportConfirm = false }
        )
    }

    if (disableWebDavConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.webdav_disable_confirm_title),
            text = stringResource(R.string.webdav_disable_confirm_body),
            confirmLabel = stringResource(R.string.webdav_disable_confirm_action),
            onConfirm = {
                scope.launch {
                    busy = true
                    runCatching {
                        container.settings.clearWebDav()
                        container.workScheduler.cancelAutoSync()
                    }.onSuccess {
                        message = context.getString(R.string.webdav_disabled)
                        error = ""
                        webDavUrl = ""
                        webDavUser = ""
                        webDavPassword = ""
                    }.onFailure {
                        error = it.screenMessage(context, R.string.webdav_error_disable)
                    }
                    busy = false
                }
            },
            onDismiss = { disableWebDavConfirm = false }
        )
    }

    restoreConfirm?.let { payload ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(
                R.string.restore_confirm_body,
                container.backup.restorePointDisplayPath(),
                pluralStringResource(
                    R.plurals.entries,
                    payload.totalRecords,
                    payload.totalRecords
                )
            ),
            confirmLabel = stringResource(R.string.backup_restore_action),
            destructive = true,
            onConfirm = {
                if (!busy && !restoreRunning) {
                    container.restore.start(payload)
                    restoreConfirm = null
                }
            },
            onDismiss = { restoreConfirm = null }
        )
    }

    autoEntryToRestore?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.restore_auto_confirm_title),
            text = stringResource(
                R.string.restore_auto_confirm_body,
                container.backup.restorePointDisplayPath(),
                entry.name
            ),
            confirmLabel = stringResource(R.string.backup_restore_action),
            destructive = true,
            onConfirm = {
                if (!busy && !restoreRunning) {
                    autoEntryToRestore = null
                    busy = true
                    scope.launch {
                        val stored = container.settings.autoBackupPassword()
                        if (stored != null) {
                            val result = runCatching {
                                container.backup.readBackup(entry.uri, stored)
                            }
                            stored.fill('\u0000')
                            result.onSuccess { container.restore.start(it) }
                                .onFailure {
                                    // Пароль мог быть сменён: даём ввести его вручную.
                                    autoRestoreUri = entry.uri
                                    autoRestorePassError = ""
                                    showAutoRestorePass = true
                                }
                        } else {
                            autoRestoreUri = entry.uri
                            autoRestorePassError = ""
                            showAutoRestorePass = true
                        }
                        busy = false
                    }
                }
            },
            onDismiss = { autoEntryToRestore = null }
        )
    }

    if (showAutoPassword) {
        PassphraseDialog(
            title = stringResource(R.string.backup_auto_password_title),
            description = stringResource(R.string.backup_auto_password_desc),
            requireConfirm = true,
            onDismiss = { showAutoPassword = false },
            onConfirm = { pass ->
                showAutoPassword = false
                busy = true
                scope.launch {
                    val saved = container.settings.setAutoBackupPassword(pass)
                    pass.fill('\u0000')
                    if (!saved) {
                        error = context.getString(R.string.backup_auto_password_save_failed)
                    } else {
                        message = context.getString(R.string.backup_auto_password_saved)
                        error = ""
                        container.workScheduler.scheduleAutoBackup()
                        if (container.autoBackup.isConfigured()) {
                            container.autoBackup.run(force = true)
                            refreshAuto()
                        }
                    }
                    busy = false
                }
            }
        )
    }

    if (showAutoRestorePass) {
        PassphraseDialog(
            title = stringResource(R.string.backup_import_pass_title),
            description = stringResource(R.string.restore_prompt_password_hint),
            requireConfirm = false,
            initialError = autoRestorePassError,
            onDismiss = {
                showAutoRestorePass = false
                autoRestoreUri = null
                autoRestorePassError = ""
            },
            onConfirm = { pass ->
                val uri = autoRestoreUri ?: return@PassphraseDialog
                showAutoRestorePass = false
                busy = true
                scope.launch {
                    runCatching { container.backup.readBackup(uri, pass) }
                        .onSuccess {
                            container.restore.start(it)
                            autoRestoreUri = null
                            autoRestorePassError = ""
                        }
                        .onFailure {
                            autoRestorePassError = it.screenMessage(
                                context,
                                R.string.backup_error_decrypt
                            )
                            showAutoRestorePass = true
                        }
                    pass.fill('\u0000')
                    busy = false
                }
            }
        )
    }
}

@Composable
internal fun PassphraseDialog(
    title: String,
    description: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
    initialError: String = ""
) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember(initialError) { mutableStateOf(initialError) }
    var reveal by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mismatch = requireConfirm && confirm.isNotEmpty() && pass != confirm
    val transformation = if (reveal) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }
    val hideKeyboard = rememberKeyboardHider()
    FormDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.backup_continue),
        dismissLabel = stringResource(R.string.backup_cancel),
        onConfirm = {
                when {
                    pass.isEmpty() -> error = context.getString(R.string.backup_passphrase_required)
                    requireConfirm && pass.length < 8 ->
                        error = context.getString(R.string.backup_passphrase_min)
                    requireConfirm && pass != confirm ->
                        error = context.getString(R.string.backup_passphrase_mismatch)
                    else -> {
                        error = ""
                        onConfirm(pass.toCharArray())
                    }
                }
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(OporaSpacing.s))
            OutlinedTextField(
                value = pass,
                onValueChange = {
                    pass = it
                    error = ""
                },
                label = { Text(stringResource(R.string.backup_passphrase_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = if (requireConfirm) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                visualTransformation = transformation,
                isError = error.isNotBlank(),
                supportingText = if (error.isNotBlank()) {
                    { Text(error) }
                } else null,
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(
                                if (reveal) R.string.webdav_hide_password else R.string.webdav_show_password
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().formDialogField()
            )
            if (requireConfirm) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        error = ""
                    },
                    label = { Text(stringResource(R.string.backup_passphrase_repeat)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                    visualTransformation = transformation,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.backup_passphrase_mismatch)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().formDialogField()
                )
            }
        }
    }
}

private fun dateStamp(): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

/**
 * Backup code often fails without a user-facing message. Data errors carry a
 * resource and are resolved; generic failures fall back to the screen's own hint.
 */
private fun Throwable.screenMessage(context: Context, @StringRes fallback: Int): String =
    if (this is UserMessageException) {
        userMessage(context)
    } else {
        message ?: context.getString(fallback)
    }

private fun formatSize(context: Context, bytes: Long, locale: Locale): String = when {
    bytes < 1024 -> context.getString(R.string.backup_size_b, bytes.toString())
    bytes < 1024 * 1024 ->
        context.getString(R.string.backup_size_kb, (bytes / 1024).toString())
    else -> context.getString(
        R.string.backup_size_mb,
        formatDecimal(bytes / 1024.0 / 1024.0, locale)
    )
}

private fun AutoBackupFailure.text(context: Context): String =
    context.getString(messageRes)

private fun autoErrorText(context: Context, code: String): String =
    AutoBackupFailure.fromCode(code)?.text(context) ?: code
