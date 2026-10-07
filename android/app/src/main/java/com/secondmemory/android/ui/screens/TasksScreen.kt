package com.secondmemory.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.ui.components.CenteredProgress
import com.secondmemory.android.ui.components.EmptyCard
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.components.MemoryActions
import com.secondmemory.android.ui.components.SelectableChip
import com.secondmemory.android.ui.components.SwipeableTaskCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksScreen(
    modifier: Modifier,
    memories: MemoriesUiState,
    actions: MemoryActions,
    onRefresh: () -> Unit,
    onOverdueChange: (Boolean) -> Unit
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
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(stringResource(R.string.filter_all_open), selected = !memories.overdueOnly) { onOverdueChange(false) }
                    SelectableChip(stringResource(R.string.filter_overdue), selected = memories.overdueOnly) { onOverdueChange(true) }
                }
            }
            memories.error?.let { item { InlineMessage(it) } }
            when {
                memories.commitmentsLoading && memories.commitments.isEmpty() -> item { CenteredProgress() }
                memories.commitments.isEmpty() -> item {
                    EmptyCard(
                        stringResource(if (memories.overdueOnly) R.string.tasks_empty_overdue else R.string.tasks_empty),
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
