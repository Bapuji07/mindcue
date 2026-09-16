package com.secondmemory.android

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.network.BackendClient
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
    private val mutableBackendUrl = MutableStateFlow(AppSettings.backendUrl(application))
    val backendUrl: StateFlow<String> = mutableBackendUrl.asStateFlow()
    private val mutableHistory = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = mutableHistory.asStateFlow()

    init { MemoryState.restore(AppSettings.lastResult(application), AppSettings.lastAudio(application)) }

    fun updateBackendUrl(value: String) { mutableBackendUrl.value = value }

    fun saveAndTestBackend() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = AppSettings.saveBackendUrl(getApplication(), mutableBackendUrl.value)
                MemoryState.connection(BackendClient(url).health())
            } catch (ex: Exception) {
                MemoryState.connection(ex.message ?: "Connection failed")
            }
        }
    }

    fun activate(context: Context) {
        try {
            AppSettings.saveBackendUrl(context, mutableBackendUrl.value)
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
                val settings = getApplication<Application>()
                val result = BackendClient(AppSettings.backendUrl(settings)).ask(AppSettings.userId(settings), question.trim())
                MemoryState.answer(result)
            } catch (ex: Exception) {
                MemoryState.askFailure(ex.message ?: "Could not ask Memory")
            }
        }
    }

    fun loadHistory() {
        mutableHistory.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                BackendClient(AppSettings.backendUrl(app)).listSessions(AppSettings.userId(app))
            }.onSuccess { sessions ->
                mutableHistory.update { it.copy(loading = false, sessions = sessions, error = null) }
            }.onFailure { error ->
                mutableHistory.update { it.copy(loading = false, error = error.message ?: "Could not load conversations") }
            }
        }
    }

    fun openConversation(sessionId: String) {
        mutableHistory.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                BackendClient(AppSettings.backendUrl(app)).sessionDetail(AppSettings.userId(app), sessionId)
            }.onSuccess { detail ->
                mutableHistory.update { it.copy(loading = false, selected = detail, error = null) }
            }.onFailure { error ->
                mutableHistory.update { it.copy(loading = false, error = error.message ?: "Could not open conversation") }
            }
        }
    }

    fun closeConversation() = mutableHistory.update { it.copy(selected = null, error = null) }

    fun renameConversation(title: String) {
        val detail = mutableHistory.value.selected ?: return
        if (title.isBlank()) return
        mutateHistory {
            val app = getApplication<Application>()
            val updated = BackendClient(AppSettings.backendUrl(app))
                .renameSession(AppSettings.userId(app), detail.session.id, title.trim())
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
            val app = getApplication<Application>()
            BackendClient(AppSettings.backendUrl(app)).deleteSession(AppSettings.userId(app), detail.session.id)
            mutableHistory.update { state ->
                state.copy(selected = null, sessions = state.sessions.filterNot { it.id == detail.session.id })
            }
        }
    }

    fun completeMemory(memoryId: String) = updateMemory(memoryId, status = "DONE")
    fun dismissMemory(memoryId: String) = updateMemory(memoryId, active = false)

    private fun updateMemory(memoryId: String, status: String? = null, active: Boolean? = null) {
        mutateHistory {
            val app = getApplication<Application>()
            val updated = BackendClient(AppSettings.backendUrl(app))
                .updateMemory(AppSettings.userId(app), memoryId, status, active)
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
                mutableHistory.update {
                    it.copy(mutating = false, error = error.message ?: "Could not update conversation")
                }
            }
        }
    }
}
