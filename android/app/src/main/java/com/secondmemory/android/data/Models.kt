package com.secondmemory.android.data

data class MemoryItem(
    val type: String,
    val title: String,
    val content: String,
    val resolutionStatus: String?,
    val dueAt: String?,
    val importance: Double?,
    val confidence: Double?,
    val id: String? = null
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
    val updatedAt: String? = null
)

data class ConversationDetail(
    val session: ConversationSession,
    val memories: List<MemoryItem>,
    val transcript: String
)

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
    val overdueOnly: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MemoryItem>? = null,
    val searching: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)
