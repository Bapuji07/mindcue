package com.secondmemory.android

import android.Manifest
import android.content.Context
import android.text.format.DateFormat
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondmemory.android.data.MemoriesUiState
import com.secondmemory.android.data.UsageInfo
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.data.ConversationSession
import com.secondmemory.android.state.MemoryMode
import com.secondmemory.android.state.MemoryUiState
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the system bars (enforced from Android 15); Scaffold applies the insets.
        enableEdgeToEdge()
        setContent { SecondMemoryTheme { SecondMemoryApp() } }
    }
}

// Brand colours, used for the hero gradient and avatar in both light and dark themes.
private val Evergreen = Color(0xFF176B52)
private val EvergreenDark = Color(0xFF0F4E3B)
private val DeepGreen = Color(0xFF10231D)
private val Accent = Color(0xFF7FE0B2)

private val LightColors = lightColorScheme(
    primary = Evergreen, onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F4EC), onPrimaryContainer = DeepGreen,
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF7F8F5), onBackground = DeepGreen,
    surface = Color.White, onSurface = DeepGreen, onSurfaceVariant = Color(0xFF5E6D67),
    outlineVariant = Color(0xFFE1E7E3),
    error = Color(0xFFB3261E), onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6AE), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1E4A3B), onPrimaryContainer = Color(0xFFC6F1DC),
    tertiary = Color(0xFFF2C26B),
    background = Color(0xFF0E1412), onBackground = Color(0xFFDDE5E0),
    surface = Color(0xFF172019), onSurface = Color(0xFFDDE5E0), onSurfaceVariant = Color(0xFFA3B2AB),
    outlineVariant = Color(0xFF2B3631),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410)
)

// Theme roles under the names the screens use, so every screen follows light/dark automatically.
private val Primary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
private val Canvas: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
private val CardColor: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
private val Mint: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
private val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val ErrorRed: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
private val Warning: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
private val CardBorder: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

