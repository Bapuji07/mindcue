package com.secondmemory.android.state

import com.secondmemory.android.data.AskResult
import com.secondmemory.android.data.SessionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MemoryMode { INACTIVE, ACTIVE, PROCESSING, READY, ERROR }

data class MemoryUiState(
    val mode: MemoryMode = MemoryMode.INACTIVE,
    val elapsedSeconds: Long = 0,
    val status: String = "Memory is inactive",
    val result: SessionResult? = null,
    val error: String? = null,
    val lastAudioPath: String? = null,
    val askResult: AskResult? = null,
    val askError: String? = null,
    val asking: Boolean = false,
    val connectionMessage: String? = null
)

object MemoryState {
    private val mutable = MutableStateFlow(MemoryUiState())
    val state = mutable.asStateFlow()

    fun restore(result: SessionResult?, audioPath: String?) {
        if (mutable.value.mode == MemoryMode.INACTIVE && result != null) {
            mutable.value = MemoryUiState(
                mode = MemoryMode.READY,
                status = "Memory ready",
                result = result,
                lastAudioPath = audioPath
            )
        }
    }

    fun active(elapsed: Long = 0) = mutable.update {
        it.copy(mode = MemoryMode.ACTIVE, elapsedSeconds = elapsed, status = "Memory is active",
            error = null, askResult = null)
    }

    fun processing(status: String, audioPath: String) = mutable.update {
        it.copy(mode = MemoryMode.PROCESSING, status = status, lastAudioPath = audioPath, error = null)
    }

    fun ready(result: SessionResult, audioPath: String) = mutable.update {
        it.copy(mode = MemoryMode.READY, status = "Memory ready", result = result,
            lastAudioPath = audioPath, error = null)
    }

    fun failure(message: String, audioPath: String? = mutable.value.lastAudioPath) = mutable.update {
        it.copy(mode = MemoryMode.ERROR, status = "Memory needs attention", error = message,
            lastAudioPath = audioPath)
    }

    fun asking(value: Boolean) = mutable.update { it.copy(asking = value, askError = null) }
    fun answer(result: AskResult) = mutable.update { it.copy(asking = false, askResult = result, askError = null) }
    fun askFailure(message: String) = mutable.update { it.copy(asking = false, askError = message) }
    fun connection(message: String?) = mutable.update { it.copy(connectionMessage = message) }
}
