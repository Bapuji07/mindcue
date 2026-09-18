package com.secondmemory.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondmemory.android.data.MemoryItem
import com.secondmemory.android.data.HistoryUiState
import com.secondmemory.android.data.ConversationSession
import com.secondmemory.android.state.MemoryMode
import com.secondmemory.android.state.MemoryUiState
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SecondMemoryTheme { SecondMemoryApp() } }
    }
}

private val Evergreen = Color(0xFF176B52)
private val EvergreenDark = Color(0xFF0F4E3B)
private val DeepGreen = Color(0xFF10231D)
private val Mint = Color(0xFFE5F4EC)
private val Canvas = Color(0xFFF7F8F5)
private val Muted = Color(0xFF66756F)
private val ErrorRed = Color(0xFFB3261E)
private val Accent = Color(0xFF7FE0B2)
private val CardBorder = Color(0x140F4E3B)

private enum class AppPage(val label: String, val outlineIcon: ImageVector, val filledIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    MEMORIES("Memories", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
private fun SecondMemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Evergreen, onPrimary = Color.White, primaryContainer = Mint,
            onPrimaryContainer = DeepGreen, background = Canvas, surface = Color.White,
            onBackground = DeepGreen, onSurface = DeepGreen, onSurfaceVariant = Muted,
            error = ErrorRed
        ),
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
    val context = LocalContext.current
    var pageName by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    val page = AppPage.valueOf(pageName)

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

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text(if (focusMode) "MindCue" else page.label, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            if (!focusMode) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    AppPage.entries.forEach { destination ->
                        val selected = page == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { pageName = destination.name },
                            icon = {
                                Icon(
                                    if (selected) destination.filledIcon else destination.outlineIcon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Evergreen, selectedTextColor = Evergreen,
                                indicatorColor = Mint, unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            focusMode -> FocusScreen(Modifier.padding(padding), state) { vm.deactivate(context) }
            page == AppPage.HOME -> HomeScreen(
                Modifier.padding(padding), state, permissionError, activate,
                { state.lastAudioPath?.let { vm.retry(context, it) } },
                { pageName = AppPage.MEMORIES.name }
            )
            page == AppPage.MEMORIES -> MemoriesScreen(
                Modifier.padding(padding), state, history, vm::ask, vm::loadHistory,
                vm::openConversation, vm::closeConversation, vm::renameConversation,
                vm::deleteConversation, vm::completeMemory, vm::dismissMemory
            )
            else -> SettingsScreen(Modifier.padding(padding), username, vm::logout)
        }
    }
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        colors = ButtonDefaults.buttonColors(containerColor = Evergreen),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (loggingIn) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
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

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: MemoryUiState,
    permissionError: String?,
    onActivate: () -> Unit,
    onRetry: () -> Unit,
    onOpenMemories: () -> Unit
) {
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
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .shadow(14.dp, CircleShape, spotColor = Evergreen)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Evergreen, EvergreenDark)))
                    .clickable(onClick = onActivate),
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
                        "Activate\nMemory", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White
                    )
                }
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
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Evergreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Latest memory", color = Evergreen, style = MaterialTheme.typography.labelLarge)
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
                Box(Modifier.size(120.dp).background(DeepGreen, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Memory is active", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                formatDuration(state.elapsedSeconds), style = MaterialTheme.typography.displaySmall,
                color = Evergreen, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("You can lock your phone. Listening will continue.", color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onDeactivate, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
            ) { Text("Deactivate Memory", fontWeight = FontWeight.SemiBold) }
        } else {
            CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 4.dp, color = Evergreen)
            Spacer(Modifier.height(24.dp))
            Text("Creating your memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(state.status, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("It is safe to leave this screen.", color = Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MemoriesScreen(
    modifier: Modifier,
    state: MemoryUiState,
    history: HistoryUiState,
    onAsk: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCompleteMemory: (String) -> Unit,
    onDismissMemory: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    history.selected?.let {
        ConversationDetailScreen(
            modifier, history, onClose, onRename, onDelete, onCompleteMemory, onDismissMemory
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { AskMemoryCard(state, onAsk) }
        item { SectionTitle("Conversation history", history.sessions.size.toString()) }
        if (history.loading && history.sessions.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        history.error?.let { item { InlineMessage(it, ErrorRed) } }
        if (!history.loading && history.sessions.isEmpty()) {
            item { EmptyCard("Your processed conversations will appear here after you deactivate Memory.", Icons.Outlined.History) }
        } else {
            items(history.sessions, key = { it.id }) { session -> ConversationCard(session) { onOpen(session.id) } }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ConversationCard(session: ConversationSession, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
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
                Text(formatDate(session.startedAt), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            session.summary?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val label = status.lowercase().replaceFirstChar { it.uppercase() }
    val color = when (status.uppercase()) {
        "COMPLETED", "TRANSCRIPTION_COMPLETE" -> Evergreen
        "FAILED" -> ErrorRed
        else -> Color(0xFFB07E1E)
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
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onCompleteMemory: (String) -> Unit,
    onDismissMemory: (String) -> Unit
) {
    val detail = history.selected ?: return
    var title by rememberSaveable(detail.session.id) { mutableStateOf(detail.session.title) }
    var editingTitle by rememberSaveable(detail.session.id) { mutableStateOf(false) }
    var transcriptVisible by rememberSaveable(detail.session.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(detail.session.id) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete conversation?") },
            text = { Text("This permanently removes its transcript, memories, and stored recording.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Back to conversations") } }
        item {
            SurfaceCard {
                if (editingTitle) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it }, label = { Text("Conversation title") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onRename(title); editingTitle = false },
                            enabled = title.isNotBlank() && !history.mutating
                        ) { Text("Save") }
                        TextButton(onClick = { title = detail.session.title; editingTitle = false }) { Text("Cancel") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            detail.session.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { editingTitle = true }) { Text("Rename") }
                    }
                    Text(formatDate(detail.session.startedAt), color = Muted)
                }
            }
        }
        history.error?.let { item { InlineMessage(it, ErrorRed) } }
        if (history.mutating) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            SectionTitle("Summary")
            Spacer(Modifier.height(8.dp))
            SurfaceCard { Text(detail.session.summary ?: "No summary was generated.") }
        }
        item { SectionTitle("Memories", detail.memories.size.toString()) }
        if (detail.memories.isEmpty()) {
            item { EmptyCard("No active memories remain in this conversation.", Icons.Outlined.AutoAwesome) }
        } else {
            items(detail.memories, key = { it.id ?: "${it.type}-${it.title}" }) { memory ->
                MemoryCard(
                    memory,
                    onDone = memory.id?.takeIf { memory.resolutionStatus == "OPEN" }?.let { id -> { onCompleteMemory(id) } },
                    onDismiss = memory.id?.let { id -> { onDismissMemory(id) } }
                )
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
        item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete conversation", color = ErrorRed)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, username: String?, onLogout: () -> Unit) {
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
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Log out")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        detail?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Evergreen) }
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp),
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
                Icon(icon, contentDescription = null, tint = Evergreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

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
private fun MemoryCard(memory: MemoryItem, onDone: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(memoryTypeIcon(memory.type), contentDescription = null, tint = Evergreen, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(memory.type, color = Evergreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(9.dp))
        Text(memory.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(memory.content)
        if (memory.dueAt != null || memory.confidence != null) {
            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                memory.dueAt?.let { Text("Due ${it.replace('T', ' ').take(16)}", style = MaterialTheme.typography.labelMedium) }
                memory.confidence?.let {
                    Text("${String.format(Locale.US, "%.0f", it * 100)}% confidence", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (onDone != null || onDismiss != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDone?.let {
                    OutlinedButton(onClick = it) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Mark done")
                    }
                }
                onDismiss?.let { TextButton(onClick = it) { Text("Dismiss") } }
            }
        }
    }
}

@Composable
private fun AskMemoryCard(state: MemoryUiState, onAsk: (String) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = Evergreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Ask Memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Text("Find something from your conversations.", color = Muted)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = question, onValueChange = { question = it },
            placeholder = { Text("What did I agree to do?") }, modifier = Modifier.fillMaxWidth(),
            minLines = 1, maxLines = 3
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onAsk(question) }, enabled = question.isNotBlank() && !state.asking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.asking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Ask")
            }
        }
        AnimatedVisibility(state.askResult != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        state.askResult?.let { result ->
            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(14.dp))
            Text(result.answer, fontWeight = FontWeight.Medium)
            if (result.sources.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Sources", color = Evergreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
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
        state.askError?.let {
            Spacer(Modifier.height(10.dp)); Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
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

private fun formatDate(value: String): String = value.replace('T', ' ').take(16).ifBlank { "Unknown date" }
