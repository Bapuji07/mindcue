package com.secondmemory.android.ui.components

import android.content.res.Configuration
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.ui.friendlyDue
import com.secondmemory.android.ui.isOverdue
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.MindCueTheme
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary
import com.secondmemory.android.ui.theme.PrimarySoft
import com.secondmemory.android.ui.theme.Warning
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a memory card can do; the ids are memory ids except [onOpenConversation] (a session id). */
internal class MemoryActions(
    val onDone: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onReschedule: (String, Long?) -> Unit,
    val onOpenConversation: (String) -> Unit,
    val onEdit: (String, String, String) -> Unit
)

private const val LOW_CONFIDENCE = 0.6
private const val MAX_TITLE_LENGTH = 500

@Composable
internal fun ActionableMemoryCard(memory: MemoryItem, actions: MemoryActions, showSource: Boolean) {
    val id = memory.id
    val open = memory.resolutionStatus == "OPEN"
    MemoryCard(
        memory,
        onDone = if (id != null && open) ({ actions.onDone(id) }) else null,
        onDelete = if (id != null) ({ actions.onDelete(id) }) else null,
        onReschedule = if (id != null && open) ({ days: Long? -> actions.onReschedule(id, days) }) else null,
        onOpenConversation = memory.sessionId?.takeIf { showSource }?.let { sessionId -> { actions.onOpenConversation(sessionId) } },
        onEdit = if (id != null) ({ title: String, content: String -> actions.onEdit(id, title, content) }) else null
    )
}

/**
 * A task card with swipe shortcuts: right marks it done, left moves it to tomorrow. The buttons on
 * the card do the same, so nothing depends on the gesture (screen readers use the buttons).
 */
@Composable
internal fun SwipeableTaskCard(memory: MemoryItem, actions: MemoryActions, modifier: Modifier = Modifier) {
    val id = memory.id
    if (id == null) {
        Box(modifier) { ActionableMemoryCard(memory, actions, showSource = true) }
        return
    }
    val haptics = LocalHapticFeedback.current
    // The swipe state can confirm the same gesture more than once; act only once per gesture.
    var lastActionAt by remember(id) { mutableLongStateOf(0L) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val now = SystemClock.uptimeMillis()
            if (value == SwipeToDismissBoxValue.Settled || now - lastActionAt < 800) {
                false
            } else {
                lastActionAt = now
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                if (value == SwipeToDismissBoxValue.StartToEnd) {
                    actions.onDone(id) // leaves the list at once; Undo brings it back
                    true
                } else {
                    actions.onReschedule(id, 1) // stays in the list with tomorrow's date
                    false
                }
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection) }
    ) {
        ActionableMemoryCard(memory, actions, showSource = true)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val done = direction == SwipeToDismissBoxValue.StartToEnd
    val container = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (done) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(container).padding(horizontal = 24.dp),
        contentAlignment = if (done) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Snooze, contentDescription = null, tint = content)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (done) R.string.swipe_done else R.string.swipe_tomorrow),
                color = content, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryCard(
    memory: MemoryItem,
    onDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReschedule: ((Long?) -> Unit)? = null,
    onOpenConversation: (() -> Unit)? = null,
    onEdit: ((String, String) -> Unit)? = null
) {
    var dueMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val overdue = isOverdue(memory)
    val lowConfidence = (memory.confidence ?: 1.0) < LOW_CONFIDENCE

    if (editing && onEdit != null) {
        EditMemoryDialog(memory, onDismiss = { editing = false }, onSave = { title, content -> editing = false; onEdit(title, content) })
    }

    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(PrimarySoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(memoryTypeIcon(memory.type), contentDescription = null, tint = Primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(memoryTypeLabel(memory.type), color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            if (memory.resolutionStatus == "DONE") {
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.memory_done), color = Muted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            if (overdue) {
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.memory_overdue), color = ErrorColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            memory.owner?.let { OwnerChip(it, memory.ownerIsSelf) }
            if (onEdit != null || onOpenConversation != null || onDelete != null) {
                Box {
                    IconButton(onClick = { moreMenuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options), tint = Muted)
                    }
                    DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                        onEdit?.let {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = { moreMenuOpen = false; editing = true }
                            )
                        }
                        onOpenConversation?.let { open ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_view_conversation)) },
                                onClick = { moreMenuOpen = false; open() }
                            )
                        }
                        // No confirmation dialog: the snackbar offers Undo before the delete is sent.
                        onDelete?.let { delete ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = ErrorColor) },
                                onClick = { moreMenuOpen = false; delete() }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(memory.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(memory.content)
        if (memory.dueAt != null || lowConfidence) {
            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(9.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                memory.dueAt?.let {
                    Text(
                        stringResource(R.string.due_at, friendlyDue(it)), style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) ErrorColor else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (lowConfidence) {
                    Text(stringResource(R.string.low_confidence), style = MaterialTheme.typography.labelMedium, color = Warning)
                }
            }
        }
        if (onDone != null || onReschedule != null) {
            Spacer(Modifier.height(10.dp))
            // FlowRow wraps the buttons onto a second line on narrow screens instead of overflowing.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                onDone?.let {
                    OutlinedButton(onClick = it) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_mark_done))
                    }
                }
                onReschedule?.let { reschedule ->
                    Box {
                        TextButton(onClick = { dueMenuOpen = true }) {
                            Text(stringResource(if (memory.dueAt == null) R.string.action_set_due else R.string.action_reschedule))
                        }
                        DropdownMenu(expanded = dueMenuOpen, onDismissRequest = { dueMenuOpen = false }) {
                            listOf(R.string.due_tomorrow to 1L, R.string.due_in_3_days to 3L, R.string.due_in_a_week to 7L)
                                .forEach { (label, days) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = { dueMenuOpen = false; reschedule(days) }
                                    )
                                }
                            if (memory.dueAt != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.due_clear)) },
                                    onClick = { dueMenuOpen = false; reschedule(null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Who must act on it: "You" when it's the user, otherwise the speaker or name from the transcript. */
@Composable
private fun OwnerChip(owner: String, isSelf: Boolean) {
    val color = if (isSelf) Primary else Muted
    Row(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Person, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            if (isSelf) stringResource(R.string.owner_you) else owner,
            color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp)
        )
    }
}

