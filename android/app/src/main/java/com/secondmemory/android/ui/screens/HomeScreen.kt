package com.secondmemory.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.data.UsageInfo
import com.secondmemory.android.state.MemoryUiState
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.components.SurfaceCard
import com.secondmemory.android.ui.resetDate
import com.secondmemory.android.ui.theme.Accent
import com.secondmemory.android.ui.theme.BrandGradient
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.Evergreen
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary

@Composable
internal fun HomeScreen(
    modifier: Modifier,
    state: MemoryUiState,
    usage: UsageInfo?,
    permissionError: String?,
    onActivate: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onOpenLatest: () -> Unit
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.dialog_discard_title)) },
            text = { Text(stringResource(R.string.dialog_discard_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscard() }) {
                    Text(stringResource(R.string.action_discard), color = ErrorColor)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(Modifier.height(18.dp)) }
        item {
            Text(
                stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_subtitle), color = Muted, textAlign = TextAlign.Center)
        }
        item { ActivateButton(enabled = usage == null || usage.recordableMinutes > 0, onActivate = onActivate) }
        usage?.takeIf { !it.unlimited }?.let { info ->
            item {
                val left = info.audioMinutesLeft
                Text(
                    if (left > 0) pluralStringResource(R.plurals.minutes_left_this_month, left, left)
                    else stringResource(R.string.minutes_used_up, resetDate(info.audioResetsAt)),
                    color = if (left > 0) Muted else ErrorColor, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        permissionError?.let { item { InlineMessage(it) } }
        state.error?.let { message ->
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.recording_needs_attention), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = Muted)
                    if (state.lastAudioPath != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_retry_processing))
                        }
                        TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_discard_recording), color = ErrorColor)
                        }
                    }
                }
            }
        }
        state.result?.let { result ->
            item {
                SurfaceCard {
                    Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.latest_memory), color = Primary, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                result.summary.ifBlank { stringResource(R.string.latest_memory_fallback) },
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = onOpenLatest) { Text(stringResource(R.string.action_view)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** The big round record button: a real button for screen readers, faded when recording isn't possible. */
@Composable
private fun ActivateButton(enabled: Boolean, onActivate: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(min = 190.dp)
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (enabled) 14.dp else 0.dp, CircleShape, spotColor = Evergreen)
            .clip(CircleShape)
            .background(BrandGradient)
            .clickable(
                enabled = enabled, role = Role.Button,
                onClickLabel = stringResource(R.string.action_start_recording), onClick = onActivate
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.action_activate_memory), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White,
                modifier = Modifier.widthIn(max = 140.dp)
            )
        }
    }
}
