package com.omnitask.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.actions.ActionExecutor
import com.omnitask.ai.actions.ActionParser
import com.omnitask.ai.data.AiClient
import com.omnitask.ai.data.AppConfig
import com.omnitask.ai.data.ChatMessage
import com.omnitask.ai.data.Presets
import com.omnitask.ai.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    config: AppConfig,
    messages: SnapshotStateList<ChatMessage>,
    onOpenSettings: () -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) {
        val last = messages.size - 1 + if (busy) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        messages.add(ChatMessage(role = "user", content = text))
        busy = true
        errorText = null
        scope.launch {
            try {
                val history = messages.map { ChatMessage(id = it.id, role = it.role, content = it.content) }
                val reply = withContext(Dispatchers.IO) { AiClient.chat(config, history) }
                val actions = ActionParser.parse(reply)
                val clean = ActionParser.strip(reply)
                var results: List<String> = emptyList()
                var executed = false
                if (actions != null && config.autoExecute) {
                    results = withContext(Dispatchers.IO) { ActionExecutor.executeAll(ctx, actions) }
                    executed = true
                }
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = clean.ifBlank { "Done." },
                        actionsJson = actions?.toString(),
                        executed = executed,
                        results = results
                    )
                )
                Store.saveMessages(ctx, messages)
            } catch (e: Exception) {
                errorText = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OmniTask AI", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${Presets.nameOf(config.providerId)} • ${config.model}" +
                                if (config.apiKey.isBlank() && config.providerId != "ollama" && config.providerId != "custom")
                                    " • no API key" else "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear chat")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Hello! I am OmniTask AI.", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Ask me anything, or give me a phone task like:\n" +
                            "- Open WhatsApp\n" +
                            "- Set an alarm for 6:30 AM\n" +
                            "- Turn on the flashlight\n" +
                            "- Search YouTube for lofi music\n\n" +
                            "First time? Tap the gear icon to add your AI provider and API key.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { m ->
                        MessageBubble(m, enabled = !busy) { msg ->
                            scope.launch {
                                val arr = ActionParser.fromJson(msg.actionsJson) ?: return@launch
                                val res = withContext(Dispatchers.IO) { ActionExecutor.executeAll(ctx, arr) }
                                val idx = messages.indexOfFirst { it.id == msg.id }
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(executed = true, results = res)
                                    Store.saveMessages(ctx, messages)
                                }
                            }
                        }
                    }
                    if (busy) {
                        item {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            errorText?.let { e ->
                Text(
                    text = "Error: $e",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask anything or give a task…") },
                    maxLines = 4,
                    enabled = !busy
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { send() }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage, enabled: Boolean, onRun: (ChatMessage) -> Unit) {
    val isUser = msg.role == "user"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = msg.content,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
            )
            if (msg.actionsJson != null && !msg.executed) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onRun(msg) }, enabled = enabled) {
                    Text("Run actions")
                }
            }
            msg.results.forEach { r ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "- $r",
                    fontSize = 12.sp,
                    color = if (isUser) Color(0xCCFFFFFF)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
