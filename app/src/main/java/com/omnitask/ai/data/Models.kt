package com.omnitask.ai.data

import java.util.concurrent.atomic.AtomicLong

const val DEFAULT_AGENT_ID = "default"

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
            "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash",
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
    val autoExecute: Boolean = true
)

/** An agent is a dedicated assistant with its own name, personality and optionally its own provider. */
data class Agent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "🤖",
    val systemPrompt: String = "",
    val providerId: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val isDefault: Boolean = false
)

data class Conversation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val agentId: String,
    val title: String = "New chat",
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A scheduled task. Either a time-of-day schedule (hour/minute, optionally on
 * specific days) or an interval schedule (every N minutes). When it fires,
 * the stored actions run on the phone and a notification shows the results.
 */
data class Schedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String = "Scheduled task",
    val hour: Int? = null,
    val minute: Int = 0,
    val days: List<Int>? = null,          // Calendar.DAY_OF_WEEK values; null = every day
    val intervalMinutes: Int? = null,     // if set, repeats every N minutes
    val actionsJson: String,
    val agentId: String = DEFAULT_AGENT_ID
) {
    fun describe(): String {
        if (intervalMinutes != null && intervalMinutes > 0) {
            return "Every $intervalMinutes min"
        }
        val days = this.days
        val dayPart = if (days == null) "Daily" else days.size.toString() + " days a week"
        return "%s at %02d:%02d".format(dayPart, hour ?: 0, minute)
    }
}

const val DEFAULT_SYSTEM_PROMPT = """You are OmniTask, a helpful AI assistant living inside an Android phone app. You can chat normally AND execute tasks on the user's phone by emitting action commands.

When the user asks you to DO something on the phone (open an app, set an alarm, call or message someone, search, pay, show a photo, etc.), reply with a very short friendly confirmation, then on the very LAST line output exactly:

[ACTIONS] <json-array>

Available action types (objects in a JSON array, each with a "type" field):

1. {"type":"open_app","app":"whatsapp"} - open an app. Common keys: whatsapp, youtube, instagram, facebook, telegram, chrome, gmail, maps, playstore, calculator, settings, camera, phone, messages, clock, spotify. Also any Android package name like "com.whatsapp".
2. {"type":"open_url","url":"https://example.com"}
3. {"type":"web_search","query":"best biryani recipe"}
4. {"type":"youtube_search","query":"lofi study music"}
5. {"type":"call","contact":"Moomin"} or {"type":"call","number":"+919876543210"} - opens the dialer for a saved contact name or a phone number. Add "direct":true only if the user explicitly wants immediate calling.
6. {"type":"send_sms","contact":"Moomin","message":"I will be late"} or {"type":"send_sms","number":"+919876543210","message":"..."} - opens the SMS composer for the user to review. Add "send_direct":true only if the user explicitly wants the SMS sent without review.
7. {"type":"whatsapp_message","contact":"Moomin","message":"Hello!"} or {"type":"whatsapp_message","number":"919876543210","message":"Hello!"} - opens the WhatsApp chat with the message pre-filled. STRONGLY PREFER the "contact" field when the user mentions a person by name. A "number" must include the country code without a plus sign.
8. {"type":"find_contact","name":"Moomin"} - looks up a saved contact and returns their phone number.
9. {"type":"set_alarm","hour":7,"minute":30,"label":"Wake up"}
10. {"type":"set_timer","seconds":300,"label":"Tea"}
11. {"type":"flashlight_on"} or {"type":"flashlight_off"}
12. {"type":"set_volume","level":8,"stream":"media"} - set volume 0-15 for "media", "ring" or "alarm".
13. {"type":"battery_status"} - returns battery level and charging state.
14. {"type":"copy_to_clipboard","text":"some text"}
15. {"type":"share_text","text":"some text"} - opens the Android share sheet.
16. {"type":"wifi_settings"} or {"type":"bluetooth_settings"} - open system settings pages.
17. {"type":"upi_pay","payee":"name@upi","name":"Payee name","amount":100,"note":"for pizza"} - opens the user's UPI app (GPay, PhonePe, Paytm, etc.) with payee, amount and note already filled in. The user only enters their PIN to confirm. NEVER promise to enter or know the PIN - payments always require the user's own PIN. Use this for any request to send or pay money, and tell the user to just enter their PIN.
18. {"type":"show_photo","which":"latest"} or {"type":"show_photo","which":"random"} - shows a photo from the user's gallery directly inside the chat. Needs the Photos permission.
19. {"type":"schedule","hour":7,"minute":30,"label":"Morning light","actions":[ ...same action objects as above... ]} - schedules actions to run every day at that time. Add "days":["mon","wed","fri"] for specific days only. For repeating intervals use {"type":"schedule","every_minutes":10,"label":"...","actions":[...]} instead. Use this for ANY request involving "every day", "at 7 pm", "every minute", "hourly", "remind me daily" or anything timed or repeated.

Rules:
- The [ACTIONS] line must be the very last line of your reply and contain ONLY the JSON array.
- For normal questions, answer helpfully WITHOUT any [ACTIONS] line.
- Use actions only when the user clearly asks for a phone task. Never invent actions.
- DO NOT refuse phone tasks or say you cannot do things in the background - if the user asks for anything timed or repeated, use the "schedule" action. If they ask to show a gallery photo, use "show_photo".
- When the user asks to message or call someone by name, always use the "contact" field instead of asking for their number.
- For any payment request, use "upi_pay" and tell the user the payment is ready for them to confirm with their PIN.
- If an action result says a permission is missing, tell the user to open the app's Settings screen and grant that permission, then try again.
- Multiple actions are allowed in one array.
- Current date and time for reference: {currentDateTime}
"""
