package dev.rafaelm.pandora.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import dev.rafaelm.pandora.ui.navigation.PandoraRoute

private enum class Destination(
    val route: PandoraRoute,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val tag: String,
) {
    HOME(PandoraRoute.Home, R.string.nav_home, R.drawable.ic_home, "navigation_home"),
    EXPLORE(PandoraRoute.Explore, R.string.nav_explore, R.drawable.ic_explore, "navigation_explore"),
    DEVELOPMENT(PandoraRoute.Development, R.string.nav_development, R.drawable.ic_development, "navigation_development"),
    SETTINGS(PandoraRoute.Settings, R.string.nav_settings, R.drawable.ic_settings, "navigation_settings"),
}

@Composable
fun PandoraApp(
    state: WelcomeState,
    onRetry: () -> Unit,
    development: DevelopmentState = DevelopmentState(),
    developmentActions: DevelopmentActions = DevelopmentActions(),
    appearance: AppearancePreferences = AppearancePreferences(),
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onThemeChange: (CatTheme, Boolean) -> Unit = { _, _ -> },
    onBothThemesChange: (CatTheme) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()

    fun isSelected(destination: Destination): Boolean =
        entry?.destination?.hasRoute(destination.route::class) == true ||
            (destination == Destination.SETTINGS && entry?.destination?.hasRoute<PandoraRoute.Appearance>() == true)

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

    Scaffold(
        bottomBar = {
            if (entry?.destination?.hasRoute<PandoraRoute.Appearance>() != true) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = isSelected(destination),
                        onClick = { navigate(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        modifier = Modifier.testTag(destination.tag),
                    )
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = PandoraRoute.Home,
            modifier = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
        ) {
            composable<PandoraRoute.Home> {
                WelcomeScreen(state, onRetry)
            }
            composable<PandoraRoute.Explore> {
                ExploreScreen()
            }
            composable<PandoraRoute.Development> {
                DevelopmentScreen(development, developmentActions, onOpenUrl = uriHandler::openUri)
            }
            composable<PandoraRoute.Settings> {
                SettingsScreen(appearance, onAppearance = { navController.navigate(PandoraRoute.Appearance) })
            }
            composable<PandoraRoute.Appearance> {
                AppearanceScreen(appearance, onThemeModeChange, onThemeChange, onBothThemesChange,
                    onBack = { navController.popBackStack() })
            }
        }
    }
}
