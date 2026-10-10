package dev.rafaelm.pandora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.rafaelm.pandora.data.*

data class DevelopmentActions(
    val refresh: () -> Unit = {},
    val connect: () -> Unit = {},
    val disconnect: () -> Unit = {},
    val cancelConnection: () -> Unit = {},
    val selectRepositories: (Set<String>) -> Unit = {},
    val loadMore: (String) -> Unit = {},
    val newIssue: (GitHubRepository) -> Unit = {},
    val closeEditor: () -> Unit = {},
    val retryOptions: () -> Unit = {},
    val editTitle: (String) -> Unit = {},
    val editBody: (String) -> Unit = {},
    val editLabels: (Set<String>) -> Unit = {},
    val editAssignees: (Set<String>) -> Unit = {},
    val submit: () -> Unit = {},
) {
    companion object {
        fun from(model: DevelopmentViewModel) = DevelopmentActions(
            refresh = model::refresh, connect = model::connect, disconnect = model::disconnect,
            cancelConnection = model::cancelConnection,
            selectRepositories = model::selectRepositories, loadMore = model::loadMore,
            newIssue = model::newIssue, closeEditor = model::closeEditor, retryOptions = model::retryOptions,
            editTitle = { model.edit(title = it) }, editBody = { model.edit(body = it) },
            editLabels = { model.edit(labels = it) }, editAssignees = { model.edit(assignees = it) }, submit = model::submit,
        )
    }
}

@Composable
fun DevelopmentScreen(state: DevelopmentState, actions: DevelopmentActions, onOpenUrl: (String) -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.refresh() }
    DisposableEffect(Unit) { onDispose { actions.closeEditor() } }
    val editor = state.editor
    if (editor != null) {
        BackHandler { actions.closeEditor() }
        IssueForm(editor, actions, onOpenUrl)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("development_screen"),
        contentPadding = PaddingValues(PandoraSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large),
    ) {
        item { Text("Development", style = MaterialTheme.typography.headlineLarge) }
        if (state.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading GitHub…") }
        }
        state.created?.let { issue ->
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PandoraSpacing.large)) {
                Text("Issue #${issue.number} created", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onOpenUrl(issue.html_url) }) { Text("Open on GitHub") }
            } } }
        }
        val profile = state.profile
        if (profile == null && state.sessionAvailable) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PandoraSpacing.extraLarge)) {
                Text("GitHub is unavailable", style = MaterialTheme.typography.titleLarge)
                Text("Connect to the internet to load your GitHub account and issues.")
                Button(onClick = actions.refresh, enabled = !state.loading) { Text("Retry") }
                TextButton(onClick = actions.disconnect, enabled = !state.loading) { Text("Disconnect") }
            } } }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        } else if (profile == null) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PandoraSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                Text("Your repositories, on the go", style = MaterialTheme.typography.titleLarge)
                Text("Connect your GitHub account to browse open issues and create new ones in your personal repositories.")
                val authorization = state.authorization
                if (authorization != null) {
                    Text("Enter this code on GitHub:")
                    SelectionContainer { Text(authorization.userCode, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("github_device_code")) }
                    Text("Open GitHub, enter the code, and approve access. Return here to finish connecting. You can long-press the code to copy it.")
                    Button(onClick = { onOpenUrl(authorization.verificationUrl) }) { Text("Open GitHub") }
                    if (state.connecting) {
                        CircularProgressIndicator()
                        Text("Waiting for GitHub authorization…")
                    } else {
                        TextButton(onClick = actions.refresh) { Text("Retry connection") }
                    }
                    TextButton(onClick = actions.cancelConnection) { Text("Cancel") }
                } else if (state.connecting) {
                    CircularProgressIndicator()
                    Text("Requesting a GitHub login code…")
                    TextButton(onClick = actions.cancelConnection) { Text("Cancel") }
                } else {
                    if (state.configurationRequired) Text("GitHub setup is pending. Connect will be available once Pandora's GitHub App is configured.")
                    Button(onClick = actions.connect, enabled = !state.loading && !state.configurationRequired) { Text("Connect GitHub") }
                }
            } } }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        } else {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PandoraSpacing.large), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                    AsyncImage(profile.avatar_url, contentDescription = "GitHub avatar", modifier = Modifier.size(56.dp).clip(CircleShape))
                    Column {
                        Text(profile.name ?: profile.login, style = MaterialTheme.typography.titleLarge)
                        Text("@${profile.login}")
                        Text(if (state.offline) "Offline" else "Connected", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row {
                    TextButton(onClick = { onOpenUrl(profile.html_url) }) { Text("GitHub profile") }
                    TextButton(onClick = actions.disconnect, enabled = !state.loading) { Text("Disconnect") }
                }
            } } }
            if (state.offline) {
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PandoraSpacing.extraLarge)) {
                    Text("GitHub is unavailable", style = MaterialTheme.typography.titleLarge)
                    Text("Connect to the internet to load issues and create a new issue.")
                    Button(onClick = actions.refresh, enabled = !state.loading) { Text("Retry") }
                } } }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            } else {
                item { Row(horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                    Button(onClick = { selecting = true }, enabled = !state.loading) { Text("Select repositories") }
                    TextButton(onClick = actions.refresh, enabled = !state.loading) { Text("Refresh") }
                } }
                state.installationUrl?.let { url -> item { TextButton(onClick = { onOpenUrl(url) }) { Text("Manage GitHub App access") } } }
                if (state.repositories.isEmpty() && !state.loading) {
                    item { Text("No available repositories. Install the GitHub App on your personal repositories with issues enabled, then refresh.") }
                } else if (state.selected.isEmpty() && !state.loading) {
                    item { Text("Choose repositories to see their open issues here.") }
                }
                items(state.repositories.filter { it.name in state.selected }, key = { it.id }) { repo ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(PandoraSpacing.large), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                        Text(repo.full_name, style = MaterialTheme.typography.titleMedium)
                        Text(if (repo.private) "Private repository" else "Public repository", style = MaterialTheme.typography.labelMedium)
                        Button(onClick = { actions.newIssue(repo) }, enabled = !state.loading && !state.creating, modifier = Modifier.testTag("new_issue:${repo.name}")) { Text("New issue") }
                        val page = state.issues[repo.name]
                        if (page != null && page.items.isEmpty()) Text("No open issues on this page.")
                        page?.items?.forEach { issue ->
                            Column(Modifier.fillMaxWidth().clickable { onOpenUrl(issue.html_url) }.padding(vertical = PandoraSpacing.small)) {
                                Text(issue.title, style = MaterialTheme.typography.bodyLarge)
                                Text("#${issue.number}", style = MaterialTheme.typography.labelMedium)
                            }
                            HorizontalDivider()
                        }
                        if (page?.next_page != null) {
                            TextButton(onClick = { actions.loadMore(repo.name) }, enabled = repo.name !in state.loadingMore && !state.loading) {
                                Text(if (repo.name in state.loadingMore) "Loading…" else "Load more")
                            }
                        }
                    } }
                }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
    if (selecting) {
        MultiPicker("Repositories", state.repositories.map { it.name }, state.selected,
            descriptions = state.repositories.associate { it.name to if (it.private) "Private" else "Public" },
            onDismiss = { selecting = false }, onApply = { actions.selectRepositories(it); selecting = false })
    }
}

