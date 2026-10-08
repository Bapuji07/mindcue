package com.secondmemory.android.data

data class MemoryItem(
    val type: String,
    val title: String,
    val content: String,
    val resolutionStatus: String?,
    val dueAt: String?,
    val importance: Double?,
    val confidence: Double?,
    val id: String? = null,
    val sessionId: String? = null,
    /** Who must act on it or made the promise ("Speaker 2", a name); null if unclear. */
    val owner: String? = null,
    /** True when [owner] is the person using the app. */
    val ownerIsSelf: Boolean = false
)

data class SessionResult(
    val sessionId: String,
    val transcript: String,
    val summary: String,
    val memories: List<MemoryItem>
)

data class AskSource(
    val title: String,
    val content: String,
    val evidence: List<String>
)

data class AskResult(val answer: String, val sources: List<AskSource>)

data class ConversationSession(
    val id: String,
    val title: String,
    val status: String,
    val startedAt: String,
    val durationSeconds: Int?,
    val summary: String?,
    val errorMessage: String? = null,
    val updatedAt: String? = null,
    /** Which transcript speaker is the person using the app, once known. */
    val selfSpeaker: String? = null
)

data class ConversationDetail(
    val session: ConversationSession,
    val memories: List<MemoryItem>,
    val transcript: String,
    val transcriptLines: List<TranscriptLine> = emptyList()
) {
    /** Distinct speaker labels in the order they first speak. */
    val speakers: List<String> get() = transcriptLines.mapNotNull { it.speaker }.distinct()
}

/** One speaker turn; [speaker] is null for transcripts recorded before speaker labels existed. */
data class TranscriptLine(val speaker: String?, val text: String)

/** The Tasks tab's filter. WAITING = open items someone else owns. */
enum class TaskFilter { ALL, OVERDUE, WAITING }

data class HistoryUiState(
    val loading: Boolean = false,
    val sessions: List<ConversationSession> = emptyList(),
    val selected: ConversationDetail? = null,
    val error: String? = null,
    val mutating: Boolean = false
)

/** Cross-conversation memory views: open commitments and text search. */
data class MemoriesUiState(
    val commitments: List<MemoryItem> = emptyList(),
    val commitmentsLoading: Boolean = false,
    val taskFilter: TaskFilter = TaskFilter.ALL,
    /** Open items due today or overdue, for the Home card (independent of the Tasks filter). */
    val dueSoon: List<MemoryItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MemoryItem>? = null,
    val searching: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

/** The account's metered usage, from GET /api/v1/usage. Reset times are ISO-8601 UTC instants. */
data class UsageInfo(
    val unlimited: Boolean,
    val audioMinutesUsed: Int,
    val audioMinutesLimit: Int,
    val audioResetsAt: String,
    val aiRequestsToday: Int,
    val aiRequestsLimit: Int,
    val aiResetsAt: String,
    val maxRecordingMinutes: Int
) {
    val audioMinutesLeft: Int get() = if (unlimited) Int.MAX_VALUE else (audioMinutesLimit - audioMinutesUsed).coerceAtLeast(0)

    /** Longest recording that can still be processed right now. */
    val recordableMinutes: Int get() = minOf(audioMinutesLeft, maxRecordingMinutes)
}

/** Reminder and morning-summary preferences (device settings, kept across sign-outs). */
data class NotificationSettings(
    val remindersEnabled: Boolean,
    val digestEnabled: Boolean,
    /** Morning summary time as minutes after midnight. */
    val digestMinuteOfDay: Int,
    /** The one-time "turn on notifications" card on Tasks was dismissed. */
    val cardDismissed: Boolean
)

/** The app's light/dark choice; SYSTEM follows the phone setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Progress of a delete-account request, shown in its confirmation dialog. */
data class AccountDeletionState(val deleting: Boolean = false, val error: String? = null)
