package dev.rafaelm.pandora.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.ThemeMode
import dev.rafaelm.pandora.data.CatTheme
import dev.rafaelm.pandora.data.AppearancePreferences
import dev.rafaelm.pandora.data.Module
import dev.rafaelm.pandora.ui.navigation.PandoraRoute

private enum class Destination(
    val route: PandoraRoute,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val tag: String,
    /** The module that must be on for this page to be reachable; null pages are always there. */
    val module: Module? = null,
) {
    HOME(PandoraRoute.Home, R.string.nav_home, R.drawable.ic_home, "navigation_home"),
    TASKS(PandoraRoute.Tasks, R.string.nav_tasks, R.drawable.ic_tasks, "navigation_tasks", Module.TASKS),
    DEVELOPMENT(PandoraRoute.Development, R.string.nav_development, R.drawable.ic_development, "navigation_development", Module.DEVELOPMENT),
}

@Composable
fun PandoraApp(
    development: DevelopmentState = DevelopmentState(),
    developmentActions: DevelopmentActions = DevelopmentActions(),
    appearance: AppearancePreferences = AppearancePreferences(),
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onThemeChange: (CatTheme, Boolean) -> Unit = { _, _ -> },
    onBothThemesChange: (CatTheme) -> Unit = {},
    modules: Set<Module> = Module.entries.toSet(),
    onModuleChange: (Module, Boolean) -> Unit = { _, _ -> },
    tasks: TasksState = TasksState(),
    tasksActions: TasksActions = TasksActions(),
) {
    val uriHandler = LocalUriHandler.current
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()

    fun isSelected(destination: Destination): Boolean =
        entry?.destination?.hasRoute(destination.route::class) == true

    fun navigate(destination: Destination) {
        if (isSelected(destination)) return
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val destinations = Destination.entries.filter { it.module == null || it.module in modules }
    // A page whose module was switched off in Settings falls back to Home once it is on screen again.
    LaunchedEffect(entry, modules) {
        if (Destination.entries.any { isSelected(it) && it !in destinations }) {
            navController.popBackStack(PandoraRoute.Home, inclusive = false)
        }
    }

    // Settings is a sub-page opened from the header of each dock page, so the dock hides there,
    // and on the full-screen task and issue forms.
    val showDock = Destination.entries.any(::isSelected) && tasks.editor == null && development.editor == null
    var creating by rememberSaveable { mutableStateOf(false) }
    // A task chosen from the add sheet opens once the Tasks page has loaded its calendars.
    var pendingTask by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pendingTask, tasks.access, tasks.loaded, tasks.loading) {
        if (!pendingTask) return@LaunchedEffect
        if (tasks.loaded && !tasks.loading) { pendingTask = false; tasksActions.newTask() }
        else if (!tasks.access) pendingTask = false
    }
    val openSettings = { navController.navigate(PandoraRoute.Settings) { launchSingleTop = true } }
    // Pages draw under the dock and pad their scrolling content by LocalDockClearance.
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDockClearance provides if (showDock) DockClearance else 0.dp) {
            NavHost(
                navController = navController,
                startDestination = PandoraRoute.Home,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { if (targetState.isSubPage()) slideIn(forward = true) else fadeThroughIn() },
                exitTransition = { if (targetState.isSubPage()) slideOut(forward = true) else fadeThroughOut() },
                popEnterTransition = { if (initialState.isSubPage()) slideIn(forward = false) else fadeThroughIn() },
                popExitTransition = { if (initialState.isSubPage()) slideOut(forward = false) else fadeThroughOut() },
            ) {
                composable<PandoraRoute.Home> {
                    WelcomeScreen(onOpenSettings = openSettings, showModulesHint = modules.isEmpty())
                }
                composable<PandoraRoute.Tasks> {
                    TasksScreen(tasks, tasksActions, onOpenSettings = openSettings)
                }
                composable<PandoraRoute.Development> {
                    DevelopmentScreen(development, developmentActions, onOpenUrl = uriHandler::openUri, onOpenSettings = openSettings)
                }
                composable<PandoraRoute.Settings> {
                    SettingsScreen(appearance, onAppearance = { navController.navigate(PandoraRoute.Appearance) },
                        modules = modules, onModuleChange = onModuleChange, tasks = tasks, tasksActions = tasksActions,
                        development = development, developmentActions = developmentActions, onOpenUrl = uriHandler::openUri, onBack = { navController.popBackStack() })
                }
                composable<PandoraRoute.Appearance> {
                    AppearanceScreen(appearance, onThemeModeChange, onThemeChange, onBothThemesChange,
                        onBack = { navController.popBackStack() })
                }
            }
        }
        AnimatedVisibility(showDock, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(FadeInMillis, FadeOutMillis)), exit = fadeOut(tween(FadeOutMillis))) {
            PandoraDock(destinations, ::isSelected, ::navigate, onAdd = if (modules.isEmpty()) null else ({ creating = true }))
        }
    }
    if (creating) {
        CreateSheet(modules, development, onDismiss = { creating = false },
            onNewTask = { navigate(Destination.TASKS); pendingTask = true },
            onNewIssue = { repo -> navigate(Destination.DEVELOPMENT); developmentActions.newIssue(repo) },
            onOpenDevelopment = { navigate(Destination.DEVELOPMENT) })
    }
}

