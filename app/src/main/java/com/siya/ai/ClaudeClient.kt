package com.siya.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Claude (Anthropic Messages API) ko call karta hai. Blocking hai, isliye IO thread par chalao. */
class ClaudeClient(private val store: Store) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun send(system: String, tools: JSONArray, messages: JSONArray): JSONObject {
        val body = JSONObject()
            .put("model", store.model)
            .put("max_tokens", 1024)
            .put("system", system)
            .put("tools", tools)
            .put("messages", messages)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", store.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("API ${r.code}: ${txt.take(200)}")
            return JSONObject(txt)
        }
    }
}
