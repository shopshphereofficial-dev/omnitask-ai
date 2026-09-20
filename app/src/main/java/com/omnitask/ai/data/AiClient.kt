package com.omnitask.ai.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenAI-compatible chat client with streaming support. Works with
 * OpenAI, Grok (xAI), Gemini (via its OpenAI-compatible endpoint), DeepSeek,
 * Kimi/Moonshot, Sarvam, Ollama and any other provider that implements
 * POST {baseUrl}/chat/completions.
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

    /** Streaming chat. Calls onDelta with each chunk as it arrives, returns the full reply. */
    fun chatStream(
        cfg: AppConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        onDelta: (String) -> Unit
    ): String {
        val body = buildBody(cfg, systemPrompt, history, stream = true)
        val request = buildRequest(cfg, body)
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                throw RuntimeException("HTTP ${resp.code}: ${text.take(400)}")
            }
            val source = resp.body?.source() ?: throw IOException("Empty response body")
            val full = StringBuilder()
            var first = true
            var sawSse = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) continue
                if (!line.startsWith("data:")) {
                    // Some providers ignore "stream" and return one plain JSON object.
                    if (first && line.trim().startsWith("{")) {
                        return parseFullReply(line)
                    }
                    continue
                }
                first = false
                sawSse = true
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                try {
                    val j = JSONObject(data)
                    val delta = j.getJSONArray("choices").optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content")
                    if (!delta.isNullOrEmpty()) {
                        full.append(delta)
                        onDelta(delta)
                    }
                } catch (e: Exception) {
                    // skip malformed chunk
                }
            }
            if (full.isEmpty() && !sawSse) throw IOException("Empty response")
            return full.toString()
        }
    }

    /** Blocking (non-streaming) chat, used as a fallback. */
    fun chatBlocking(
        cfg: AppConfig,
        systemPrompt: String,
        history: List<ChatMessage>
    ): String {
        val body = buildBody(cfg, systemPrompt, history, stream = false)
        val request = buildRequest(cfg, body)
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}: ${text.take(400)}")
            }
            return parseFullReply(text)
        }
    }

    private fun parseFullReply(text: String): String {
        val json = JSONObject(text)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun buildBody(
        cfg: AppConfig,
        systemPrompt: String,
        history: List<ChatMessage>,
        stream: Boolean
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
        history.takeLast(24).forEach { m ->
            messages.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        return JSONObject()
            .put("model", cfg.model.trim())
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("stream", stream)
    }

    private fun buildRequest(cfg: AppConfig, body: JSONObject): Request =
        Request.Builder()
            .url(cfg.baseUrl.trim().trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${cfg.apiKey.trim()}")
            .post(body.toString().toRequestBody(JSON))
            .build()
}
