package dev.rafaelm.pandora

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rafaelm.pandora.data.AndroidIssueImages
import dev.rafaelm.pandora.data.DirectGitHubRepository
import dev.rafaelm.pandora.data.GitHubConfiguration
import dev.rafaelm.pandora.data.DevelopmentStorage
import dev.rafaelm.pandora.data.AppearanceRepository
import dev.rafaelm.pandora.data.Module
import dev.rafaelm.pandora.data.ModulesRepository
import dev.rafaelm.pandora.data.AndroidCalendar
import dev.rafaelm.pandora.data.TaskStorage
import dev.rafaelm.pandora.data.TasksRepository
import dev.rafaelm.pandora.ui.AppearanceViewModel
import dev.rafaelm.pandora.ui.DevelopmentViewModel
import dev.rafaelm.pandora.ui.DevelopmentActions
import dev.rafaelm.pandora.ui.TasksActions
import dev.rafaelm.pandora.ui.TasksViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafaelm.pandora.ui.PandoraApp
import dev.rafaelm.pandora.ui.PandoraTheme
import dev.rafaelm.pandora.ui.colors
import dev.rafaelm.pandora.ui.isDark

class MainActivity : ComponentActivity() {
    private val appearanceRepository by lazy { AppearanceRepository(applicationContext) }
    private val modulesRepository by lazy { ModulesRepository(applicationContext) }

    private val tasksRepository by lazy { TasksRepository(AndroidCalendar(applicationContext), TaskStorage(applicationContext)) }

    private val issueImages by lazy { AndroidIssueImages(contentResolver) }

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
                val modules by modulesRepository.enabled.collectAsStateWithLifecycle()
                val developmentModel: DevelopmentViewModel = viewModel(factory = DevelopmentViewModel.Factory(developmentRepository, issueImages::read))
                val developmentState by developmentModel.state.collectAsStateWithLifecycle()
                val tasksModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory(tasksRepository))
                val tasksState by tasksModel.state.collectAsStateWithLifecycle()
                // Modules that are off do no work; turning one back on refreshes it from its saved data.
                val developmentOn = Module.DEVELOPMENT in modules
                if (developmentOn) LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { developmentModel.refresh() }
                LaunchedEffect(developmentOn) { if (!developmentOn) developmentModel.stop() }
                val tasksOn = Module.TASKS in modules
                LaunchedEffect(tasksOn) { if (!tasksOn) tasksModel.stop() }
                PandoraApp(
                    development = developmentState, developmentActions = DevelopmentActions.from(developmentModel),
                    appearance = appearance, onThemeModeChange = appearanceModel::setMode,
                    onThemeChange = appearanceModel::setTheme, onBothThemesChange = appearanceModel::setBothThemes,
                    tasks = tasksState, tasksActions = TasksActions.from(tasksModel),
                    modules = modules, onModuleChange = modulesRepository::setEnabled,
                )
            }
        }
    }
}
