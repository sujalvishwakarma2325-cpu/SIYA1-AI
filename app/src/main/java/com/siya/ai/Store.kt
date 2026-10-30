package com.siya.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saari settings, notes aur skills phone me hi (SharedPreferences) rakhta hai. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("siya", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("apiKey", "") ?: ""
        set(v) { p.edit().putString("apiKey", v.trim()).apply() }

    var model: String
        get() = p.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        set(v) { p.edit().putString("model", v.trim()).apply() }

    /** "gemini" (free) ya "claude" (paid). */
    var provider: String
        get() = p.getString("provider", "gemini") ?: "gemini"
        set(v) { p.edit().putString("provider", v).apply() }

    var geminiKey: String
        get() = p.getString("geminiKey", "") ?: ""
        set(v) { p.edit().putString("geminiKey", v.trim()).apply() }

    var geminiModel: String
        get() = p.getString("geminiModel", "gemini-flash-latest") ?: "gemini-flash-latest"
        set(v) { p.edit().putString("geminiModel", v.trim().ifBlank { "gemini-flash-latest" }).apply() }

    fun activeKey(): String = if (provider == "claude") apiKey else geminiKey

    var assistantName: String
        get() = p.getString("name", "Siya") ?: "Siya"
        set(v) { p.edit().putString("name", v.trim().ifBlank { "Siya" }).apply() }

    var language: String
        get() = p.getString("lang", "hi-IN") ?: "hi-IN"
        set(v) { p.edit().putString("lang", v.trim().ifBlank { "hi-IN" }).apply() }

    var voiceReplies: Boolean
        get() = p.getBoolean("voice", true)
        set(v) { p.edit().putBoolean("voice", v).apply() }

    var conversationMode: Boolean
        get() = p.getBoolean("convo", false)
        set(v) { p.edit().putBoolean("convo", v).apply() }

    var extraInstructions: String
        get() = p.getString("extra", "") ?: ""
        set(v) { p.edit().putString("extra", v.trim()).apply() }

    // ---- notes ----
    private fun notesArr(): JSONArray = try { JSONArray(p.getString("notes", "[]")) } catch (e: Exception) { JSONArray() }

    fun notes(): List<String> = (0 until notesArr().length()).map { notesArr().getString(it) }

    fun addNote(t: String) { p.edit().putString("notes", notesArr().put(t).toString()).apply() }

    fun clearNotes() { p.edit().putString("notes", "[]").apply() }

    // ---- skills (user ke kehne par jodi gayi nayi kaabiliyatein) ----
    fun skills(): JSONArray = try { JSONArray(p.getString("skills", "[]")) } catch (e: Exception) { JSONArray() }

    fun skillCount(): Int = skills().length()

    fun findSkill(name: String): JSONObject? {
        val a = skills()
        for (i in 0 until a.length()) {
            val s = a.getJSONObject(i)
            if (s.getString("name").equals(name.trim(), ignoreCase = true)) return s
        }
        return null
    }

    fun saveSkill(skill: JSONObject) {
        val a = skills()
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val s = a.getJSONObject(i)
            if (!s.getString("name").equals(skill.getString("name"), ignoreCase = true)) out.put(s)
        }
        out.put(skill)
        p.edit().putString("skills", out.toString()).apply()
    }

    fun deleteSkill(name: String): Boolean {
        val a = skills()
        val out = JSONArray()
        var found = false
        for (i in 0 until a.length()) {
            val s = a.getJSONObject(i)
            if (s.getString("name").equals(name.trim(), ignoreCase = true)) found = true else out.put(s)
        }
        p.edit().putString("skills", out.toString()).apply()
        return found
    }
}
