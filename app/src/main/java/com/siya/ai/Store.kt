package com.siya.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saari settings, keys, notes aur skills phone me hi (SharedPreferences) rakhta hai. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("siya", Context.MODE_PRIVATE)

    private fun s(k: String): String = p.getString(k, "") ?: ""
    private fun put(k: String, v: String) { p.edit().putString(k, v).apply() }

    // ---------------------------------------------------------------- providers
    fun key(id: String): String = s("key_$id")
    fun setKey(id: String, v: String) { put("key_$id", v.trim()) }

    fun model(id: String, def: String): String = s("model_$id").ifBlank { def }
    fun setModel(id: String, v: String) { put("model_$id", v.trim()) }

    fun account(id: String): String = s("acct_$id")
    fun setAccount(id: String, v: String) { put("acct_$id", v.trim()) }

    fun baseUrl(id: String, def: String): String = s("url_$id").ifBlank { def }
    fun setBaseUrl(id: String, v: String) { put("url_$id", v.trim()) }

    var mainProvider: String
        get() = s("main").ifBlank { "gemini" }
        set(v) { put("main", v) }

    /** Main AI ka limit khatam ya kharab ho to agle provider par chale jao. */
    var fallback: Boolean
        get() = p.getBoolean("fallback", true)
        set(v) { p.edit().putBoolean("fallback", v).apply() }

    /** Tavily (web search) tool ki key. */
    var tavilyKey: String
        get() = s("key_tavily")
        set(v) { put("key_tavily", v.trim()) }

    private fun customArr(): JSONArray = try { JSONArray(s("customs").ifBlank { "[]" }) } catch (e: Exception) { JSONArray() }

    fun customs(): List<Provider> {
        val a = customArr()
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Provider(o.getString("id"), o.getString("label"), Kind.OPENAI, o.getString("baseUrl"), o.optString("model", ""), custom = true)
        }
    }

    fun addCustom(label: String, baseUrl: String, model: String): String {
        val slug = label.lowercase().filter { it.isLetterOrDigit() }.ifBlank { System.currentTimeMillis().toString() }
        val id = "c_$slug"
        val a = customArr()
        val out = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) out.put(a.getJSONObject(i))
        out.put(
            JSONObject().put("id", id).put("label", label.trim())
                .put("baseUrl", baseUrl.trim().trimEnd('/')).put("model", model.trim())
        )
        put("customs", out.toString())
        return id
    }

    fun removeCustom(id: String) {
        val a = customArr()
        val out = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) out.put(a.getJSONObject(i))
        put("customs", out.toString())
        put("key_$id", "")
    }

    fun allProviders(): List<Provider> = Providers.builtIn + customs()

    /** Jin providers ki key (aur Cloudflare ka account id) bhari hui hai. */
    fun ready(): List<Provider> =
        allProviders().filter { key(it.id).isNotBlank() && (!it.needsAccountId || account(it.id).isNotBlank()) }

    /** Try karne ka order: pehle Main, phir (agar fallback on hai) baaki. */
    fun chain(): List<Provider> {
        val r = ready()
        val main = r.firstOrNull { it.id == mainProvider }
        val rest = if (fallback || main == null) r.filter { it.id != main?.id } else emptyList()
        return listOfNotNull(main) + rest
    }

    fun resolvedBase(pr: Provider): String =
        baseUrl(pr.id, pr.baseUrl).replace("{ACCOUNT_ID}", account(pr.id)).trimEnd('/')

    // ---------------------------------------------------------------- assistant
    var assistantName: String
        get() = p.getString("name", "Siya") ?: "Siya"
        set(v) { put("name", v.trim().ifBlank { "Siya" }) }

    var language: String
        get() = p.getString("lang", "hi-IN") ?: "hi-IN"
        set(v) { put("lang", v.trim().ifBlank { "hi-IN" }) }

    var voiceReplies: Boolean
        get() = p.getBoolean("voice", true)
        set(v) { p.edit().putBoolean("voice", v).apply() }

    var conversationMode: Boolean
        get() = p.getBoolean("convo", false)
        set(v) { p.edit().putBoolean("convo", v).apply() }

    var extraInstructions: String
        get() = p.getString("extra", "") ?: ""
        set(v) { put("extra", v.trim()) }

    // ---------------------------------------------------------------- notes
    private fun notesArr(): JSONArray = try { JSONArray(p.getString("notes", "[]")) } catch (e: Exception) { JSONArray() }

    fun notes(): List<String> = (0 until notesArr().length()).map { notesArr().getString(it) }

    fun addNote(t: String) { put("notes", notesArr().put(t).toString()) }

    fun clearNotes() { put("notes", "[]") }

    // ---------------------------------------------------------------- skills
    fun skills(): JSONArray = try { JSONArray(p.getString("skills", "[]")) } catch (e: Exception) { JSONArray() }

    fun skillCount(): Int = skills().length()

    fun findSkill(name: String): JSONObject? {
        val a = skills()
        for (i in 0 until a.length()) {
            val sk = a.getJSONObject(i)
            if (sk.getString("name").equals(name.trim(), ignoreCase = true)) return sk
        }
        return null
    }

    fun saveSkill(skill: JSONObject) {
        val a = skills()
        val out = JSONArray()
        for (i in 0 until a.length()) {
            val sk = a.getJSONObject(i)
            if (!sk.getString("name").equals(skill.getString("name"), ignoreCase = true)) out.put(sk)
        }
        out.put(skill)
        put("skills", out.toString())
    }

    fun deleteSkill(name: String): Boolean {
        val a = skills()
        val out = JSONArray()
        var found = false
        for (i in 0 until a.length()) {
            val sk = a.getJSONObject(i)
            if (sk.getString("name").equals(name.trim(), ignoreCase = true)) found = true else out.put(sk)
        }
        put("skills", out.toString())
        return found
    }
}
