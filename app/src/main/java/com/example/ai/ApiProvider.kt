package com.example.ai

/**
 * Supported AI Providers for MAX Assistant with pattern-matching detection,
 * default models, and available version lists.
 */
enum class ApiProvider(
    val id: String,
    val displayName: String,
    val providerFamily: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val keyPrefix: String,
    val placeholder: String,
    val docsUrl: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        providerFamily = "Google AI Studio",
        defaultModel = "gemini-3.5-flash",
        availableModels = listOf(
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash",
            "gemini-3.6-flash"
        ),
        keyPrefix = "AIzaSy",
        placeholder = "AIzaSy...",
        docsUrl = "https://aistudio.google.com/app/apikey"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq Cloud",
        providerFamily = "Groq Ultra-Fast LPU",
        defaultModel = "llama-3.3-70b-versatile",
        availableModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it",
            "deepseek-r1-distill-llama-70b"
        ),
        keyPrefix = "gsk_",
        placeholder = "gsk_...",
        docsUrl = "https://console.groq.com/keys"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        providerFamily = "OpenAI API",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "o3-mini"
        ),
        keyPrefix = "sk-proj-",
        placeholder = "sk-proj-... / sk-...",
        docsUrl = "https://platform.openai.com/api-keys"
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        providerFamily = "Anthropic API",
        defaultModel = "claude-3-7-sonnet-latest",
        availableModels = listOf(
            "claude-3-7-sonnet-latest",
            "claude-3-5-haiku-latest",
            "claude-3-5-sonnet-latest"
        ),
        keyPrefix = "sk-ant-",
        placeholder = "sk-ant-...",
        docsUrl = "https://console.anthropic.com/settings/keys"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        providerFamily = "OpenRouter Gateway",
        defaultModel = "deepseek/deepseek-r1",
        availableModels = listOf(
            "deepseek/deepseek-r1",
            "meta-llama/llama-3.3-70b-instruct",
            "anthropic/claude-3.5-haiku",
            "google/gemini-2.0-flash-001"
        ),
        keyPrefix = "sk-or-",
        placeholder = "sk-or-...",
        docsUrl = "https://openrouter.ai/keys"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        providerFamily = "DeepSeek AI",
        defaultModel = "deepseek-chat",
        availableModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        ),
        keyPrefix = "sk-",
        placeholder = "sk-...",
        docsUrl = "https://platform.deepseek.com/api_keys"
    ),
    PERPLEXITY(
        id = "perplexity",
        displayName = "Perplexity AI",
        providerFamily = "Perplexity Sonar",
        defaultModel = "sonar-pro",
        availableModels = listOf(
            "sonar-pro",
            "sonar"
        ),
        keyPrefix = "pplx-",
        placeholder = "pplx-...",
        docsUrl = "https://www.perplexity.ai/settings/api"
    ),
    UNKNOWN(
        id = "unknown",
        displayName = "Custom / Unknown",
        providerFamily = "Custom AI Engine",
        defaultModel = "gemini-3.5-flash",
        availableModels = listOf("gemini-3.5-flash", "gemini-2.5-flash"),
        keyPrefix = "",
        placeholder = "Enter API Key...",
        docsUrl = ""
    );

    companion object {
        fun fromId(id: String?): ApiProvider {
            if (id.isNullOrBlank()) return GEMINI
            return entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: GEMINI
        }

        /**
         * Pattern-matching algorithm to automatically identify the API provider
         * based on structural characteristics, prefixes, and length.
         */
        fun detectProvider(key: String): ApiProvider {
            val trimmed = key.trim().removeSurrounding("\"").removeSurrounding("'")
            if (trimmed.isEmpty()) return GEMINI

            return when {
                trimmed.startsWith("AIzaSy", ignoreCase = false) -> GEMINI
                trimmed.startsWith("gsk_", ignoreCase = false) -> GROQ
                trimmed.startsWith("sk-ant-", ignoreCase = false) -> ANTHROPIC
                trimmed.startsWith("sk-or-", ignoreCase = false) -> OPENROUTER
                trimmed.startsWith("pplx-", ignoreCase = false) -> PERPLEXITY
                trimmed.startsWith("sk-proj-", ignoreCase = false) -> OPENAI
                trimmed.startsWith("sk-admin-", ignoreCase = false) -> OPENAI
                trimmed.startsWith("sk-", ignoreCase = false) -> OPENAI
                else -> UNKNOWN
            }
        }

        /**
         * Returns a friendly description of key format expectations.
         */
        fun getFormatHint(provider: ApiProvider): String {
            return when (provider) {
                GEMINI -> "Starts with 'AIzaSy...' (~39 characters)"
                GROQ -> "Starts with 'gsk_...' (~56 characters)"
                OPENAI -> "Starts with 'sk-proj-...' or 'sk-...'"
                ANTHROPIC -> "Starts with 'sk-ant-...'"
                OPENROUTER -> "Starts with 'sk-or-...'"
                DEEPSEEK -> "Starts with 'sk-...'"
                PERPLEXITY -> "Starts with 'pplx-...'"
                UNKNOWN -> "Unrecognized format. Enter an API key."
            }
        }
    }
}
