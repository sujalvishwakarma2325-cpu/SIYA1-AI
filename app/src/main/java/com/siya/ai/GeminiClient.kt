package com.siya.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Google Gemini (free tier) ko call karta hai. Blocking hai, isliye IO thread par chalao. */
class GeminiClient(private val store: Store) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Tools ke schemas Claude ke format me hain; yahan Gemini ke format me badalte hain. */
    private fun convertSchema(s: JSONObject): JSONObject {
        val type = s.optString("type", "string")
        val out = JSONObject()
        if (type == "object") {
            val props = s.optJSONObject("properties")
            if (props == null || props.length() == 0) {
                // Gemini khaali object nahi leta, isliye JSON text maang lete hain.
                return JSONObject().put("type", "string")
                    .put("description", s.optString("description", "") + " (JSON text, e.g. {\"key\":\"value\"})")
            }
            out.put("type", "object")
            val np = JSONObject()
            props.keys().forEach { k -> np.put(k, convertSchema(props.getJSONObject(k))) }
            out.put("properties", np)
            val req = s.optJSONArray("required")
            if (req != null && req.length() > 0) out.put("required", req)
        } else if (type == "array") {
            out.put("type", "array")
            s.optJSONObject("items")?.let { out.put("items", convertSchema(it)) }
        } else {
            out.put("type", type)
        }
        if (s.has("description") && !out.has("description")) out.put("description", s.getString("description"))
        return out
    }

    private fun convertTools(tools: JSONArray): JSONArray {
        val decls = JSONArray()
        for (i in 0 until tools.length()) {
            val t = tools.getJSONObject(i)
            val d = JSONObject().put("name", t.getString("name")).put("description", t.optString("description"))
            val schema = t.optJSONObject("input_schema")
            val props = schema?.optJSONObject("properties")
            if (schema != null && props != null && props.length() > 0) d.put("parameters", convertSchema(schema))
            decls.put(d)
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", decls))
    }

    fun send(system: String, tools: JSONArray, contents: JSONArray): JSONObject {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", convertTools(tools))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${store.geminiModel}:generateContent"
        val req = Request.Builder()
            .url(url)
            .header("x-goog-api-key", store.geminiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: ""
            if (r.code == 429) throw IOException("Free limit abhi poori ho gayi hai. Thodi der baad dobara try karo.")
            if (!r.isSuccessful) throw IOException("Gemini ${r.code}: ${txt.take(200)}")
            return JSONObject(txt)
        }
    }
}
