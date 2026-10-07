package com.secondmemory.android

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.data.UsageInfo
import com.secondmemory.android.network.AuthException
import com.secondmemory.android.network.BackendClient
import com.secondmemory.android.network.ConflictException
import com.secondmemory.android.network.LoginResult
import com.secondmemory.android.recording.MemoryRecordingService
import com.secondmemory.android.state.MemoryMode
import com.secondmemory.android.state.MemoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.secondmemory.android.state.MemoryUiState
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<MemoryUiState> = MemoryState.state
    private val mutableHistory = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = mutableHistory.asStateFlow()
    private val mutableAuth = MutableStateFlow(AppSettings.isLoggedIn(application))
    val isLoggedIn: StateFlow<Boolean> = mutableAuth.asStateFlow()
    private val mutableLoginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = mutableLoginError.asStateFlow()
    private val mutableLoggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = mutableLoggingIn.asStateFlow()
    private val mutableUsername = MutableStateFlow(AppSettings.username(application))
    val username: StateFlow<String?> = mutableUsername.asStateFlow()

    private val mutableUsage = MutableStateFlow<UsageInfo?>(null)
    val usage: StateFlow<UsageInfo?> = mutableUsage.asStateFlow()

    private val mutableMemories = MutableStateFlow(MemoriesUiState())
    val memories: StateFlow<MemoriesUiState> = mutableMemories.asStateFlow()
    private val pollJobs = ConcurrentHashMap<String, Job>()

    init {
        // A recording whose processing never finished (process killed, offline, backend error) stays
        // retryable across app restarts as long as its audio file still exists.
        val pending = AppSettings.pendingAudio(application)?.takeIf { File(it).exists() }
        MemoryState.restore(AppSettings.lastResult(application), AppSettings.lastAudio(application), pending)
        if (mutableAuth.value) refreshUsage()
        // A finished recording has used minutes, so refresh what's left.
        viewModelScope.launch {
            MemoryState.state.map { it.mode }.distinctUntilChanged().collect { mode ->
                if (mode == MemoryMode.READY && mutableAuth.value) refreshUsage()
            }
        }
    }

    /** Reloads the account's usage; failures keep the last known value (the server enforces limits anyway). */
    fun refreshUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().usage() }
                .onSuccess { mutableUsage.value = it }
                .onFailure { if (it is AuthException) handleAuthFailure() }
        }
    }

    private fun client(): BackendClient {
        val app = getApplication<Application>()
        return BackendClient(BuildConfig.DEFAULT_BACKEND_URL, AppSettings.authToken(app))
    }

    private fun handleAuthFailure() {
        AppSettings.clearSession(getApplication())
        mutableAuth.value = false
        mutableUsername.value = null
    }

    fun login(username: String, password: String) = authenticate(username, password) { user, pass ->
        BackendClient(BuildConfig.DEFAULT_BACKEND_URL).login(user, pass)
    }

    fun register(username: String, password: String) = authenticate(username, password) { user, pass ->
        BackendClient(BuildConfig.DEFAULT_BACKEND_URL).register(user, pass)
    }

    private fun authenticate(username: String, password: String, call: suspend (String, String) -> LoginResult) {
        if (username.isBlank() || password.isBlank()) return
        mutableLoggingIn.value = true
        mutableLoginError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                call(username.trim(), password)
            }.onSuccess { result ->
                AppSettings.saveSession(getApplication(), result.token, result.userId, result.username)
                mutableUsername.value = result.username
                mutableLoggingIn.value = false
                mutableAuth.value = true
                refreshUsage()
            }.onFailure { error ->
                mutableLoggingIn.value = false
                mutableLoginError.value = error.message ?: "Could not sign in"
            }
        }
    }

    fun logout() {
        mutableUsage.value = null
        AppSettings.clearSession(getApplication())
        mutableAuth.value = false
        mutableUsername.value = null
    }

    fun activate(context: Context) {
        val usage = mutableUsage.value
        if (usage != null && usage.recordableMinutes <= 0) {
            MemoryState.failure("You've used this month's recording minutes. They reset on ${resetDate(usage.audioResetsAt)}.")
            return
        }
        // Stop automatically before the recording outgrows what the account can still process.
        val maxSeconds = (usage?.recordableMinutes ?: DEFAULT_MAX_RECORDING_MINUTES) * 60L
        try {
            MemoryRecordingService.activate(context, maxSeconds)
        } catch (ex: Exception) { MemoryState.failure(ex.message ?: "Could not activate Memory") }
    }

    fun deactivate(context: Context) = MemoryRecordingService.deactivate(context)
    fun retry(context: Context, path: String) = MemoryRecordingService.retry(context, path)

    /** Gives up on a failed recording: removes the local audio and clears the retry state. */
    fun discardRecording() {
        val app = getApplication<Application>()
        val path = MemoryState.state.value.lastAudioPath
        AppSettings.clearPending(app)
        MemoryState.dismissError()
        viewModelScope.launch(Dispatchers.IO) { path?.let { runCatching { File(it).delete() } } }
    }

    fun ask(question: String) {
        if (question.isBlank()) return
        MemoryState.asking(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = client().ask(question.trim())
                MemoryState.answer(result)
            } catch (ex: AuthException) {
                handleAuthFailure()
                MemoryState.askFailure(ex.message ?: "Session expired, please sign in again")
            } catch (ex: Exception) {
                MemoryState.askFailure(ex.message ?: "Could not ask Memory")
            }
            refreshUsage()
        }
    }

    fun loadHistory() {
        mutableHistory.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                client().listSessions()
            }.onSuccess { sessions ->
                mutableHistory.update { it.copy(loading = false, sessions = sessions, error = null) }
            }.onFailure { error ->
                if (error is AuthException) handleAuthFailure()
                mutableHistory.update { it.copy(loading = false, error = error.message ?: "Could not load conversations") }
            }
        }
    }

    fun openConversation(sessionId: String) {
        mutableHistory.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                client().sessionDetail(sessionId)
            }.onSuccess { detail ->
                mutableHistory.update { it.copy(loading = false, selected = detail, error = null) }
            }.onFailure { error ->
                if (error is AuthException) handleAuthFailure()
                mutableHistory.update { it.copy(loading = false, error = error.message ?: "Could not open conversation") }
            }
        }
    }

    fun closeConversation() = mutableHistory.update { it.copy(selected = null, error = null) }

    fun renameConversation(title: String) {
        val detail = mutableHistory.value.selected ?: return
        if (title.isBlank()) return
        mutateHistory {
            val updated = client().renameSession(detail.session.id, title.trim())
            mutableHistory.update { state ->
                state.copy(
                    selected = state.selected?.copy(session = updated),
                    sessions = state.sessions.map { if (it.id == updated.id) updated else it }
                )
            }
        }
    }

    fun deleteConversation() {
        val detail = mutableHistory.value.selected ?: return
        mutateHistory {
            client().deleteSession(detail.session.id)
            mutableHistory.update { state ->
                state.copy(selected = null, sessions = state.sessions.filterNot { it.id == detail.session.id })
            }
        }
    }

    fun refreshMemories() {
        loadHistory()
        loadCommitments()
    }

    fun loadCommitments() {
        val overdue = mutableMemories.value.overdueOnly
        mutableMemories.update { it.copy(commitmentsLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().openMemories(overdue) }
                .onSuccess { items ->
                    mutableMemories.update {
                        if (it.overdueOnly == overdue) it.copy(commitmentsLoading = false, commitments = items) else it
                    }
                }
                .onFailure { error -> memoriesFailure(error, "Could not load commitments") }
        }
    }

    fun setOverdueOnly(value: Boolean) {
        mutableMemories.update { it.copy(overdueOnly = value) }
        loadCommitments()
    }

    fun searchMemories(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearSearch()
            return
        }
        mutableMemories.update { it.copy(searchQuery = trimmed, searching = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().searchMemories(trimmed) }
                .onSuccess { results ->
                    mutableMemories.update {
                        if (it.searchQuery == trimmed) it.copy(searching = false, searchResults = results) else it
                    }
                }
                .onFailure { error -> memoriesFailure(error, "Could not search memories") }
        }
    }

    fun clearSearch() = mutableMemories.update { it.copy(searchQuery = "", searchResults = null, searching = false) }

    fun completeMemory(memoryId: String) = memoryOperation(memoryId) { updateMemory(memoryId, status = "DONE") }

    fun removeMemory(memoryId: String) = memoryOperation(memoryId) { deleteMemory(memoryId); null }

    /** Moves the due date [days] from now, or clears it when [days] is null. */
    fun rescheduleMemory(memoryId: String, days: Long?) = memoryOperation(memoryId, reloadCommitments = true) {
        if (days == null) updateMemory(memoryId, clearDueAt = true)
        else updateMemory(memoryId, dueAt = Instant.now().plus(days, ChronoUnit.DAYS).toString())
    }

    /** Runs a memory change on the server and mirrors it into every list that shows the memory. */
    private fun memoryOperation(
        memoryId: String,
        reloadCommitments: Boolean = false,
        operation: BackendClient.() -> MemoryItem?
    ) {
        mutableMemories.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().operation() }
                .onSuccess { updated ->
                    applyMemoryChange(memoryId, updated)
                    if (reloadCommitments) loadCommitments()
                }
                .onFailure { error -> memoriesFailure(error, "Could not update memory") }
        }
    }

    /** [updated] null means the memory was deleted. */
    private fun applyMemoryChange(memoryId: String, updated: MemoryItem?) {
        mutableMemories.update { state ->
            state.copy(
                busy = false,
                commitments = state.commitments.mapNotNull { memory ->
                    if (memory.id != memoryId) memory else updated?.takeIf { it.resolutionStatus == "OPEN" }
                },
                searchResults = state.searchResults?.mapNotNull { memory ->
                    if (memory.id != memoryId) memory else updated
                }
            )
        }
        mutableHistory.update { history ->
            val current = history.selected ?: return@update history
            history.copy(selected = current.copy(memories = current.memories.mapNotNull { memory ->
                if (memory.id != memoryId) memory else updated
            }))
        }
    }

    private fun memoriesFailure(error: Throwable, fallback: String) {
        if (error is AuthException) handleAuthFailure()
        mutableMemories.update {
            it.copy(
                busy = false, commitmentsLoading = false, searching = false,
                error = error.message ?: fallback
            )
        }
    }

    /** Re-runs server-side processing for a failed or stuck conversation and follows it to the end. */
    fun retrySession(sessionId: String) {
        mutableHistory.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().startProcessing(sessionId) }
                .onSuccess {
                    setSessionProcessing(sessionId)
                    mutableHistory.update { it.copy(mutating = false) }
                    pollSession(sessionId)
                }
                .onFailure { error ->
                    if (error is ConflictException) {
                        // Already running (or just finished) on the server: follow it instead of failing.
                        mutableHistory.update { it.copy(mutating = false) }
                        pollSession(sessionId)
                        return@onFailure
                    }
                    if (error is AuthException) handleAuthFailure()
                    mutableHistory.update {
                        it.copy(mutating = false, error = error.message ?: "Could not retry processing")
                    }
                }
        }
    }

    private fun setSessionProcessing(sessionId: String) = mutableHistory.update { history ->
        val now = Instant.now().toString()
        history.copy(
            sessions = history.sessions.map {
                if (it.id == sessionId) it.copy(status = "PROCESSING", errorMessage = null, updatedAt = now) else it
            },
            selected = history.selected?.let { detail ->
                if (detail.session.id != sessionId) detail
                else detail.copy(session = detail.session.copy(status = "PROCESSING", errorMessage = null, updatedAt = now))
            }
        )
    }

    private fun pollSession(sessionId: String) {
        pollJobs.remove(sessionId)?.cancel()
        pollJobs[sessionId] = viewModelScope.launch(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 10 * 60_000L
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(4_000)
                val detail = runCatching { client().sessionDetail(sessionId) }.getOrNull() ?: continue
                val status = detail.session.status
                mutableHistory.update { history ->
                    history.copy(
                        sessions = history.sessions.map { if (it.id == sessionId) detail.session else it },
                        selected = history.selected?.let { if (it.session.id == sessionId) detail else it }
                    )
                }
                if (status == "COMPLETED" || status == "FAILED") break
            }
            pollJobs.remove(sessionId)
        }
    }

    private fun mutateHistory(operation: () -> Unit) {
        mutableHistory.update { it.copy(mutating = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching(operation).onSuccess {
                mutableHistory.update { it.copy(mutating = false) }
            }.onFailure { error ->
                if (error is AuthException) handleAuthFailure()
                mutableHistory.update {
                    it.copy(mutating = false, error = error.message ?: "Could not update conversation")
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_RECORDING_MINUTES = 60
    }
}

/** "1 Nov" style date for a reset instant, or the raw value if it can't be parsed. */
fun resetDate(instant: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(instant))
}.getOrDefault(instant)
