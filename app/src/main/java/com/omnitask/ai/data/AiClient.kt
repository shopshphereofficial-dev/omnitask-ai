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

/**
 * Minimal OpenAI-compatible chat client. Works with OpenAI, Grok (xAI), Gemini
 * (via its OpenAI-compatible endpoint), DeepSeek, Kimi/Moonshot, Sarvam, Ollama
 * and any other provider that implements POST {baseUrl}/chat/completions.
 */
object AiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun chat(cfg: AppConfig, history: List<ChatMessage>): String {
        val base = cfg.baseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "API Base URL is empty. Open Settings (gear icon) and configure your provider." }

        val now = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val messages = JSONArray()
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", cfg.systemPrompt.replace("{currentDateTime}", now))
        )
        history.takeLast(24).forEach { m ->
            messages.put(JSONObject().put("role", m.role).put("content", m.content))
        }

        val body = JSONObject()
            .put("model", cfg.model.trim())
            .put("messages", messages)
            .put("temperature", 0.7)

        val request = Request.Builder()
            .url("$base/chat/completions")
            .addHeader("Authorization", "Bearer ${cfg.apiKey.trim()}")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}: ${text.take(400)}")
            }
            val json = JSONObject(text)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
