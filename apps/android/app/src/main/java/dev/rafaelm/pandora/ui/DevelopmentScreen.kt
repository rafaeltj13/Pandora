package dev.rafaelm.pandora.ui

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.rafaelm.pandora.R
import dev.rafaelm.pandora.data.*
import kotlinx.coroutines.launch

data class DevelopmentActions(
    val refresh: () -> Unit = {},
    val dismissCreated: () -> Unit = {},
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
    val setUploadToken: (String) -> Unit = {},
    val clearUploadToken: () -> Unit = {},
    val addImage: (String) -> Unit = {},
    val removeImage: (String) -> Unit = {},
    val submit: () -> Unit = {},
) {
    companion object {
        fun from(model: DevelopmentViewModel) = DevelopmentActions(
            dismissCreated = model::dismissCreated, refresh = model::refresh, connect = model::connect, disconnect = model::disconnect,
            cancelConnection = model::cancelConnection,
            selectRepositories = model::selectRepositories, loadMore = model::loadMore,
            newIssue = model::newIssue, closeEditor = model::closeEditor, retryOptions = model::retryOptions,
            editTitle = { model.edit(title = it) }, editBody = { model.edit(body = it) },
            setUploadToken = model::setUploadToken, clearUploadToken = model::clearUploadToken,
            addImage = model::addImage, removeImage = model::removeImage,
            editLabels = { model.edit(labels = it) }, editAssignees = { model.edit(assignees = it) }, submit = model::submit,
        )
    }
}

@Composable
fun DevelopmentScreen(state: DevelopmentState, actions: DevelopmentActions, onOpenUrl: (String) -> Unit, onOpenSettings: (() -> Unit)? = null) {
    var selecting by remember { mutableStateOf(false) }
    var showingAccount by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.refresh() }
    DisposableEffect(Unit) { onDispose { actions.closeEditor() } }
    val editor = state.editor
    if (editor != null) {
        BackHandler { actions.closeEditor() }
        IssueForm(state, actions, onOpenUrl)
        return
    }
    val profile = state.profile
    PandoraScaffold("Development", actions = {
        if (profile != null && !state.offline) {
            IconButton(onClick = actions.refresh, enabled = !state.loading) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh")
            }
        }
    }, onOpenSettings = onOpenSettings) { bottom ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("development_screen"),
            contentPadding = PaddingValues(PandoraSpacing.large, PandoraSpacing.large, PandoraSpacing.large, PandoraSpacing.large + bottom), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large),
        ) {
            if (state.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.created?.let { issue ->
                item { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PandoraListItem("Issue #${issue.number} created", supporting = issue.title,
                            modifier = Modifier.weight(1f),
                            trailing = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                            onClick = { onOpenUrl(issue.html_url) })
                        IconButton(onClick = actions.dismissCreated) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Dismiss creation message")
                        }
                    }
                } }
            }
            if (profile == null && state.sessionAvailable) {
                item { PandoraNotice("GitHub is unavailable", "Connect to the internet to load your GitHub account and issues.") {
                    Button(onClick = actions.refresh, enabled = !state.loading) { Text("Retry") }
                    TextButton(onClick = actions.disconnect, enabled = !state.loading) { Text("Disconnect") }
                } }
                state.error?.let { message -> item { ErrorText(message) } }
            } else if (profile == null) {
                item { ConnectCard(state, actions, onOpenUrl) }
                state.error?.let { message -> item { ErrorText(message) } }
            } else {
                item { PandoraSection(null) {
                    PandoraListItem(profile.name ?: profile.login, supporting = "@${profile.login}",
                        leading = { AsyncImage(profile.avatar_url, contentDescription = "GitHub avatar",
                            modifier = Modifier.size(PandoraSpacing.extraLarge + PandoraSpacing.large).clip(CircleShape)) },
                        trailing = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.small)) {
                            Text(if (state.offline) "Offline" else "Connected", style = MaterialTheme.typography.labelMedium,
                                color = if (state.offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                        } }, onClick = { showingAccount = true }, modifier = Modifier.testTag("development_account"))
                } }
                if (state.offline) {
                    item { PandoraNotice("GitHub is unavailable", "Connect to the internet to load issues and create a new issue.") {
                        Button(onClick = actions.refresh, enabled = !state.loading) { Text("Retry") }
                    } }
                    state.error?.let { message -> item { ErrorText(message) } }
                } else {
                    item { Row(Modifier.fillMaxWidth().padding(start = PandoraSpacing.large),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Repositories", style = MaterialTheme.typography.titleMedium)
                            Text("${state.selected.size} selected", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { selecting = true }, enabled = !state.loading,
                            modifier = Modifier.testTag("select_repositories")) { Text("Choose") }
                    } }
                    if (state.repositories.isEmpty() && !state.loading) {
                        item { Hint("No available repositories. Install the GitHub App on your personal repositories with issues enabled, then refresh.") }
                    } else if (state.selected.isEmpty() && !state.loading) {
                        item { Hint("Choose repositories to see their open issues here.") }
                    }
                    items(state.repositories.filter { it.name in state.selected }, key = { it.id }) { repo ->
                        RepositorySection(repo, state, actions, onOpenUrl)
                    }
                    state.error?.let { message -> item { ErrorText(message) } }
                }
            }
        }
    }
    if (showingAccount && profile != null) AlertDialog(onDismissRequest = { showingAccount = false },
        title = { Text("GitHub account") }, text = { Column {
            Text("@${profile.login}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showingAccount = false; onOpenUrl(profile.html_url) }) { Text("GitHub profile") }
            state.installationUrl?.let { url ->
                TextButton(onClick = { showingAccount = false; onOpenUrl(url) }) { Text("Manage access") }
            }
            TextButton(onClick = { showingAccount = false; actions.disconnect() }, enabled = !state.loading) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        } }, confirmButton = { TextButton(onClick = { showingAccount = false }) { Text("Done") } })
    if (selecting) {
        MultiPicker("Repositories", state.repositories.map { it.name }, state.selected,
            descriptions = state.repositories.associate { it.name to if (it.private) "Private" else "Public" },
            onDismiss = { selecting = false }, onApply = { actions.selectRepositories(it); selecting = false })
    }
}