@Composable
private fun EditMemoryDialog(memory: MemoryItem, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(memory.title) }
    var content by rememberSaveable { mutableStateOf(memory.content) }
    val changed = title.trim() != memory.title || content.trim() != memory.content
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_memory_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { if (it.length <= MAX_TITLE_LENGTH) title = it },
                    label = { Text(stringResource(R.string.field_title)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text(stringResource(R.string.field_details)) }, minLines = 3, maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), content.trim()) },
                enabled = changed && title.isNotBlank() && content.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@StringRes
private fun memoryTypeLabelRes(type: String): Int? = when (type.uppercase(Locale.ROOT)) {
    "FACT" -> R.string.memory_type_fact
    "DECISION" -> R.string.memory_type_decision
    "TASK" -> R.string.memory_type_task
    "PROMISE" -> R.string.memory_type_promise
    "PROBLEM" -> R.string.memory_type_problem
    "IDEA" -> R.string.memory_type_idea
    "QUESTION" -> R.string.memory_type_question
    "PREFERENCE" -> R.string.memory_type_preference
    "EVENT" -> R.string.memory_type_event
    "NOTE" -> R.string.memory_type_note
    else -> null
}

/** "TASK" -> "Task" (localized); unknown types are title-cased as they come. */
@Composable
private fun memoryTypeLabel(type: String): String =
    memoryTypeLabelRes(type)?.let { stringResource(it) }
        ?: type.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

private fun memoryTypeIcon(type: String): ImageVector = when (type.uppercase(Locale.ROOT)) {
    "TASK" -> Icons.Outlined.CheckCircle
    "PROMISE" -> Icons.Outlined.Handshake
    "IDEA" -> Icons.Outlined.Lightbulb
    "QUESTION" -> Icons.AutoMirrored.Outlined.HelpOutline
    "FACT" -> Icons.Outlined.Info
    "DECISION" -> Icons.AutoMirrored.Outlined.Rule
    "PROBLEM" -> Icons.Outlined.WarningAmber
    "EVENT" -> Icons.Outlined.Event
    "PREFERENCE" -> Icons.Outlined.Favorite
    else -> Icons.AutoMirrored.Outlined.Notes
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemoryCardPreview() {
    MindCueTheme(darkTheme = isSystemInDarkTheme()) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            MemoryCard(
                MemoryItem(
                    type = "PROMISE", title = "Send the invoice to Priya",
                    content = "Promised to email the September invoice before Friday's call.",
                    resolutionStatus = "OPEN", dueAt = Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                    importance = 0.8, confidence = 0.55, id = "1", sessionId = "s1", owner = "Speaker 2"
                ),
                onDone = {}, onDelete = {}, onReschedule = {}, onOpenConversation = {}, onEdit = { _, _ -> }
            )
        }
    }
}
