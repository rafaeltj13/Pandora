package dev.rafaelm.pandora.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.rafaelm.pandora.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IssueEditor(
    val repository: GitHubRepository,
    val title: String = "",
    val body: String = "",
    val labels: Set<String> = emptySet(),
    val assignees: Set<String> = emptySet(),
    val options: GitHubIssueOptions? = null,
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
)
data class DevelopmentState(
    val loading: Boolean = false,
    val connecting: Boolean = false,
    val authorization: GitHubAuthorization? = null,
    val configurationRequired: Boolean = false,
    val profile: GitHubProfile? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val selected: Set<String> = emptySet(),
    val issues: Map<String, GitHubIssuePage> = emptyMap(),
    val loadingMore: Set<String> = emptySet(),
    val installationUrl: String? = null,
    val offline: Boolean = false,
    val error: String? = null,
    val editor: IssueEditor? = null,
    val created: GitHubIssue? = null,
    val creating: Boolean = false,
    val sessionAvailable: Boolean = false,
)

class DevelopmentViewModel(private val repository: DevelopmentRepository) : ViewModel() {
    private val mutable = MutableStateFlow(DevelopmentState(sessionAvailable = repository.hasSession(), configurationRequired = !repository.isConfigured()))
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var editorJob: Job? = null
    private var accountEpoch = 0
    private fun update(block: (DevelopmentState) -> DevelopmentState) { mutable.value = block(mutable.value) }
    private fun failure(error: Exception) {
        if (error is CancellationException) throw error
        if (error is DevelopmentApiException && error.status == 401) {
            repository.clearSession()
            accountEpoch++
            mutable.value = DevelopmentState(error = error.message, configurationRequired = !repository.isConfigured())
        } else {
            update { it.copy(loading = false, connecting = false, offline = it.profile != null || it.sessionAvailable, error = error.message ?: "GitHub is unavailable. Check your connection and retry.") }
        }
    }
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            try {
                if (repository.isPending()) { update { it.copy(authorization = repository.pendingAuthorization()) }; pollConnection(); return@launch }
                if (!repository.hasSession()) { update { it.copy(loading = false, configurationRequired = !repository.isConfigured()) }; return@launch }
                loadDashboard()
            } catch (error: Exception) { failure(error) }
        }
    }
    private suspend fun loadDashboard() {
        update { it.copy(loading = true, sessionAvailable = true, error = null) }
        val profile = repository.profile()
        update { it.copy(profile = profile) }
        val repos = repository.repositories()
        val selected = repository.selectedRepositories(profile.login).intersect(repos.repositories.map { it.name }.toSet())
        val pages = selected.associateWith { repository.issues(it) }
        update { it.copy(loading = false, connecting = false, authorization = null, profile = profile, repositories = repos.repositories, selected = selected, issues = pages, installationUrl = repos.installation_url, offline = false, error = null) }
    }
    fun connect() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            update { it.copy(connecting = true, error = null) }
            try {
                val authorization = repository.connect()
                update { it.copy(authorization = authorization) }
                pollConnection()
            } catch (error: Exception) { failure(error) }
        }
    }
    private suspend fun pollConnection() {
        update { it.copy(connecting = true) }
        while (repository.isPending()) {
            // The repository enforces GitHub's interval, slowdown, and expiry rules.
            if (repository.connection()) { loadDashboard(); return }
        }
        update { it.copy(connecting = false) }
    }
    fun cancelConnection() {
        loadJob?.cancel(); accountEpoch++; repository.clearSession()
        mutable.value = DevelopmentState(configurationRequired = !repository.isConfigured())
    }
    fun disconnect() {
        loadJob?.cancel(); editorJob?.cancel(); accountEpoch++
        loadJob = viewModelScope.launch {
            try { repository.disconnect(); mutable.value = DevelopmentState(configurationRequired = !repository.isConfigured()) }
            catch (error: Exception) { failure(error) }
        }
    }
    fun selectRepositories(names: Set<String>) {
        val profile = mutable.value.profile ?: return
        if (loadJob?.isActive == true) return
        repository.selectRepositories(profile.login, names)
        update { it.copy(selected = names) }; refresh()
    }
    fun loadMore(name: String) {
        val current = mutable.value.issues[name] ?: return
        val next = current.next_page ?: return
        if (name in mutable.value.loadingMore || mutable.value.offline || mutable.value.loading) return
        val epoch = accountEpoch
        viewModelScope.launch {
            update { it.copy(loadingMore = it.loadingMore + name) }
            try {
                val page = repository.issues(name, next)
                if (epoch != accountEpoch) return@launch
                update {
                    if (it.issues[name]?.next_page != next) it else it.copy(issues = it.issues + (name to GitHubIssuePage((it.issues[name]!!.items + page.items).distinctBy { issue -> issue.number }, page.next_page)))
                }
            } catch (error: Exception) { if (epoch == accountEpoch) failure(error) else if (error is CancellationException) throw error }
            finally { if (epoch == accountEpoch) update { it.copy(loadingMore = it.loadingMore - name) } }
        }
    }
    fun newIssue(repo: GitHubRepository) {
        if (mutable.value.offline || mutable.value.loading || mutable.value.creating) return
        editorJob?.cancel()
        update { it.copy(editor = IssueEditor(repo), created = null) }
        loadOptions(repo)
    }
    private fun loadOptions(repo: GitHubRepository) {
        editorJob = viewModelScope.launch {
            try {
                val options = repository.options(repo.name)
                update { it.copy(editor = it.editor?.takeIf { editor -> editor.repository.id == repo.id }?.copy(options = options, loading = false)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is DevelopmentApiException && error.status == 401) { failure(error); return@launch }
                update { it.copy(editor = it.editor?.copy(loading = false, error = error.message ?: "Could not load issue options.")) }
            }
        }
    }
    fun retryOptions() { mutable.value.editor?.let { editor -> update { it.copy(editor = editor.copy(loading = true, error = null)) }; loadOptions(editor.repository) } }
    fun edit(title: String? = null, body: String? = null, labels: Set<String>? = null, assignees: Set<String>? = null) {
        update { state -> state.copy(editor = state.editor?.let { if (it.submitting) it else it.copy(title = title ?: it.title, body = body ?: it.body, labels = labels ?: it.labels, assignees = assignees ?: it.assignees) }) }
    }
    fun closeEditor() { editorJob?.cancel(); update { it.copy(editor = null) } }
    /** The module was switched off: stop loading but keep the session and any pending authorization. */
    fun stop() { loadJob?.cancel(); closeEditor(); update { it.copy(loading = false, connecting = false) } }
    fun submit() {
        val editor = mutable.value.editor ?: return
        if (editor.submitting || editor.loading || editor.options == null || editor.title.isBlank() || mutable.value.offline || mutable.value.creating) return
        update { it.copy(editor = editor.copy(submitting = true, error = null), creating = true) }
        val epoch = accountEpoch
        // Keep the mutation running when leaving the form; never retry automatically.
        viewModelScope.launch {
            try {
                val issue = repository.create(editor.repository.name, NewGitHubIssue(editor.title.trim(), editor.body, editor.labels.toList(), editor.assignees.toList()))
                if (epoch != accountEpoch) return@launch
                update { it.copy(editor = null, created = issue, creating = false) }
                refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (epoch != accountEpoch) return@launch
                if (error is DevelopmentApiException && error.status == 401) { failure(error); return@launch }
                val message = if (error is DevelopmentApiException) error.message else "Creation could not be confirmed. Check the repository on GitHub before retrying."
                update { it.copy(creating = false, editor = it.editor?.copy(submitting = false, error = message), error = if (it.editor == null) message else it.error) }
            }
        }
    }
    class Factory(private val repository: DevelopmentRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DevelopmentViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return DevelopmentViewModel(repository) as T
        }
    }
}
