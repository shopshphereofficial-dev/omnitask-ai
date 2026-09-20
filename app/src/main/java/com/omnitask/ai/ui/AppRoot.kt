package com.omnitask.ai.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import com.omnitask.ai.data.AppConfig
import com.omnitask.ai.data.ChatMessage
import com.omnitask.ai.data.Store

@Composable
fun AppRoot(activity: ComponentActivity) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("chat") }
    var config by remember { mutableStateOf(Store.loadConfig(ctx)) }
    val messages = remember { mutableListOf<ChatMessage>().toMutableStateList() }

    LaunchedEffect(Unit) {
        messages.addAll(Store.loadMessages(ctx))
    }

    when (screen) {
        "settings" -> SettingsScreen(
            current = config,
            onBack = { screen = "chat" },
            onSave = { cfg ->
                config = cfg
                Store.saveConfig(ctx, cfg)
                screen = "chat"
            }
        )
        else -> ChatScreen(
            config = config,
            messages = messages,
            onOpenSettings = { screen = "settings" },
            onClear = {
                messages.clear()
                Store.clearMessages(ctx)
            }
        )
    }
}
