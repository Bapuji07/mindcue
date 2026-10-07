package com.secondmemory.android.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmemory.android.R
import com.secondmemory.android.ui.theme.CardBorder
import com.secondmemory.android.ui.theme.CardSurface
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.MindCueTheme
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary
import com.secondmemory.android.ui.theme.PrimarySoft
import com.secondmemory.android.ui.theme.Warning
import java.util.Locale

@Composable
internal fun SurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

@Composable
internal fun SectionTitle(title: String, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        detail?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Primary) }
    }
}

@Composable
internal fun EmptyCard(message: String, icon: ImageVector = Icons.Outlined.History) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).background(PrimarySoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

/** A tinted inline message: errors by default, or information with [isError] false. */
@Composable
internal fun InlineMessage(message: String, isError: Boolean = true) {
    val color = if (isError) ErrorColor else Primary
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isError) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null, tint = color, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(message, color = color)
        }
    }
}

@Composable
internal fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

/** Server statuses reduced to what a person cares about. */
@Composable
internal fun StatusPill(status: String) {
    val (label, color) = when (status.uppercase(Locale.ROOT)) {
        "COMPLETED" -> stringResource(R.string.status_ready) to Primary
        "FAILED" -> stringResource(R.string.status_failed) to ErrorColor
        "RECORDING" -> stringResource(R.string.status_recording) to Warning
        else -> stringResource(R.string.status_processing) to Warning
    }
    StatusLabel(label, color)
}

@Composable
private fun StatusLabel(label: String, color: Color) {
    Box(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComponentsPreview() {
    MindCueTheme(darkTheme = isSystemInDarkTheme()) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            SectionTitle("Conversations", "3")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("COMPLETED"); StatusPill("PROCESSING"); StatusPill("FAILED")
            }
            Spacer(Modifier.height(8.dp))
            InlineMessage("Could not load conversations")
            Spacer(Modifier.height(8.dp))
            EmptyCard("Your conversations appear here after you record one on Home.")
        }
    }
}
