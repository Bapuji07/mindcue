package com.secondmemory.android.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.data.TaskFilter
import com.secondmemory.android.ui.components.CenteredProgress
import com.secondmemory.android.ui.components.EmptyCard
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.components.MemoryActions
import com.secondmemory.android.ui.components.SelectableChip
import com.secondmemory.android.ui.components.SurfaceCard
import com.secondmemory.android.ui.components.SwipeableTaskCard
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksScreen(
    modifier: Modifier,
    memories: MemoriesUiState,
    actions: MemoryActions,
    showNotificationCard: Boolean,
    onRefresh: () -> Unit,
    onFilterChange: (TaskFilter) -> Unit,
    onEnableNotifications: () -> Unit,
    onDismissNotificationCard: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    PullToRefreshBox(
        isRefreshing = memories.commitmentsLoading && memories.commitments.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TaskFilter.ALL to R.string.filter_all_open,
                        TaskFilter.OVERDUE to R.string.filter_overdue,
                        TaskFilter.WAITING to R.string.filter_waiting
                    ).forEach { (filter, label) ->
                        SelectableChip(stringResource(label), selected = memories.taskFilter == filter) { onFilterChange(filter) }
                    }
                }
            }
            if (showNotificationCard) {
                item { NotificationCard(onEnableNotifications, onDismissNotificationCard) }
            }
            memories.error?.let { item { InlineMessage(it) } }
            when {
                memories.commitmentsLoading && memories.commitments.isEmpty() -> item { CenteredProgress() }
                memories.commitments.isEmpty() -> item {
                    EmptyCard(
                        stringResource(
                            when (memories.taskFilter) {
                                TaskFilter.ALL -> R.string.tasks_empty
                                TaskFilter.OVERDUE -> R.string.tasks_empty_overdue
                                TaskFilter.WAITING -> R.string.tasks_empty_waiting
                            }
                        ),
                        Icons.Outlined.CheckCircle
                    )
                }
                else -> items(memories.commitments, key = { "open-${it.id ?: it.hashCode()}" }) { memory ->
                    SwipeableTaskCard(memory, actions, Modifier.animateItem())
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/** Shown once, until turned on or dismissed: reminders need the notification permission. */
@Composable
private fun NotificationCard(onEnable: () -> Unit, onDismiss: () -> Unit) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notification_card_title), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.notification_card_text), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onEnable) { Text(stringResource(R.string.action_turn_on)) }
        }
    }
}
