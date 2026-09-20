package com.omnitask.ai.data

import java.util.concurrent.atomic.AtomicLong

data class ChatMessage(
    val id: Long = nextId(),
    val role: String,
    val content: String,
    val ts: Long = System.currentTimeMillis(),
    val actionsJson: String? = null,
    val executed: Boolean = false,
    val results: List<String> = emptyList()
) {
    companion object {
        private val counter = AtomicLong(System.currentTimeMillis())
        fun nextId(): Long = counter.incrementAndGet()
    }
}

data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String
)

object Presets {
    val ALL = listOf(
        ProviderPreset(
            "openai", "OpenAI",
            "https://api.openai.com/v1", "gpt-4o-mini",
            "https://platform.openai.com/api-keys"
        ),
        ProviderPreset(
            "grok", "Grok (xAI)",
            "https://api.x.ai/v1", "grok-3-latest",
            "https://console.x.ai"
        ),
        ProviderPreset(
            "gemini", "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash",
            "https://aistudio.google.com/apikey"
        ),
        ProviderPreset(
            "deepseek", "DeepSeek",
            "https://api.deepseek.com/v1", "deepseek-chat",
            "https://platform.deepseek.com"
        ),
        ProviderPreset(
            "kimi", "Kimi (Moonshot AI)",
            "https://api.moonshot.ai/v1", "moonshot-v1-8k",
            "https://platform.moonshot.ai"
        ),
        ProviderPreset(
            "sarvam", "Sarvam AI",
            "https://api.sarvam.ai/v1", "sarvam-m",
            "https://dashboard.sarvam.ai"
        ),
        ProviderPreset(
            "ollama", "Ollama (free, local)",
            "http://localhost:11434/v1", "llama3.2",
            "https://ollama.com"
        ),
        ProviderPreset(
            "custom", "Custom (any OpenAI-compatible API)",
            "", "",
            ""
        )
    )

    fun byId(id: String): ProviderPreset? = ALL.firstOrNull { it.id == id }
    fun nameOf(id: String): String = byId(id)?.name ?: "Custom"
}

data class AppConfig(
    val providerId: String = "openai",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val apiKey: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val autoExecute: Boolean = true
)

const val DEFAULT_SYSTEM_PROMPT = """You are OmniTask, a helpful AI assistant running inside an Android phone app. You can chat normally AND execute tasks on the user's phone by emitting action commands.

When the user asks you to DO something on the phone (open an app, set an alarm, search, call, message, etc.), reply with a very short friendly confirmation, then on the very LAST line output exactly:

[ACTIONS] <json-array>

Each element of the JSON array is an object with a "type" field:

1. {"type":"open_app","app":"whatsapp"} - open an app. Common keys: whatsapp, youtube, instagram, facebook, telegram, chrome, gmail, maps, playstore, calculator, settings, camera, phone, messages, clock, spotify. You may also use a full Android package name like "com.whatsapp".
2. {"type":"open_url","url":"https://example.com"}
3. {"type":"web_search","query":"best biryani recipe"}
4. {"type":"youtube_search","query":"lofi study music"}
5. {"type":"call","number":"+919876543210"} - opens the phone dialer. Add "direct":true only if the user explicitly wants immediate calling.
6. {"type":"send_sms","number":"+919876543210","message":"I will be late"} - opens the SMS composer for the user to review. Add "send_direct":true only if the user explicitly wants the SMS sent without review.
7. {"type":"whatsapp_message","number":"919876543210","message":"Hello!"} - opens the WhatsApp chat with the message pre-filled. The number must include the country code without a plus sign.
8. {"type":"set_alarm","hour":7,"minute":30,"label":"Wake up"}
9. {"type":"set_timer","seconds":300,"label":"Tea"}
10. {"type":"flashlight_on"} or {"type":"flashlight_off"}
11. {"type":"copy_to_clipboard","text":"some text"}
12. {"type":"share_text","text":"some text"} - opens the Android share sheet.
13. {"type":"wifi_settings"} or {"type":"bluetooth_settings"} - open system settings pages.

Rules:
- The [ACTIONS] line must be the very last line of your reply and contain ONLY the JSON array.
- For normal questions and chat, answer helpfully WITHOUT any [ACTIONS] line.
- Use phone actions only when the user clearly asks for a phone task. Never invent actions for general questions.
- Multiple actions are allowed in one array.
- Current date and time for reference: {currentDateTime}
"""