@Composable
private fun ConnectCard(state: DevelopmentState, actions: DevelopmentActions, onOpenUrl: (String) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    PandoraNotice("Your repositories, on the go",
        "Connect your GitHub account to browse open issues and create new ones in your personal repositories.") {
        val authorization = state.authorization
        if (authorization != null) {
            Text("Enter this code on GitHub:")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer {
                        Text(authorization.userCode, style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = PandoraSpacing.large, vertical = PandoraSpacing.small).testTag("github_device_code"))
                    }
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("GitHub device code", authorization.userCode)))
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy GitHub code")
                    }
                }
            }
            Text("Copy the code, open GitHub, and paste it to approve access. Return here to finish connecting.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onOpenUrl(authorization.verificationUrl) }) { Text("Open GitHub") }
            if (state.connecting) {
                Waiting("Waiting for GitHub authorization…")
            } else {
                TextButton(onClick = actions.refresh) { Text("Retry connection") }
            }
            TextButton(onClick = actions.cancelConnection) { Text("Cancel") }
        } else if (state.connecting) {
            Waiting("Requesting a GitHub login code…")
            TextButton(onClick = actions.cancelConnection) { Text("Cancel") }
        } else {
            if (state.configurationRequired) Text("GitHub setup is pending. Connect will be available once Pandora's GitHub App is configured.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = actions.connect, enabled = !state.loading && !state.configurationRequired) { Text("Connect GitHub") }
        }
    }
}

@Composable
private fun RepositorySection(repo: GitHubRepository, state: DevelopmentState, actions: DevelopmentActions, onOpenUrl: (String) -> Unit) {
    PandoraSection(null) {
        PandoraListItem(repo.name, supporting = "${repo.owner.login} · ${if (repo.private) "Private" else "Public"}")
        val page = state.issues[repo.name]
        if (page != null) HorizontalDivider(Modifier.padding(horizontal = PandoraSpacing.large), color = MaterialTheme.colorScheme.outlineVariant)
        if (page != null && page.items.isEmpty()) PandoraListItem("No open issues", supporting = "You’re all caught up on this page.")
        page?.items?.forEach { issue ->
            PandoraListItem(issue.title, supporting = "#${issue.number}",
                trailing = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                onClick = { onOpenUrl(issue.html_url) })
        }
        if (page?.next_page != null) {
            TextButton(onClick = { actions.loadMore(repo.name) }, enabled = repo.name !in state.loadingMore && !state.loading,
                modifier = Modifier.padding(horizontal = PandoraSpacing.small)) {
                Text(if (repo.name in state.loadingMore) "Loading…" else "Load more")
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PandoraSpacing.large))
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = PandoraSpacing.large))
}

