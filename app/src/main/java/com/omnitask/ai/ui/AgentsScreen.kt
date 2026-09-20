package com.omnitask.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.data.Agent
import com.omnitask.ai.data.Presets
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    agents: List<Agent>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Agent) -> Unit,
    onUse: (Agent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New agent")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Each agent is a dedicated assistant with its own name, personality and instructions. " +
                    "You can even give an agent its own AI provider.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            agents.forEach { a ->
                ListItem(
                    headlineContent = {
                        Text("${a.emoji.ifBlank { "🤖" }} ${a.name}", fontWeight = FontWeight.Medium)
                    },
                    supportingContent = {
                        Text(
                            if (a.providerId.isNotBlank() && a.baseUrl.isNotBlank())
                                "Custom AI: ${Presets.nameOf(a.providerId)} • ${a.model}"
                            else "Uses the default provider from Settings",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onUse(a) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Chat with this agent")
                            }
                            IconButton(onClick = { onEdit(a) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditScreen(
    initial: Agent?,
    onBack: () -> Unit,
    onSave: (Agent) -> Unit,
    onDelete: (Agent) -> Unit
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🤖") }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt ?: "") }
    var useCustom by remember { mutableStateOf(initial != null && initial.providerId.isNotBlank()) }
    var providerId by remember { mutableStateOf(initial?.providerId?.takeIf { it.isNotBlank() } ?: "openai") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl?.takeIf { it.isNotBlank() } ?: Presets.ALL[0].baseUrl) }
    var model by remember { mutableStateOf(initial?.model?.takeIf { it.isNotBlank() } ?: Presets.ALL[0].defaultModel) }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val emojiChoices = listOf("🤖", "🧠", "💼", "📚", "🏋️", "👨‍🍳", "🎵", "✈️", "💰", "🩺", "📝", "🛠️", "🎓", "🎮", "🧑‍💻")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New agent" else "Edit agent", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji.ifBlank { "🤖" }, fontSize = 40.sp)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { if (it.length <= 4) emoji = it },
                    label = { Text("Emoji") },
                    modifier = Modifier.width(110.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                emojiChoices.take(8).forEach { e ->
                    Text(
                        e,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable { emoji = e }
                            .padding(4.dp)
                    )
                }
            }

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("Instructions (system prompt)") },
                supportingText = { Text("Tell this agent who it is and how it should behave") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useCustom, onCheckedChange = { useCustom = it })
                Column {
                    Text("Use a custom AI provider for this agent")
                    Text(
                        "Otherwise it uses your default provider from Settings",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (useCustom) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = Presets.nameOf(providerId),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Presets.ALL.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.name, fontWeight = FontWeight.Medium)
                                        if (p.baseUrl.isNotEmpty()) {
                                            Text(
                                                p.baseUrl,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    providerId = p.id
                                    if (p.baseUrl.isNotEmpty()) baseUrl = p.baseUrl
                                    if (p.defaultModel.isNotEmpty()) model = p.defaultModel
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    onSave(
                        Agent(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            emoji = emoji.ifBlank { "🤖" },
                            systemPrompt = systemPrompt,
                            providerId = if (useCustom) providerId else "",
                            baseUrl = if (useCustom) baseUrl else "",
                            model = if (useCustom) model else "",
                            apiKey = if (useCustom) apiKey else "",
                            isDefault = initial?.isDefault ?: false
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text(if (isNew) "Create agent" else "Save changes")
            }

            if (!isNew && initial?.isDefault != true && initial != null) {
                OutlinedButton(
                    onClick = { onDelete(initial) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete agent", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
