package com.secondmemory.android.state

import com.secondmemory.android.data.AskResult
import com.secondmemory.android.data.SessionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MemoryMode { INACTIVE, ACTIVE, PROCESSING, READY, ERROR }

/**
 * Shared recording state. [status] is the progress line shown while PROCESSING; [error] is shown
 * on Home in ERROR mode. Both arrive already localized from the code that sets them.
 */
data class MemoryUiState(
    val mode: MemoryMode = MemoryMode.INACTIVE,
    val elapsedSeconds: Long = 0,
    val status: String = "",
    val result: SessionResult? = null,
    val error: String? = null,
    val lastAudioPath: String? = null,
    val askResult: AskResult? = null,
    val askError: String? = null,
    val asking: Boolean = false
)

object MemoryState {
    private val mutable = MutableStateFlow(MemoryUiState())
    val state = mutable.asStateFlow()

    // Microphone level 0..1 while recording, kept apart from [state] so the meter can update many
    // times a second without recomposing everything that observes the recording state.
    private val mutableLevel = MutableStateFlow(0f)
    val level: StateFlow<Float> = mutableLevel.asStateFlow()

    /**
     * Rebuilds UI state after the process was killed. An unfinished recording takes priority over
     * the last result so the user can retry it, but the last result is kept for display.
     */
    fun restore(result: SessionResult?, pendingPath: String?, pendingMessage: String) {
        if (mutable.value.mode != MemoryMode.INACTIVE) return
        when {
            pendingPath != null -> mutable.value = MemoryUiState(
                mode = MemoryMode.ERROR, result = result, error = pendingMessage, lastAudioPath = pendingPath
            )
            result != null -> mutable.value = MemoryUiState(mode = MemoryMode.READY, result = result)
        }
    }

    /** Forgets everything, e.g. when the person signs out. */
    fun reset() {
        mutable.value = MemoryUiState()
        mutableLevel.value = 0f
    }

    /** Clears a failed recording after the user discards it. */
    fun dismissError() = mutable.update {
        it.copy(mode = if (it.result != null) MemoryMode.READY else MemoryMode.INACTIVE, error = null, lastAudioPath = null)
    }

    fun active(elapsed: Long = 0) = mutable.update {
        it.copy(mode = MemoryMode.ACTIVE, elapsedSeconds = elapsed, error = null, askResult = null)
    }

    fun processing(status: String, audioPath: String) = mutable.update {
        it.copy(mode = MemoryMode.PROCESSING, status = status, lastAudioPath = audioPath, error = null)
    }

    fun ready(result: SessionResult) = mutable.update {
        it.copy(mode = MemoryMode.READY, result = result, lastAudioPath = null, error = null)
    }

    fun failure(message: String, audioPath: String? = mutable.value.lastAudioPath) = mutable.update {
        it.copy(mode = MemoryMode.ERROR, error = message, lastAudioPath = audioPath)
    }

    fun level(value: Float) {
        mutableLevel.value = value
    }

    /** Whether anything is drawing the level meter, so the recorder can poll less often when not. */
    fun levelObserved(): Boolean = mutableLevel.subscriptionCount.value > 0

    fun asking(value: Boolean) = mutable.update { it.copy(asking = value, askError = null) }
    fun answer(result: AskResult) = mutable.update { it.copy(asking = false, askResult = result, askError = null) }
    fun askFailure(message: String) = mutable.update { it.copy(asking = false, askError = message) }
    fun clearAnswer() = mutable.update { it.copy(askResult = null, askError = null) }
}
