package com.omnitask.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.omnitask.ai.actions.ActionExecutor
import com.omnitask.ai.actions.ActionParser
import com.omnitask.ai.data.Agent
import com.omnitask.ai.data.AppConfig
import com.omnitask.ai.data.AiClient
import com.omnitask.ai.data.ChatMessage
import com.omnitask.ai.data.Conversation
import com.omnitask.ai.data.DEFAULT_AGENT_ID
import com.omnitask.ai.data.Presets
import com.omnitask.ai.data.ProviderPreset
import com.omnitask.ai.data.Store
import com.omnitask.ai.schedule.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var schedules by remember { mutableStateOf(Store.loadSchedules(ctx)) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Restore the last open conversation and ask for the notification permission
    LaunchedEffect(Unit) {
        val cid = activeConvId
        val conv = conversations.firstOrNull { it.id == cid }
        if (conv != null) {
            messages.addAll(conv.messages)
            activeAgentId = conv.agentId
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    fun quickSwitchProvider(p: ProviderPreset) {
        val newCfg = config.copy(
            providerId = p.id,
            baseUrl = if (p.baseUrl.isNotBlank()) p.baseUrl else config.baseUrl,
            model = if (p.defaultModel.isNotBlank()) p.defaultModel else config.model
        )
        config = newCfg
        Store.saveConfig(ctx, newCfg)
        val a = agents.firstOrNull { it.id == activeAgentId }
        if (a != null && a.providerId.isNotBlank()) {
            val na = a.copy(providerId = p.id, baseUrl = p.baseUrl, model = p.defaultModel)
            agents = agents.map { if (it.id == na.id) na else it }
            Store.saveAgents(ctx, agents)
        }
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
        if (activeConvId == null) {
            val conv = Conversation(agentId = agent.id, title = t.take(40))
            conversations = conversations + conv
            activeConvId = conv.id
            Store.setActiveConvId(ctx, conv.id)
        }
        messages.add(ChatMessage(role = "user", content = t))
        busy = true
        errorText = null
        status = "Thinking…"
        val prompt = AiClient.systemPromptFor(agent)

        job = scope.launch {
            // The whole conversation is the context, so it never forgets what it was working on.
            val history = ArrayList<ChatMessage>()
            messages.takeLast(40).forEach { m ->
                if (m.content.isNotBlank()) {
                    history.add(ChatMessage(role = m.role, content = m.content))
                }
            }
            if (history.isEmpty()) history.add(ChatMessage(role = "user", content = t))
            try {
                var step = 0
                val maxSteps = 20
                var didWork = false
                var nudged = false
                while (step < maxSteps) {
                    step++
                    val live = agents.firstOrNull { it.id == activeAgentId } ?: agent
                    val liveCfg = effectiveCfg(live)
                    val reply = withContext(Dispatchers.IO) {
                        AiClient.chatBlocking(liveCfg, prompt, history)
                    }
                    val actions = ActionParser.parse(reply)
                    val clean = ActionParser.stripCodeFences(ActionParser.strip(reply))
                        .ifBlank { "Working on it…" }
                    history.add(ChatMessage(role = "assistant", content = clean))

                    if (actions == null || actions.length() == 0) {
                        if (didWork && !nudged) {
                            // It stopped right after doing work - make sure it really finished.
                            nudged = true
                            messages.add(ChatMessage(role = "assistant", content = clean))
                            history.add(
                                ChatMessage(
                                    role = "user",
                                    content = "Double-check before you finish: is everything the user asked for really done and verified? " +
                                        "If anything is still missing, do it now. If it is all done, reply with one short line and no [ACTIONS] block."
                                )
                            )
                            continue
                        }
                        messages.add(ChatMessage(role = "assistant", content = clean))
                        break
                    }
                    if (!liveCfg.autoExecute) {
                        messages.add(
                            ChatMessage(
                                role = "assistant",
                                content = clean,
                                actionsJson = actions.toString()
                            )
                        )
                        break
                    }

                    status = if (step == 1) "Running actions…" else "Step $step…"
                    val results = withContext(Dispatchers.IO) { ActionExecutor.executeAll(ctx, actions) }
                    didWork = true
                    messages.add(
                        ChatMessage(
                            role = "assistant",
                            content = clean,
                            actionsJson = actions.toString(),
                            executed = true,
                            results = results
                        )
                    )
                    val toolText = "[ACTION RESULTS]\n" + results.joinToString("\n") +
                        "\n\nContinue the task now. If everything the user asked for is finished, reply with one short line and no [ACTIONS] block. If a step failed, fix it yourself and try again. Never paste code into the reply."
                    history.add(ChatMessage(role = "user", content = toolText))
                }
                if (step >= maxSteps) {
                    messages.add(
                        ChatMessage(
                            role = "assistant",
                            content = "I paused at my step limit. Say \"continue\" and I will carry on from here."
                        )
                    )
                }
                persistConversation()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    messages.add(ChatMessage(role = "assistant", content = "Stopped."))
                } else {
                    errorText = e.message ?: "Something went wrong"
                }
                persistConversation()
            } finally {
                busy = false
                status = ""
                job = null
            }
        }
    }

    fun stopTask() {
        job?.cancel()
        job = null
        busy = false
        status = ""
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

        "schedules" -> SchedulesScreen(
            schedules = schedules,
            onBack = { screen = "chat" },
            onDelete = { s ->
                Scheduler.cancel(ctx, s.id)
                schedules = schedules.filterNot { it.id == s.id }
                Store.saveSchedules(ctx, schedules)
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

        "github" -> GithubScreen(
            config = config,
            onBack = { screen = "chat" },
            onOpenSettings = { screen = "settings" }
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
                        onOpenSchedules = {
                            screen = "schedules"
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
                        },
                        onOpenGithub = {
                            screen = "github"
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
                    onNewChat = { newChat() },
                    onQuickProvider = { p -> quickSwitchProvider(p) },
                    status = status,
                    onStop = { stopTask() }
                )
            }
        }
    }
}
