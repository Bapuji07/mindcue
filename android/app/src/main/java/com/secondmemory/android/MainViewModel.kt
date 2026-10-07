package com.secondmemory.android

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondmemory.android.data.AccountDeletionState
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.data.ThemeMode
import com.secondmemory.android.data.UsageInfo
import com.secondmemory.android.network.AuthException
import com.secondmemory.android.network.BackendClient
import com.secondmemory.android.network.ConflictException
import com.secondmemory.android.network.LoginResult
import com.secondmemory.android.recording.MemoryRecordingService
import com.secondmemory.android.state.MemoryMode
import com.secondmemory.android.state.MemoryState
import com.secondmemory.android.state.MemoryUiState
import com.secondmemory.android.ui.resetDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * A one-off message for the snackbar. [onUndo] adds an Undo action; [onTimeout] runs when the
 * message goes away without Undo (used to commit a delete only after the undo window).
 */
class UserMessage(val text: String, val onUndo: (() -> Unit)? = null, val onTimeout: (() -> Unit)? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<MemoryUiState> = MemoryState.state
    val level: StateFlow<Float> = MemoryState.level
    private val mutableHistory = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = mutableHistory.asStateFlow()
    private val mutableAuth = MutableStateFlow(AppSettings.isLoggedIn(application))
    val isLoggedIn: StateFlow<Boolean> = mutableAuth.asStateFlow()
    private val mutableLoginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = mutableLoginError.asStateFlow()
    private val mutableLoginNotice = MutableStateFlow<String?>(null)
    val loginNotice: StateFlow<String?> = mutableLoginNotice.asStateFlow()
    private val mutableLoggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = mutableLoggingIn.asStateFlow()
    private val mutableUsername = MutableStateFlow(AppSettings.username(application))
    val username: StateFlow<String?> = mutableUsername.asStateFlow()
    private val mutableThemeMode = MutableStateFlow(AppSettings.themeMode(application))
    val themeMode: StateFlow<ThemeMode> = mutableThemeMode.asStateFlow()
    private val mutableAccountDeletion = MutableStateFlow(AccountDeletionState())
    val accountDeletion: StateFlow<AccountDeletionState> = mutableAccountDeletion.asStateFlow()

    private val mutableUsage = MutableStateFlow<UsageInfo?>(null)
    val usage: StateFlow<UsageInfo?> = mutableUsage.asStateFlow()

    private val mutableMemories = MutableStateFlow(MemoriesUiState())
    val memories: StateFlow<MemoriesUiState> = mutableMemories.asStateFlow()
    private val pollJobs = ConcurrentHashMap<String, Job>()
    private var searchJob: Job? = null

    private val mutableMessages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = mutableMessages.asSharedFlow()

    init {
        // A recording whose processing never finished (process killed, offline, backend error) stays
        // retryable across app restarts as long as its audio file still exists.
        val pending = AppSettings.pendingAudio(application)?.takeIf { File(it).exists() }
        MemoryState.restore(AppSettings.lastResult(application), pending, str(R.string.pending_recording_message))
        if (mutableAuth.value) refreshUsage()
        // A finished recording has used minutes, so refresh what's left.
        viewModelScope.launch {
            MemoryState.state.map { it.mode }.distinctUntilChanged().collect { mode ->
                if (mode == MemoryMode.READY && mutableAuth.value) refreshUsage()
            }
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

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

    fun setThemeMode(mode: ThemeMode) {
        AppSettings.saveThemeMode(getApplication(), mode)
        mutableThemeMode.value = mode
    }

    // ---- Accounts ----------------------------------------------------------------------------

    /**
     * The session expired. The person will most likely sign back in, so their pending recording and
     * last result stay on the phone; [authenticate] wipes them if a different account signs in.
     */
    private fun handleAuthFailure() {
        // Late failures from work started before signing out (e.g. a delayed delete) are not news.
        if (!mutableAuth.value) return
        AppSettings.clearSession(getApplication())
        resetSignedInState()
        mutableLoginError.value = str(R.string.error_session_expired)
        mutableAuth.value = false
    }

    /** Clears what this view model holds for the signed-in person (lists, usage, search, polling). */
    private fun resetSignedInState() {
        pollJobs.values.forEach { it.cancel() }
        pollJobs.clear()
        searchJob?.cancel()
        mutableHistory.value = HistoryUiState()
        mutableMemories.value = MemoriesUiState()
        mutableUsage.value = null
        mutableUsername.value = null
        mutableAccountDeletion.value = AccountDeletionState()
    }

    /** Signs out and removes everything this person left on the phone, recordings included. */
    private fun signOutAndWipe(notice: String? = null) {
        val app = getApplication<Application>()
        AppSettings.clearUserData(app)
        resetSignedInState()
        MemoryState.reset()
        viewModelScope.launch(Dispatchers.IO) { deleteLocalRecordings(app) }
        mutableLoginError.value = null
        mutableLoginNotice.value = notice
        mutableAuth.value = false
    }

    private fun deleteLocalRecordings(context: Context) {
        File(context.filesDir, MemoryRecordingService.RECORDINGS_DIR).listFiles()?.forEach { runCatching { it.delete() } }
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
        mutableLoginNotice.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                call(username.trim(), password)
            }.onSuccess { result ->
                val app = getApplication<Application>()
                // Results and recordings left by a different account must not show up for this one.
                val owner = AppSettings.dataOwner(app)
                if (owner != null && owner != result.userId) {
                    AppSettings.clearUserData(app)
                    MemoryState.reset()
                    deleteLocalRecordings(app)
                }
                AppSettings.saveSession(app, result.token, result.userId, result.username)
                AppSettings.saveDataOwner(app, result.userId)
                mutableUsername.value = result.username
                // Signed-in first: the login screen leaves while still "submitting", which is its cue
                // to let the password manager save the credentials.
                mutableAuth.value = true
                mutableLoggingIn.value = false
                refreshUsage()
            }.onFailure { error ->
                mutableLoggingIn.value = false
                mutableLoginError.value = error.message ?: str(R.string.error_sign_in)
            }
        }
    }

    /** True when an unprocessed recording would be lost by signing out. */
    fun hasPendingRecording(): Boolean =
        AppSettings.pendingAudio(getApplication())?.let { File(it).exists() } == true

    fun logout() = signOutAndWipe()

    fun deleteAccount(password: String) {
        if (password.isBlank() || mutableAccountDeletion.value.deleting) return
        mutableAccountDeletion.value = AccountDeletionState(deleting = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().deleteAccount(password) }
                .onSuccess { signOutAndWipe(notice = str(R.string.account_deleted_notice)) }
                .onFailure { error ->
                    if (error is AuthException) {
                        handleAuthFailure()
                    } else {
                        mutableAccountDeletion.value = AccountDeletionState(error = error.message ?: str(R.string.error_delete_account))
                    }
                }
        }
    }

    fun clearAccountDeletionError() = mutableAccountDeletion.update { it.copy(error = null) }

    // ---- Recording ---------------------------------------------------------------------------

    fun activate(context: Context) {
        val usage = mutableUsage.value
        if (usage != null && usage.recordableMinutes <= 0) {
            MemoryState.failure(str(R.string.recording_minutes_used_up, resetDate(usage.audioResetsAt)))
            return
        }
        // Stop automatically before the recording outgrows what the account can still process.
        val maxSeconds = (usage?.recordableMinutes ?: DEFAULT_MAX_RECORDING_MINUTES) * 60L
        try {
            MemoryRecordingService.activate(context, maxSeconds)
        } catch (ex: Exception) { MemoryState.failure(ex.message ?: str(R.string.error_activate)) }
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

    // ---- Ask and search ----------------------------------------------------------------------

    fun ask(question: String) {
        if (question.isBlank()) return
        MemoryState.asking(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = client().ask(question.trim())
                MemoryState.answer(result)
            } catch (ex: AuthException) {
                MemoryState.askFailure(str(R.string.error_session_expired))
                handleAuthFailure()
            } catch (ex: Exception) {
                MemoryState.askFailure(ex.message ?: str(R.string.error_ask))
            }
            refreshUsage()
        }
    }

    /** Keyword search as the user types (debounced); an answer from a previous question is cleared. */
    fun onSearchQueryChange(query: String) {
        // The answer belongs to the text it was asked with; re-running the same search keeps it.
        if (query != mutableMemories.value.searchQuery) MemoryState.clearAnswer()
        mutableMemories.update { it.copy(searchQuery = query, error = null) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            mutableMemories.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350)
            mutableMemories.update { it.copy(searching = true) }
            runCatching { client().searchMemories(trimmed) }
                .onSuccess { results ->
                    mutableMemories.update {
                        if (it.searchQuery.trim() == trimmed) it.copy(searching = false, searchResults = results) else it
                    }
                }
                .onFailure { error -> memoriesFailure(error, R.string.error_search) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        MemoryState.clearAnswer()
        mutableMemories.update { it.copy(searchQuery = "", searchResults = null, searching = false) }
    }

    // ---- Conversations -----------------------------------------------------------------------

    fun loadHistory() {
        mutableHistory.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                client().listSessions()
            }.onSuccess { sessions ->
                mutableHistory.update { it.copy(loading = false, sessions = sessions, error = null) }
            }.onFailure { error ->
                if (error is AuthException) return@onFailure handleAuthFailure()
                mutableHistory.update { it.copy(loading = false, error = error.message ?: str(R.string.error_load_conversations)) }
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
                if (error is AuthException) return@onFailure handleAuthFailure()
                mutableHistory.update { it.copy(loading = false, error = error.message ?: str(R.string.error_open_conversation)) }
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
                    if (error is AuthException) return@onFailure handleAuthFailure()
                    mutableHistory.update {
                        it.copy(mutating = false, error = error.message ?: str(R.string.error_retry_processing))
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
                if (error is AuthException) return@onFailure handleAuthFailure()
                mutableHistory.update {
                    it.copy(mutating = false, error = error.message ?: str(R.string.error_update_conversation))
                }
            }
        }
    }

    // ---- Tasks and memories ------------------------------------------------------------------

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
                .onFailure { error -> memoriesFailure(error, R.string.error_load_tasks) }
        }
    }

    fun setOverdueOnly(value: Boolean) {
        mutableMemories.update { it.copy(overdueOnly = value) }
        loadCommitments()
    }

    /** Marks the memory done at once (it leaves Tasks immediately); the server change follows. */
    fun completeMemory(memoryId: String) {
        findMemory(memoryId)?.let { applyMemoryChange(memoryId, it.copy(resolutionStatus = "DONE")) }
        memoryOperation(
            memoryId, onSuccess = { notify(UserMessage(str(R.string.marked_done), onUndo = { reopenMemory(memoryId) })) }
        ) { updateMemory(memoryId, status = "DONE") }
    }

    private fun reopenMemory(memoryId: String) =
        memoryOperation(memoryId, reloadCommitments = true) { updateMemory(memoryId, status = "OPEN") }

    /**
     * Hides the memory at once and deletes it on the server only after the Undo window passes,
     * so an accidental delete can be taken back.
     */
    fun removeMemory(memoryId: String) {
        applyMemoryChange(memoryId, null)
        notify(UserMessage(str(R.string.memory_deleted), onUndo = ::reloadMemoryLists, onTimeout = { commitDelete(memoryId) }))
    }

    private fun commitDelete(memoryId: String) {
        // Signed out during the undo window: the token is gone, so leave the memory as it is.
        if (!mutableAuth.value) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().deleteMemory(memoryId) }.onFailure { error ->
                if (error is AuthException) return@onFailure handleAuthFailure()
                notify(UserMessage(error.message ?: str(R.string.error_delete_memory)))
                reloadMemoryLists()
            }
        }
    }

    /** Moves the due date [days] from now, or clears it when [days] is null; Undo restores the old date. */
    fun rescheduleMemory(memoryId: String, days: Long?) {
        val previousDue = findMemory(memoryId)?.dueAt
        val message = when (days) {
            null -> R.string.due_date_cleared
            1L -> R.string.moved_to_tomorrow
            else -> R.string.rescheduled
        }
        memoryOperation(
            memoryId, reloadCommitments = true,
            onSuccess = { notify(UserMessage(str(message), onUndo = { restoreDueDate(memoryId, previousDue) })) }
        ) {
            if (days == null) updateMemory(memoryId, clearDueAt = true)
            else updateMemory(memoryId, dueAt = Instant.now().plus(days, ChronoUnit.DAYS).toString())
        }
    }

    private fun restoreDueDate(memoryId: String, dueAt: String?) = memoryOperation(memoryId, reloadCommitments = true) {
        if (dueAt == null) updateMemory(memoryId, clearDueAt = true) else updateMemory(memoryId, dueAt = dueAt)
    }

    /** Re-reads every list a memory can appear in (after an undo or a failed change). */
    private fun reloadMemoryLists() {
        loadCommitments()
        val query = mutableMemories.value.searchQuery
        if (query.isNotBlank()) onSearchQueryChange(query)
        mutableHistory.value.selected?.let { openConversation(it.session.id) }
    }

    private fun findMemory(memoryId: String): MemoryItem? {
        val memories = mutableMemories.value
        return memories.commitments.firstOrNull { it.id == memoryId }
            ?: memories.searchResults?.firstOrNull { it.id == memoryId }
            ?: mutableHistory.value.selected?.memories?.firstOrNull { it.id == memoryId }
    }

    private fun notify(message: UserMessage) {
        mutableMessages.tryEmit(message)
    }

    /**
     * Runs a memory change on the server and mirrors it into every list that shows the memory. On
     * failure the lists are re-read, which also undoes any optimistic change made beforehand.
     */
    private fun memoryOperation(
        memoryId: String,
        reloadCommitments: Boolean = false,
        onSuccess: () -> Unit = {},
        operation: BackendClient.() -> MemoryItem?
    ) {
        mutableMemories.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client().operation() }
                .onSuccess { updated ->
                    applyMemoryChange(memoryId, updated)
                    if (reloadCommitments) loadCommitments()
                    onSuccess()
                }
                .onFailure { error ->
                    if (error is AuthException) return@onFailure handleAuthFailure()
                    mutableMemories.update { it.copy(busy = false) }
                    notify(UserMessage(error.message ?: str(R.string.error_update_memory)))
                    reloadMemoryLists()
                }
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

    private fun memoriesFailure(error: Throwable, @StringRes fallback: Int) {
        if (error is AuthException) return handleAuthFailure()
        mutableMemories.update {
            it.copy(
                busy = false, commitmentsLoading = false, searching = false,
                error = error.message ?: str(fallback)
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_RECORDING_MINUTES = 60
    }
}
