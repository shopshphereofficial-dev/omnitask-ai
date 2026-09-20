# OmniTask AI

A professional Android app that lets you give tasks to any AI using your own API key — and the AI actually **does things on your phone**: opens apps, sets alarms, messages your contacts on WhatsApp and SMS, shows gallery photos in chat, prepares UPI payments, and runs tasks on a schedule. Voice input included.

## What's new in v3.0

- **Voice input** — tap the mic and speak your task, no typing needed
- **Scheduled tasks** — "every day at 7 AM turn on the flashlight", "every 30 minutes tell me the battery", "every Monday at 9 AM whatsapp Moomin good morning". Tasks run in the background, survive phone restarts, and show results as notifications
- **UPI payments (safe)** — "pay 150 rupees to name@upi" opens your UPI app with everything filled in; you just enter your PIN. The AI never sees or enters your PIN — that is UPI's security design
- **Gallery photos in chat** — "show my latest photo" displays it right inside the conversation (needs Photos permission)
- The AI is now instructed not to refuse background/timed tasks — it schedules them instead

## Also from v2.0

- ChatGPT-style UI with streaming replies, sidebar menu and chat history
- Agents — multiple assistants, each with its own personality and optionally its own AI provider
- Contacts lookup — message/call people by name
- In-app permissions screen

## Supported providers (bring your own API key)

| Provider | Base URL (auto-filled) | Default model | Get a key |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | https://platform.openai.com/api-keys |
| Grok (xAI) | `https://api.x.ai/v1` | `grok-3-latest` | https://console.x.ai |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.8-flash` | https://aistudio.google.com/apikey |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | https://platform.deepseek.com |
| Kimi (Moonshot AI) | `https://api.moonshot.ai/v1` | `moonshot-v1-8k` | https://platform.moonshot.ai |
| Sarvam AI | `https://api.sarvam.ai/v1` | `sarvam-m` | https://dashboard.sarvam.ai |
| Ollama (free, local) | `http://localhost:11434/v1` | `llama3.2` | https://ollama.com |
| Custom | anything you like | anything you like | — |

## How to get the APK

1. Go to **Releases** and download `OmniTask-AI.apk`.
2. Install it (allow "install unknown apps"). Play Protect may warn for sideloaded apps with SMS/call permissions — tap **More details → Install anyway**.

## Privacy

- Your API key is stored only on your phone.
- No analytics, no third-party servers, no tracking. The app talks **only** to the AI provider you configure.

## Tech

Kotlin, Jetpack Compose (Material 3), OkHttp, AlarmManager — no other dependencies.
