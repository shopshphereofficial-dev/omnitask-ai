package com.omnitask.ai.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.data.AiClient
import com.omnitask.ai.data.AppConfig
import com.omnitask.ai.data.ChatMessage
import com.omnitask.ai.data.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: AppConfig,
    onBack: () -> Unit,
    onSave: (AppConfig) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var providerId by remember { mutableStateOf(current.providerId) }
    var baseUrl by remember { mutableStateOf(current.baseUrl) }
    var apiKey by remember { mutableStateOf(current.apiKey) }
    var model by remember { mutableStateOf(current.model) }
    var systemPrompt by remember { mutableStateOf(current.systemPrompt) }
    var autoExecute by remember { mutableStateOf(current.autoExecute) }
    var expanded by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun buildConfig() = AppConfig(
        providerId = providerId,
        baseUrl = baseUrl,
        model = model,
        apiKey = apiKey,
        systemPrompt = systemPrompt,
        autoExecute = autoExecute
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle key visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Presets.byId(providerId)?.keyUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                Text(
                    "Get a key: $url",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                supportingText = { Text("OpenAI-compatible URL, usually ends with /v1") },
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-execute actions")
                    Text(
                        "When off, you approve each task with a Run actions button",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoExecute, onCheckedChange = { autoExecute = it })
            }

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System prompt") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Optional permissions: to let the AI send SMS or place calls directly, " +
                    "grant the SMS and Phone permissions from Android settings.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + ctx.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ) {
                Text("Open app permission settings")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            try {
                                val cfg = buildConfig()
                                val r = withContext(Dispatchers.IO) {
                                    AiClient.chat(
                                        cfg,
                                        listOf(ChatMessage(role = "user", content = "Reply with exactly: OK"))
                                    )
                                }
                                testResult = "Success! $r"
                            } catch (e: Exception) {
                                testResult = "Failed: ${e.message}"
                            } finally {
                                testing = false
                            }
                        }
                    },
                    enabled = !testing && baseUrl.isNotBlank() && model.isNotBlank()
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test connection")
                    }
                }
            }
            testResult?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = { onSave(buildConfig()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() || providerId == "ollama" || providerId == "custom"
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
