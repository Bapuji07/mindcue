package com.secondmemory.android.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmemory.android.BuildConfig
import com.secondmemory.android.R
import com.secondmemory.android.data.AccountDeletionState
import com.secondmemory.android.data.ThemeMode
import com.secondmemory.android.data.UsageInfo
import com.secondmemory.android.ui.components.InlineMessage
import com.secondmemory.android.ui.components.SurfaceCard
import com.secondmemory.android.ui.resetDate
import com.secondmemory.android.ui.theme.BrandGradient
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.MindCueTheme
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary
import com.secondmemory.android.ui.theme.PrimarySoft
import com.secondmemory.android.ui.theme.ScreenBackground

@Composable
internal fun SettingsScreen(
    modifier: Modifier,
    username: String?,
    usage: UsageInfo?,
    themeMode: ThemeMode,
    accountDeletion: AccountDeletionState,
    hasPendingRecording: () -> Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearDeletionError: () -> Unit
) {
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (showPrivacy) PrivacyDialog(onDismiss = { showPrivacy = false })
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_title)) },
            text = {
                Text(stringResource(if (hasPendingRecording()) R.string.logout_pending_text else R.string.logout_text))
            },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text(stringResource(R.string.action_log_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (confirmDelete) {
        DeleteAccountDialog(
            state = accountDeletion,
            onDismiss = { confirmDelete = false; onClearDeletionError() },
            onDelete = onDeleteAccount,
            onPasswordChange = { if (accountDeletion.error != null) onClearDeletionError() }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            ProfileCard(username)
        }
        usage?.let { info -> item { UsageCard(info) } }
        item {
            SettingsSection(stringResource(R.string.appearance)) {
                Box(Modifier.padding(16.dp)) { ThemeSelector(themeMode, onThemeChange) }
            }
        }
        item {
            SettingsSection(stringResource(R.string.about)) {
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip, title = stringResource(R.string.privacy_title),
                    summary = stringResource(R.string.privacy_summary), onClick = { showPrivacy = true }
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Info, title = stringResource(R.string.version_label),
                    summary = stringResource(R.string.version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.account)) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout, title = stringResource(R.string.action_log_out),
                    onClick = { confirmLogout = true }
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever, title = stringResource(R.string.delete_account),
                    summary = stringResource(R.string.delete_account_summary), destructive = true,
                    onClick = { confirmDelete = true }
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ProfileCard(username: String?) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(BrandGradient, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    username?.take(1)?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.signed_in_as), color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(
                    username ?: stringResource(R.string.unknown_user), fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/** A titled group of settings rows on one card. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title, color = Primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() }
        )
        // Rows bring their own padding (ListItem), so the card adds only a little at the top and bottom.
        SurfaceCard(contentPadding = PaddingValues(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val color = if (destructive) ErrorColor else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = summary?.let { { Text(it, color = Muted) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (destructive) ErrorColor else Muted) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    val points = listOf(
        R.string.privacy_point_upload, R.string.privacy_point_transcribe, R.string.privacy_point_storage,
        R.string.privacy_point_ask, R.string.privacy_point_delete
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
        title = { Text(stringResource(R.string.privacy_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                points.forEach { point ->
                    Row {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).background(Primary, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(point))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun DeleteAccountDialog(
    state: AccountDeletionState,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onPasswordChange: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.deleting) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = ErrorColor) },
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_account_text))
                OutlinedTextField(
                    value = password, onValueChange = { password = it; onPasswordChange() },
                    label = { Text(stringResource(R.string.delete_account_password)) },
                    singleLine = true, enabled = !state.deleting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password }
                )
                state.error?.let { InlineMessage(it) }
                if (state.deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(password) }, enabled = password.isNotBlank() && !state.deleting) {
                if (state.deleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.delete_account), color = ErrorColor)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.deleting) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
internal fun UsageCard(usage: UsageInfo) {
    SurfaceCard {
        Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (usage.unlimited) {
            Text(stringResource(R.string.usage_unlimited), color = Primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(pluralStringResource(R.plurals.usage_unlimited_minutes, usage.audioMinutesUsed, usage.audioMinutesUsed), color = Muted)
            Text(pluralStringResource(R.plurals.usage_unlimited_questions, usage.aiRequestsToday, usage.aiRequestsToday), color = Muted)
            return@SurfaceCard
        }
        Spacer(Modifier.height(10.dp))
        UsageMeter(
            stringResource(R.string.usage_recording_this_month),
            stringResource(R.string.usage_meter_minutes, usage.audioMinutesUsed.coerceAtMost(usage.audioMinutesLimit), usage.audioMinutesLimit),
            fraction(usage.audioMinutesUsed, usage.audioMinutesLimit),
            stringResource(R.string.usage_resets_on, resetDate(usage.audioResetsAt))
        )
        Spacer(Modifier.height(14.dp))
        UsageMeter(
            stringResource(R.string.usage_ask_today),
            pluralStringResource(
                R.plurals.usage_meter_questions, usage.aiRequestsLimit,
                usage.aiRequestsToday.coerceAtMost(usage.aiRequestsLimit), usage.aiRequestsLimit
            ),
            fraction(usage.aiRequestsToday, usage.aiRequestsLimit),
            stringResource(R.string.usage_resets_daily)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            pluralStringResource(R.plurals.usage_max_recording, usage.maxRecordingMinutes, usage.maxRecordingMinutes), color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun fraction(used: Int, limit: Int): Float = if (limit <= 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)

@Composable
private fun UsageMeter(label: String, amount: String, fraction: Float, footnote: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(amount, color = if (fraction >= 1f) ErrorColor else Muted)
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp).clip(RoundedCornerShape(4.dp)),
        color = if (fraction >= 1f) ErrorColor else Primary,
        trackColor = PrimarySoft
    )
    Spacer(Modifier.height(4.dp))
    Text(footnote, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    MindCueTheme(darkTheme = isSystemInDarkTheme()) {
        Box(Modifier.background(ScreenBackground)) {
            SettingsScreen(
                Modifier, username = "priya",
                usage = UsageInfo(
                    unlimited = false, audioMinutesUsed = 37, audioMinutesLimit = 120,
                    audioResetsAt = "2026-11-01T00:00:00Z", aiRequestsToday = 4, aiRequestsLimit = 50,
                    aiResetsAt = "2026-10-08T00:00:00Z", maxRecordingMinutes = 60
                ),
                themeMode = ThemeMode.SYSTEM, accountDeletion = AccountDeletionState(),
                hasPendingRecording = { false }, onThemeChange = {}, onLogout = {}, onDeleteAccount = {},
                onClearDeletionError = {}
            )
        }
    }
}
