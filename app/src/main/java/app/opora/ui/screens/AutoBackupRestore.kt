package app.opora.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.data.backup.BackupPayload
import app.opora.data.backup.RestoreOffer
import app.opora.data.backup.RestoreOfferKind
import app.opora.data.backup.RestoreState
import app.opora.data.userMessage
import app.opora.domain.dateTime
import app.opora.security.BackupCrypto
import app.opora.ui.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Открывает системный пикер сразу в «Загрузках». После переустановки это
 * единственный законный способ показать файл: scoped storage не даёт
 * приложению перечислять чужие файлы в Download.
 */
private class OpenBackupFile : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        runCatching {
            intent.putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootUri(
                    "com.android.externalstorage.documents",
                    "downloads"
                )
            )
        }
        return intent
    }
}

/**
 * Первый запуск после переустановки: база пуста, но в выбранной папке или
 * в «Загрузках» остался файл `.opora`. Предлагаем восстановить его сразу,
 * без похода в «Резервные копии». Пароль спрашиваем только если он не сохранён.
 */
@Composable
fun AutoBackupRestorePrompt() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val restoreState by container.restore.state.collectAsState()

    var offer by remember { mutableStateOf<RestoreOffer?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf("") }
    var pendingPayload by remember { mutableStateOf<BackupPayload?>(null) }
    var restored by remember { mutableStateOf<BackupPayload?>(null) }
    var failure by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val settings = container.settings.current()
        if (!settings.restorePromptDismissed && restored == null) {
            offer = container.autoBackup.restoreOffer()
        }
    }

    // Итог показываем только когда импорт действительно завершился.
    LaunchedEffect(restoreState) {
        when (val state = restoreState) {
            is RestoreState.Success -> {
                pendingPayload?.let { restored = it }
                pendingPayload = null
            }
            is RestoreState.Failed -> {
                failure = state.message
                pendingPayload = null
            }
            else -> Unit
        }
    }

    fun dismiss() {
        offer = null
        pendingUri = null
        showPassword = false
        scope.launch { container.settings.setRestorePromptDismissed(true) }
    }

    fun start(payload: BackupPayload) {
        // Закрываем предложение до показа сводки: иначе его можно случайно
        // закрыть тапом под диалогом и больше не увидеть в этом запуске.
        offer = null
        showPassword = false
        pendingUri = null
        pendingPayload = payload
        container.restore.start(payload)
    }

    fun askPassword(uri: Uri, error: String = "") {
        pendingUri = uri
        passwordError = error
        showPassword = true
    }

    fun openWithStoredPassword(uri: Uri) {
        scope.launch {
            val stored = container.settings.autoBackupPassword()
            if (stored == null) {
                askPassword(uri)
                return@launch
            }
            val result = runCatching { container.backup.readBackup(uri, stored) }
            stored.fill('\u0000')
            result.onSuccess { start(it) }
                .onFailure { askPassword(uri, it.userMessage(context)) }
        }
    }

    val picked = rememberLauncherForActivityResult(OpenBackupFile()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    if (BackupCrypto.isEncrypted(container.backup.readFile(uri))) {
                        askPassword(uri)
                    } else {
                        start(container.backup.readBackup(uri, null))
                    }
                }.onFailure { failure = it.userMessage(context) }
            }
        }
    }

    if (showPassword) {
        val uri = pendingUri
        if (uri != null) {
            PassphraseDialog(
                title = stringResource(R.string.backup_import_pass_title),
                description = stringResource(R.string.restore_prompt_password_hint),
                requireConfirm = false,
                initialError = passwordError,
                onDismiss = {
                    showPassword = false
                    pendingUri = null
                },
                onConfirm = { pass ->
                    showPassword = false
                    scope.launch {
                        runCatching { container.backup.readBackup(uri, pass) }
                            .onSuccess {
                                pendingUri = null
                                start(it)
                            }
                            .onFailure {
                                // Никаких падений: показываем ошибку и даём повторить.
                                pendingUri = uri
                                askPassword(uri, it.userMessage(context))
                            }
                        pass.fill('\u0000')
                    }
                }
            )
        }
        return
    }

    val summary = restored
    if (summary != null) {
        RestoreSummaryDialog(
            medications = summary.medications.size,
            doses = summary.doses.size,
            moods = summary.moods.size,
            journal = summary.journal.size,
            onDone = { restored = null }
        )
        return
    }

    if (failure.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { failure = "" },
            title = { Text(stringResource(R.string.errors_restore_failed)) },
            text = { Text(failure) },
            confirmButton = {
                TextButton(onClick = { failure = "" }) {
                    Text(stringResource(R.string.backup_ack))
                }
            }
        )
        return
    }

    if (restoreState is RestoreState.Running) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_progress_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.restore_running))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { container.restore.cancel() }) {
                    Text(stringResource(R.string.restore_cancel))
                }
            }
        )
        return
    }

    val current = offer ?: return
    val pickFile = current.kind == RestoreOfferKind.PICK_FILE
    RestoreFoundDialog(
        title = stringResource(
            if (pickFile) R.string.restore_found_pick_title else R.string.restore_found_title
        ),
        body = when (current.kind) {
            RestoreOfferKind.FOLDER -> stringResource(
                R.string.restore_found_folder_body,
                current.savedAt.dateTime(locale)
            )
            RestoreOfferKind.DOWNLOADS -> stringResource(
                R.string.restore_found_downloads_body,
                current.savedAt.dateTime(locale)
            )
            RestoreOfferKind.PICK_FILE -> stringResource(R.string.restore_found_pick_body)
        },
        canRestore = current.knownUri != null,
        onRestore = {
            val uri = current.knownUri
            if (uri != null) {
                offer = null
                openWithStoredPassword(uri)
            }
        },
        onChoose = { picked.launch(arrayOf("*/*")) },
        onLater = { dismiss() },
        onDismissRequest = { dismiss() }
    )
}

/**
 * Отдельный composable, чтобы диалог можно было проверить UI-тестом без
 * настоящей SAF-папки и пустой базы.
 */
@Composable
internal fun RestoreFoundDialog(
    title: String,
    body: String,
    canRestore: Boolean,
    onRestore: () -> Unit,
    onChoose: () -> Unit,
    onLater: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body)
                if (canRestore) {
                    TextButton(onClick = onChoose) {
                        Text(stringResource(R.string.restore_found_choose))
                    }
                }
            }
        },
        confirmButton = {
            if (canRestore) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.restore_found_restore))
                }
            } else {
                TextButton(onClick = onChoose) {
                    Text(stringResource(R.string.restore_found_choose))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.restore_found_later))
            }
        }
    )
}

/** Сводка после восстановления: сколько записей каждого типа вернулось. */
@Composable
internal fun RestoreSummaryDialog(
    medications: Int,
    doses: Int,
    moods: Int,
    journal: Int,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.restore_summary_title)) },
        text = {
            Text(
                stringResource(
                    R.string.restore_summary_body,
                    medications,
                    doses,
                    moods,
                    journal
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.restore_summary_ok))
            }
        }
    )
}
