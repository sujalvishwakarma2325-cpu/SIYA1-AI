package com.siya.ai

enum class Kind { CLAUDE, GEMINI, OPENAI }

/**
 * Ek AI provider. Naya provider jodna ho to builtIn list me ek line badho,
 * ya app ke Settings me "Naya provider jodo" se bina code ke jodo (OpenAI-compatible hona chahiye).
 */
data class Provider(
    val id: String,
    val label: String,
    val kind: Kind,
    val baseUrl: String,
    val model: String,
    val needsAccountId: Boolean = false,
    val keyUrl: String = "",
    val custom: Boolean = false
)

object Providers {
    val builtIn: List<Provider> = listOf(
        Provider("gemini", "Gemini (free)", Kind.GEMINI, "", "gemini-flash-latest", keyUrl = "aistudio.google.com/apikey"),
        Provider("groq", "Groq", Kind.OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", keyUrl = "console.groq.com/keys"),
        Provider("openrouter", "OpenRouter", Kind.OPENAI, "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", keyUrl = "openrouter.ai/keys"),
        Provider("mistral", "Mistral", Kind.OPENAI, "https://api.mistral.ai/v1", "mistral-small-latest", keyUrl = "console.mistral.ai/api-keys"),
        Provider(
            "cloudflare", "Cloudflare Workers AI", Kind.OPENAI,
            "https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/v1",
            "@cf/meta/llama-3.3-70b-instruct-fp8-fast", needsAccountId = true, keyUrl = "dash.cloudflare.com (API token)"
        ),
        Provider("nvidia", "NVIDIA NIM", Kind.OPENAI, "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct", keyUrl = "build.nvidia.com"),
        Provider("cohere", "Cohere", Kind.OPENAI, "https://api.cohere.ai/compatibility/v1", "command-a-03-2025", keyUrl = "dashboard.cohere.com/api-keys"),
        Provider("sambanova", "SambaNova Cloud", Kind.OPENAI, "https://api.sambanova.ai/v1", "Meta-Llama-3.3-70B-Instruct", keyUrl = "cloud.sambanova.ai"),
        Provider("zai", "Z.ai (GLM)", Kind.OPENAI, "https://api.z.ai/api/paas/v4", "glm-4.5-flash", keyUrl = "z.ai (API keys)"),
        Provider("claude", "Claude (paid)", Kind.CLAUDE, "", "claude-sonnet-5-5", keyUrl = "console.anthropic.com")
    )
}