/*
 * Material motion: dock pages fade through (the old page fades out quickly, then the new one fades in
 * while settling from a slight zoom); Settings and Appearance slide along the x-axis as sub-pages.
 */
private const val FadeOutMillis = 90
private const val FadeInMillis = 210
private const val SlideMillis = 300

private fun NavBackStackEntry.isSubPage() =
    destination.hasRoute(PandoraRoute.Settings::class) || destination.hasRoute(PandoraRoute.Appearance::class)

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(FadeInMillis, FadeOutMillis, LinearOutSlowInEasing)) +
        scaleIn(tween(FadeInMillis, FadeOutMillis, LinearOutSlowInEasing), initialScale = 0.96f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(FadeOutMillis))

/** Sub-pages enter from the end and leave toward it; [forward] is false when going back. */
private fun slideIn(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(SlideMillis, easing = FastOutSlowInEasing)) { if (forward) it / 10 else -it / 10 } +
        fadeIn(tween(FadeInMillis, FadeOutMillis, LinearOutSlowInEasing))

private fun slideOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(SlideMillis, easing = FastOutSlowInEasing)) { if (forward) -it / 10 else it / 10 } +
        fadeOut(tween(FadeOutMillis))

private val DockHeight = 64.dp
private val DockClearance = DockHeight + PandoraSpacing.medium * 2

/** Floating dock: page tabs in a pill sized to its tabs, with a separate round add button when anything can be created. */
@Composable
private fun PandoraDock(
    destinations: List<Destination>,
    isSelected: (Destination) -> Boolean,
    navigate: (Destination) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = PandoraSpacing.large, vertical = PandoraSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.height(DockHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.fillMaxHeight().padding(PandoraSpacing.small).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    DockTab(destination, isSelected(destination)) { navigate(destination) }
                }
            }
        }
        if (onAdd != null) Surface(
            onClick = onAdd,
            modifier = Modifier.size(DockHeight).testTag("dock_add"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.dock_add))
            }
        }
    }
}

/** The selected tab grows into a primary-container pill with its icon; the others show only their label. */
@Composable
private fun DockTab(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    // Icon, width, and colors share one quick spring so the old tab shrinks while the new one grows.
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
        spring(stiffness = Spring.StiffnessMedium), label = "dockTabContainer",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        spring(stiffness = Spring.StiffnessMedium), label = "dockTabContent",
    )
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxHeight().clip(CircleShape)
            .selectable(selected, role = Role.Tab, onClick = onClick).testTag(destination.tag),
    ) {
        Row(
            Modifier.padding(horizontal = PandoraSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                selected,
                enter = expandHorizontally(DockTabSize) + fadeIn(DockTabFade),
                exit = shrinkHorizontally(DockTabSize) + fadeOut(DockTabFade),
            ) {
                Icon(painterResource(destination.icon), contentDescription = null,
                    modifier = Modifier.padding(end = PandoraSpacing.small))
            }
            Text(stringResource(destination.label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private val DockTabSize = spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntSize.VisibilityThreshold)
private val DockTabFade = spring<Float>(stiffness = Spring.StiffnessMedium)
