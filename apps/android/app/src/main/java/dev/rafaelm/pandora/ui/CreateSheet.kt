package dev.rafaelm.pandora.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.GitHubRepository
import dev.rafaelm.pandora.data.Module
import kotlinx.coroutines.launch

/**
 * Opened from the dock's add button: lists what each enabled module can create.
 * An issue first asks for its repository in the same sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateSheet(
    modules: Set<Module>,
    development: DevelopmentState,
    onDismiss: () -> Unit,
    onNewTask: () -> Unit,
    onNewIssue: (GitHubRepository) -> Unit,
    onOpenDevelopment: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var choosingRepository by remember { mutableStateOf(false) }
    // Animate the sheet away before the chosen form or page appears.
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss(); then() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("create_sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = PandoraSpacing.large)) {
            Text(if (choosingRepository) "Choose a repository" else "Create", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = PandoraSpacing.extraLarge, vertical = PandoraSpacing.medium))
            if (!choosingRepository) {
                if (Module.TASKS in modules) PandoraListItem("Task", supporting = "Add it to Google Calendar", icon = R.drawable.ic_tasks,
                    onClick = { close(onNewTask) }, modifier = Modifier.testTag("create_option_task"))
                if (Module.DEVELOPMENT in modules) PandoraListItem("Issue", supporting = "Open a GitHub issue in one of your repositories",
                    icon = R.drawable.ic_development, modifier = Modifier.testTag("create_option_issue"), onClick = {
                        // Without a working GitHub connection, the Development page explains what to do.
                        if (development.profile == null || development.offline) close(onOpenDevelopment) else choosingRepository = true
                    })
            } else {
                val busy = development.loading || development.creating
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PandoraSpacing.extraLarge))
                if (development.repositories.isEmpty() && !busy) {
                    Text("No available repositories. Install the GitHub App on your personal repositories with issues enabled, then refresh.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PandoraSpacing.extraLarge))
                }
                // Repositories shown on the Development page come first.
                development.repositories.sortedBy { it.name !in development.selected }.forEach { repo ->
                    PandoraListItem(repo.full_name, supporting = if (repo.private) "Private repository" else "Public repository",
                        onClick = if (busy) null else ({ close { onNewIssue(repo) } }), modifier = Modifier.testTag("create_repository:${repo.name}"))
                }
            }
        }
    }
}