@Composable
private fun IssueForm(editor: IssueEditor, actions: DevelopmentActions, onOpenUrl: (String) -> Unit) {
    var picker by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(PandoraSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large), modifier = Modifier.fillMaxSize().imePadding().testTag("issue_form")) {
        item { Text("New issue", style = MaterialTheme.typography.headlineLarge); Text(editor.repository.full_name) }
        item { OutlinedTextField(editor.title, actions.editTitle, label = { Text("Title") }, singleLine = true, enabled = !editor.submitting, modifier = Modifier.fillMaxWidth().testTag("issue_title")) }
        item { OutlinedTextField(editor.body, actions.editBody, label = { Text("Description (optional)") }, minLines = 5, enabled = !editor.submitting, modifier = Modifier.fillMaxWidth()) }
        if (editor.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading labels and assignees…") }
        item {
            OutlinedButton(onClick = { picker = "Labels" }, enabled = editor.options != null && !editor.submitting, modifier = Modifier.fillMaxWidth()) { Text("Labels: ${editor.labels.joinToString().ifEmpty { "None" }}") }
            OutlinedButton(onClick = { picker = "Assignees" }, enabled = editor.options != null && !editor.submitting, modifier = Modifier.fillMaxWidth()) { Text("Assignees: ${editor.assignees.joinToString().ifEmpty { "None" }}") }
        }
        editor.error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
            if (editor.options == null) TextButton(onClick = actions.retryOptions) { Text("Retry options") }
            TextButton(onClick = { onOpenUrl("${editor.repository.html_url}/issues") }) { Text("Check repository on GitHub") }
        } }
        item {
            Button(onClick = actions.submit, enabled = editor.title.isNotBlank() && editor.title.length <= 256 && editor.options != null && !editor.loading && !editor.submitting, modifier = Modifier.fillMaxWidth().testTag("create_issue")) { Text(if (editor.submitting) "Creating…" else "Create issue") }
            TextButton(onClick = actions.closeEditor) { Text("Discard") }
        }
    }
    picker?.let { title ->
        val options = editor.options ?: return@let
        val labels = title == "Labels"
        MultiPicker(title, if (labels) options.labels.map { it.name } else options.assignees.map { it.login }, if (labels) editor.labels else editor.assignees,
            limit = if (labels) 100 else 10, onDismiss = { picker = null }, onApply = { if (labels) actions.editLabels(it) else actions.editAssignees(it); picker = null })
    }
}

@Composable
private fun MultiPicker(title: String, choices: List<String>, selected: Set<String>, descriptions: Map<String, String> = emptyMap(), limit: Int = Int.MAX_VALUE, onDismiss: () -> Unit, onApply: (Set<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(selected) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column {
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val filtered = choices.filter { it.contains(query, ignoreCase = true) }
                if (filtered.isEmpty()) item { Text("No matches", modifier = Modifier.padding(PandoraSpacing.medium)) }
                items(filtered, key = { it }) { choice ->
                    Row(Modifier.fillMaxWidth().padding(vertical = PandoraSpacing.extraSmall)) {
                        Checkbox(checked = choice in selection, enabled = choice in selection || selection.size < limit, onCheckedChange = { checked -> selection = if (checked) selection + choice else selection - choice })
                        Column(Modifier.padding(top = PandoraSpacing.medium)) { Text(choice); descriptions[choice]?.let { Text(it, style = MaterialTheme.typography.labelSmall) } }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onApply(selection) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
