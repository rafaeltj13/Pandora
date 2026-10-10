package dev.rafaelm.pandora

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rafaelm.pandora.data.HttpStatusRepository
import dev.rafaelm.pandora.data.DirectGitHubRepository
import dev.rafaelm.pandora.data.GitHubConfiguration
import dev.rafaelm.pandora.data.DevelopmentStorage
import dev.rafaelm.pandora.data.AppearanceRepository
import dev.rafaelm.pandora.ui.AppearanceViewModel
import dev.rafaelm.pandora.ui.DevelopmentViewModel
import dev.rafaelm.pandora.ui.DevelopmentActions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafaelm.pandora.ui.PandoraApp
import dev.rafaelm.pandora.ui.PandoraTheme
import dev.rafaelm.pandora.ui.colors
import dev.rafaelm.pandora.ui.WelcomeViewModel
import dev.rafaelm.pandora.ui.isDark

class MainActivity : ComponentActivity() {
    private val repository by lazy { HttpStatusRepository(BuildConfig.API_BASE_URL) }
    private val appearanceRepository by lazy { AppearanceRepository(applicationContext) }

    private val developmentRepository by lazy { DirectGitHubRepository(
        GitHubConfiguration(BuildConfig.GITHUB_CLIENT_ID, BuildConfig.GITHUB_APP_SLUG, BuildConfig.GITHUB_LOGIN),
        DevelopmentStorage(applicationContext),
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearanceModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory(appearanceRepository))
            val appearance by appearanceModel.appearance.collectAsStateWithLifecycle()
            val darkTheme = appearance.mode.isDark()
            PandoraTheme(darkTheme = darkTheme, theme = if (darkTheme) appearance.darkTheme else appearance.lightTheme) {
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(
                            appearance.lightTheme.colors(false).background.copy(alpha = 0.9f).toArgb(),
                            appearance.darkTheme.colors(true).background.copy(alpha = 0.5f).toArgb(),
                        ) { darkTheme },
                    )
                    window.setBackgroundDrawable(backgroundColor.toDrawable())
                }
                val model: WelcomeViewModel = viewModel(factory = WelcomeViewModel.Factory(repository))
                val state by model.state.collectAsStateWithLifecycle()
                val developmentModel: DevelopmentViewModel = viewModel(factory = DevelopmentViewModel.Factory(developmentRepository))
                val developmentState by developmentModel.state.collectAsStateWithLifecycle()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { developmentModel.refresh() }
                PandoraApp(
                    state, onRetry = model::retry,
                    development = developmentState, developmentActions = DevelopmentActions.from(developmentModel),
                    appearance = appearance, onThemeModeChange = appearanceModel::setMode,
                    onThemeChange = appearanceModel::setTheme, onBothThemesChange = appearanceModel::setBothThemes,
                )
            }
        }
    }
}
