# OmniTask AI

An Android app that lets you give tasks to any AI using your own API key, and the AI can actually **do things on your phone** — open apps, set alarms, send messages, search the web and more.

## Supported providers (bring your own API key)

| Provider | Base URL (auto-filled) | Default model | Get a key |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | https://platform.openai.com/api-keys |
| Grok (xAI) | `https://api.x.ai/v1` | `grok-3-latest` | https://console.x.ai |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.0-flash` | https://aistudio.google.com/apikey |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | https://platform.deepseek.com |
| Kimi (Moonshot AI) | `https://api.moonshot.ai/v1` | `moonshot-v1-8k` | https://platform.moonshot.ai |
| Sarvam AI | `https://api.sarvam.ai/v1` | `sarvam-m` | https://dashboard.sarvam.ai |
| Ollama (free, local) | `http://localhost:11434/v1` | `llama3.2` | https://ollama.com |
| Custom | anything you like | anything you like | — |

The app speaks the **OpenAI-compatible `/chat/completions` API**, so any provider that supports it works, including self-hosted ones (Ollama, LM Studio, vLLM, OpenRouter, etc.). Model names in the table may become outdated — you can edit the model in Settings anytime.

## What the AI can do on your phone

- Open apps (WhatsApp, YouTube, Instagram, Telegram, any installed app by name)
- Open a URL, do a web search, search YouTube
- Open the dialer for a number / place a call (with permission)
- Compose an SMS (reviewed by you) or send it directly (with SMS permission)
- Open a WhatsApp chat with a pre-filled message
- Set alarms and timers
- Turn the flashlight on/off
- Copy text to the clipboard, open the share sheet
- Open Wi-Fi / Bluetooth settings

Sensitive things (calls, SMS) fall back to a review screen unless you explicitly grant the permissions in Android settings. In Settings you can also turn off "Auto-execute actions" so every task needs a tap on **Run actions** before it happens.

## How to get the APK (no Android Studio needed)

1. This repository has a GitHub Actions workflow that builds the app on every push.
2. Go to the **Actions** tab → click the latest **Build Android APK** run → scroll to **Artifacts** → download `OmniTask-AI-debug-apk` and unzip it.
3. Copy `app-debug.apk` to your phone and install it (allow "install unknown apps" when asked).

## Build it yourself

```bash
# needs JDK 17 and Android SDK 34
gradle assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio (JDK 17) and press Run.

## Privacy

- Your API key is stored only on your phone (plain SharedPreferences; do not hand your phone to strangers).
- No analytics, no third-party servers, no tracking. The app talks **only** to the AI provider you configure.

## Tech

Kotlin, Jetpack Compose (Material 3), OkHttp, no other dependencies. Single-activity app, ~1300 lines of straightforward code.
