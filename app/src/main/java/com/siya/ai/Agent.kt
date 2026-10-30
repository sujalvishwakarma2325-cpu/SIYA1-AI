package com.siya.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime

/**
 * Siya ka dimaag. Provider chain me pehle Main AI, phir baaki (agar fallback on hai).
 * Har provider ka apna format hai, par baat-cheet ki history sab me ek hi rehti hai.
 */
class Agent(ctx: Context, private val store: Store) {
    private val tools = Tools(ctx, store)
    private val claude = ClaudeClient()
    private val gemini = GeminiClient()
    private val openai = OpenAiClient()

    private val hist = mutableListOf<Pair<String, String>>() // ("user" | "assistant", text)
    private var sideEffects = false
    private val readOnly = setOf("phone_status", "read_notes", "internet_search")

    private fun systemPrompt(): String {
        val skills = store.skills()
        val skillLines = (0 until skills.length()).joinToString("\n") {
            val s = skills.getJSONObject(it)
            "- ${s.getString("name")}: ${s.optString("description")}"
        }
        val search = if (store.tavilyKey.isNotBlank())
            "For current facts, news, prices or anything you are unsure about, use internet_search before answering."
        else ""
        return """
You are ${store.assistantName}, a personal voice assistant running inside an Android app on the user's own phone.
Reply in the language the user speaks: Hindi, Hinglish or English. Keep replies short and natural to hear aloud. No markdown, no lists, no emoji.
Use the tools to actually do things on the phone. Do the action, then confirm in one short sentence. Do not describe your tools.
$search
If a request needs something no tool can do, say so honestly in one sentence. If it could be built as a skill from existing tools, offer that and ask first.
You may change your own settings (change_my_setting) or add or delete skills (add_skill, delete_skill) only when the user asks, or after you proposed the change in words and the user agreed. The app shows the user a confirmation before it applies. Never say a change is done until the tool result says so.
Never place calls or send SMS unless the user clearly asked for it.
Current local time: ${LocalDateTime.now()}.
${if (store.extraInstructions.isNotBlank()) "User's standing instructions: " + store.extraInstructions else ""}
Saved skills (run with run_skill):
${if (skillLines.isBlank()) "(none yet)" else skillLines}
        """.trimIndent()
    }

    private suspend fun runTool(
        name: String,
        input: JSONObject,
        confirm: suspend (String) -> Boolean,
        status: (String) -> Unit
    ): String {
        status("kar rahi hoon: $name")
        if (name !in readOnly) sideEffects = true
        return tools.run(name, input, confirm)
    }

    private fun remember(user: String, answer: String) {
        hist.add("user" to user)
        hist.add("assistant" to answer)
        while (hist.size > 16) {
            hist.removeAt(0)
            hist.removeAt(0)
        }
    }

    suspend fun ask(
        text: String,
        confirm: suspend (String) -> Boolean,
        status: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val chain = store.chain()
        if (chain.isEmpty()) return@withContext "Pehle Settings me kam se kam ek AI ki API key daalo."
        sideEffects = false
        var lastErr = ""
        for ((i, p) in chain.withIndex()) {
            try {
                if (i > 0) status("${p.label} se try kar rahi hoon")
                val answer = when (p.kind) {
                    Kind.CLAUDE -> askClaude(p, text, confirm, status)
                    Kind.GEMINI -> askGemini(p, text, confirm, status)
                    Kind.OPENAI -> askOpenAi(p, text, confirm, status)
                }
                remember(text, answer)
                return@withContext answer
            } catch (e: Exception) {
                lastErr = "${p.label}: ${e.message}"
                // Kuch kaam (jaise SMS) ho chuka ho to doosri AI se dobara mat chalao.
                if (sideEffects) break
            }
        }
        "Dikkat aayi. $lastErr" + if (sideEffects) " (Kuch kaam ho chuka tha, isliye doosri AI par nahi gayi.)" else ""
    }

