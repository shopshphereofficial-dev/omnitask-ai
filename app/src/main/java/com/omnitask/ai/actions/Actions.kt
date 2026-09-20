package com.omnitask.ai.actions

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extracts the [ACTIONS] block that the AI appends to its reply.
 */
object ActionParser {
    private const val TAG = "[ACTIONS]"

    fun parse(reply: String): JSONArray? {
        val idx = reply.lastIndexOf(TAG)
        if (idx < 0) return null
        var rest = reply.substring(idx + TAG.length).trim()
        rest = rest.removePrefix("```json").removePrefix("```").trim()
        val end = rest.lastIndexOf(']')
        if (end < 0) return null
        rest = rest.substring(0, end + 1)
        return try {
            JSONArray(rest)
        } catch (e: Exception) {
            null
        }
    }

    fun fromJson(s: String?): JSONArray? {
        if (s == null) return null
        return try {
            JSONArray(s)
        } catch (e: Exception) {
            null
        }
    }

    fun strip(reply: String): String {
        val idx = reply.indexOf(TAG)
        return if (idx < 0) reply.trim() else reply.substring(0, idx).trim()
    }
}

/**
 * Executes AI-requested actions on the phone using standard Android intents.
 * Everything that needs a sensitive permission gracefully falls back to a
 * user-reviewable intent (dialer / SMS composer) when the permission is missing.
 */
object ActionExecutor {

    private val APP_PACKAGES: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube"),
        "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "telegram" to listOf("org.telegram.messenger", "org.telegram.plus"),
        "chrome" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "spotify" to listOf("com.spotify.music"),
        "calculator" to listOf(
            "com.google.android.calculator",
            "com.miui.calculator",
            "com.sec.android.app.popupcalculator"
        ),
        "chatgpt" to listOf("com.openai.chatgpt"),
        "snapchat" to listOf("com.snapchat.android"),
        "zoom" to listOf("us.zoom.videomeetings"),
        "linkedin" to listOf("com.linkedin.android"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "threads" to listOf("com.instagram.barcelona")
    )

    fun executeAll(ctx: Context, actions: JSONArray): List<String> {
        val results = ArrayList<String>()
        for (i in 0 until actions.length()) {
            try {
                val o = actions.getJSONObject(i)
                results.add(execute(ctx, o))
            } catch (e: Exception) {
                results.add("Failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return results
    }

    fun execute(ctx: Context, o: JSONObject): String {
        return when (o.optString("type").trim().lowercase()) {
            "open_app" -> openApp(ctx, o)
            "open_url" -> {
                val u = o.optString("url")
                openUrl(ctx, u)
                "Opened $u"
            }
            "web_search" -> {
                val q = o.optString("query")
                webSearch(ctx, q)
                "Searching for \"$q\""
            }
            "youtube_search" -> {
                val q = o.optString("query")
                openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))
                "Searching YouTube for \"$q\""
            }
            "call" -> call(ctx, o)
            "send_sms" -> sendSms(ctx, o)
            "whatsapp_message" -> whatsapp(ctx, o)
            "set_alarm" -> setAlarm(ctx, o)
            "set_timer" -> setTimer(ctx, o)
            "flashlight_on" -> torch(ctx, true)
            "flashlight_off" -> torch(ctx, false)
            "copy_to_clipboard" -> {
                copy(ctx, o.optString("text"))
                "Copied to clipboard"
            }
            "share_text" -> {
                share(ctx, o.optString("text"))
                "Opened the share sheet"
            }
            "wifi_settings" -> {
                ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opened Wi-Fi settings"
            }
            "bluetooth_settings" -> {
                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opened Bluetooth settings"
            }
            else -> "Unknown action type \"${o.optString("type")}\""
        }
    }

    private fun openApp(ctx: Context, o: JSONObject): String {
        val app = o.optString("app").trim()
        if (app.isEmpty()) return "open_app failed: no app given"
        val key = app.lowercase()

        when (key) {
            "settings" -> {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opened Settings"
            }
            "phone", "dialer" -> {
                ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Opened the dialer"
            }
            "camera" -> {
                ctx.startActivity(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return "Opened the camera"
            }
            "messages", "sms" -> {
                ctx.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return "Opened Messages"
            }
            "clock" -> {
                ctx.startActivity(
                    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return "Opened the clock"
            }
        }

        val pm = ctx.packageManager
        val candidates = APP_PACKAGES[key] ?: listOf(app)
        for (pkg in candidates) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launch)
                return "Opened $pkg"
            }
        }

        // Fallback: search installed apps by display label
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(main, 0)
        for (info in matches) {
            val label = info.loadLabel(pm).toString().lowercase()
            if (label == key || label.contains(key)) {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return "Opened \"${info.loadLabel(pm)}\""
            }
        }
        return "Could not find an app called \"$app\""
    }

