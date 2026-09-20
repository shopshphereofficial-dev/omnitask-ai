package com.omnitask.ai.actions

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.omnitask.ai.data.Schedule
import com.omnitask.ai.data.Store
import com.omnitask.ai.schedule.Scheduler
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

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
 * Sensitive permissions gracefully fall back to user-reviewable intents
 * (dialer / SMS composer) when they are not granted. Payments always open
 * the user's UPI app with everything filled in - the user enters their PIN.
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
            "find_contact" -> findContact(ctx, o)
            "upi_pay" -> upiPay(ctx, o)
            "show_photo" -> showPhoto(ctx, o)
            "schedule" -> scheduleTask(ctx, o)
            "set_alarm" -> setAlarm(ctx, o)
            "set_timer" -> setTimer(ctx, o)
            "flashlight_on" -> torch(ctx, true)
            "flashlight_off" -> torch(ctx, false)
            "set_volume" -> setVolume(ctx, o)
            "battery_status" -> battery(ctx)
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

    // ---------- contacts ----------

    private class Target(val label: String, val number: String, val error: String?)

    private fun hasContactsPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Looks up a contact by (partial) name. Returns display name to phone number. */
    private fun findContactNumber(ctx: Context, name: String): Pair<String, String>? {
        val lower = name.trim().lowercase()
        if (lower.isEmpty()) return null
        return try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                var best: Pair<String, String>? = null
                while (c.moveToNext()) {
                    val dn = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    val d = dn.lowercase()
                    if (d == lower) return Pair(dn, num)
                    if (best == null && d.contains(lower)) best = Pair(dn, num)
                }
                best
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Resolves a "number" or "contact" field from an action into a phone number. */
    private fun resolveTarget(ctx: Context, o: JSONObject): Target {
        val number = o.optString("number").trim()
        if (number.isNotEmpty()) {
            val label = o.optString("contact").trim().ifEmpty { number }
            return Target(label, number, null)
        }
        val contact = o.optString("contact").trim()
        if (contact.isEmpty()) return Target("", "", "no number or contact given")
        if (!hasContactsPermission(ctx)) {
            return Target(
                "", "",
                "Contacts permission not granted - grant it from the app's Settings screen and try again"
            )
        }
        val found = findContactNumber(ctx, contact)
            ?: return Target("", "", "could not find a contact named \"$contact\" - check the spelling")
        return Target(found.first, found.second, null)
    }

    private fun findContact(ctx: Context, o: JSONObject): String {
        val name = o.optString("name").ifBlank { o.optString("contact") }.trim()
        if (name.isEmpty()) return "find_contact failed: no name given"
        if (!hasContactsPermission(ctx)) {
            return "Contacts permission not granted - grant it from the app's Settings screen and try again"
        }
        val found = findContactNumber(ctx, name)
            ?: return "No contact found for \"$name\""
        return "Found: ${found.first} - ${found.second}"
    }

    // ---------- payments, photos, schedules ----------

    /** Opens the user's UPI app with payee, amount and note filled in. The user confirms with their PIN. */
    private fun upiPay(ctx: Context, o: JSONObject): String {
        val payee = o.optString("payee").trim()
        if (payee.isEmpty()) return "upi_pay failed: no payee UPI id given (like name@upi)"
        val name = o.optString("name", "Payee").ifBlank { "Payee" }
        val amount = o.optString("amount", "").trim()
        val note = o.optString("note", "").trim()
        var uriStr = "upi://pay?pa=" + Uri.encode(payee) + "&pn=" + Uri.encode(name) + "&cu=INR"
        if (amount.isNotEmpty()) uriStr += "&am=" + Uri.encode(amount)
        if (note.isNotEmpty()) uriStr += "&tn=" + Uri.encode(note)
        val uri = Uri.parse(uriStr)
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Payment ready in your UPI app - check the amount and enter your PIN to pay"
        } catch (e: Exception) {
            try {
                val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Pay with")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(chooser)
                "Payment ready - pick your UPI app and enter your PIN"
            } catch (e2: Exception) {
                "No UPI app found on this phone"
            }
        }
    }

    private fun showPhoto(ctx: Context, o: JSONObject): String {
        if (!hasPhotoPermission(ctx)) {
            return "Photo permission not granted - grant Photos access from the app's Settings screen and try again"
        }
        val which = o.optString("which", "latest").lowercase()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return try {
            val uri: Uri? = if (which == "random") {
                ctx.contentResolver.query(
                    collection, arrayOf(MediaStore.Images.Media._ID), null, null, null
                )?.use { c ->
                    if (c.count > 0) {
                        c.moveToPosition(Random.nextInt(c.count))
                        ContentUris.withAppendedId(collection, c.getLong(0))
                    } else null
                }
            } else {
                ctx.contentResolver.query(
                    collection, arrayOf(MediaStore.Images.Media._ID), null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )?.use { c ->
                    if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
                }
            }
            if (uri == null) "No photos found in the gallery" else "PHOTO:$uri"
        } catch (e: SecurityException) {
            "Photo permission not granted"
        }
    }

    private fun hasPhotoPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        else
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun scheduleTask(ctx: Context, o: JSONObject): String {
        val actions = o.optJSONArray("actions")
            ?: return "schedule failed: include an \"actions\" array with the tasks to run"
        if (actions.length() == 0) return "schedule failed: the actions array is empty"
        val label = o.optString("label", o.optString("task", "Scheduled task")).ifBlank { "Scheduled task" }
        val every = o.optInt("every_minutes", 0)
        val hour = o.optInt("hour", -1)
        val minute = o.optInt("minute", 0)
        val schedule: Schedule = if (every > 0) {
            Schedule(id = UUID.randomUUID().toString(), label = label, intervalMinutes = every, actionsJson = actions.toString())
        } else if (hour in 0..23) {
            Schedule(
                id = UUID.randomUUID().toString(),
                label = label,
                hour = hour,
                minute = minute,
                days = parseDays(o.optJSONArray("days")),
                actionsJson = actions.toString()
            )
        } else {
            return "schedule failed: give either hour/minute or every_minutes"
        }
        Store.saveSchedules(ctx, Store.loadSchedules(ctx) + schedule)
        Scheduler.armNext(ctx, schedule)
        return if (every > 0) {
            "Scheduled: $label (every $every minutes)"
        } else {
            "Scheduled: " + label + " - " + schedule.describe()
        }
    }

    private fun parseDays(arr: JSONArray?): List<Int>? {
        if (arr == null || arr.length() == 0) return null
        val map = mapOf(
            "sun" to 1, "mon" to 2, "tue" to 3, "wed" to 4,
            "thu" to 5, "fri" to 6, "sat" to 7,
            "sunday" to 1, "monday" to 2, "tuesday" to 3, "wednesday" to 4,
            "thursday" to 5, "friday" to 6, "saturday" to 7
        )
        val out = ArrayList<Int>()
        for (i in 0 until arr.length()) {
            map[arr.optString(i).lowercase().take(3)]?.let { out.add(it) }
        }
        return if (out.isEmpty()) null else out
    }

    // ---------- actions ----------

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
        val t = resolveTarget(ctx, o)
        if (t.error != null) return "call failed: ${t.error}"
        val action = if (o.optBoolean("direct", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        ) Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(
            Intent(action, Uri.parse("tel:" + Uri.encode(t.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return if (action == Intent.ACTION_CALL) "Calling ${t.label}…" else "Opened the dialer for ${t.label}"
    }

    private fun sendSms(ctx: Context, o: JSONObject): String {
        val t = resolveTarget(ctx, o)
        if (t.error != null) return "send_sms failed: ${t.error}"
        val message = o.optString("message")
        if (o.optBoolean("send_direct", false) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        ) {
            val sm: SmsManager =
                if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                else SmsManager.getDefault()
            sm.sendTextMessage(t.number, null, message, null, null)
            return "SMS sent to ${t.label}"
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(t.number)))
            .putExtra("sms_body", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Opened SMS to ${t.label} - review and press send"
    }

    private fun whatsapp(ctx: Context, o: JSONObject): String {
        val t = resolveTarget(ctx, o)
        if (t.error != null) return "whatsapp_message failed: ${t.error}"
        val message = o.optString("message", "")
        var digits = t.number.replace(Regex("[^0-9]"), "")
        // Local 10-digit numbers get a default country code so wa.me links work
        if (digits.length == 10) digits = "91$digits"
        if (digits.isEmpty()) return "whatsapp_message failed: no usable number for ${t.label}"
        val uri = "https://wa.me/$digits" + if (message.isNotEmpty()) "?text=" + Uri.encode(message) else ""
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            i.setPackage("com.whatsapp")
            ctx.startActivity(i)
            "Opened WhatsApp chat with ${t.label} ($digits)"
        } catch (e: Exception) {
            try {
                i.setPackage(null)
                ctx.startActivity(i)
                "Opened WhatsApp link for ${t.label}"
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

    private fun setVolume(ctx: Context, o: JSONObject): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamName = o.optString("stream", "media").lowercase()
        val stream = when (streamName) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(stream)
        val level = o.optInt("level", -1)
        if (level < 0 || level > max) return "set_volume failed: level must be 0-$max"
        am.setStreamVolume(stream, level, 0)
        return "Volume ($streamName) set to $level out of $max"
    }

    private fun battery(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return "Battery is at $level%" + if (charging) " and charging" else ""
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
