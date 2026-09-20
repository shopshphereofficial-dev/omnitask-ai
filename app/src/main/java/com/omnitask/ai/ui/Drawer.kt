package com.omnitask.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitask.ai.data.Agent
import com.omnitask.ai.data.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OmniDrawer(
    agents: List<Agent>,
    activeAgentId: String,
    conversations: List<Conversation>,
    onNewChat: () -> Unit,
    onSelectAgent: (Agent) -> Unit,
    onManageAgents: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Text(
            "OmniTask AI",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp)
        )
        ListItem(
            headlineContent = { Text("New chat") },
            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onNewChat)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "AGENTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        agents.forEach { a ->
            ListItem(
                headlineContent = {
                    Text(
                        a.name,
                        fontWeight = if (a.id == activeAgentId) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingContent = { Text(a.emoji.ifBlank { "🤖" }, fontSize = 20.sp) },
                trailingContent = {
                    if (a.id == activeAgentId) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.clickable { onSelectAgent(a) }
            )
        }
        ListItem(
            headlineContent = { Text("Manage agents") },
            leadingContent = { Icon(Icons.Default.SmartToy, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onManageAgents)
        )
        ListItem(
            headlineContent = { Text("Scheduled tasks") },
            leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenSchedules)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "RECENT CHATS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        if (conversations.isEmpty()) {
            Text(
                "No chats yet",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        } else {
            conversations.sortedByDescending { it.updatedAt }.take(20).forEach { c ->
                ListItem(
                    headlineContent = {
                        Text(
                            c.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = { Text(formatTime(c.updatedAt), fontSize = 12.sp) },
                    leadingContent = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onDeleteConversation(c) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                        }
                    },
                    modifier = Modifier.clickable { onOpenConversation(c) }
                )
            }
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Settings") },
            leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenSettings)
        )
    }
}

private fun formatTime(ts: Long): String {
    if (ts <= 0L) return ""
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(ts))
}