    private fun call(ctx: Context, o: JSONObject): String {
        val number = o.optString("number").trim()
        if (number.isEmpty()) return "call failed: no number given"
        val action = if (o.optBoolean("direct", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        ) Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(
            Intent(action, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return if (action == Intent.ACTION_CALL) "Calling $number…" else "Opened the dialer for $number"
    }

    private fun sendSms(ctx: Context, o: JSONObject): String {
        val number = o.optString("number").trim()
        val message = o.optString("message")
        if (number.isEmpty()) return "send_sms failed: no number given"
        if (o.optBoolean("send_direct", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        ) {
            val sm: SmsManager =
                if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                else SmsManager.getDefault()
            sm.sendTextMessage(number, null, message, null, null)
            return "SMS sent to $number"
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
            .putExtra("sms_body", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Opened SMS to $number - review and press send"
    }

    private fun whatsapp(ctx: Context, o: JSONObject): String {
        val number = o.optString("number").replace(Regex("[^0-9]"), "")
        val message = o.optString("message", "")
        if (number.isEmpty()) return "whatsapp_message failed: no number given"
        val uri = "https://wa.me/$number" + if (message.isNotEmpty()) "?text=" + Uri.encode(message) else ""
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            i.setPackage("com.whatsapp")
            ctx.startActivity(i)
            "Opened WhatsApp chat for +$number"
        } catch (e: Exception) {
            try {
                i.setPackage(null)
                ctx.startActivity(i)
                "Opened WhatsApp link for +$number"
            } catch (e2: Exception) {
                "WhatsApp is not installed"
            }
        }
    }

    private fun setAlarm(ctx: Context, o: JSONObject): String {
        val hour = o.optInt("hour", -1)
        val minute = o.optInt("minute", 0)
        if (hour !in 0..23) return "set_alarm failed: hour missing"
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label", "OmniTask alarm"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Alarm set for %02d:%02d".format(hour, minute)
    }

    private fun setTimer(ctx: Context, o: JSONObject): String {
        val seconds = o.optInt("seconds", 0)
        if (seconds <= 0) return "set_timer failed: seconds missing"
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label", "Timer"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        val m = seconds / 60
        val s = seconds % 60
        return "Timer set for " + (if (m > 0) "${m}m " else "") + "${s}s"
    }

    private fun torch(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cm.cameraIdList) {
            val ch = cm.getCameraCharacteristics(id)
            if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                cm.setTorchMode(id, on)
                return if (on) "Flashlight ON" else "Flashlight OFF"
            }
        }
        return "No flashlight available on this device"
    }

    private fun copy(ctx: Context, text: String) {
        val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("OmniTask", text))
    }

    private fun share(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(Intent.createChooser(i, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun webSearch(ctx: Context, query: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            openUrl(ctx, "https://www.google.com/search?q=" + Uri.encode(query))
        }
    }

    private fun openUrl(ctx: Context, url: String) {
        var u = url.trim()
        if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
