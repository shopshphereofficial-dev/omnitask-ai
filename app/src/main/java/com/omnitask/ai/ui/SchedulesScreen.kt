package com.omnitask.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.data.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    schedules: List<Schedule>,
    onBack: () -> Unit,
    onDelete: (Schedule) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled tasks", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (schedules.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No scheduled tasks yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Just ask the AI, for example:\n\n" +
                        "- every day at 7 AM turn on the flashlight\n" +
                        "- at 10 PM every night open YouTube\n" +
                        "- every 30 minutes tell me the battery status\n" +
                        "- every monday at 9 AM whatsapp moomin good morning",
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxWidth()
            ) {
                items(schedules, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = {
                            Text(s.label, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(
                                s.describe() + " • " + s.actionsJson.take(80) + if (s.actionsJson.length > 80) "…" else "",
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDelete(s) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
