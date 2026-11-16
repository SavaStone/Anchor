package app.opora

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.opora.data.prefs.AppSettings
import app.opora.ui.LocalAppContainer
import app.opora.ui.OporaRoot
import app.opora.ui.components.BrandMark
import app.opora.ui.screens.LockScreen
import app.opora.ui.screens.OnboardingScreen
import app.opora.ui.theme.LocalIsDark
import app.opora.ui.theme.OporaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        AppLanguage.setTag(AppLanguage.resolveStartupTag(newBase))
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OporaApp
        val container = app.container
        val processToken = app.processToken
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            CompositionLocalProvider(LocalAppContainer provides container) {
                val current = settings
                if (current == null) {
                    // Transparent placeholder: the themed windowBackground from the
                    // XML theme shows through, so there is no white flash in dark mode.
                    Box(Modifier.fillMaxSize())
                } else {
                    OporaTheme(
                        themeMode = current.themeMode,
                        dynamicColor = current.dynamicColor
                    ) {
                        // Surface задаёт contentColor из активной схемы. Без него экраны
                        // без собственного Surface (онбординг) наследуют чёрный
                        // LocalContentColor и теряют текст на тёмном фоне.
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AppContent(container, current, processToken)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(container: AppContainer, settings: AppSettings, processToken: String) {
    val activity = LocalView.current.context as? ComponentActivity
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    // Re-checked on every resume: the user may add or remove the screen lock in Settings.
    var capabilityCheck by remember { mutableIntStateOf(0) }
    val deviceSecure = remember(capabilityCheck) { keyguard?.isDeviceSecure == true }
    val canBiometric = remember(capabilityCheck) {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val canLock = deviceSecure || canBiometric
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // Unlock state survives rotation but not process death: a value saved by another
    // process is invalidated, so the app re-locks instead of reopening silently.
    val lockStateSaver = remember(processToken, settings.appLock) {
        Saver<Boolean?, String>(
            save = { value -> processToken + "|" + (value?.toString() ?: "null") },
            restore = { saved ->
                if (saved.substringBefore('|') != processToken) {
                    // A different process saved this state: never restore an unlock.
                    if (settings.appLock) true else null
                } else {
                    when (saved.substringAfter('|')) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                }
            }
        )
    }
    var locked by rememberSaveable(stateSaver = lockStateSaver) {
        mutableStateOf<Boolean?>(null)
    }
    val backgroundAt = remember { mutableLongStateOf(0L) }
    val latest = remember(settings) { settings }

    DisposableEffect(owner, latest.appLock, latest.lockTimeoutSeconds) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isChangingConfigurations != true) {
                        backgroundAt.longValue = SystemClock.elapsedRealtime()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val elapsed = SystemClock.elapsedRealtime() - backgroundAt.longValue
                    if (latest.appLock && backgroundAt.longValue > 0 &&
                        elapsed > latest.lockTimeoutSeconds * 1000L
                    ) {
                        locked = true
                        backgroundAt.longValue = 0L
                    }
                }
                Lifecycle.Event.ON_RESUME -> capabilityCheck++
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Следим именно за переходом «выключено → включено»: при повороте
    // настройка уже true, и эффект не должен заново запирать разблокированный
    // экран. Начальное значение соответствует текущей настройке.
    var previousAppLock by remember { mutableStateOf(settings.appLock) }
    LaunchedEffect(settings.appLock) {
        when {
            !settings.appLock -> locked = false
            !previousAppLock -> locked = true
        }
        previousAppLock = settings.appLock
    }

    SideEffect {
        runCatching {
            if (settings.secureScreen) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
    SystemBars(activity)

    if (!settings.onboardingDone) {
        OnboardingScreen(
            onDone = { name ->
                scope.launch {
                    container.settings.setOnboardingDone(true)
                    container.settings.setDisclaimerAccepted(true)
                    if (name.isNotBlank()) container.settings.setUserName(name)
                }
            }
        )
        return
    }
    if (activity == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BrandMark(size = 72.dp)
        }
        return
    }

    // Корень навигации остаётся в дереве, пока показана блокировка: NavHost,
    // back stack, ViewModel'ы и rememberSaveable форм не пересоздаются.
    Box(Modifier.fillMaxSize()) {
        OporaRoot(settings = settings, activity = activity)
        val lockVisible = settings.appLock && (locked ?: true)
        if (lockVisible) {
            // Dialog, а не Surface в этом же окне: он перекрывает и обычные
            // Compose-диалоги (прекращение приёма и т.п.), которые живут
            // в отдельных окнах. При разблокировке диалог под ним остаётся.
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (canLock) {
                        LockScreen(
                            activity = activity,
                            onUnlock = { locked = false },
                            onDisableLock = {
                                locked = false
                                scope.launch { container.settings.setAppLock(false) }
                            }
                        )
                    } else {
                        // Fail closed: the lock cannot be verified because the device
                        // has no PIN/biometrics. Explain it and offer an explicit way out.
                        LockUnavailableScreen(
                            onOpenSecuritySettings = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                }
                            },
                            onDisableLock = {
                                locked = false
                                scope.launch { container.settings.setAppLock(false) }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shown when the app lock is enabled but the device cannot confirm identity:
 * the device has no screen lock (PIN/pattern/password) and no enrolled biometrics.
 * The app does not silently disable its own protection.
 */
@Composable
private fun LockUnavailableScreen(
    onOpenSecuritySettings: () -> Unit,
    onDisableLock: () -> Unit
) {
    val appName = stringResource(R.string.app_name)
    var confirmDisable by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.main_lock_unavailable_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.main_lock_unavailable_body, appName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.main_lock_unavailable_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onOpenSecuritySettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.main_setup_lock))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDisable = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.main_disable_lock))
            }
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.main_disable_lock_title)) },
            text = { Text(stringResource(R.string.main_disable_lock_body, appName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisable = false
                        onDisableLock()
                    }
                ) { Text(stringResource(R.string.main_disable_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) {
                    Text(stringResource(R.string.main_cancel))
                }
            }
        )
    }
}

@Composable
private fun SystemBars(activity: ComponentActivity?) {
    val view = LocalView.current
    val dark = LocalIsDark.current
    SideEffect {
        if (activity == null) return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}