    // ------------------------------------------------------------------ Claude
    private suspend fun askClaude(
        p: Provider, text: String, confirm: suspend (String) -> Boolean, status: (String) -> Unit
    ): String {
        val msgs = JSONArray()
        for ((r, t) in hist) msgs.put(JSONObject().put("role", r).put("content", t))
        msgs.put(JSONObject().put("role", "user").put("content", text))

        var finalText = ""
        for (step in 0 until 8) {
            val res = claude.send(store.key(p.id), store.model(p.id, p.model), systemPrompt(), tools.schemas(), msgs)
            val content = res.getJSONArray("content")
            msgs.put(JSONObject().put("role", "assistant").put("content", content))

            val results = JSONArray()
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                when (b.getString("type")) {
                    "text" -> sb.append(b.getString("text"))
                    "tool_use" -> {
                        val out = runTool(b.getString("name"), b.getJSONObject("input"), confirm, status)
                        results.put(
                            JSONObject().put("type", "tool_result")
                                .put("tool_use_id", b.getString("id"))
                                .put("content", out)
                        )
                    }
                }
            }
            finalText = sb.toString().trim()
            if (results.length() == 0) break
            msgs.put(JSONObject().put("role", "user").put("content", results))
        }
        return finalText.ifBlank { "Ho gaya." }
    }

    // ------------------------------------------------------------------ Gemini
    private fun textPart(t: String) = JSONObject().put("text", t)

    private suspend fun askGemini(
        p: Provider, text: String, confirm: suspend (String) -> Boolean, status: (String) -> Unit
    ): String {
        val contents = JSONArray()
        for ((r, t) in hist) {
            contents.put(JSONObject().put("role", if (r == "user") "user" else "model").put("parts", JSONArray().put(textPart(t))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(textPart(text))))

        var finalText = ""
        for (step in 0 until 8) {
            val res = gemini.send(store.key(p.id), store.model(p.id, p.model), systemPrompt(), tools.schemas(), contents)
            val cand = res.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IOException("Gemini ne jawab nahi diya")
            val content = cand.optJSONObject("content") ?: break
            val parts = content.optJSONArray("parts") ?: JSONArray()
            // Model ka poora jawab jyon ka tyon wapas bhejte hain (thought signature isi me hota hai).
            contents.put(content)

            val results = JSONArray()
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val fc = part.optJSONObject("functionCall")
                if (fc != null) {
                    val name = fc.getString("name")
                    val out = runTool(name, fc.optJSONObject("args") ?: JSONObject(), confirm, status)
                    results.put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject().put("name", name).put("response", JSONObject().put("result", out))
                        )
                    )
                } else if (part.has("text") && !part.optBoolean("thought", false)) {
                    sb.append(part.getString("text"))
                }
            }
            finalText = sb.toString().trim()
            if (results.length() == 0) break
            contents.put(JSONObject().put("role", "user").put("parts", results))
        }
        return finalText.ifBlank { "Ho gaya." }
    }

    // ------------------------------------------------------------------ OpenAI-compatible
    private suspend fun askOpenAi(
        p: Provider, text: String, confirm: suspend (String) -> Boolean, status: (String) -> Unit
    ): String {
        val toolDefs = JSONArray()
        val schemas = tools.schemas()
        for (i in 0 until schemas.length()) {
            val t = schemas.getJSONObject(i)
            toolDefs.put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject().put("name", t.getString("name"))
                        .put("description", t.optString("description"))
                        .put("parameters", t.getJSONObject("input_schema"))
                )
            )
        }

        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", systemPrompt()))
        for ((r, t) in hist) msgs.put(JSONObject().put("role", r).put("content", t))
        msgs.put(JSONObject().put("role", "user").put("content", text))

        val base = store.resolvedBase(p)
        var finalText = ""
        for (step in 0 until 8) {
            val res = openai.send(base, store.key(p.id), store.model(p.id, p.model), msgs, toolDefs)
            val msg = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw IOException("Khaali jawab mila")
            val c = msg.opt("content")
            val content = if (c is String) c else ""
            val calls = msg.optJSONArray("tool_calls")

            if (calls == null || calls.length() == 0) {
                finalText = content.trim()
                break
            }
            // Saaf assistant message wapas bhejte hain (kuch providers extra fields par error dete hain).
            msgs.put(
                JSONObject().put("role", "assistant")
                    .put("content", if (content.isBlank()) JSONObject.NULL else content)
                    .put("tool_calls", calls)
            )
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val fn = call.getJSONObject("function")
                val name = fn.getString("name")
                val raw = fn.optString("arguments", "{}")
                val args = try { JSONObject(if (raw.isBlank()) "{}" else raw) } catch (e: Exception) { JSONObject() }
                val out = runTool(name, args, confirm, status)
                msgs.put(
                    JSONObject().put("role", "tool")
                        .put("tool_call_id", call.optString("id"))
                        .put("name", name)
                        .put("content", out)
                )
            }
        }
        return finalText.ifBlank { "Ho gaya." }
    }
}
