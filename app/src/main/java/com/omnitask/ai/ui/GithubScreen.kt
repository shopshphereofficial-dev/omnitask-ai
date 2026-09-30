package com.omnitask.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.data.AppConfig
import com.omnitask.ai.data.GithubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubScreen(
    config: AppConfig,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf(config.githubRepo) }
    var newName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    fun run(label: String, block: () -> String) {
        if (busy) return
        busy = true
        output = "Working..."
        scope.launch {
            output = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                label + " failed: " + (e.message ?: "unknown error")
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (config.githubToken.isBlank()) {
                Text(
                    "No GitHub token yet. Add one in Settings to let the assistant create repositories, push code and start builds.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenSettings) { Text("Open Settings") }
            } else {
                Text("Connected account: " + config.githubOwner.ifBlank { "unknown" }, fontSize = 13.sp)
                Text("Default repository: " + config.githubRepo.ifBlank { "none" }, fontSize = 13.sp)

                OutlinedButton(
                    onClick = {
                        run("Check connection") {
                            "Signed in as " + GithubClient.login(config.githubToken)
                        }
                    },
                    enabled = !busy
                ) { Text("Check connection") }

                OutlinedButton(
                    onClick = {
                        run("List repositories") {
                            val repos = GithubClient.listRepos(config.githubToken)
                            "Repositories (" + repos.size + "):\n" + repos.take(30).joinToString("\n")
                        }
                    },
                    enabled = !busy
                ) { Text("List my repositories") }

                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repository (owner/name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            run("Start build") {
                                GithubClient.dispatchBuild(config.githubToken, repo.ifBlank { config.githubRepo })
                            }
                        },
                        enabled = !busy
                    ) { Text("Start build") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            run("Build status") {
                                val r = repo.ifBlank { config.githubRepo }
                                var s = GithubClient.latestRunStatus(config.githubToken, r)
                                val apk = GithubClient.latestApkUrl(config.githubToken, r)
                                if (apk != null) s += "\nAPK: " + apk
                                s
                            }
                        },
                        enabled = !busy
                    ) { Text("Build status") }
                }

                Spacer(Modifier.height(4.dp))
                Text("Create a new repository", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Repository name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Private", Modifier.weight(1f))
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
                OutlinedButton(
                    onClick = {
                        run("Create repository") {
                            if (newName.isBlank()) {
                                "Enter a repository name first"
                            } else {
                                "Created " + GithubClient.createRepo(
                                    config.githubToken,
                                    newName,
                                    isPrivate,
                                    "Created by OmniTask AI"
                                )
                            }
                        }
                    },
                    enabled = !busy
                ) { Text("Create repository") }
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Working...")
                }
            }
            if (output.isNotBlank()) {
                Text(output, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
