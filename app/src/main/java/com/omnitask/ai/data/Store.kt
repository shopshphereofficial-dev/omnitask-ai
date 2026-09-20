package com.omnitask.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val PREFS = "omnitask_prefs"
    private const val KEY_CFG = "config"
    private const val KEY_AGENTS = "agents"
    private const val KEY_CONVS = "conversations"
    private const val KEY_SCHEDS = "schedules"
    private const val KEY_ACTIVE_AGENT = "active_agent"
    private const val KEY_ACTIVE_CONV = "active_conv"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- config ----------

    fun saveConfig(ctx: Context, cfg: AppConfig) {
        val o = JSONObject()
            .put("providerId", cfg.providerId)
            .put("baseUrl", cfg.baseUrl)
            .put("model", cfg.model)
            .put("apiKey", cfg.apiKey)
            .put("autoExecute", cfg.autoExecute)
        prefs(ctx).edit().putString(KEY_CFG, o.toString()).apply()
    }

    fun loadConfig(ctx: Context): AppConfig {
        val s = prefs(ctx).getString(KEY_CFG, null) ?: return AppConfig()
        return try {
            val o = JSONObject(s)
            AppConfig(
                providerId = o.optString("providerId", "openai"),
                baseUrl = o.optString("baseUrl", ""),
                model = o.optString("model", ""),
                apiKey = o.optString("apiKey", ""),
                autoExecute = o.optBoolean("autoExecute", true)
            )
        } catch (e: Exception) {
            AppConfig()
        }
    }

    // ---------- agents ----------

    fun saveAgents(ctx: Context, agents: List<Agent>) {
        val arr = JSONArray()
        agents.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("emoji", a.emoji)
                    .put("systemPrompt", a.systemPrompt)
                    .put("providerId", a.providerId)
                    .put("baseUrl", a.baseUrl)
                    .put("model", a.model)
                    .put("apiKey", a.apiKey)
                    .put("isDefault", a.isDefault)
            )
        }
        prefs(ctx).edit().putString(KEY_AGENTS, arr.toString()).apply()
    }

    fun loadAgents(ctx: Context): List<Agent> {
        val s = prefs(ctx).getString(KEY_AGENTS, null)
        val list = try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Agent(
                    id = o.optString("id"),
                    name = o.optString("name", "Agent"),
                    emoji = o.optString("emoji", "🤖"),
                    systemPrompt = o.optString("systemPrompt", ""),
                    providerId = o.optString("providerId", ""),
                    baseUrl = o.optString("baseUrl", ""),
                    model = o.optString("model", ""),
                    apiKey = o.optString("apiKey", ""),
                    isDefault = o.optBoolean("isDefault", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        return if (list.any { it.id == DEFAULT_AGENT_ID }) {
            list
        } else {
            listOf(defaultAgent()) + list
        }
    }

    private fun defaultAgent() = Agent(
        id = DEFAULT_AGENT_ID,
        name = "Assistant",
        emoji = "🤖",
        systemPrompt = "",
        isDefault = true
    )

    // ---------- conversations ----------

    fun saveConversations(ctx: Context, convs: List<Conversation>) {
        val arr = JSONArray()
        convs.forEach { c ->
            val msgs = JSONArray()
            c.messages.forEach { m ->
                msgs.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("role", m.role)
                        .put("content", m.content)
                        .put("ts", m.ts)
                        .put("actionsJson", m.actionsJson ?: JSONObject.NULL)
                        .put("executed", m.executed)
                        .put("results", JSONArray(m.results))
                )
            }
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("agentId", c.agentId)
                    .put("title", c.title)
                    .put("updatedAt", c.updatedAt)
                    .put("messages", msgs)
            )
        }
        prefs(ctx).edit().putString(KEY_CONVS, arr.toString()).apply()
    }

    fun loadConversations(ctx: Context): List<Conversation> {
        val s = prefs(ctx).getString(KEY_CONVS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val mArr = o.optJSONArray("messages") ?: JSONArray()
                val messages = (0 until mArr.length()).map { j ->
                    val mo = mArr.getJSONObject(j)
                    ChatMessage(
                        id = mo.optLong("id", j.toLong()),
                        role = mo.optString("role", "assistant"),
                        content = mo.optString("content", ""),
                        ts = mo.optLong("ts", 0L),
                        actionsJson = if (mo.isNull("actionsJson")) null else mo.optString("actionsJson"),
                        executed = mo.optBoolean("executed", false),
                        results = mo.optJSONArray("results")?.let { r ->
                            (0 until r.length()).map { k -> r.optString(k) }
                        } ?: emptyList()
                    )
                }
                Conversation(
                    id = o.optString("id"),
                    agentId = o.optString("agentId", DEFAULT_AGENT_ID),
                    title = o.optString("title", "New chat"),
                    messages = messages,
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- schedules ----------

    fun saveSchedules(ctx: Context, schedules: List<Schedule>) {
        val arr = JSONArray()
        schedules.forEach { s ->
            val o = JSONObject()
                .put("id", s.id)
                .put("label", s.label)
                .put("minute", s.minute)
                .put("actionsJson", s.actionsJson)
                .put("agentId", s.agentId)
            s.hour?.let { o.put("hour", it) }
            s.intervalMinutes?.let { o.put("intervalMinutes", it) }
            s.days?.let { days ->
                val d = JSONArray()
                days.forEach { d.put(it) }
                o.put("days", d)
            }
            arr.put(o)
        }
        prefs(ctx).edit().putString(KEY_SCHEDS, arr.toString()).apply()
    }

    fun loadSchedules(ctx: Context): List<Schedule> {
        val s = prefs(ctx).getString(KEY_SCHEDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Schedule(
                    id = o.optString("id"),
                    label = o.optString("label", "Scheduled task"),
                    hour = if (o.isNull("hour")) null else o.optInt("hour", 0),
                    minute = o.optInt("minute", 0),
                    days = o.optJSONArray("days")?.let { d ->
                        (0 until d.length()).map { k -> d.optInt(k) }
                    },
                    intervalMinutes = if (o.isNull("intervalMinutes")) null else o.optInt("intervalMinutes", 0),
                    actionsJson = o.optString("actionsJson", "[]"),
                    agentId = o.optString("agentId", DEFAULT_AGENT_ID)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- active selections ----------

    fun getActiveAgentId(ctx: Context): String =
        prefs(ctx).getString(KEY_ACTIVE_AGENT, DEFAULT_AGENT_ID) ?: DEFAULT_AGENT_ID

    fun setActiveAgentId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE_AGENT, id).apply()
    }

    fun getActiveConvId(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACTIVE_CONV, null)

    fun setActiveConvId(ctx: Context, id: String?) {
        prefs(ctx).edit().apply {
            if (id == null) remove(KEY_ACTIVE_CONV) else putString(KEY_ACTIVE_CONV, id)
            apply()
        }
    }
}
