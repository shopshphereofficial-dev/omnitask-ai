package com.omnitask.ai.actions

import android.Manifest
import android.app.ActivityManager
import android.app.SearchManager
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.CalendarContract
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
import java.util.Calendar
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
 * Executes AI-requested actions on the phone using standard Android APIs.
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
            "delete_photo" -> deletePhoto(ctx, o)
            "take_photo" -> takePhoto(ctx)
            "set_wallpaper" -> setWallpaper(ctx, o)
            "set_brightness" -> setBrightness(ctx, o)
            "screen_timeout" -> screenTimeout(ctx, o)
            "ringer_mode" -> ringerMode(ctx, o)
            "vibrate" -> vibrate(ctx, o)
            "send_email" -> sendEmail(ctx, o)
            "get_location" -> getLocation(ctx)
            "list_contacts" -> listContacts(ctx, o)
            "list_apps" -> listApps(ctx)
            "device_info" -> deviceInfo(ctx)
            "calendar_event" -> calendarEvent(ctx, o)
            "maps_search" -> {
                val q = o.optString("query")
                openUrl(ctx, "https://www.google.com/maps/search/" + Uri.encode(q))
                "Searching Maps for \"$q\""
            }
            "spotify_search" -> {
                val q = o.optString("query")
                openUrl(ctx, "https://open.spotify.com/search/" + Uri.encode(q))
                "Searching Spotify for \"$q\""
            }
            "wifi_panel" -> settingsPanel(ctx, "wifi", "Wi-Fi")
            "bluetooth_panel" -> {
                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opened Bluetooth settings"
            }
            "unschedule_all" -> {
                Store.loadSchedules(ctx).forEach { Scheduler.cancel(ctx, it.id) }
                Store.saveSchedules(ctx, emptyList())
                "All scheduled tasks cleared"
            }
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

    private fun queryPhotoUri(ctx: Context, which: String): Uri? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return try {
            if (which == "random") {
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
        } catch (e: SecurityException) {
            null
        }
    }

    private fun deletePhoto(ctx: Context, o: JSONObject): String {
        if (!hasPhotoPermission(ctx)) {
            return "Photo permission not granted - grant Photos access from the app's Settings screen and try again"
        }
        val which = o.optString("which", "latest").lowercase()
        val uri = queryPhotoUri(ctx, which) ?: return "No photos found in the gallery"
        return if (Build.VERSION.SDK_INT >= 29) {
            try {
                val pending = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(uri))
                ctx.startIntentSender(
                    pending.intentSender, null, 0, 0,
                    Intent.FLAG_ACTIVITY_NEW_TASK, null
                )
                "Opened the delete confirmation - tap Delete to remove the photo"
            } catch (e: Exception) {
                "Could not open the delete dialog"
            }
        } else {
            try {
                val deleted = ctx.contentResolver.delete(uri, null, null)
                if (deleted > 0) "Photo deleted" else "Photo not found"
            } catch (e: SecurityException) {
                "Photo deletion was not allowed"
            }
        }
    }

    private fun takePhoto(ctx: Context): String {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "OmniTask_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val uri = ctx.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return "Could not prepare the camera"
            val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                clipData = ClipData.newRawUri("photo", uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            "Camera opened - tap the shutter, the photo saves to the gallery automatically"
        } catch (e: Exception) {
            "No camera app found"
        }
    }

    private fun setWallpaper(ctx: Context, o: JSONObject): String {
        if (!hasPhotoPermission(ctx)) {
            return "Photo permission not granted - grant Photos access from the app's Settings screen and try again"
        }
        val which = o.optString("which", "latest").lowercase()
        val uri = queryPhotoUri(ctx, which) ?: return "No photos found in the gallery"
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val dm = ctx.resources.displayMetrics
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= dm.widthPixels &&
                bounds.outHeight / (sample * 2) >= dm.heightPixels
            ) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return "Could not read that photo"
            WallpaperManager.getInstance(ctx).setBitmap(bmp)
            "Wallpaper set from your $which photo"
        } catch (e: Exception) {
            "set_wallpaper failed: ${e.message}"
        }
    }

    private fun setBrightness(ctx: Context, o: JSONObject): String {
        val level = o.optInt("level", -1)
        if (level !in 0..100) return "set_brightness failed: level must be 0-100"
        return if (Settings.System.canWrite(ctx)) {
            Settings.System.putInt(
                ctx.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (level * 255 / 100)
            )
            "Brightness set to $level%"
        } else {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + ctx.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Needs 'Modify system settings' access - allow OmniTask on the next screen, then try again"
        }
    }

    private fun screenTimeout(ctx: Context, o: JSONObject): String {
        val seconds = o.optInt("seconds", -1)
        if (seconds < 5) return "screen_timeout failed: seconds must be at least 5"
        return if (Settings.System.canWrite(ctx)) {
            Settings.System.putLong(
                ctx.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                seconds * 1000L
            )
            "Screen timeout set to $seconds seconds"
        } else {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + ctx.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Needs 'Modify system settings' access - allow OmniTask on the next screen, then try again"
        }
    }

    private fun ringerMode(ctx: Context, o: JSONObject): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            when (o.optString("mode").lowercase()) {
                "silent" -> {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT; "Phone set to silent"
                }
                "vibrate" -> {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE; "Phone set to vibrate"
                }
                "normal" -> {
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL; "Phone set to normal ringing"
                }
                else -> "ringer_mode failed: mode must be silent, vibrate or normal"
            }
        } catch (e: SecurityException) {
            "Silent mode needs Do Not Disturb access - allow it for OmniTask in phone Settings, then try again"
        }
    }

    private fun vibrate(ctx: Context, o: JSONObject): String {
        val ms = o.optInt("milliseconds", 1000).coerceIn(100, 10000)
        val vib: Vibrator = ctx.getSystemService(Vibrator::class.java) ?: return "No vibrator on this device"
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms.toLong())
            }
            "Vibrated for ${ms}ms"
        } catch (e: Exception) {
            "Could not vibrate"
        }
    }

    private fun sendEmail(ctx: Context, o: JSONObject): String {
        val to = o.optString("to").trim()
        if (to.isEmpty()) return "send_email failed: no 'to' address given"
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to)))
            .putExtra(Intent.EXTRA_SUBJECT, o.optString("subject", ""))
            .putExtra(Intent.EXTRA_TEXT, o.optString("body", ""))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "Opened email to $to - review and send"
        } catch (e: Exception) {
            "No email app found"
        }
    }

    private fun getLocation(ctx: Context): String {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            return "Location permission not granted - grant Location access from the app's Settings screen and try again"
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(p)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (e: SecurityException) {
                // provider not allowed
            } catch (e: IllegalArgumentException) {
                // provider not available
            }
        }
        if (best == null) {
            return "No recent location found - open Google Maps once with GPS on, then try again"
        }
        val lat = "%.5f".format(best.latitude)
        val lng = "%.5f".format(best.longitude)
        return "Last known location: $lat, $lng (accuracy ~${best.accuracy.toInt()}m) - https://maps.google.com/?q=$lat,$lng"
    }

    private fun listContacts(ctx: Context, o: JSONObject): String {
        if (!hasContactsPermission(ctx)) {
            return "Contacts permission not granted - grant it from the app's Settings screen and try again"
        }
        val q = o.optString("query", "").trim()
        return try {
            val sel = if (q.isEmpty()) null
            else ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
            val args = if (q.isEmpty()) null else arrayOf("%$q%")
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                sel, args,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                val sb = StringBuilder()
                var n = 0
                while (c.moveToNext() && n < 25) {
                    val name = c.getString(0) ?: continue
                    val num = c.getString(1) ?: ""
                    if (n > 0) sb.append(", ")
                    sb.append(name).append(": ").append(num)
                    n++
                }
                when {
                    n == 0 -> if (q.isEmpty()) "No contacts found" else "No contacts found for \"$q\""
                    else -> "Contacts ($n): $sb"
                }
            } ?: "Could not read contacts"
        } catch (e: SecurityException) {
            "Contacts permission not granted"
        }
    }

    private fun listApps(ctx: Context): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() }
            .distinct()
            .sorted()
        return "Installed apps (${apps.size}): " + apps.take(60).joinToString(", ")
    }

    private fun deviceInfo(ctx: Context): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1.0e9
        val totalGb = stat.totalBytes / 1.0e9
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return "Model: ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            "Storage: %.1f of %.1f GB free, RAM: %.1f of %.1f GB"
                .format(freeGb, totalGb, mi.availMem / 1.0e9, mi.totalMem / 1.0e9)
    }

    private fun calendarEvent(ctx: Context, o: JSONObject): String {
        val title = o.optString("title", "Event")
        val cal = Calendar.getInstance()
        if (o.has("day") || o.has("hour")) {
            if (o.has("day")) cal.set(
                o.optInt("year", cal.get(Calendar.YEAR)),
                o.optInt("month", cal.get(Calendar.MONTH) + 1) - 1,
                o.optInt("day", cal.get(Calendar.DAY_OF_MONTH)),
                o.optInt("hour", 9),
                o.optInt("minute", 0)
            )
            else cal.set(Calendar.HOUR_OF_DAY, o.optInt("hour", 9))
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
        }
        val end = (cal.clone() as Calendar).apply {
            add(Calendar.MINUTE, o.optInt("duration_minutes", 60))
        }
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, o.optString("note", ""))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "Opened the calendar to create \"$title\" - check the details and save"
        } catch (e: Exception) {
            "No calendar app found"
        }
    }

    private fun settingsPanel(ctx: Context, which: String, label: String): String {
        val i = if (Build.VERSION.SDK_INT >= 29) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "Opened the $label panel"
        } catch (e: Exception) {
            "Could not open the $label panel"
        }
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
