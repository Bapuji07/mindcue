package com.secondmemory.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
private val DeepGreen = Color(0xFF10231D)
private val Mint = Color(0xFFE5F4EC)
private val Canvas = Color(0xFFF7F8F5)
private val Muted = Color(0xFF66756F)
private val ErrorRed = Color(0xFFB3261E)

private enum class AppPage(val label: String) {
    HOME("Home"), MEMORIES("Memories"), SETTINGS("Settings")
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
    val state by vm.state.collectAsStateWithLifecycle()
    val backendUrl by vm.backendUrl.collectAsStateWithLifecycle()
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
                title = { Text(if (focusMode) "Second Memory" else page.label, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            if (!focusMode) {
                NavigationBar(containerColor = Color.White) {
                    AppPage.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { pageName = destination.name },
                            icon = {
                                Box(
                                    Modifier.size(if (page == destination) 8.dp else 6.dp)
                                        .background(if (page == destination) Evergreen else Muted, CircleShape)
                                )
                            },
                            label = { Text(destination.label) }
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
            else -> SettingsScreen(
                Modifier.padding(padding), state, backendUrl, vm::updateBackendUrl, vm::saveAndTestBackend
            )
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
            Button(
                onClick = onActivate, modifier = Modifier.size(190.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Evergreen),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(14.dp).background(Color(0xFF7FE0B2), CircleShape))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Activate\nMemory", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                }
            }
        }
        permissionError?.let { item { InlineMessage(it, ErrorRed) } }
        state.error?.let { message ->
            item {
                SurfaceCard {
                    Text("Recording needs attention", fontWeight = FontWeight.Bold)
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
                            Text("Latest memory", color = Evergreen, style = MaterialTheme.typography.labelLarge)
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
            Box(Modifier.size(150.dp).background(DeepGreen, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(22.dp).background(Color(0xFF69DCA5), CircleShape))
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
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
            ) { Text("Deactivate Memory", fontWeight = FontWeight.SemiBold) }
        } else {
            CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 4.dp)
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
            item { EmptyCard("Your processed conversations will appear here after you deactivate Memory.") }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    session.title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    session.status.lowercase().replaceFirstChar { it.uppercase() }, color = Evergreen,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(formatDate(session.startedAt), color = Muted, style = MaterialTheme.typography.bodySmall)
            session.summary?.let {
                Spacer(Modifier.height(9.dp))
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
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
            item { EmptyCard("No active memories remain in this conversation.") }
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
private fun SettingsScreen(
    modifier: Modifier,
    state: MemoryUiState,
    backendUrl: String,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SurfaceCard {
                Text("Backend connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Connect this app to your MindCue server.", color = Muted)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = backendUrl, onValueChange = onUrlChange, label = { Text("Backend URL") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save and test") }
                state.connectionMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Muted)
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
        modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun EmptyCard(message: String) {
    SurfaceCard { Text(message, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun MemoryCard(memory: MemoryItem, onDone: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    SurfaceCard {
        Text(memory.type, color = Evergreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(7.dp))
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
                onDone?.let { OutlinedButton(onClick = it) { Text("Mark done") } }
                onDismiss?.let { TextButton(onClick = it) { Text("Dismiss") } }
            }
        }
    }
}

@Composable
private fun AskMemoryCard(state: MemoryUiState, onAsk: (String) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    SurfaceCard {
        Text("Ask Memory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            else Text("Ask")
        }
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
    ) { Text(message, color = color, modifier = Modifier.padding(14.dp)) }
}

private fun formatDuration(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)

private fun formatDate(value: String): String = value.replace('T', ' ').take(16).ifBlank { "Unknown date" }
