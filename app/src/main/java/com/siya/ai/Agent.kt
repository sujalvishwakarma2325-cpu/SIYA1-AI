package com.siya.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Siya ka dimaag: Claude se baat karta hai, tools chalata hai, jawab wapas deta hai. */
class Agent(ctx: Context, private val store: Store) {
    private val tools = Tools(ctx, store)
    private val client = ClaudeClient(store)
    private val history = JSONArray() // sirf user/assistant ka seedha text

    private fun systemPrompt(): String {
        val skills = store.skills()
        val skillLines = (0 until skills.length()).joinToString("\n") {
            val s = skills.getJSONObject(it)
            "- ${s.getString("name")}: ${s.optString("description")}"
        }
        return """
You are ${store.assistantName}, a personal voice assistant running inside an Android app on the user's own phone.
Reply in the language the user speaks: Hindi, Hinglish or English. Keep replies short and natural to hear aloud. No markdown, no lists, no emoji.
Use the tools to actually do things on the phone. Do the action, then confirm in one short sentence. Do not describe your tools.
If a request needs something no tool can do, say so honestly in one sentence. If it could be built as a skill from existing tools, offer that and ask first.
You may change your own settings (change_my_setting) or add or delete skills (add_skill, delete_skill) only when the user asks, or after you proposed the change in words and the user agreed. The app shows the user a confirmation before it applies. Never say a change is done until the tool result says so.
Never place calls or send SMS unless the user clearly asked for it.
Current local time: ${LocalDateTime.now()}.
${if (store.extraInstructions.isNotBlank()) "User's standing instructions: " + store.extraInstructions else ""}
Saved skills (run with run_skill):
${if (skillLines.isBlank()) "(none yet)" else skillLines}
        """.trimIndent()
    }

    suspend fun ask(
        text: String,
        confirm: suspend (String) -> Boolean,
        status: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (store.apiKey.isBlank()) return@withContext "Pehle Settings me apna Claude API key daalo."

        val msgs = JSONArray()
        for (i in 0 until history.length()) msgs.put(history.get(i))
        msgs.put(JSONObject().put("role", "user").put("content", text))

        var finalText = ""
        for (step in 0 until 8) {
            val res = client.send(systemPrompt(), tools.schemas(), msgs)
            val content = res.getJSONArray("content")
            msgs.put(JSONObject().put("role", "assistant").put("content", content))

            val results = JSONArray()
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                when (b.getString("type")) {
                    "text" -> sb.append(b.getString("text"))
                    "tool_use" -> {
                        val name = b.getString("name")
                        status("kar rahi hoon: $name")
                        val out = tools.run(name, b.getJSONObject("input"), confirm)
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

        val answer = finalText.ifBlank { "Ho gaya." }
        history.put(JSONObject().put("role", "user").put("content", text))
        history.put(JSONObject().put("role", "assistant").put("content", answer))
        while (history.length() > 16) {
            history.remove(0)
            history.remove(0)
        }
        answer
    }
}
