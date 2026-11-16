@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.opora.ui.screens

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.opora.AppLanguage
import app.opora.diagnostics.ProblemReport
import app.opora.findActivity
import app.opora.ui.theme.OporaIconSize
import app.opora.data.backup.RestorePointInfo
import app.opora.domain.hhmm
import app.opora.domain.HomeBlock
import app.opora.domain.dateTime
import app.opora.data.userMessage
import app.opora.domain.parseHomeBlocks
import app.opora.notifications.MedicationAlarms
import app.opora.notifications.Notify
import app.opora.ui.LocalAppContainer
import app.opora.ui.SettingsViewModel
import app.opora.R
import app.opora.ui.components.ConfirmDialog
import app.opora.ui.components.FormDialog
import app.opora.ui.components.rememberKeyboardHider
import app.opora.ui.components.formDialogField
import app.opora.ui.components.IconTextRow
import app.opora.ui.components.InfoRow
import app.opora.ui.components.MenuIcon
import app.opora.ui.components.OporaCard
import app.opora.ui.theme.OporaSpacing
import app.opora.ui.components.OporaTimePickerDialog
import app.opora.ui.components.ScreenTitle
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Уведомления доступны, только если выдано runtime-разрешение И приложение
 * не выключено в системных настройках. Одного areNotificationsEnabled мало:
 * после отказа в POST_NOTIFICATIONS он остаётся true.
 */
internal fun notificationAvailability(
    permissionGranted: Boolean,
    appNotificationsEnabled: Boolean
): Boolean = permissionGranted && appNotificationsEnabled

