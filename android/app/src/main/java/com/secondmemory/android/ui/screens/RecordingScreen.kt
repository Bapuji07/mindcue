package com.secondmemory.android.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.state.MemoryMode
import com.secondmemory.android.state.MemoryUiState
import com.secondmemory.android.ui.formatDuration
import com.secondmemory.android.ui.theme.Accent
import com.secondmemory.android.ui.theme.BrandGradient
import com.secondmemory.android.ui.theme.MindCueTheme
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary
import com.secondmemory.android.ui.theme.ScreenBackground

/**
 * Full-screen state while Memory records or processes. [level] is read lazily so only the level
 * meter recomposes as the microphone level changes.
 */
@Composable
internal fun RecordingScreen(modifier: Modifier, state: MemoryUiState, level: () -> Float, onStop: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.mode == MemoryMode.ACTIVE) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val ringScale by pulse.animateFloat(
                initialValue = 1f, targetValue = 1.35f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                label = "ringScale"
            )
            val ringAlpha by pulse.animateFloat(
                initialValue = 0.35f, targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                label = "ringAlpha"
            )
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).scale(ringScale).background(Accent.copy(alpha = ringAlpha), CircleShape))
                Box(Modifier.size(120.dp).background(BrandGradient, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.recording_active_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                formatDuration(state.elapsedSeconds), style = MaterialTheme.typography.displaySmall,
                color = Primary, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            VoiceLevelBars(level)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.recording_lock_hint), color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.ToggleOff); onStop() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(stringResource(R.string.action_stop_and_save), fontWeight = FontWeight.SemiBold) }
        } else {
            CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 4.dp, color = Primary)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.processing_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(state.status, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.processing_leave_hint), color = Muted, textAlign = TextAlign.Center)
        }
    }
}

private val BarWeights = listOf(0.45f, 0.7f, 0.95f, 0.8f, 1f, 0.75f, 0.5f)

/** Bars that move with the microphone level, so it's obvious the app can hear you. Decorative for screen readers. */
@Composable
private fun VoiceLevelBars(level: () -> Float) {
    val animated by animateFloatAsState(
        targetValue = level(), animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "level"
    )
    Row(
        Modifier.height(40.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarWeights.forEach { weight ->
            Box(
                Modifier.width(6.dp).fillMaxHeight((0.12f + animated * weight).coerceIn(0.12f, 1f))
                    .background(Primary, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecordingScreenPreview() {
    MindCueTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.background(ScreenBackground)) {
            RecordingScreen(Modifier, MemoryUiState(mode = MemoryMode.ACTIVE, elapsedSeconds = 754), level = { 0.6f }, onStop = {})
        }
    }
}
