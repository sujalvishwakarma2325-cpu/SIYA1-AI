package com.siya.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phone par Siya jo kaam kar sakti hai, wo yahin hai.
 * Naya kaam jodna ho to: schemas() me ek tool() line aur run() me ek branch.
 */
class Tools(private val ctx: Context, private val store: Store) {

    /** Ye tools chalne se pehle user se poocha jaata hai. */
    val sensitive = setOf("call_contact", "send_sms", "change_my_setting", "add_skill", "delete_skill", "clear_notes")

    /** Skill ke andar ye tools allow nahi (khud ko badalne wale). */
    private val notInSkills = setOf("add_skill", "delete_skill", "run_skill", "change_my_setting")

    private fun tool(
        name: String,
        desc: String,
        props: List<Triple<String, String, String>> = emptyList(),
        req: List<String> = emptyList()
    ): JSONObject {
        val p = JSONObject()
        props.forEach { (n, t, d) -> p.put(n, JSONObject().put("type", t).put("description", d)) }
        return JSONObject().put("name", name).put("description", desc).put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(req))
        )
    }

    fun schemas(): JSONArray {
        val a = JSONArray()
        a.put(tool("open_app", "Open an installed app by name (e.g. WhatsApp, YouTube, Camera, Settings).",
            listOf(Triple("name", "string", "App name")), listOf("name")))
        a.put(tool("open_url", "Open a web address in the browser.",
            listOf(Triple("url", "string", "Full URL starting with https://")), listOf("url")))
        a.put(tool("web_search", "Search Google in the browser.",
            listOf(Triple("query", "string", "Search text")), listOf("query")))
        a.put(tool("call_contact", "Place a phone call to a saved contact name or a number.",
            listOf(Triple("who", "string", "Contact name or phone number")), listOf("who")))
        a.put(tool("send_sms", "Send an SMS text message to a contact name or number.",
            listOf(Triple("to", "string", "Contact name or phone number"), Triple("message", "string", "Message text")),
            listOf("to", "message")))
        a.put(tool("set_alarm", "Set an alarm clock at a given time (24-hour).",
            listOf(Triple("hour", "integer", "0-23"), Triple("minute", "integer", "0-59"), Triple("label", "string", "Alarm label")),
            listOf("hour", "minute")))
        a.put(tool("set_timer", "Start a countdown timer.",
            listOf(Triple("seconds", "integer", "Duration in seconds"), Triple("label", "string", "Timer label")),
            listOf("seconds")))
        a.put(tool("flashlight", "Turn the phone torch on or off.",
            listOf(Triple("on", "boolean", "true for on, false for off")), listOf("on")))
        a.put(tool("set_volume", "Set volume as a percentage.",
            listOf(Triple("percent", "integer", "0-100"), Triple("kind", "string", "music, ring or alarm (default music)")),
            listOf("percent")))
        a.put(tool("set_brightness", "Set screen brightness as a percentage.",
            listOf(Triple("percent", "integer", "0-100")), listOf("percent")))
        a.put(tool("media_control", "Control music or video playback.",
            listOf(Triple("action", "string", "play_pause, next, previous or stop")), listOf("action")))
        a.put(tool("phone_status", "Get battery level, charging state and the current date and time."))
        a.put(tool("open_settings", "Open a phone settings page. Android does not let apps flip Wi-Fi or Bluetooth silently, so the user taps the switch.",
            listOf(Triple("page", "string", "wifi, bluetooth, display, sound, battery, location, airplane, apps or main")),
            listOf("page")))
        a.put(tool("navigate", "Start navigation to a place in Google Maps.",
            listOf(Triple("place", "string", "Place name or address")), listOf("place")))
        a.put(tool("create_calendar_event", "Open a pre-filled calendar event for the user to save.",
            listOf(Triple("title", "string", "Event title"),
                Triple("start", "string", "Local start time as YYYY-MM-DDTHH:MM"),
                Triple("end", "string", "Optional local end time as YYYY-MM-DDTHH:MM")),
            listOf("title", "start")))
        a.put(tool("save_note", "Save a short note for the user.",
            listOf(Triple("text", "string", "Note text")), listOf("text")))
        a.put(tool("read_notes", "Read all saved notes."))
        a.put(tool("clear_notes", "Delete all saved notes."))

        // ---- khud ko badalne wale tools (har baar user se poochhe jaate hain) ----
        a.put(tool("change_my_setting",
            "Change the assistant's own setting. Keys: assistant_name, language (hi-IN, en-IN, en-US), voice_replies (true/false), extra_instructions (free text on how to behave).",
            listOf(Triple("key", "string", "Setting key"), Triple("value", "string", "New value")),
            listOf("key", "value")))

        val stepItem = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("tool", JSONObject().put("type", "string").put("description", "Name of an existing tool"))
                .put("input", JSONObject().put("type", "object").put("description", "Arguments for that tool")))
            .put("required", JSONArray(listOf("tool", "input")))
        val addSkill = tool("add_skill",
            "Teach the assistant a new reusable skill: a named sequence of existing tool calls (a routine such as 'good night').",
            listOf(Triple("name", "string", "Short skill name"), Triple("description", "string", "When to use this skill")),
            listOf("name", "description", "steps"))
        addSkill.getJSONObject("input_schema").getJSONObject("properties")
            .put("steps", JSONObject().put("type", "array").put("items", stepItem).put("description", "Ordered tool calls"))
        a.put(addSkill)
        a.put(tool("run_skill", "Run a saved skill by name.", listOf(Triple("name", "string", "Skill name")), listOf("name")))
        a.put(tool("delete_skill", "Delete a saved skill.", listOf(Triple("name", "string", "Skill name")), listOf("name")))
        return a
    }

    private val names: Set<String> by lazy {
        val a = schemas()
        (0 until a.length()).map { a.getJSONObject(it).getString("name") }.toSet()
    }

    fun describe(name: String, i: JSONObject): String = when (name) {
        "call_contact" -> "Kya main ${i.optString("who")} ko call karun?"
        "send_sms" -> "Kya main ${i.optString("to")} ko SMS bhejun: \"${i.optString("message")}\"?"
        "change_my_setting" -> "Kya main apni setting badlun: ${i.optString("key")} = ${i.optString("value")}?"
        "add_skill" -> "Kya main nayi skill jodun: ${i.optString("name")} (${i.optString("description")})?"
        "delete_skill" -> "Kya main skill hata dun: ${i.optString("name")}?"
        "clear_notes" -> "Kya main saare notes mita dun?"
        else -> "Kya main ye karun: $name?"
    }

    suspend fun run(name: String, input: JSONObject, confirm: suspend (String) -> Boolean): String {
        if (name in sensitive && !confirm(describe(name, input))) {
            return "User ne mana kar diya. Ye kaam nahi kiya gaya."
        }
        return try {
            when (name) {
                "open_app" -> openApp(input.getString("name"))
                "open_url" -> go(Intent(Intent.ACTION_VIEW, Uri.parse(input.getString("url"))), "Link khol diya.")
                "web_search" -> go(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(input.getString("query")))),
                    "Search khol diya."
                )
                "call_contact" -> call(input.getString("who"))
                "send_sms" -> sms(input.getString("to"), input.getString("message"))
                "set_alarm" -> go(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, input.getInt("hour"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, input.getInt("minute"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, input.optString("label", "Siya"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                    "Alarm set ho gaya."
                )
                "set_timer" -> go(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, input.getInt("seconds"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, input.optString("label", "Siya"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                    "Timer shuru ho gaya."
                )
                "flashlight" -> flashlight(input.getBoolean("on"))
                "set_volume" -> volume(input.getInt("percent"), input.optString("kind", "music"))
                "set_brightness" -> brightness(input.getInt("percent"))
                "media_control" -> media(input.getString("action"))
                "phone_status" -> status()
                "open_settings" -> openSettings(input.optString("page", "main"))
                "navigate" -> go(
                    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(input.getString("place")))),
                    "Navigation shuru."
                )
                "create_calendar_event" -> calendar(input)
                "save_note" -> { store.addNote(input.getString("text")); "Note save ho gaya." }
                "read_notes" -> store.notes().let { if (it.isEmpty()) "Koi note nahi hai." else it.mapIndexed { n, t -> "${n + 1}. $t" }.joinToString("\n") }
                "clear_notes" -> { store.clearNotes(); "Saare notes mita diye." }
                "change_my_setting" -> changeSetting(input.getString("key"), input.getString("value"))
                "add_skill" -> addSkill(input)
                "run_skill" -> runSkill(input.getString("name"), confirm)
                "delete_skill" -> if (store.deleteSkill(input.getString("name"))) "Skill hata di." else "Aisi skill nahi mili."
                else -> "Ye tool maujood nahi hai: $name"
            }
        } catch (e: Exception) {
            "Error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun go(i: Intent, done: String): String {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return done
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val q = name.lowercase().trim()
        val hit = pm.queryIntentActivities(main, 0)
            .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "App nahi mila: $name"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "App khul nahi sakta: $name"
        return go(i, "${hit.loadLabel(pm)} khol diya.")
    }

    private fun resolveNumber(who: String): String? {
        if (who.count { it.isDigit() } >= 5) return who.filter { it.isDigit() || it == '+' }
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        ) ?: return null
        c.use {
            while (it.moveToNext()) {
                if (it.getString(0).lowercase().contains(who.lowercase().trim())) return it.getString(1)
            }
        }
        return null
    }

    private fun call(who: String): String {
        if (!granted(Manifest.permission.CALL_PHONE)) return "Call karne ki permission nahi hai."
        val num = resolveNumber(who) ?: return "Contact ya number nahi mila: $who"
        return go(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num))), "$who ko call laga diya.")
    }

    @Suppress("DEPRECATION")
    private fun sms(to: String, msg: String): String {
        if (!granted(Manifest.permission.SEND_SMS)) return "SMS bhejne ki permission nahi hai."
        val num = resolveNumber(to) ?: return "Contact ya number nahi mila: $to"
        val m = SmsManager.getDefault()
        m.sendMultipartTextMessage(num, null, m.divideMessage(msg), null, null)
        return "SMS bhej diya ($num)."
    }

    private fun flashlight(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return "Is phone me torch nahi mili."
        cm.setTorchMode(id, on)
        return if (on) "Torch on kar di." else "Torch off kar di."
    }

    private fun volume(pct: Int, kind: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (kind) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(stream)
        am.setStreamVolume(stream, pct.coerceIn(0, 100) * max / 100, AudioManager.FLAG_SHOW_UI)
        return "Volume $pct% kar diya."
    }

    private fun brightness(pct: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)), "")
            return "Pehle 'Modify system settings' allow karo, phir dobara bolo."
        }
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, pct.coerceIn(0, 100) * 255 / 100)
        return "Brightness $pct% kar di."
    }

    private fun media(action: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val code = when (action) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "Ye media action nahi hai: $action"
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return "Ho gaya."
    }

    private fun status(): String {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return "Battery $pct%, charging: ${if (charging) "haan" else "nahi"}. Abhi: ${LocalDateTime.now()}"
    }

    private fun openSettings(page: String): String {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return go(Intent(action), "$page settings khol di. Switch aapko khud dabana hoga.")
    }

    private fun toMillis(s: String): Long =
        LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun calendar(i: JSONObject): String {
        val start = toMillis(i.getString("start"))
        val end = i.optString("end").takeIf { it.isNotBlank() }?.let { toMillis(it) } ?: (start + 3_600_000L)
        return go(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, i.getString("title"))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end),
            "Calendar event khol diya, save dabao."
        )
    }

    // ------------------------------------------------------------------ khud ko badalna

    private fun changeSetting(key: String, v: String): String {
        when (key) {
            "assistant_name" -> store.assistantName = v
            "language" -> store.language = v
            "voice_replies" -> store.voiceReplies = v.equals("true", true) || v.equals("on", true) || v.equals("haan", true)
            "extra_instructions" -> store.extraInstructions = v
            else -> return "Ye setting maujood nahi hai: $key"
        }
        return "Setting badal di: $key."
    }

    private fun addSkill(i: JSONObject): String {
        val steps = i.getJSONArray("steps")
        if (steps.length() == 0) return "Skill me kam se kam ek step chahiye."
        for (n in 0 until steps.length()) {
            val st = steps.getJSONObject(n)
            val t = st.getString("tool")
            if (t !in names || t in notInSkills) return "Step ${n + 1} me tool allowed nahi hai: $t"
            // Gemini input ko JSON text ki tarah bhejta hai; use object bana lo.
            val raw = st.opt("input")
            if (raw is String) st.put("input", if (raw.isBlank()) JSONObject() else JSONObject(raw))
            else if (raw == null) st.put("input", JSONObject())
        }
        store.saveSkill(
            JSONObject().put("name", i.getString("name")).put("description", i.optString("description")).put("steps", steps)
        )
        return "Nayi skill jod di: ${i.getString("name")}."
    }

    private suspend fun runSkill(name: String, confirm: suspend (String) -> Boolean): String {
        val s = store.findSkill(name) ?: return "Aisi skill nahi mili: $name"
        val steps = s.getJSONArray("steps")
        val out = StringBuilder()
        for (n in 0 until steps.length()) {
            val st = steps.getJSONObject(n)
            out.append(run(st.getString("tool"), st.getJSONObject("input"), confirm)).append('\n')
        }
        return out.toString().trim()
    }
}