@Composable
fun MoreScreen(
    vm: SettingsViewModel,
    onOpenAppointments: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    canLockOverride: (() -> Boolean)? = null,
    onOpenAuthor: () -> Unit = {}
) {
    val settings by vm.state.collectAsState()
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var nameDialog by remember { mutableStateOf(false) }
    var demoConfirm by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var discardPointConfirm by remember { mutableStateOf(false) }
    var restorePoint by remember { mutableStateOf<RestorePointInfo?>(null) }
    var moodTimePicker by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var lockHelp by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    fun deviceCanLock(): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return keyguard?.isDeviceSecure == true ||
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun canLockNow(): Boolean = canLockOverride?.invoke() ?: deviceCanLock()

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun channelOn(channelId: String): Boolean = Notify.isChannelEnabled(context, channelId)

    fun openChannel(channelId: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            )
        }
    }

    fun notificationsAllowed(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return notificationAvailability(
            permissionGranted,
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }

    // These capabilities can change in system Settings, so they are re-read on resume.
    var canLock by remember { mutableStateOf(canLockNow()) }
    var canExact by remember { mutableStateOf(exactAlarmsAllowed()) }
    var notificationsOn by remember { mutableStateOf(notificationsAllowed()) }
    var medsChannelOn by remember { mutableStateOf(channelOn(Notify.CHANNEL_MEDS)) }
    var refillChannelOn by remember { mutableStateOf(channelOn(Notify.CHANNEL_REFILL)) }
    var dailyChannelOn by remember { mutableStateOf(channelOn(Notify.CHANNEL_DAILY)) }
    var lockDisableConfirm by remember { mutableStateOf(false) }
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hiddenHome = remember(settings.hiddenHomeBlocks) {
        settings.hiddenHomeBlocks.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    val hiddenTabs = remember(settings.hiddenTabs) {
        settings.hiddenTabs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canLock = canLockNow()
        canExact = exactAlarmsAllowed()
        notificationsOn = notificationsAllowed()
        medsChannelOn = channelOn(Notify.CHANNEL_MEDS)
        refillChannelOn = channelOn(Notify.CHANNEL_REFILL)
        dailyChannelOn = channelOn(Notify.CHANNEL_DAILY)
        scope.launch {
            restorePoint = runCatching { container.backup.restorePointInfo() }.getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            restorePoint = runCatching { container.backup.restorePointInfo() }.getOrNull()
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
            ScreenTitle(title = stringResource(R.string.more_title))
        }
        item {
            OporaCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (settings.userName.isBlank()) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Text(
                                avatarInitial(settings.userName),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.userName.isBlank()) {
                                stringResource(R.string.more_name_prompt)
                            } else {
                                settings.userName
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (settings.userName.isBlank()) {
                            Text(
                                stringResource(R.string.more_name_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { nameDialog = true }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = if (settings.userName.isBlank()) {
                                    stringResource(R.string.more_name_set_cd)
                                } else {
                                    stringResource(R.string.more_name_edit_cd)
                                },
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
        item { MoreSectionHeader(stringResource(R.string.more_section_health)) }
        item {
            OporaCard(contentPadding = OporaSpacing.l) {
                InfoRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.more_appointments),
                    trailing = { TrailingChevron() },
                    modifier = Modifier.clickableRow(onOpenAppointments)
                )
            }
        }
        item {
            MoreSectionHeader(
                stringResource(R.string.more_section_reminders),
                subtitle = stringResource(R.string.more_reminders_subtitle)
            )
        }
        item {
            OporaCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.more_med_toggle),
                        subtitle = if (medsChannelOn) {
                            stringResource(R.string.more_meds_channel_on)
                        } else {
                            stringResource(R.string.more_channel_off)
                        },
                        checked = settings.medReminders,
                        onChange = vm::setMedReminders
                    )
                    SwitchRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.more_mood_toggle),
                        subtitle = if (dailyChannelOn) {
                            stringResource(R.string.more_mood_channel_on)
                        } else {
                            stringResource(R.string.more_channel_off)
                        },
                        checked = settings.moodReminder,
                        onChange = { vm.setMoodReminder(it, settings.moodReminderTime) }
                    )
                    if (settings.moodReminder) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 46.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.more_time_label, settings.moodReminderTime),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { moodTimePicker = true }) {
                                Text(stringResource(R.string.action_edit))
                            }
                        }
                    }
                    SwitchRow(
                        icon = Icons.Outlined.Medication,
                        title = stringResource(R.string.more_refill_toggle),
                        subtitle = if (refillChannelOn) {
                            stringResource(R.string.more_refill_channel_on)
                        } else {
                            stringResource(R.string.more_channel_off)
                        },
                        checked = settings.refillReminders,
                        onChange = vm::setRefillReminders
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact) {
                        CapabilityNotice(
                            title = stringResource(R.string.more_exact_title),
                            body = stringResource(R.string.more_exact_body),
                            actionLabel = stringResource(R.string.more_exact_action)
                        ) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                            canExact = context.getSystemService(AlarmManager::class.java)
                                .canScheduleExactAlarms()
                        }
                    }
                    if (!notificationsOn) {
                        CapabilityNotice(
                            title = stringResource(R.string.more_notifications_off_title),
                            body = stringResource(R.string.more_notifications_off_body),
                            actionLabel = stringResource(R.string.open_settings)
                        ) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        }
                    }
                    if (notificationsOn && settings.medReminders && !medsChannelOn) {
                        CapabilityNotice(
                            title = stringResource(R.string.more_meds_off_title),
                            body = stringResource(R.string.more_meds_off_body),
                            actionLabel = stringResource(R.string.more_open_channel)
                        ) { openChannel(Notify.CHANNEL_MEDS) }
                    }
                    if (notificationsOn && settings.refillReminders && !refillChannelOn) {
                        CapabilityNotice(
                            title = stringResource(R.string.more_refill_off_title),
                            body = stringResource(R.string.more_refill_off_body),
                            actionLabel = stringResource(R.string.more_open_channel)
                        ) { openChannel(Notify.CHANNEL_REFILL) }
                    }
                    if (notificationsOn && settings.moodReminder && !dailyChannelOn) {
                        CapabilityNotice(
                            title = stringResource(R.string.more_mood_off_title),
                            body = stringResource(R.string.more_mood_off_body),
                            actionLabel = stringResource(R.string.more_open_channel)
                        ) { openChannel(Notify.CHANNEL_DAILY) }
                    }
                }
            }
        }
        item { MoreSectionHeader(stringResource(R.string.more_section_appearance)) }
        item {
            OporaCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.more_theme), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "system" to R.string.more_theme_system,
                            "light" to R.string.more_theme_light,
                            "dark" to R.string.more_theme_dark
                        ).forEach { (mode, labelRes) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { vm.setTheme(mode) },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    SwitchRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.more_dynamic_title),
                        subtitle = if (supportsDynamicColor) {
                            stringResource(R.string.more_dynamic_on)
                        } else {
                            stringResource(R.string.more_dynamic_unavailable)
                        },
                        checked = supportsDynamicColor && settings.dynamicColor,
                        enabled = supportsDynamicColor,
                        onChange = vm::setDynamic
                    )
                    SwitchRow(
                        icon = Icons.Outlined.SwapHoriz,
                        title = stringResource(R.string.more_left_title),
                        subtitle = stringResource(R.string.more_left_body),
                        checked = settings.leftHanded,
                        onChange = vm::setLeftHanded
                    )
                    SwitchRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.more_lock_title),
                        subtitle = stringResource(R.string.more_lock_body),
                        checked = settings.appLock,
                        onChange = { enabled ->
                            // PIN могли выставить в системных настройках, пока
                            // экран уже открыт: проверяем ещё раз в момент действия.
                            val freshCanLock = canLockNow()
                            canLock = freshCanLock
                            when {
                                !enabled -> lockDisableConfirm = true
                                freshCanLock -> vm.setAppLock(true)
                                else -> lockHelp = true
                            }
                        }
                    )
                    SwitchRow(
                        icon = Icons.Outlined.VisibilityOff,
                        title = stringResource(R.string.more_secure_title),
                        subtitle = stringResource(R.string.more_secure_body),
                        checked = settings.secureScreen,
                        onChange = vm::setSecureScreen
                    )
                }
            }
        }
        item {
            OporaCard(contentPadding = OporaSpacing.l) {
                InfoRow(
                    icon = Icons.Outlined.Translate,
                    title = stringResource(R.string.language_title),
                    subtitle = languageLabel(settings.language),
                    trailing = { TrailingChevron() },
                    modifier = Modifier.clickableRow { languageDialog = true }
                )
            }
        }
        item {
            MoreSectionHeader(
                stringResource(R.string.more_section_home),
                subtitle = stringResource(R.string.more_home_subtitle)
            )
        }
        item {
            OporaCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)) {
                    Text(
                        stringResource(R.string.more_home_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val ordered = parseHomeBlocks(settings.homeBlocks)
                    ordered.forEachIndexed { index, block ->
                        HomeBlockRow(
                            block = block,
                            checked = block.key !in hiddenHome,
                            canMoveUp = index > 0,
                            canMoveDown = index < ordered.lastIndex,
                            onMoveUp = { vm.moveHomeBlock(block.key, -1) },
                            onMoveDown = { vm.moveHomeBlock(block.key, 1) },
                            onToggle = { vm.setHomeBlockVisible(block.key, it) }
                        )
                    }
                    TextButton(
                        onClick = vm::resetHomeLayout,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.more_home_reset)) }
                }
            }
        }
        item {
            MoreSectionHeader(
                stringResource(R.string.more_section_tabs),
                subtitle = stringResource(R.string.more_tabs_subtitle)
            )
        }
        item {
            OporaCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OporaSpacing.xs)) {
                    listOf(
                        Triple("mood", R.string.home_block_mood, Icons.Outlined.Mood),
                        Triple("meds", R.string.home_block_meds, Icons.Outlined.Medication),
                        Triple("journal", R.string.home_block_journal, Icons.Outlined.AutoStories)
                    ).forEach { (route, titleRes, icon) ->
                        SwitchRow(
                            icon = icon,
                            title = stringResource(titleRes),
                            checked = route !in hiddenTabs,
                            onChange = { vm.setTabVisible(route, it) }
                        )
                    }
                }
            }
        }
        item { MoreSectionHeader(stringResource(R.string.more_section_data)) }
        item {
            OporaCard(contentPadding = OporaSpacing.l) {
                Column {
                    InfoRow(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.more_backup),
                        subtitle = stringResource(R.string.more_backup_body),
                        trailing = { TrailingChevron() },
                        modifier = Modifier.clickableRow(onOpenBackup)
                    )
                    InfoRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.more_report),
                        subtitle = stringResource(R.string.more_report_body),
                        trailing = { TrailingChevron() },
                        modifier = Modifier.clickableRow(onOpenReport)
                    )
                    InfoRow(
                        icon = Icons.Outlined.Science,
                        title = stringResource(R.string.more_demo),
                        subtitle = stringResource(R.string.more_demo_hint),
                        modifier = Modifier.clickableRow { demoConfirm = true }
                    )
                    restorePoint?.let { point ->
                        val stamp = point.savedAt.dateTime(locale)
                        InfoRow(
                            icon = Icons.Outlined.Restore,
                            title = stringResource(R.string.more_restore),
                            subtitle = stringResource(
                                R.string.more_restore_point,
                                stamp,
                                pluralStringResource(R.plurals.entries, point.records, point.records)
                            ),
                            modifier = Modifier.clickableRow { restoreConfirm = true }
                        )
                        InfoRow(
                            icon = Icons.Outlined.DeleteOutline,
                            title = stringResource(R.string.more_discard_point_title),
                            subtitle = stringResource(R.string.more_discard_point),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickableRow { discardPointConfirm = true }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    InfoRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = stringResource(R.string.more_clear),
                        subtitle = stringResource(R.string.more_clear_hint),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickableRow { clearConfirm = true }
                    )
                }
            }
        }
        if (message.isNotBlank()) {
            item {
                OporaCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
        item { MoreSectionHeader(stringResource(R.string.more_section_about)) }
        item {
            OporaCard(contentPadding = OporaSpacing.l) {
                Column {
                    InfoRow(
                        icon = Icons.Outlined.AccountCircle,
                        title = stringResource(R.string.more_author),
                        subtitle = stringResource(R.string.more_author_body),
                        trailing = { TrailingChevron() },
                        modifier = Modifier.clickableRow(onOpenAuthor)
                    )
                    InfoRow(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.more_problem_report),
                        subtitle = stringResource(R.string.more_problem_report_body),
                        trailing = { TrailingChevron() },
                        modifier = Modifier.clickableRow {
                            if (!ProblemReport.share(context, settings)) {
                                message = context.getString(R.string.problem_report_error)
                            }
                        }
                    )
                    InfoRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.about_title),
                        trailing = { TrailingChevron() },
                        modifier = Modifier.clickableRow(onOpenAbout)
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.more_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                Column {
                    AppLanguage.options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    languageDialog = false
                                    if (settings.language != option) {
                                        scope.launch {
                                            // Persist first: a recreate on Android 12
                                            // re-reads the tag in attachBaseContext.
                                            container.settings.setLanguage(option)
                                            AppLanguage.setTag(option)
                                            AppLanguage.applyToSystem(context, option)
                                            // Channel names live in the system UI; recreate
                                            // them so they follow the new language too.
                                            Notify.ensureChannels(context)
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                                context.findActivity()?.recreate()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                languageLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (settings.language == option) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (nameDialog) {
        var name by remember { mutableStateOf(settings.userName) }
        val hideKeyboard = rememberKeyboardHider()
        FormDialog(
            title = if (settings.userName.isBlank()) {
                stringResource(R.string.more_name_prompt)
            } else {
                stringResource(R.string.more_name_dialog_title)
            },
            onDismissRequest = { nameDialog = false },
            confirmLabel = stringResource(R.string.more_save),
            dismissLabel = stringResource(R.string.more_cancel),
            onConfirm = {
                vm.setUserName(name)
                nameDialog = false
            }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.more_name_label)) },
                modifier = Modifier.fillMaxWidth().formDialogField(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { hideKeyboard() })
            )
        }
    }

    if (moodTimePicker) {
        OporaTimePickerDialog(
            initial = runCatching { LocalTime.parse(settings.moodReminderTime) }
                .getOrDefault(LocalTime.of(21, 0)),
            onDismiss = { moodTimePicker = false },
            onSelect = {
                vm.setMoodReminder(true, it.hhmm())
            }
        )
    }

    if (demoConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.more_demo_confirm),
            text = stringResource(R.string.more_demo_body),
            confirmLabel = stringResource(R.string.more_demo_action),
            destructive = false,
            onConfirm = {
                scope.launch {
                    runCatching { container.repository.seedDemoData() }
                        .onSuccess {
                            message = context.getString(R.string.more_demo_done)
                            vm.setMoodReminder(settings.moodReminder, settings.moodReminderTime)
                        }
                        .onFailure { message = it.userMessage(context) }
                }
            },
            onDismiss = { demoConfirm = false }
        )
    }

    if (clearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.more_clear_title),
            text = stringResource(R.string.more_clear_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                scope.launch {
                    runCatching {
                        // Сначала снимок: если он не создастся, удаление не начнётся.
                        container.backup.createRestorePoint(settings.userName)
                        container.repository.clearAllData()
                        container.repository.deleteReports()
                        container.backup.deleteAllLocalBackups()
                        container.settings.clearBackupTimestamps()
                        container.settings.clearUserName()
                        container.settings.clearWebDav()
                        container.workScheduler.cancelAutoSync()
                        container.workScheduler.scheduleMaintenance()
                    }
                        .onSuccess {
                            message = context.getString(R.string.more_clear_done)
                            restorePoint = runCatching { container.backup.restorePointInfo() }.getOrNull()
                        }
                        .onFailure { message = it.userMessage(context) }
                }
            },
            onDismiss = { clearConfirm = false }
        )
    }

    val point = restorePoint
    if (restoreConfirm && point != null) {
        ConfirmDialog(
            title = stringResource(R.string.more_restore_title),
            text = stringResource(R.string.more_restore_body),
            confirmLabel = stringResource(R.string.more_restore_action),
            destructive = false,
            onConfirm = {
                scope.launch {
                    runCatching {
                        val report = container.backup.restorePoint()
                        container.repository.ensureTodayLogs()
                        MedicationAlarms(context, container.db).scheduleAll()
                        report.payload.appointments.filter { !it.done }.forEach { appointment ->
                            container.workScheduler.scheduleAppointment(appointment)
                        }
                        container.workScheduler.restoreAll()
                    }
                        .onSuccess {
                            message = context.getString(R.string.more_restore_done)
                            restorePoint = null
                            restoreConfirm = false
                        }
                        .onFailure {
                            message = it.userMessage(context)
                            restoreConfirm = false
                        }
                }
            },
            onDismiss = { restoreConfirm = false }
        )
    }

    if (discardPointConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.more_discard_title),
            text = stringResource(R.string.more_discard_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                scope.launch {
                    container.backup.discardRestorePoint()
                    restorePoint = null
                    message = context.getString(R.string.more_discard_done)
                }
            },
            onDismiss = { discardPointConfirm = false }
        )
    }

    if (lockDisableConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.more_lock_disable_title),
            text = stringResource(R.string.more_lock_disable_body),
            confirmLabel = stringResource(R.string.more_lock_disable_action),
            dismissLabel = stringResource(R.string.more_lock_keep),
            destructive = false,
            onConfirm = { vm.setAppLock(false) },
            onDismiss = { lockDisableConfirm = false }
        )
    }

    if (lockHelp) {
        AlertDialog(
            onDismissRequest = { lockHelp = false },
            title = { Text(stringResource(R.string.more_lock_help_title)) },
            text = {
                Text(stringResource(R.string.more_lock_help_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    lockHelp = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                }) { Text(stringResource(R.string.more_lock_help_action)) }
            },
            dismissButton = {
                TextButton(onClick = { lockHelp = false }) {
                    Text(stringResource(R.string.more_later))
                }
            }
        )
    }
}

