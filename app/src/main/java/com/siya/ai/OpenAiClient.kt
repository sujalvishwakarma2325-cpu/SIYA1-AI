package com.siya.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible /chat/completions. Groq, OpenRouter, Mistral, NVIDIA, Cohere,
 * SambaNova, Z.ai, Cloudflare aur koi bhi custom provider isi se chalte hain.
 */
class OpenAiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun send(baseUrl: String, key: String, model: String, messages: JSONArray, tools: JSONArray): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", 1024)
        if (tools.length() > 0) body.put("tools", tools)
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val why = when (r.code) {
                    401, 403 -> "key galat hai ya allowed nahi"
                    404 -> "address ya model ka naam galat hai"
                    429 -> "free limit poori ya bahut tez"
                    else -> txt.take(200)
                }
                throw IOException("HTTP ${r.code}: $why")
            }
            return JSONObject(txt)
        }
    }
}
