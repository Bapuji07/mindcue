package com.secondmemory.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmemory.android.MainViewModel
import com.secondmemory.android.R
import com.secondmemory.android.reminders.Notifications
import com.secondmemory.android.state.MemoryMode
import com.secondmemory.android.ui.components.MemoryActions
import com.secondmemory.android.ui.screens.DeleteConversationDialog
import com.secondmemory.android.ui.screens.HomeScreen
import com.secondmemory.android.ui.screens.LoginScreen
import com.secondmemory.android.ui.screens.MemoriesScreen
import com.secondmemory.android.ui.screens.RecordingScreen
import com.secondmemory.android.ui.screens.RenameDialog
import com.secondmemory.android.ui.screens.SettingsScreen
import com.secondmemory.android.ui.screens.TasksScreen
import com.secondmemory.android.ui.theme.CardSurface
import com.secondmemory.android.ui.theme.ErrorColor
import com.secondmemory.android.ui.theme.Muted
import com.secondmemory.android.ui.theme.Primary
import com.secondmemory.android.ui.theme.PrimarySoft
import com.secondmemory.android.ui.theme.ScreenBackground
import kotlinx.coroutines.launch

internal enum class AppPage(@param:StringRes val label: Int, val outlineIcon: ImageVector, val filledIcon: ImageVector) {
    HOME(R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TASKS(R.string.nav_tasks, Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
    MEMORIES(R.string.nav_memories, Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

/** Which microphone-permission explanation is showing, if any. */
private enum class MicDialog { RATIONALE, OPEN_SETTINGS }

/** Content stays readable on tablets and foldables instead of stretching edge to edge. */
private val MaxContentWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindCueApp(vm: MainViewModel) {
    val isLoggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    if (!isLoggedIn) {
        val loginError by vm.loginError.collectAsStateWithLifecycle()
        val loginNotice by vm.loginNotice.collectAsStateWithLifecycle()
        val loggingIn by vm.loggingIn.collectAsStateWithLifecycle()
        LoginScreen(loggingIn, loginError, loginNotice, vm::login, vm::register)
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val accountDeletion by vm.accountDeletion.collectAsStateWithLifecycle()
    val notificationSettings by vm.notificationSettings.collectAsStateWithLifecycle()
    val requestedPage by vm.requestedPage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptics = LocalHapticFeedback.current
    var pageName by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var micDialog by rememberSaveable { mutableStateOf<MicDialog?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    val page = runCatching { AppPage.valueOf(pageName) }.getOrDefault(AppPage.HOME)
    LaunchedEffect(page) {
        if (page == AppPage.HOME || page == AppPage.SETTINGS) vm.refreshUsage()
        if (page == AppPage.HOME) vm.loadDueSoon()
    }
    // A notification asked for a tab (e.g. a reminder opening Tasks).
    LaunchedEffect(requestedPage) {
        requestedPage?.let { name ->
            AppPage.entries.firstOrNull { it.name == name }?.let { pageName = it.name }
            vm.consumePageRequest()
        }
    }

    // Notifications can be switched off outside the app, so re-check whenever it comes back.
    var notificationsAllowed by remember { mutableStateOf(Notifications.allowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsAllowed = Notifications.allowed(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = Notifications.allowed(context)
        when {
            granted -> vm.onNotificationsAllowed()
            // Android no longer shows the prompt: the switch is in the app's system settings.
            activity != null && Build.VERSION.SDK_INT >= 33 &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) ->
                openNotificationSettings(context)
        }
    }
    val enableNotifications = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context) // allowed, but switched off in system settings
        }
    }

    val openConversation: (String) -> Unit = { sessionId ->
        vm.openConversation(sessionId)
        pageName = AppPage.MEMORIES.name
    }
    val memoryActions = remember(vm) {
        MemoryActions(vm::completeMemory, vm::removeMemory, vm::rescheduleMemory, openConversation, vm::editMemory)
    }

    // Snackbar messages from the view model. A message's onTimeout also runs if the screen goes
    // away mid-message, so a pending delete is never silently dropped.
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            launch {
                var undone = false
                try {
                    undone = snackbar.showSnackbar(
                        message.text,
                        actionLabel = if (message.onUndo != null) undoLabel else null,
                        duration = SnackbarDuration.Short
                    ) == SnackbarResult.ActionPerformed
                } finally {
                    if (undone) message.onUndo?.invoke() else message.onTimeout?.invoke()
                }
            }
        }
    }

    // Microphone permission: ask in context; explain after a first refusal; once Android stops
    // showing the prompt, the only way back is the app's system settings.
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val micNeeded = stringResource(R.string.permission_mic_needed)
    val startRecording = {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        vm.activate(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        when {
            result[Manifest.permission.RECORD_AUDIO] == true -> { permissionError = null; startRecording() }
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) ->
                micDialog = MicDialog.OPEN_SETTINGS
            else -> permissionError = micNeeded
        }
    }
    val activate = {
        pageName = AppPage.HOME.name
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                startRecording()
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) ->
                micDialog = MicDialog.RATIONALE
            else -> permissionLauncher.launch(permissions)
        }
    }
    micDialog?.let { dialog ->
        MicPermissionDialog(
            dialog,
            onDismiss = { micDialog = null },
            onConfirm = {
                micDialog = null
                if (dialog == MicDialog.RATIONALE) {
                    permissionLauncher.launch(permissions)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            }
        )
    }

    val focusMode = state.mode == MemoryMode.ACTIVE || state.mode == MemoryMode.PROCESSING
    val detail = history.selected
    val inDetail = page == AppPage.MEMORIES && detail != null

    // System back: conversation detail -> list -> Home tab, then leave the app.
    BackHandler(enabled = !focusMode && (inDetail || page != AppPage.HOME)) {
        if (inDetail) vm.closeConversation() else pageName = AppPage.HOME.name
    }

    if (detail != null && renaming) {
        RenameDialog(
            initial = detail.session.title,
            onDismiss = { renaming = false },
            onSave = { vm.renameConversation(it); renaming = false }
        )
    }
    if (detail != null && confirmDeleteConversation) {
        DeleteConversationDialog(
            onDismiss = { confirmDeleteConversation = false },
            onDelete = { confirmDeleteConversation = false; vm.deleteConversation() }
        )
    }

    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            focusMode -> stringResource(R.string.app_name)
                            inDetail -> stringResource(R.string.title_conversation)
                            else -> stringResource(page.label)
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (inDetail && !focusMode) {
                        IconButton(onClick = vm::closeConversation) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (inDetail && !focusMode) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    onClick = { menuOpen = false; renaming = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete_conversation), color = ErrorColor) },
                                    onClick = { menuOpen = false; confirmDeleteConversation = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScreenBackground)
            )
        },
        bottomBar = {
            if (!focusMode) {
                NavigationBar(containerColor = CardSurface, tonalElevation = 0.dp) {
                    AppPage.entries.forEach { destination ->
                        val selected = page == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Re-selecting Memories while a conversation is open returns to the list.
                                if (destination == AppPage.MEMORIES && inDetail) vm.closeConversation()
                                pageName = destination.name
                            },
                            icon = {
                                Icon(if (selected) destination.filledIcon else destination.outlineIcon, contentDescription = null)
                            },
                            label = {
                                Text(stringResource(destination.label), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary, selectedTextColor = Primary,
                                indicatorColor = PrimarySoft, unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Clear of the system bars and of the keyboard; centred and width-capped on large screens.
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val content = Modifier.widthIn(max = MaxContentWidth).fillMaxSize()
            when {
                focusMode -> {
                    val level by vm.level.collectAsStateWithLifecycle()
                    RecordingScreen(content, state, level = { level }) { vm.deactivate(context) }
                }
                page == AppPage.HOME -> HomeScreen(
                    content, state, usage, memories.dueSoon, permissionError, activate,
                    onRetry = { state.lastAudioPath?.let { vm.retry(context, it) } },
                    onDiscard = vm::discardRecording,
                    onOpenLatest = { state.result?.sessionId?.let(openConversation) ?: run { pageName = AppPage.MEMORIES.name } },
                    onTaskDone = vm::completeMemory,
                    onSeeAllTasks = { pageName = AppPage.TASKS.name }
                )
                page == AppPage.TASKS -> TasksScreen(
                    content, memories, memoryActions,
                    showNotificationCard = notificationSettings.remindersEnabled && !notificationsAllowed &&
                        !notificationSettings.cardDismissed,
                    onRefresh = vm::loadCommitments,
                    onFilterChange = vm::setTaskFilter,
                    onEnableNotifications = enableNotifications,
                    onDismissNotificationCard = vm::dismissNotificationCard
                )
                page == AppPage.MEMORIES -> MemoriesScreen(
                    content, state, history, memories, memoryActions,
                    vm::ask, vm::loadHistory, vm::onSearchQueryChange, vm::clearSearch,
                    vm::openConversation, vm::retrySession, vm::setSelfSpeaker
                )
                else -> SettingsScreen(
                    content, username, usage, themeMode, accountDeletion, notificationSettings, notificationsAllowed,
                    hasPendingRecording = vm::hasPendingRecording,
                    onThemeChange = vm::setThemeMode,
                    onRemindersChange = vm::setRemindersEnabled,
                    onDigestChange = vm::setDigestEnabled,
                    onDigestTimeChange = vm::setDigestTime,
                    onEnableNotifications = enableNotifications,
                    onLogout = vm::logout,
                    onDeleteAccount = vm::deleteAccount,
                    onClearDeletionError = vm::clearAccountDeletionError
                )
            }
        }
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

@Composable
private fun MicPermissionDialog(dialog: MicDialog, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val rationale = dialog == MicDialog.RATIONALE
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
        title = {
            Text(stringResource(if (rationale) R.string.permission_rationale_title else R.string.permission_settings_title))
        },
        text = {
            Text(stringResource(if (rationale) R.string.permission_rationale_text else R.string.permission_settings_text))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (rationale) R.string.action_continue else R.string.action_open_settings))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } }
    )
}
