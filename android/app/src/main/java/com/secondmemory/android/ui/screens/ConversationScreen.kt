package com.secondmemory.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.data.ConversationDetail
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.ui.canRetry
import com.secondmemory.android.ui.components.ActionableMemoryCard
import com.secondmemory.android.ui.components.EmptyCard
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.components.MemoryActions
import com.secondmemory.android.ui.components.SelectableChip
import com.secondmemory.android.ui.components.SectionTitle
import com.secondmemory.android.ui.components.StatusPill
import com.secondmemory.android.ui.components.SurfaceCard
import com.secondmemory.android.ui.friendlyDateTime
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary

/** One conversation: summary, its memories and the transcript. Rename/delete live in the top bar. */
@Composable
internal fun ConversationScreen(
    modifier: Modifier,
    history: HistoryUiState,
    memories: MemoriesUiState,
    actions: MemoryActions,
    onRetry: (String) -> Unit,
    onSelectSelfSpeaker: (String) -> Unit
) {
    val detail = history.selected ?: return
    var transcriptVisible by rememberSaveable(detail.session.id) { mutableStateOf(false) }
    var choosingSpeaker by rememberSaveable(detail.session.id) { mutableStateOf(false) }
    val selfSpeaker = detail.session.selfSpeaker
    val speakers = detail.speakers

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SurfaceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        detail.session.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(detail.session.status)
                }
                Spacer(Modifier.height(4.dp))
                Text(friendlyDateTime(detail.session.startedAt), color = Muted)
            }
        }
        history.error?.let { item { InlineMessage(it) } }
        if (history.mutating || memories.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (canRetry(detail.session)) {
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (detail.session.status.equals("FAILED", true)) R.string.processing_failed else R.string.processing_stuck
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    detail.session.errorMessage?.let { Spacer(Modifier.height(6.dp)); Text(it, color = Muted) }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onRetry(detail.session.id) }, enabled = !history.mutating,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.action_retry_processing)) }
                }
            }
        }
        // Who the user is decides which tasks are "yours"; only asked when there is a real choice.
        if (speakers.size >= 2) {
            item {
                if (selfSpeaker == null || choosingSpeaker) {
                    SpeakerChooser(speakers, selfSpeaker) { speaker ->
                        choosingSpeaker = false
                        if (speaker != selfSpeaker) onSelectSelfSpeaker(speaker)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.you_are_speaker, selfSpeaker), color = Muted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { choosingSpeaker = true }, enabled = !history.mutating) {
                            Text(stringResource(R.string.action_change))
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.summary))
            Spacer(Modifier.height(8.dp))
            SurfaceCard { Text(detail.session.summary ?: stringResource(R.string.summary_missing)) }
        }
        item { SectionTitle(stringResource(R.string.memories_section), detail.memories.size.toString()) }
        if (detail.memories.isEmpty()) {
            item { EmptyCard(stringResource(R.string.memories_empty_in_conversation), Icons.Outlined.AutoAwesome) }
        } else {
            items(detail.memories, key = { "memory-${it.id ?: "${it.type}-${it.title}"}" }) { memory ->
                ActionableMemoryCard(memory, actions, showSource = false)
            }
        }
        item {
            OutlinedButton(onClick = { transcriptVisible = !transcriptVisible }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (transcriptVisible) R.string.hide_transcript else R.string.show_transcript))
            }
        }
        if (transcriptVisible) {
            item { SurfaceCard { Transcript(detail, selfSpeaker) } }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeakerChooser(speakers: List<String>, selected: String?, onPick: (String) -> Unit) {
    SurfaceCard {
        Text(stringResource(R.string.which_speaker_title), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.which_speaker_text), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speakers.forEach { speaker -> SelectableChip(speaker, selected = speaker == selected) { onPick(speaker) } }
        }
    }
}

/** One paragraph per speaker turn, the speaker in bold ("You" for the user). */
@Composable
private fun Transcript(detail: ConversationDetail, selfSpeaker: String?) {
    if (detail.transcriptLines.none { it.speaker != null }) {
        Text(detail.transcript.ifBlank { stringResource(R.string.transcript_missing) })
        return
    }
    val you = stringResource(R.string.owner_you)
    val speakerColor = Primary
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        detail.transcriptLines.forEach { line ->
            Text(buildAnnotatedString {
                line.speaker?.let { speaker ->
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = speakerColor)) {
                        append(if (speaker == selfSpeaker) you else speaker)
                        append(": ")
                    }
                }
                append(line.text)
            })
        }
    }
}

@Composable
internal fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_title)) },
        text = {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.field_title)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim()) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun DeleteConversationDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_conversation_title)) },
        text = { Text(stringResource(R.string.dialog_delete_conversation_text)) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = ErrorColor) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