@Composable
private fun IssueForm(state: DevelopmentState, actions: DevelopmentActions, onOpenUrl: (String) -> Unit) {
    val editor = state.editor ?: return
    var configuringUpload by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { actions.addImage(it.toString()) }
    }
    PandoraScaffold("New issue", modifier = Modifier.imePadding(), navigationIcon = {
        IconButton(onClick = actions.closeEditor) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
    }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = PandoraSpacing.large, vertical = PandoraSpacing.small),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PandoraSpacing.medium)) {
                    TextButton(onClick = actions.closeEditor) { Text("Discard") }
                    Button(onClick = actions.submit,
                        enabled = editor.title.isNotBlank() && editor.title.length <= 256 && editor.options != null && !editor.loading && !editor.submitting && !editor.uploadingImage,
                        modifier = Modifier.weight(1f).testTag("create_issue")) {
                        Text(if (editor.submitting) "Creating…" else "Create issue")
                    }
                }
            }
        }
    }) { _ ->
        LazyColumn(contentPadding = PaddingValues(PandoraSpacing.large), verticalArrangement = Arrangement.spacedBy(PandoraSpacing.large), modifier = Modifier.fillMaxSize().testTag("issue_form")) {
            item { Hint(editor.repository.full_name) }
            item { OutlinedTextField(editor.title, actions.editTitle, label = { Text("Title") }, singleLine = true, enabled = !editor.submitting, modifier = Modifier.fillMaxWidth().testTag("issue_title")) }
            item { OutlinedTextField(editor.body, actions.editBody, label = { Text("Description (optional)") }, minLines = 3, maxLines = 8, enabled = !editor.submitting, modifier = Modifier.fillMaxWidth().testTag("issue_body")) }
            item {
                PandoraSection(null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = PandoraSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (editor.images.isEmpty()) "Images" else "Images (${editor.images.size})",
                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = state.uploadTokenConfigured && !editor.submitting && !editor.uploadingImage,
                            modifier = Modifier.testTag("add_issue_image")) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(PandoraSpacing.large))
                            Spacer(Modifier.width(PandoraSpacing.extraSmall))
                            Text("Add image")
                        }
                    }
                    if (editor.images.isEmpty()) Text("Add screenshots or photos. Up to 10 MB each.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PandoraSpacing.large, vertical = PandoraSpacing.extraSmall))
                    editor.images.forEach { image ->
                        PandoraListItem(image.name, supporting = "Attached", leading = {
                            IssueImagePreview(image)
                        }, trailing = {
                            IconButton(onClick = { actions.removeImage(image.url) }, enabled = !editor.submitting) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove ${image.name}")
                            }
                        })
                    }
                    if (editor.uploadingImage) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Uploading image…", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = PandoraSpacing.large, vertical = PandoraSpacing.small))
                    }
                    editor.imageError?.let { ErrorText(it) }
                    if (!state.uploadTokenConfigured || editor.imageError != null) {
                        TextButton(onClick = { configuringUpload = true }, enabled = !editor.submitting && !editor.uploadingImage,
                            modifier = Modifier.padding(horizontal = PandoraSpacing.small)) {
                            Text(if (state.uploadTokenConfigured) "Check attachment token" else "Set up image uploads")
                        }
                    }
                }
            }
            if (editor.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Hint("Loading labels and assignees…") }
            item {
                val enabled = editor.options != null && !editor.submitting
                PandoraSection(null) {
                    PandoraListItem("Labels", supporting = editor.labels.joinToString().ifEmpty { "None" },
                        onClick = if (enabled) ({ picker = "Labels" }) else null)
                    PandoraListItem("Assignees", supporting = editor.assignees.joinToString().ifEmpty { "None" },
                        onClick = if (enabled) ({ picker = "Assignees" }) else null)
                }
            }
            editor.error?.let { message -> item {
                ErrorText(message)
                if (editor.options == null) TextButton(onClick = actions.retryOptions) { Text("Retry options") }
                TextButton(onClick = { onOpenUrl("${editor.repository.html_url}/issues") }) { Text("Check repository on GitHub") }
            } }

        }
    }
    if (configuringUpload) GitHubUploadTokenDialog(state, actions, onOpenUrl) { configuringUpload = false }
    picker?.let { title ->
        val options = editor.options ?: return@let
        val labels = title == "Labels"
        MultiPicker(title, if (labels) options.labels.map { it.name } else options.assignees.map { it.login }, if (labels) editor.labels else editor.assignees,
            limit = if (labels) 100 else 10, onDismiss = { picker = null }, onApply = { if (labels) actions.editLabels(it) else actions.editAssignees(it); picker = null })
    }
}

@Composable
private fun IssueImagePreview(image: IssueImage) {
    var loaded by remember(image.previewUri, image.url) { mutableStateOf(false) }
    var useRemote by remember(image.previewUri, image.url) { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(Modifier.size(PandoraSpacing.large * 4)) {
            AsyncImage(if (useRemote) image.url else image.previewUri ?: image.url, contentDescription = null,
                contentScale = ContentScale.Crop, onSuccess = { loaded = true }, onError = {
                    loaded = false
                    if (image.previewUri != null && !useRemote) useRemote = true
                },
                modifier = Modifier.matchParentSize())
            if (!loaded) Icon(painterResource(R.drawable.ic_image), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(PandoraSpacing.extraLarge))
        }
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
                    Row(Modifier.fillMaxWidth().padding(vertical = PandoraSpacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = choice in selection, enabled = choice in selection || selection.size < limit, onCheckedChange = { checked -> selection = if (checked) selection + choice else selection - choice })
                        Column { Text(choice); descriptions[choice]?.let { Text(it, style = MaterialTheme.typography.labelSmall) } }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { onApply(selection) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
