package com.omnitask.ai.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One tool call the model asked for. */
data class ToolCall(val id: String, val name: String, val arguments: String)

/** A model reply: the short visible text plus any tool calls it wants run. */
data class ChatResult(val content: String, val toolCalls: List<ToolCall>)

/**
 * OpenAI-compatible chat client that uses real function calling. Works with
 * OpenAI, Grok (xAI), Gemini (OpenAI-compatible endpoint), DeepSeek, Kimi,
 * Sarvam, Ollama and any other provider that implements POST {baseUrl}/chat/completions.
 *
 * Two tools are offered on every request so the reply is structured instead of
 * free text: run_actions (do anything on the phone or GitHub) and wait_for_build
 * (wait for a GitHub build to finish). If a provider rejects tools, the caller
 * retries once with useTools = false and the model falls back to writing an
 * [ACTIONS] block, which ActionParser understands.
 */
object AiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun systemPromptFor(agent: Agent): String =
        agent.systemPrompt.trim().ifEmpty { DEFAULT_SYSTEM_PROMPT }

    /** The two tools, kept deliberately small so every provider accepts the schema. */
    fun toolsJson(): JSONArray {
        val runParams = JSONObject()
            .put(
                "type", "object"
            )
            .put(
                "properties",
                JSONObject().put(
                    "actions",
                    JSONObject()
                        .put("type", "array")
                        .put("description", "The actions to run, in order. Each item is an object with a \"type\" field, exactly as listed in the system prompt.")
                        .put("items", JSONObject().put("type", "object"))
                )
            )
            .put("required", JSONArray().put("actions"))

        val waitParams = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "repo",
                    JSONObject()
                        .put("type", "string")
                        .put("description", "Repository as owner/name. Optional when a default repository is set in Settings.")
                )
            )

        return JSONArray()
            .put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject()
                        .put("name", "run_actions")
                        .put("description", "Run phone or GitHub actions for the user. Use this for every task - never write commands or code in your reply.")
                        .put("parameters", runParams)
                )
            )
            .put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject()
                        .put("name", "wait_for_build")
                        .put("description", "Wait for the newest GitHub Actions build of a repository to finish (this can take several minutes) and return its final result and the APK link. Call this ONCE after starting a build - never poll the build status repeatedly.")
                        .put("parameters", waitParams)
                )
            )
    }

    /**
     * One chat turn. With [useTools] the reply may contain structured tool calls;
     * without it the model is expected to write an [ACTIONS] block in its text.
     */
    fun chat(cfg: AppConfig, systemPrompt: String, history: List<ChatMessage>, useTools: Boolean): ChatResult {
        val body = buildBody(cfg, systemPrompt, history, useTools)
        val request = buildRequest(cfg, body)
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}: " + text.take(400))
            }
            return parseReply(text)
        }
    }

    /** Text-only chat, used by the connection test in Settings. */
    fun chatBlocking(cfg: AppConfig, systemPrompt: String, history: List<ChatMessage>): String =
        chat(cfg, systemPrompt, history, false).content

    private fun parseReply(text: String): ChatResult {
        val json = JSONObject(text)
        val msg = json.getJSONArray("choices").getJSONObject(0).optJSONObject("message")
            ?: return ChatResult("", emptyList())
        val content = if (msg.isNull("content")) "" else msg.optString("content", "")
        val calls = ArrayList<ToolCall>()
        val arr = msg.optJSONArray("tool_calls")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val fn = c.optJSONObject("function") ?: continue
                // Some providers (Gemini included) send arguments as an object instead of a string.
                val raw = fn.opt("arguments")
                val args = when (raw) {
                    is JSONObject -> raw.toString()
                    is JSONArray -> raw.toString()
                    is String -> raw
                    else -> "{}"
                }
                calls.add(ToolCall(c.optString("id", "call_" + i), fn.optString("name"), args))
            }
        }
        return ChatResult(content, calls)
    }

    private fun buildBody(
        cfg: AppConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        useTools: Boolean
    ): JSONObject {
        val base = cfg.baseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "API Base URL is empty. Open Settings and configure your provider." }

        val now = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", systemPrompt.replace("{currentDateTime}", now))
        )
        history.takeLast(40).forEach { m ->
            val o = JSONObject().put("role", m.role)
            if (m.role == "assistant" && !m.toolCallsJson.isNullOrBlank()) {
                o.put("content", if (m.content.isNotBlank()) m.content else JSONObject.NULL)
                o.put("tool_calls", JSONArray(m.toolCallsJson))
            } else if (m.role == "tool") {
                o.put("content", m.content)
                if (!m.toolCallId.isNullOrBlank()) o.put("tool_call_id", m.toolCallId)
            } else {
                o.put("content", m.content)
            }
            messages.put(o)
        }

        val out = JSONObject()
            .put("model", cfg.model.trim())
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("stream", false)
        if (useTools) {
            out.put("tools", toolsJson())
            out.put("tool_choice", "auto")
        }
        return out
    }

    private fun buildRequest(cfg: AppConfig, body: JSONObject): Request =
        Request.Builder()
            .url(cfg.baseUrl.trim().trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer " + cfg.apiKey.trim())
            .post(body.toString().toRequestBody(JSON))
            .build()
}