private enum class AppPage(val label: String, val outlineIcon: ImageVector, val filledIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    TASKS("Tasks", Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
    MEMORIES("Memories", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
private fun SecondMemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondMemoryApp(vm: MainViewModel = viewModel()) {
    val isLoggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    if (!isLoggedIn) {
        val loginError by vm.loginError.collectAsStateWithLifecycle()
        val loggingIn by vm.loggingIn.collectAsStateWithLifecycle()
        LoginScreen(loggingIn, loginError, vm::login, vm::register)
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pageName by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    val page = runCatching { AppPage.valueOf(pageName) }.getOrDefault(AppPage.HOME)
    LaunchedEffect(page) { if (page == AppPage.HOME || page == AppPage.SETTINGS) vm.refreshUsage() }

    val openConversation: (String) -> Unit = { sessionId ->
        vm.openConversation(sessionId)
        pageName = AppPage.MEMORIES.name
    }
    val memoryActions = remember(vm) {
        MemoryActions(vm::completeMemory, vm::removeMemory, vm::rescheduleMemory, openConversation)
    }

    // Snackbar messages from the view model. A message's onTimeout also runs if the screen goes
    // away mid-message, so a pending delete is never silently dropped.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            launch {
                var undone = false
                try {
                    undone = snackbar.showSnackbar(
                        message.text,
                        actionLabel = if (message.onUndo != null) "Undo" else null,
                        duration = SnackbarDuration.Short
                    ) == SnackbarResult.ActionPerformed
                } finally {
                    if (undone) message.onUndo?.invoke() else message.onTimeout?.invoke()
                }
            }
        }
    }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            permissionError = null
            vm.activate(context)
        } else permissionError = "Microphone permission is needed to activate Memory."
    }
    val activate = {
        pageName = AppPage.HOME.name
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.activate(context)
        } else permissionLauncher.launch(permissions)
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
        AlertDialog(
            onDismissRequest = { confirmDeleteConversation = false },
            title = { Text("Delete conversation?") },
            text = { Text("This permanently removes its transcript and memories. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteConversation = false; vm.deleteConversation() }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteConversation = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            focusMode -> "MindCue"
                            inDetail -> "Conversation"
                            else -> page.label
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (inDetail && !focusMode) {
                        IconButton(onClick = vm::closeConversation) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (inDetail && !focusMode) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { menuOpen = false; renaming = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete conversation", color = ErrorRed) },
                                    onClick = { menuOpen = false; confirmDeleteConversation = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            if (!focusMode) {
                NavigationBar(containerColor = CardColor, tonalElevation = 0.dp) {
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
                                Icon(
                                    if (selected) destination.filledIcon else destination.outlineIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(destination.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary, selectedTextColor = Primary,
                                indicatorColor = Mint, unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Keep content clear of the system bars and of the keyboard when a text field is focused.
        val content = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
        when {
            focusMode -> FocusScreen(content, state) { vm.deactivate(context) }
            page == AppPage.HOME -> HomeScreen(
                content, state, usage, permissionError, activate,
                { state.lastAudioPath?.let { vm.retry(context, it) } },
                vm::discardRecording,
                { state.result?.sessionId?.let(openConversation) ?: run { pageName = AppPage.MEMORIES.name } }
            )
            page == AppPage.TASKS -> TasksScreen(content, memories, memoryActions, vm::loadCommitments, vm::setOverdueOnly)
            page == AppPage.MEMORIES -> MemoriesScreen(
                content, state, history, memories, memoryActions,
                vm::ask, vm::loadHistory, vm::onSearchQueryChange, vm::clearSearch,
                vm::openConversation, vm::retrySession
            )
            else -> SettingsScreen(content, username, usage, vm::logout)
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("Title") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim()) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    loggingIn: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registerMode by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = Canvas) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(72.dp).background(Brush.linearGradient(listOf(Evergreen, EvergreenDark)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(18.dp))
            Text("MindCue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (registerMode) "Create an account to continue." else "Sign in to continue.",
                color = Muted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it }, label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        supportingText = { if (registerMode) Text("At least 8 characters") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AnimatedVisibility(error != null) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            error?.let { InlineMessage(it, ErrorRed) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { if (registerMode) onRegister(username, password) else onLogin(username, password) },
                        enabled = !loggingIn && username.isNotBlank() &&
                            (if (registerMode) password.length >= 8 else password.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (loggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        else Text(if (registerMode) "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { registerMode = !registerMode }) {
                Text(if (registerMode) "Already have an account? Sign in" else "Don't have an account? Create one")
            }
        }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: MemoryUiState,
    usage: UsageInfo?,
    permissionError: String?,
    onActivate: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onOpenMemories: () -> Unit
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard recording?") },
            text = { Text("The unprocessed recording is deleted from this phone and cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } }
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
                "Be present. Remember later.", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text("Activate Memory when a conversation matters.", color = Muted, textAlign = TextAlign.Center)
        }
        item {
            val canRecord = usage == null || usage.recordableMinutes > 0
            Box(
                modifier = Modifier
                    .widthIn(min = 190.dp)
                    .aspectRatio(1f)
                    .alpha(if (canRecord) 1f else 0.45f)
                    .shadow(if (canRecord) 14.dp else 0.dp, CircleShape, spotColor = Evergreen)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Evergreen, EvergreenDark)))
                    .clickable(enabled = canRecord, role = Role.Button, onClickLabel = "Start recording", onClick = onActivate)
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
                        "Activate Memory", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                }
            }
        }
        usage?.takeIf { !it.unlimited }?.let { info ->
            item {
                val left = info.audioMinutesLeft
                Text(
                    if (left > 0) "$left min of recording left this month"
                    else "No recording minutes left this month. They reset on ${resetDate(info.audioResetsAt)}.",
                    color = if (left > 0) Muted else ErrorRed, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        permissionError?.let { item { InlineMessage(it, ErrorRed) } }
        state.error?.let { message ->
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recording needs attention", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = Muted)
                    if (state.lastAudioPath != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry processing") }
                        TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Discard recording", color = ErrorRed)
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
                                Text("Latest memory", color = Primary, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                result.summary.ifBlank { "Your latest conversation is ready." },
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = onOpenMemories) { Text("View") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FocusScreen(modifier: Modifier, state: MemoryUiState, onDeactivate: () -> Unit) {
    val active = state.mode == MemoryMode.ACTIVE
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (active) {
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
                Box(
                    Modifier.size(150.dp).scale(ringScale)
                        .background(Accent.copy(alpha = ringAlpha), CircleShape)
                )
                Box(Modifier.size(120.dp).background(Brush.linearGradient(listOf(Evergreen, EvergreenDark)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Memory is active", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                formatDuration(state.elapsedSeconds), style = MaterialTheme.typography.displaySmall,
                color = Primary, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("You can lock your phone. Listening will continue.", color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onDeactivate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Deactivate Memory", fontWeight = FontWeight.SemiBold) }
        } else {
            CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 4.dp, color = Primary)
            Spacer(Modifier.height(24.dp))
            Text("Creating your memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(state.status, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("It is safe to leave this screen.", color = Muted, textAlign = TextAlign.Center)
        }
    }
}

private class MemoryActions(
    val onDone: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onReschedule: (String, Long?) -> Unit,
    val onOpenConversation: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksScreen(
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
                    SelectableChip("All open", selected = !memories.overdueOnly) { onOverdueChange(false) }
                    SelectableChip("Overdue", selected = memories.overdueOnly) { onOverdueChange(true) }
                }
            }
            memories.error?.let { item { InlineMessage(it, ErrorRed) } }
            when {
                memories.commitmentsLoading && memories.commitments.isEmpty() -> item { CenteredProgress() }
                memories.commitments.isEmpty() -> item {
                    EmptyCard(
                        if (memories.overdueOnly) "Nothing is overdue. Nice work."
                        else "You're all caught up. Tasks and promises from your conversations appear here.",
                        Icons.Outlined.CheckCircle
                    )
                }
                else -> items(memories.commitments, key = { "open-${it.id ?: it.hashCode()}" }) {
                    ActionableMemoryCard(it, actions, showSource = true)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoriesScreen(
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
    onRetrySession: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    if (history.selected != null) {
        ConversationDetailScreen(modifier, history, memories, actions, onRetrySession)
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
                item { SectionTitle("Matching memories", results?.size?.toString()) }
                memories.error?.let { item { InlineMessage(it, ErrorRed) } }
                when {
                    results == null && (memories.searching || query.trim().length >= 2) -> item { CenteredProgress() }
                    results == null -> item { Text("Keep typing to search…", color = Muted) }
                    results.isEmpty() -> item {
                        EmptyCard("No memories mention “${query.trim()}”. Try asking instead.", Icons.Outlined.Search)
                    }
                    else -> items(results, key = { "search-${it.id ?: it.hashCode()}" }) {
                        ActionableMemoryCard(it, actions, showSource = true)
                    }
                }
            } else {
                item { SectionTitle("Conversations", history.sessions.size.toString()) }
                history.error?.let { item { InlineMessage(it, ErrorRed) } }
                when {
                    history.loading && history.sessions.isEmpty() -> item { CenteredProgress() }
                    history.sessions.isEmpty() -> item {
                        EmptyCard("Your conversations appear here after you record one on Home.", Icons.Outlined.History)
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
        placeholder = { Text("Search or ask your memories…") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardColor, unfocusedContainerColor = CardColor,
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
            Box(Modifier.size(34.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (result == null) "Ask Memory" else "Answer", fontWeight = FontWeight.Bold)
                if (result == null) {
                    Text("Get an answer from your conversations.", color = Muted, style = MaterialTheme.typography.bodySmall)
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
                    Text("Ask “$question”", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            state.askError?.let {
                Spacer(Modifier.height(10.dp)); Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(result.answer)
            if (result.sources.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Sources", color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
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
        colors = CardDefaults.cardColors(containerColor = CardColor),
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
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            onRetry?.let {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = it, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Retry processing")
                }
            }
        }
    }
}

/** Server statuses reduced to what a user cares about. */
@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status.uppercase()) {
        "COMPLETED" -> "Ready" to Primary
        "FAILED" -> "Failed" to ErrorRed
        "RECORDING" -> "Recording" to Warning
        else -> "Processing" to Warning
    }
    Box(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConversationDetailScreen(
    modifier: Modifier,
    history: HistoryUiState,
    memories: MemoriesUiState,
    actions: MemoryActions,
    onRetry: (String) -> Unit
) {
    val detail = history.selected ?: return
    var transcriptVisible by rememberSaveable(detail.session.id) { mutableStateOf(false) }

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
        history.error?.let { item { InlineMessage(it, ErrorRed) } }
        if (history.mutating || memories.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (canRetry(detail.session)) {
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (detail.session.status.equals("FAILED", true)) "Processing failed" else "Processing seems stuck",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    detail.session.errorMessage?.let { Spacer(Modifier.height(6.dp)); Text(it, color = Muted) }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onRetry(detail.session.id) }, enabled = !history.mutating,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Retry processing") }
                }
            }
        }
        item {
            SectionTitle("Summary")
            Spacer(Modifier.height(8.dp))
            SurfaceCard { Text(detail.session.summary ?: "No summary was generated.") }
        }
        item { SectionTitle("Memories", detail.memories.size.toString()) }
        if (detail.memories.isEmpty()) {
            item { EmptyCard("No active memories remain in this conversation.", Icons.Outlined.AutoAwesome) }
        } else {
            items(detail.memories, key = { "memory-${it.id ?: "${it.type}-${it.title}"}" }) { memory ->
                ActionableMemoryCard(memory, actions, showSource = false)
            }
        }
        item {
            OutlinedButton(onClick = { transcriptVisible = !transcriptVisible }, modifier = Modifier.fillMaxWidth()) {
                Text(if (transcriptVisible) "Hide transcript" else "Show transcript")
            }
        }
        if (transcriptVisible) {
            item { SurfaceCard { Text(detail.transcript.ifBlank { "No transcript returned." }) } }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, username: String?, usage: UsageInfo?, onLogout: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).background(Brush.linearGradient(listOf(Evergreen, EvergreenDark)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            username?.take(1)?.uppercase() ?: "?",
                            color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Signed in as", color = Muted, style = MaterialTheme.typography.labelMedium)
                        Text(
                            username ?: "Unknown", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Log out")
                }
            }
        }
        usage?.let { info -> item { UsageCard(info) } }
    }
}

@Composable
private fun UsageCard(usage: UsageInfo) {
    SurfaceCard {
        Text("Usage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (usage.unlimited) {
            Text("Unlimited account", color = Primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("${usage.audioMinutesUsed} min recorded this month, ${usage.aiRequestsToday} questions today.", color = Muted)
            return@SurfaceCard
        }
        Spacer(Modifier.height(10.dp))
        UsageMeter(
            "Recording this month", usage.audioMinutesUsed, usage.audioMinutesLimit, "min",
            "Resets ${resetDate(usage.audioResetsAt)}"
        )
        Spacer(Modifier.height(14.dp))
        UsageMeter(
            "Ask Memory today", usage.aiRequestsToday, usage.aiRequestsLimit, "questions",
            "Resets daily"
        )
        Spacer(Modifier.height(10.dp))
        Text("Each recording can be up to ${usage.maxRecordingMinutes} minutes.", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun UsageMeter(label: String, used: Int, limit: Int, unit: String, footnote: String) {
    val fraction = if (limit <= 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text("${used.coerceAtMost(limit)} / $limit $unit", color = if (fraction >= 1f) ErrorRed else Muted)
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = if (fraction >= 1f) ErrorRed else Primary,
        trackColor = Mint
    )
    Spacer(Modifier.height(4.dp))
    Text(footnote, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SectionTitle(title: String, detail: String? = null) {
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
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun EmptyCard(message: String, icon: ImageVector = Icons.Outlined.History) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(48.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

private const val LOW_CONFIDENCE = 0.6

/** "TASK" -> "Task". */
private fun memoryTypeLabel(type: String): String =
    type.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

private fun memoryTypeIcon(type: String): ImageVector = when (type.uppercase()) {
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

@Composable
private fun ActionableMemoryCard(memory: MemoryItem, actions: MemoryActions, showSource: Boolean) {
    val id = memory.id
    val open = memory.resolutionStatus == "OPEN"
    MemoryCard(
        memory,
        onDone = if (id != null && open) ({ actions.onDone(id) }) else null,
        onDelete = if (id != null) ({ actions.onDelete(id) }) else null,
        onReschedule = if (id != null && open) ({ days: Long? -> actions.onReschedule(id, days) }) else null,
        onOpenConversation = memory.sessionId?.takeIf { showSource }?.let { sessionId -> { actions.onOpenConversation(sessionId) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryCard(
    memory: MemoryItem,
    onDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReschedule: ((Long?) -> Unit)? = null,
    onOpenConversation: (() -> Unit)? = null
) {
    var dueMenuOpen by remember { mutableStateOf(false) }
    val overdue = isOverdue(memory)
    val lowConfidence = (memory.confidence ?: 1.0) < LOW_CONFIDENCE

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(memoryTypeIcon(memory.type), contentDescription = null, tint = Primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(memoryTypeLabel(memory.type), color = Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            if (memory.resolutionStatus == "DONE") {
                Spacer(Modifier.width(10.dp))
                Text("Done", color = Muted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            if (overdue) {
                Spacer(Modifier.width(10.dp))
                Text("Overdue", color = ErrorRed, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
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
                        "Due ${friendlyDateTime(it)}", style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) ErrorRed else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (lowConfidence) {
                    Text("Unsure — check the transcript", style = MaterialTheme.typography.labelMedium, color = Warning)
                }
            }
        }
        if (onDone != null || onDelete != null || onReschedule != null || onOpenConversation != null) {
            Spacer(Modifier.height(10.dp))
            // FlowRow wraps the buttons onto a second line on narrow screens instead of overflowing.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                onDone?.let {
                    OutlinedButton(onClick = it) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Mark done")
                    }
                }
                onReschedule?.let { reschedule ->
                    Box {
                        TextButton(onClick = { dueMenuOpen = true }) {
                            Text(if (memory.dueAt == null) "Set due" else "Reschedule")
                        }
                        DropdownMenu(expanded = dueMenuOpen, onDismissRequest = { dueMenuOpen = false }) {
                            listOf("Tomorrow" to 1L, "In 3 days" to 3L, "In a week" to 7L).forEach { (label, days) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { dueMenuOpen = false; reschedule(days) }
                                )
                            }
                            if (memory.dueAt != null) {
                                DropdownMenuItem(
                                    text = { Text("Clear due date") },
                                    onClick = { dueMenuOpen = false; reschedule(null) }
                                )
                            }
                        }
                    }
                }
                onOpenConversation?.let { TextButton(onClick = it) { Text("View conversation") } }
                // No confirmation dialog: the snackbar offers Undo before the delete is sent.
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = ErrorRed) } }
            }
        }
    }
}

@Composable
private fun InlineMessage(message: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, color = color)
        }
    }
}

private fun formatDuration(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)

private val InProgressStatuses = setOf("AUDIO_RECEIVED", "TRANSCRIBING", "TRANSCRIPTION_COMPLETE", "PROCESSING")
private const val STALE_AFTER_MINUTES = 10L

/** Failed conversations, and in-progress ones that have not moved for a while (e.g. server restarted). */
private fun canRetry(session: ConversationSession): Boolean {
    val status = session.status.uppercase()
    if (status == "FAILED") return true
    if (status !in InProgressStatuses) return false
    val updated = parseInstant(session.updatedAt) ?: return false
    return Duration.between(updated, Instant.now()).toMinutes() >= STALE_AFTER_MINUTES
}

private fun parseInstant(value: String?): Instant? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

private fun isOverdue(memory: MemoryItem): Boolean =
    memory.resolutionStatus == "OPEN" && parseInstant(memory.dueAt)?.isBefore(Instant.now()) == true

/** "Today, 9:41 PM", "Yesterday, 21:41" or "Mon 5 Oct, 9:41 PM" in the phone's time zone and clock format. */
@Composable
private fun friendlyDateTime(value: String?): String {
    val instant = parseInstant(value) ?: return value?.replace('T', ' ')?.take(16)?.ifBlank { null } ?: "Unknown date"
    return formatWhen(instant, LocalContext.current)
}

private fun formatWhen(instant: Instant, context: Context): String {
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val time = DateFormat.getTimeFormat(context).format(Date.from(instant))
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> {
            val skeleton = if (date.year == today.year) "EEEdMMM" else "dMMMyyyy"
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)).format(date)
        }
    }
    return "$day, $time"
}
