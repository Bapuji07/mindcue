package com.secondmemory.android

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.secondmemory.android.data.AppSettings
import com.secondmemory.android.network.BackendClient
import com.secondmemory.android.recording.MemoryRecordingService
import com.secondmemory.android.state.MemoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.secondmemory.android.state.MemoryUiState
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<MemoryUiState> = MemoryState.state
    private val mutableBackendUrl = MutableStateFlow(AppSettings.backendUrl(application))
    val backendUrl: StateFlow<String> = mutableBackendUrl.asStateFlow()

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
}
