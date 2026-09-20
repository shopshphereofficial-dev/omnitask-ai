package com.omnitask.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val PREFS = "omnitask_prefs"
    private const val KEY_CFG = "config"
    private const val KEY_MSGS = "messages"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveConfig(ctx: Context, cfg: AppConfig) {
        val o = JSONObject()
            .put("providerId", cfg.providerId)
            .put("baseUrl", cfg.baseUrl)
            .put("model", cfg.model)
            .put("apiKey", cfg.apiKey)
            .put("systemPrompt", cfg.systemPrompt)
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
                systemPrompt = o.optString("systemPrompt", DEFAULT_SYSTEM_PROMPT),
                autoExecute = o.optBoolean("autoExecute", true)
            )
        } catch (e: Exception) {
            AppConfig()
        }
    }

    fun saveMessages(ctx: Context, msgs: List<ChatMessage>) {
        val arr = JSONArray()
        msgs.forEach { m ->
            arr.put(
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
        prefs(ctx).edit().putString(KEY_MSGS, arr.toString()).apply()
    }

    fun loadMessages(ctx: Context): List<ChatMessage> {
        val s = prefs(ctx).getString(KEY_MSGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.optLong("id", i.toLong()),
                    role = o.optString("role", "assistant"),
                    content = o.optString("content", ""),
                    ts = o.optLong("ts", 0L),
                    actionsJson = if (o.isNull("actionsJson")) null else o.optString("actionsJson"),
                    executed = o.optBoolean("executed", false),
                    results = o.optJSONArray("results")?.let { r ->
                        (0 until r.length()).map { j -> r.optString(j) }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearMessages(ctx: Context) {
        prefs(ctx).edit().remove(KEY_MSGS).apply()
    }
}
