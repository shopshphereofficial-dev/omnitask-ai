package com.omnitask.ai.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.omnitask.ai.actions.ActionExecutor
import com.omnitask.ai.actions.ActionParser
import com.omnitask.ai.data.Agent
import com.omnitask.ai.data.AppConfig
import com.omnitask.ai.data.AiClient
import com.omnitask.ai.data.ChatMessage
import com.omnitask.ai.data.Conversation
import com.omnitask.ai.data.DEFAULT_AGENT_ID
import com.omnitask.ai.data.Presets
import com.omnitask.ai.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var config by remember { mutableStateOf(Store.loadConfig(ctx)) }
    var agents by remember { mutableStateOf(Store.loadAgents(ctx)) }
    var activeAgentId by remember { mutableStateOf(Store.getActiveAgentId(ctx)) }
    var conversations by remember { mutableStateOf(Store.loadConversations(ctx)) }
    var activeConvId by remember { mutableStateOf(Store.getActiveConvId(ctx)) }
    var screen by remember { mutableStateOf("chat") }
    var editingAgent by remember { mutableStateOf<Agent?>(null) }

    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    // Restore the last open conversation
    LaunchedEffect(Unit) {
        val cid = activeConvId
        val conv = conversations.firstOrNull { it.id == cid }
        if (conv != null) {
            messages.addAll(conv.messages)
            activeAgentId = conv.agentId
        }
    }

    val activeAgent: Agent = agents.firstOrNull { it.id == activeAgentId } ?: agents.first()

    fun effectiveCfg(agent: Agent): AppConfig =
        if (agent.providerId.isNotBlank() && agent.baseUrl.isNotBlank()) {
            AppConfig(
                providerId = agent.providerId,
                baseUrl = agent.baseUrl,
                model = agent.model,
                apiKey = agent.apiKey,
                autoExecute = config.autoExecute
            )
        } else config

    fun persistConversation() {
        val cid = activeConvId ?: return
        val old = conversations.firstOrNull { it.id == cid }
        val conv = Conversation(
            id = cid,
            agentId = old?.agentId ?: activeAgent.id,
            title = old?.title ?: "New chat",
            messages = messages.toList(),
            updatedAt = System.currentTimeMillis()
        )
        conversations = if (old == null) conversations + conv
        else conversations.map { if (it.id == cid) conv else it }
        Store.saveConversations(ctx, conversations)
        Store.setActiveConvId(ctx, cid)
    }

    fun newChat() {
        messages.clear()
        activeConvId = null
        Store.setActiveConvId(ctx, null)
    }

    fun selectAgent(a: Agent) {
        activeAgentId = a.id
        Store.setActiveAgentId(ctx, a.id)
        newChat()
    }

    fun openConversation(c: Conversation) {
        activeConvId = c.id
        activeAgentId = c.agentId
        Store.setActiveConvId(ctx, c.id)
        Store.setActiveAgentId(ctx, c.agentId)
        messages.clear()
        messages.addAll(c.messages)
    }

    fun deleteConversation(c: Conversation) {
        conversations = conversations.filterNot { it.id == c.id }
        Store.saveConversations(ctx, conversations)
        if (activeConvId == c.id) newChat()
    }

    fun sendMessage(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val agent = agents.firstOrNull { it.id == activeAgentId } ?: agents.first()
        val cfg = effectiveCfg(agent)
        if (activeConvId == null) {
            val conv = Conversation(agentId = agent.id, title = t.take(40))
            conversations = conversations + conv
            activeConvId = conv.id
            Store.setActiveConvId(ctx, conv.id)
        }
        messages.add(ChatMessage(role = "user", content = t))
        busy = true
        errorText = null
        val prompt = AiClient.systemPromptFor(agent)
        scope.launch {
            try {
                val history = messages.map { ChatMessage(id = it.id, role = it.role, content = it.content) }
                val streamMsg = ChatMessage(role = "assistant", content = "")
                messages.add(streamMsg)
                val reply = try {
                    withContext(Dispatchers.IO) {
                        AiClient.chatStream(cfg, prompt, history) { delta ->
                            val i = messages.indexOfFirst { it.id == streamMsg.id }
                            if (i >= 0) {
                                messages[i] = messages[i].copy(content = messages[i].content + delta)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Streaming failed before producing anything - retry without streaming.
                    val i = messages.indexOfFirst { it.id == streamMsg.id }
                    if (i >= 0 && messages[i].content.isBlank()) messages.removeAt(i)
                    withContext(Dispatchers.IO) { AiClient.chatBlocking(cfg, prompt, history) }
                }
                val actions = ActionParser.parse(reply)
                val clean = ActionParser.strip(reply)
                var results: List<String> = emptyList()
                var executed = false
                if (actions != null && cfg.autoExecute) {
                    results = withContext(Dispatchers.IO) { ActionExecutor.executeAll(ctx, actions) }
                    executed = true
                }
                val finalMsg = ChatMessage(
                    id = streamMsg.id,
                    role = "assistant",
                    content = clean.ifBlank { "Done." },
                    actionsJson = actions?.toString(),
                    executed = executed,
                    results = results
                )
                val i = messages.indexOfFirst { it.id == streamMsg.id }
                if (i >= 0) messages[i] = finalMsg else messages.add(finalMsg)
                persistConversation()
            } catch (e: Exception) {
                errorText = e.message ?: "Something went wrong"
                messages.removeAll {
                    it.role == "assistant" && it.content.isBlank() &&
                        it.actionsJson == null && it.results.isEmpty()
                }
                persistConversation()
            } finally {
                busy = false
            }
        }
    }

    fun runActions(msg: ChatMessage) {
        scope.launch {
            val arr = ActionParser.fromJson(msg.actionsJson) ?: return@launch
            val res = withContext(Dispatchers.IO) { ActionExecutor.executeAll(ctx, arr) }
            val i = messages.indexOfFirst { it.id == msg.id }
            if (i >= 0) {
                messages[i] = messages[i].copy(executed = true, results = res)
                persistConversation()
            }
        }
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

        "agents" -> AgentsScreen(
            agents = agents,
            onBack = { screen = "chat" },
            onEdit = { a ->
                editingAgent = a
                screen = "agentEdit"
            },
            onCreate = {
                editingAgent = null
                screen = "agentEdit"
            },
            onUse = { a ->
                selectAgent(a)
                screen = "chat"
            }
        )

        "agentEdit" -> AgentEditScreen(
            initial = editingAgent,
            onBack = { screen = "agents" },
            onSave = { a ->
                agents = if (agents.any { it.id == a.id }) agents.map { if (it.id == a.id) a else it }
                else agents + a
                Store.saveAgents(ctx, agents)
                screen = "agents"
            },
            onDelete = { a ->
                agents = agents.filterNot { it.id == a.id }
                if (activeAgentId == a.id) {
                    activeAgentId = DEFAULT_AGENT_ID
                    Store.setActiveAgentId(ctx, DEFAULT_AGENT_ID)
                }
                Store.saveAgents(ctx, agents)
                screen = "agents"
            }
        )

        else -> {
            val agentOverride = activeAgent.providerId.isNotBlank()
            val providerName = Presets.nameOf(if (agentOverride) activeAgent.providerId else config.providerId)
            val model = if (agentOverride) activeAgent.model else config.model
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    OmniDrawer(
                        agents = agents,
                        activeAgentId = activeAgent.id,
                        conversations = conversations,
                        onNewChat = {
                            newChat()
                            scope.launch { drawerState.close() }
                        },
                        onSelectAgent = {
                            selectAgent(it)
                            scope.launch { drawerState.close() }
                        },
                        onManageAgents = {
                            screen = "agents"
                            scope.launch { drawerState.close() }
                        },
                        onOpenConversation = {
                            openConversation(it)
                            scope.launch { drawerState.close() }
                        },
                        onDeleteConversation = { deleteConversation(it) },
                        onOpenSettings = {
                            screen = "settings"
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                ChatScreen(
                    agent = activeAgent,
                    providerLabel = providerName,
                    model = model,
                    messages = messages,
                    busy = busy,
                    errorText = errorText,
                    onSend = { sendMessage(it) },
                    onRunActions = { runActions(it) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = { newChat() }
                )
            }
        }
    }
}
