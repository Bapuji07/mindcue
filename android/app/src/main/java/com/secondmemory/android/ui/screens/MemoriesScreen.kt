package com.secondmemory.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.data.ConversationSession
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.state.MemoryUiState
import com.secondmemory.android.ui.canRetry
import com.secondmemory.android.ui.components.ActionableMemoryCard
import com.secondmemory.android.ui.components.CenteredProgress
import com.secondmemory.android.ui.components.EmptyCard
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.components.MemoryActions
import com.secondmemory.android.ui.components.SectionTitle
import com.secondmemory.android.ui.components.StatusPill
import com.secondmemory.android.ui.components.SurfaceCard
import com.secondmemory.android.ui.friendlyDateTime
import com.secondmemory.android.ui.theme.CardBorder
import com.secondmemory.android.ui.theme.CardSurface
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary
import com.secondmemory.android.ui.theme.PrimarySoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoriesScreen(
    modifier: Modifier,
    state: MemoryUiState,
    history: HistoryUiState,
    memories: MemoriesUiState,
    actions: MemoryActions,
    onAsk: (String) -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpen: (String) -> Unit,
    onRetrySession: (String) -> Unit,
    onSelectSelfSpeaker: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    if (history.selected != null) {
        ConversationScreen(modifier, history, memories, actions, onRetrySession, onSelectSelfSpeaker)
        return
    }
    val query = memories.searchQuery
    PullToRefreshBox(
        isRefreshing = history.loading && history.sessions.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)); SearchOrAskBar(query, onQueryChange, onClearQuery) }
            if (query.isNotBlank()) {
                item { AskCard(query, state, onAsk) }
                val results = memories.searchResults
                item { SectionTitle(stringResource(R.string.matching_memories), results?.size?.toString()) }
                memories.error?.let { item { InlineMessage(it) } }
                when {
                    results == null && (memories.searching || query.trim().length >= 2) -> item { CenteredProgress() }
                    results == null -> item { Text(stringResource(R.string.keep_typing), color = Muted) }
                    results.isEmpty() -> item {
                        EmptyCard(stringResource(R.string.no_matches, query.trim()), Icons.Outlined.Search)
                    }
                    else -> items(results, key = { "search-${it.id ?: it.hashCode()}" }) {
                        ActionableMemoryCard(it, actions, showSource = true)
                    }
                }
            } else {
                item { SectionTitle(stringResource(R.string.conversations), history.sessions.size.toString()) }
                history.error?.let { item { InlineMessage(it) } }
                when {
                    history.loading && history.sessions.isEmpty() -> item { CenteredProgress() }
                    history.sessions.isEmpty() -> item {
                        EmptyCard(stringResource(R.string.conversations_empty), Icons.Outlined.History)
                    }
                    else -> items(history.sessions, key = { "session-${it.id}" }) { session ->
                        val retry: (() -> Unit)? = if (canRetry(session)) ({ onRetrySession(session.id) }) else null
                        ConversationCard(session, retry) { onOpen(session.id) }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SearchOrAskBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query, onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardSurface, unfocusedContainerColor = CardSurface,
            unfocusedBorderColor = CardBorder
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Turns the current search text into an AI question; shows the answer once there is one. */
@Composable
private fun AskCard(query: String, state: MemoryUiState, onAsk: (String) -> Unit) {
    val question = query.trim()
    SurfaceCard {
        val result = state.askResult
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(PrimarySoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (result == null) R.string.ask_title else R.string.answer_title), fontWeight = FontWeight.Bold)
                if (result == null) {
                    Text(stringResource(R.string.ask_subtitle), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (result == null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onAsk(question) }, enabled = !state.asking && question.length >= 3,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (state.asking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_ask, question), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            state.askError?.let {
                Spacer(Modifier.height(10.dp)); Text(it, color = ErrorColor, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(result.answer)
            if (result.sources.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.sources), color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                result.sources.forEach { source ->
                    Spacer(Modifier.height(8.dp))
                    Text(source.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    source.evidence.firstOrNull()?.let {
                        Text(
                            "“$it”", style = MaterialTheme.typography.bodySmall, color = Muted,
                            maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(session: ConversationSession, onRetry: (() -> Unit)?, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    session.title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(session.status)
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(friendlyDateTime(session.startedAt), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            session.summary?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (session.status.equals("FAILED", ignoreCase = true)) {
                session.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ErrorColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            onRetry?.let {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = it, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_retry_processing))
                }
            }
        }
    }
}