/** Первая буква имени для аватара; эмодзи не должен превращаться в обрезанный surrogate. */
internal fun avatarInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    // codePoint, а не Char: эмодзи занимает два UTF-16 элемента.
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

@Composable
private fun HomeBlockRow(
    block: HomeBlock,
    checked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    if (largeFont) {
        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTextRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(block.labelRes),
                    titleStyle = MaterialTheme.typography.titleSmall,
                    tint = MaterialTheme.colorScheme.primary,
                    gap = 12.dp,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = checked, onCheckedChange = onToggle)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Outlined.KeyboardArrowUp,
                        contentDescription = stringResource(
                            R.string.more_home_move_up_cd,
                            stringResource(block.labelRes)
                        )
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(
                            R.string.more_home_move_down_cd,
                            stringResource(block.labelRes)
                        )
                    )
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTextRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(block.labelRes),
                titleStyle = MaterialTheme.typography.titleSmall,
                tint = MaterialTheme.colorScheme.primary,
                gap = 12.dp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(
                        R.string.more_home_move_up_cd,
                        stringResource(block.labelRes)
                    )
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(
                        R.string.more_home_move_down_cd,
                        stringResource(block.labelRes)
                    )
                )
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTextRow(
            title = title,
            leadingSize = 38.dp,
            leading = {
                MenuIcon(
                    icon = icon,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            subtitle = subtitle,
            titleStyle = MaterialTheme.typography.titleSmall,
            titleColor = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            firstLine = false,
            gap = 12.dp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            // Выключенный переключатель по умолчанию сливается с фоном и читается
            // как недоступный. Дорожка остаётся нейтральной, но контур и ползунок
            // получают контраст обычного вторичного текста; включённый — прежний цвет.
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/** Заголовок раздела настроек: меньше названий карточек, но заметнее строк. */
@Composable
private fun MoreSectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CapabilityNotice(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End)
            ) { Text(actionLabel) }
        }
    }
}

@Composable
private fun TrailingChevron() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)

@Composable
private fun languageLabel(option: String): String = when (option) {
    AppLanguage.RUSSIAN -> stringResource(R.string.language_russian)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    else -> stringResource(R.string.language_system)
}
