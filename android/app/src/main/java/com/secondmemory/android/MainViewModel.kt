package com.secondmemory.android

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.network.AuthException
import com.secondmemory.android.network.BackendClient
import com.secondmemory.android.network.LoginResult
import com.secondmemory.android.recording.MemoryRecordingService
import com.secondmemory.android.state.MemoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.secondmemory.android.state.MemoryUiState
import kotlinx.coroutines.launch

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

    init { MemoryState.restore(AppSettings.lastResult(application), AppSettings.lastAudio(application)) }

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
            }.onFailure { error ->
                mutableLoggingIn.value = false
                mutableLoginError.value = error.message ?: "Could not sign in"
            }
        }
    }

    fun logout() {
        AppSettings.clearSession(getApplication())
        mutableAuth.value = false
        mutableUsername.value = null
    }

    fun activate(context: Context) {
        try {
            MemoryRecordingService.activate(context)
        } catch (ex: Exception) { MemoryState.failure(ex.message ?: "Could not activate Memory") }
    }

    fun deactivate(context: Context) = MemoryRecordingService.deactivate(context)
    fun retry(context: Context, path: String) = MemoryRecordingService.retry(context, path)

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

    fun completeMemory(memoryId: String) = updateMemory(memoryId, status = "DONE")
    fun dismissMemory(memoryId: String) = updateMemory(memoryId, active = false)

    private fun updateMemory(memoryId: String, status: String? = null, active: Boolean? = null) {
        mutateHistory {
            val updated = client().updateMemory(memoryId, status, active)
            mutableHistory.update { state ->
                val current = state.selected ?: return@update state
                val items = if (active == false) current.memories.filterNot { it.id == memoryId }
                else current.memories.map { if (it.id == memoryId) updated else it }
                state.copy(selected = current.copy(memories = items))
            }
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
}
