package app.opora.ui

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.opora.AppContainer
import app.opora.R
import app.opora.data.prefs.AppSettings
import app.opora.notifications.MedicationAlarms
import app.opora.ui.screens.AboutScreen
import app.opora.ui.screens.AppointmentsScreen
import app.opora.ui.screens.AuthorScreen
import app.opora.ui.screens.AutoBackupRestorePrompt
import app.opora.ui.screens.BackupScreen
import app.opora.ui.screens.JournalEditorScreen
import app.opora.ui.screens.JournalScreen
import app.opora.ui.screens.MedEditorScreen
import app.opora.ui.screens.MedsScreen
import app.opora.ui.screens.MoodEditorScreen
import app.opora.ui.screens.MoodScreen
import app.opora.ui.screens.MoreScreen
import app.opora.ui.screens.ReportScreen
import app.opora.ui.screens.TodayScreen
import java.time.LocalDate

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided")
}

private data class Tab(
    val route: String,
    @StringRes val label: Int,
    /** Контурная — невыбранный пункт; заполненная только у выбранного. */
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

private val tabs = listOf(
    Tab("today", R.string.tab_today, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    Tab("mood", R.string.tab_mood, Icons.Outlined.Mood, Icons.Rounded.Mood),
    Tab("meds", R.string.tab_meds, Icons.Outlined.Medication, Icons.Rounded.Medication),
    Tab("journal", R.string.tab_journal, Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    Tab("more", R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz)
)

/**
 * Нижнее меню переходит на одни иконки уже с масштаба 1.3: при 1.3 подпись
 * «Настроение» шире колонки и обрезается, хотя 1.5-порог её ещё показывал.
 */
internal fun useIconOnlyNavigation(fontScale: Float): Boolean = fontScale >= 1.3f

/**
 * Скрытая вкладка не исчезает из навигации, пока открыта по deep link:
 * иначе у пользователя не остаётся видимого выхода, кроме системного «Назад».
 * В обычном состоянии скрытые вкладки в панель не попадают.
 */
internal fun tabVisible(route: String, hiddenTabs: Set<String>, currentRoute: String?): Boolean =
    route == "today" || route == "more" || route !in hiddenTabs || route == currentRoute

@Composable
fun OporaRoot(
    settings: AppSettings,
    activity: ComponentActivity
) {
    val container = LocalAppContainer.current
    val nav = rememberNavController()
    // Один экземпляр на все маршруты настроения: сохранение из редактора
    // сразу меняет выбранный день в календаре, а не оставляет его прежним.
    val moodVm: MoodViewModel = viewModel(
        factory = vmFactory { MoodViewModel(container.repository) }
    )
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val hiddenTabs = remember(settings.hiddenTabs) {
        settings.hiddenTabs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    val visibleTabs = tabs.filter {
        tabVisible(it.route, hiddenTabs, route)
    }
    val showBar = visibleTabs.any { it.route == route }
    val context = LocalContext.current
    // Крупный системный шрифт: подписи пяти пунктов физически не помещаются
    // в узкие колонки и обрезались бы посреди слова. Оставляем узнаваемые
    // иконки, а полное имя пункта передаём в contentDescription — TalkBack
    // читает его целиком, выбранное состояние сообщает Role.Tab.
    val largeFont = useIconOnlyNavigation(LocalDensity.current.fontScale)

    fun navigateTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // launchSingleTop also absorbs rapid double taps on the same destination.
    fun openRoute(target: String) {
        nav.navigate(target) { launchSingleTop = true }
    }

    DisposableEffect(activity) {
        val listener = Consumer<Intent> { intent -> nav.handleDeepLink(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    LaunchedEffect(Unit) {
        activity.intent?.let { nav.handleDeepLink(it) }
    }

    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!wide && showBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        visibleTabs.forEach { tab ->
                            val labelText = stringResource(tab.label)
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    if (route != tab.route) {
                                        navigateTab(tab.route)
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (route == tab.route) tab.selectedIcon else tab.icon,
                                        // Без видимой подписи имя остаётся у иконки.
                                        contentDescription = if (largeFont) labelText else null
                                    )
                                },
                                label = if (largeFont) {
                                    null
                                } else {
                                    {
                                        // Одна строка при обычном шрифте: иначе подпись
                                        // рвётся посередине слова. Полное имя остаётся
                                        // в семантике, поэтому TalkBack читает его целиком.
                                        Text(
                                            text = labelText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                            modifier = Modifier.clearAndSetSemantics {
                                                contentDescription = labelText
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide && showBar) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        visibleTabs.forEach { tab ->
                            val labelText = stringResource(tab.label)
                            NavigationRailItem(
                                selected = route == tab.route,
                                onClick = {
                                    if (route != tab.route) {
                                        navigateTab(tab.route)
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (route == tab.route) tab.selectedIcon else tab.icon,
                                        contentDescription = if (largeFont) labelText else null
                                    )
                                },
                                label = if (largeFont) {
                                    null
                                } else {
                                    {
                                        Text(
                                            labelText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = "today",
                        modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()
                    ) {
            composable(
                "today",
                deepLinks = listOf(navDeepLink { uriPattern = "opora://today" })
            ) {
                val vm: TodayViewModel = viewModel(factory = vmFactory { TodayViewModel(container.repository) })
                TodayScreen(
                    vm = vm,
                    onEditMood = { date, id ->
                        openRoute(
                            if (id != null) "mood_edit?date=${date.toEpochDay()}&id=$id"
                            else "mood_edit?date=${date.toEpochDay()}"
                        )
                    },
                    onOpenMeds = { navigateTab("meds") },
                    onAddMedication = { openRoute("med_edit") },
                    onOpenJournal = { navigateTab("journal") },
                    onOpenAppointments = { openRoute("appointments") },
                    onEditMedication = { id -> openRoute("med_edit?id=$id") }
                )
            }

            composable(
                "mood",
                deepLinks = listOf(navDeepLink { uriPattern = "opora://mood" })
            ) {
                MoodScreen(
                    vm = moodVm,
                    leftHanded = settings.leftHanded,
                    onEditMood = { date, id ->
                        openRoute(
                            if (id != null) "mood_edit?date=${date.toEpochDay()}&id=$id"
                            else "mood_edit?date=${date.toEpochDay()}"
                        )
                    },
                    onEditEntry = { id ->
                        openRoute("mood_edit?date=${LocalDate.now().toEpochDay()}&id=$id")
                    }
                )
            }

            composable(
                route = "mood_edit?date={date}&id={id}",
                arguments = listOf(
                    navArgument("date") {
                        type = NavType.LongType
                        defaultValue = LocalDate.now().toEpochDay()
                    },
                    navArgument("id") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val date = LocalDate.ofEpochDay(
                    entry.arguments?.getLong("date") ?: LocalDate.now().toEpochDay()
                )
                val entryId = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                MoodEditorScreen(
                    initialDate = date,
                    entryId = entryId,
                    vm = moodVm,
                    onClose = { nav.popBackStack() }
                )
            }

            composable(
                "meds",
                deepLinks = listOf(navDeepLink { uriPattern = "opora://meds" })
            ) {
                val vm: MedsViewModel = viewModel(factory = vmFactory { MedsViewModel(container.repository) })
                MedsScreen(
                    vm = vm,
                    leftHanded = settings.leftHanded,
                    onAddMedication = { openRoute("med_edit") },
                    onEditMedication = { id -> openRoute("med_edit?id=$id") }
                )
            }

            composable(
                route = "med_edit?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                MedEditorScreen(
                    medId = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                    onClose = { nav.popBackStack() }
                )
            }

            composable("journal") {
                val vm: JournalViewModel = viewModel(factory = vmFactory { JournalViewModel(container.repository) })
                JournalScreen(
                    vm = vm,
                    leftHanded = settings.leftHanded,
                    onOpen = { id -> openRoute("journal_edit?id=$id") },
                    onNew = { openRoute("journal_edit") }
                )
            }

            composable(
                route = "journal_edit?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val vm: JournalViewModel = viewModel(factory = vmFactory { JournalViewModel(container.repository) })
                JournalEditorScreen(
                    entryId = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                    vm = vm,
                    onClose = { nav.popBackStack() },
                    onOpenMood = { date, id ->
                        openRoute(
                            if (id != null) "mood_edit?date=${date.toEpochDay()}&id=$id"
                            else "mood_edit?date=${date.toEpochDay()}"
                        )
                    }
                )
            }

            composable("more") {
                val vm: SettingsViewModel = viewModel(
                    factory = vmFactory {
                        SettingsViewModel(
                            container.settings,
                            container.workScheduler,
                            MedicationAlarms(context, container.db),
                            container.repository
                        )
                    }
                )
                MoreScreen(
                    vm = vm,
                    onOpenAppointments = { openRoute("appointments") },
                    onOpenBackup = { openRoute("backup") },
                    onOpenReport = { openRoute("report") },
                    onOpenAbout = { openRoute("about") },
                    onOpenAuthor = { openRoute("author") }
                )
            }

            composable(
                "appointments",
                deepLinks = listOf(navDeepLink { uriPattern = "opora://appointments" })
            ) {
                val vm: AppointmentsViewModel = viewModel(factory = vmFactory { AppointmentsViewModel(container.repository) })
                AppointmentsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("backup") {
                BackupScreen(settings = settings, onBack = { nav.popBackStack() })
            }
            composable("report") {
                ReportScreen(onBack = { nav.popBackStack() })
            }
            composable("about") {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable("author") {
                AuthorScreen(onBack = { nav.popBackStack() })
            }
                }
            }
        }
        // Диалог поверх навигации: показывается один раз на пустой базе.
        AutoBackupRestorePrompt()
    }
}
}
