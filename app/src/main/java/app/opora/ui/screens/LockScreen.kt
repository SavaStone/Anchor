package app.opora.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.ui.theme.OporaSpacing
import app.opora.BiometricActivity
import app.opora.ui.components.ConfirmDialog

@Composable
fun LockScreen(
    activity: ComponentActivity,
    onUnlock: () -> Unit,
    onDisableLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appName = context.getString(R.string.app_name)
    val cancelMessage = stringResource(R.string.lock_cancel_message)
    var message by remember { mutableStateOf("") }
    var disableConfirm by remember { mutableStateOf(false) }
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    // Checked again on each composition and on each attempt, so a screen lock that was
    // added in system Settings is picked up without restarting the app.
    val deviceSecure = keyguard?.isDeviceSecure == true
    val canBiometric = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS

    val biometricLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlock()
        } else {
            message = cancelMessage
        }
    }
    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlock()
        } else {
            message = cancelMessage
        }
    }

    fun authenticate() {
        message = ""
        val freshKeyguard = context.getSystemService(KeyguardManager::class.java)
        val freshDeviceSecure = freshKeyguard?.isDeviceSecure == true
        val freshCanBiometric = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
        when {
            freshCanBiometric -> {
                runCatching {
                    biometricLauncher.launch(Intent(context, BiometricActivity::class.java))
                }.onFailure {
                    message = context.getString(R.string.lock_biometric_failed)
                }
            }
            freshDeviceSecure -> {
                val intent = freshKeyguard?.createConfirmDeviceCredentialIntent(
                    appName,
                    context.getString(R.string.lock_confirm_subtitle)
                )
                if (intent != null) {
                    runCatching { credentialLauncher.launch(intent) }
                        .onFailure {
                            message = context.getString(R.string.lock_credential_failed)
                        }
                } else {
                    message = context.getString(R.string.lock_pin_failed)
                }
            }
            else -> message = context.getString(R.string.lock_no_device_security)
        }
    }

    LaunchedEffect(Unit) {
        if (canBiometric || deviceSecure) authenticate()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(OporaSpacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.lock_title, appName),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!canBiometric && !deviceSecure) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.lock_no_device_security),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(OporaSpacing.xl))
                Button(onClick = { authenticate() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (canBiometric) Icons.Outlined.Fingerprint else Icons.Outlined.Lock,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            when {
                                canBiometric -> R.string.lock_unlock
                                deviceSecure -> R.string.lock_enter_pin
                                else -> R.string.lock_retry
                            }
                        )
                    )
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                if (!canBiometric && !deviceSecure) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    }) { Text(stringResource(R.string.lock_open_security_settings)) }
                    TextButton(onClick = { disableConfirm = true }) {
                        Text(stringResource(R.string.lock_disable, appName))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.lock_biometric_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (disableConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.lock_disable_title),
            text = stringResource(R.string.lock_disable_body, appName),
            confirmLabel = stringResource(R.string.lock_disable_confirm),
            dismissLabel = stringResource(R.string.lock_disable_dismiss),
            destructive = false,
            onConfirm = onDisableLock,
            onDismiss = { disableConfirm = false }
        )
    }
}
