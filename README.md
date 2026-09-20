# OmniTask AI

A professional Android app that lets you give tasks to any AI using your own API key — and the AI actually **does things on your phone**: opens apps, sets alarms, messages your contacts on WhatsApp and SMS, makes calls and more.

## What's new in v2.0

- **Fresh ChatGPT-style UI** — clean chat interface, sidebar menu, streaming replies (text appears as it is generated)
- **Agents** — create multiple agents, each with its own name, emoji, personality and instructions; even give an agent its own AI provider
- **Chat history** — conversations are saved and listed in the sidebar
- **Contacts lookup** — "message Moomin on WhatsApp" now works; the AI finds the number from your contacts
- **Permissions screen** — grant Contacts/SMS/Phone from inside the app
- **More actions** — volume control, battery status
- Long-press any message to copy it

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

The app speaks the **OpenAI-compatible `/chat/completions` API**, so any provider that supports it works. Model names become outdated — you can always edit the model in Settings or in an agent.

## What the AI can do on your phone

- Open any installed app by name
- Call / SMS / WhatsApp **your contacts by name** (Contacts permission) — messages arrive pre-filled for you to send
- Open a URL, web search, YouTube search
- Set alarms and timers
- Flashlight on/off, volume control, battery status
- Copy to clipboard, share sheet, Wi-Fi / Bluetooth settings

## How to get the APK (no Android Studio needed)

1. Go to the **Releases** section of this repository and download `OmniTask-AI.apk`.
2. Copy it to your phone and install it (allow "install unknown apps" when asked). Google Play Protect may show a warning because the app is sideloaded — choose "More details" → "Install anyway".

## Build it yourself

```bash
# needs JDK 17 and Android SDK 34
gradle assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio (JDK 17) and press Run.

## Privacy

- Your API key is stored only on your phone.
- No analytics, no third-party servers, no tracking. The app talks **only** to the AI provider you configure.

## Tech

Kotlin, Jetpack Compose (Material 3), OkHttp, no other dependencies.
