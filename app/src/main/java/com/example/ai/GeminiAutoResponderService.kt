package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.repository.AppSettings
import com.example.data.repository.AppSettingsRepository
import com.example.security.SecureKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AiResult {
    data class Success(val text: String, val modelUsed: String) : AiResult()
    data class Error(val message: String, val isQuotaOrAuth: Boolean = false) : AiResult()
}

sealed class GeminiKeyValidationResult {
    data class Success(
        val message: String,
        val provider: ApiProvider = ApiProvider.GEMINI,
        val suggestedModel: String = "gemini-3.5-flash"
    ) : GeminiKeyValidationResult()
    data class Error(val message: String) : GeminiKeyValidationResult()
}

/**
 * Service handling Gemini AI logic using Android-native OkHttp REST API.
 * Avoids any classpath conflicts with legacy Apache HTTP on Android.
 */
class GeminiAutoResponderService(private val context: Context) {
    private val tag = "GeminiAutoResponder"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Generates a context-aware SMS auto-reply for an incoming message.
     */
    suspend fun generateSmsReply(
        senderNumber: String,
        incomingMessage: String,
        settings: AppSettings
    ): AiResult = withContext(Dispatchers.IO) {
        if (incomingMessage.isBlank()) {
            return@withContext AiResult.Error("Incoming message content is empty.")
        }

        // Fast Local Rule Engine check (0ms latency response)
        FastLocalRuleEngine.evaluate(incomingMessage)?.let { fastReply ->
            Log.i(tag, "Fast Local Rule Engine matched instant SMS reply: \"$fastReply\" (0ms API latency saved)")
            return@withContext AiResult.Success(fastReply, "Fast Local Rule Engine")
        }

        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant responding on behalf of the device owner.")
            appendLine("Owner Current Persona: ${settings.selectedPersona}")
            appendLine("Owner Context/Instructions: ${settings.customInstructions}")
            appendLine("Desired Tone: ${settings.responseTone}")
            appendLine("Sender Phone/ID: $senderNumber")
            appendLine("Incoming Message: \"$incomingMessage\"")
            appendLine()
            appendLine("TASK:")
            appendLine("Write a concise, natural, context-aware reply to this incoming message.")
            appendLine("SYSTEM RULES:")
            appendLine("1. Keep all responses concise, direct, and conversational (1-3 sentences max).")
            appendLine("2. Format text specifically for Text-to-Speech engines: avoid complex Markdown, bullet points, code blocks, or special symbols.")
            appendLine("3. Speak naturally in clear, engaging Hindi or English based on user input.")
            appendLine("4. Avoid unnecessary fillers or polite intros; provide answers immediately.")
            appendLine("5. Keep it short (maximum 160 characters if possible).")
            appendLine("6. Output ONLY the reply text directly. Do not include quotes, prefixes, or explanations.")
        }

        executeAiRequest(prompt, settings)
    }

    /**
     * Generates a context-aware auto-reply for an incoming WhatsApp notification message.
     */
    suspend fun generateWhatsAppReply(
        senderName: String,
        incomingMessage: String,
        settings: AppSettings
    ): AiResult = withContext(Dispatchers.IO) {
        if (incomingMessage.isBlank()) {
            return@withContext AiResult.Error("Incoming message content is empty.")
        }

        // Fast Local Rule Engine check (0ms latency response)
        FastLocalRuleEngine.evaluate(incomingMessage)?.let { fastReply ->
            Log.i(tag, "Fast Local Rule Engine matched instant WhatsApp reply: \"$fastReply\" (0ms API latency saved)")
            return@withContext AiResult.Success(fastReply, "Fast Local Rule Engine")
        }

        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant responding to a WhatsApp message on behalf of the device owner.")
            appendLine("Owner Persona: ${settings.selectedPersona}")
            appendLine("Owner Context/Instructions: ${settings.customInstructions}")
            appendLine("Desired Tone: ${settings.responseTone}")
            appendLine("WhatsApp Sender: $senderName")
            appendLine("Incoming WhatsApp Message: \"$incomingMessage\"")
            appendLine()
            appendLine("TASK:")
            appendLine("Write a concise, natural, context-aware auto-reply to send back via WhatsApp.")
            appendLine("RULES:")
            appendLine("1. Keep response direct, friendly, and brief (1-2 sentences max).")
            appendLine("2. Speak naturally in clear, engaging Hindi or English matching the sender's tone.")
            appendLine("3. Output ONLY the reply text directly. No quotes or prefixes.")
        }

        executeAiRequest(prompt, settings)
    }

    /**
     * Generates a context-aware notification reply for a missed phone call.
     */
    suspend fun generateMissedCallReply(
        callerNumber: String,
        settings: AppSettings
    ): AiResult = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant responding to a missed phone call on behalf of the device owner.")
            appendLine("Owner Current Persona: ${settings.selectedPersona}")
            appendLine("Owner Context/Instructions: ${settings.customInstructions}")
            appendLine("Desired Tone: ${settings.responseTone}")
            appendLine("Caller Number: $callerNumber")
            appendLine()
            appendLine("TASK:")
            appendLine("Write a polite, concise SMS notification to the caller explaining that their call was missed.")
            appendLine("SYSTEM RULES:")
            appendLine("1. Keep all responses concise, direct, and conversational (1-3 sentences max).")
            appendLine("2. Format text specifically for Text-to-Speech engines: avoid complex Markdown, bullet points, code blocks, or special symbols.")
            appendLine("3. Speak naturally in clear, engaging Hindi or English based on user input.")
            appendLine("4. Avoid unnecessary fillers or polite intros; provide answers immediately.")
            appendLine("5. Keep it very short and helpful (under 140 characters).")
            appendLine("6. Output ONLY the reply text directly. Do not include quotes, prefixes, or explanations.")
        }

        executeAiRequest(prompt, settings)
    }

    /**
     * Generates a direct response from MAX AI assistant for direct voice query / interaction.
     */
    suspend fun generateMaxVoiceResponse(
        userQuery: String,
        settings: AppSettings
    ): AiResult = withContext(Dispatchers.IO) {
        if (userQuery.isBlank()) {
            return@withContext AiResult.Error("Query is empty.")
        }

        val contextHistory = ConversationContextManager.getInstance().getFormattedHistoryForPrompt()

        // Fast Local Rule Engine check (0ms latency response)
        FastLocalRuleEngine.evaluate(userQuery)?.let { fastReply ->
            Log.i(tag, "Fast Local Rule Engine matched instant voice response: \"$fastReply\" (0ms API latency saved)")
            ConversationContextManager.getInstance().addTurn("user", userQuery)
            ConversationContextManager.getInstance().addTurn("assistant", fastReply)
            return@withContext AiResult.Success(fastReply, "Fast Local Rule Engine")
        }

        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant.")
            if (contextHistory.isNotBlank()) {
                appendLine(contextHistory)
                appendLine()
            }
            appendLine("Current User Query: \"$userQuery\"")
            appendLine()
            appendLine("SYSTEM RULES:")
            appendLine("1. Keep all responses concise, direct, and conversational (1-3 sentences max).")
            appendLine("2. Evaluate the current query relative to previous conversation history if provided (resolve pronouns like 'he', 'she', 'it', 'that', or follow-up questions).")
            appendLine("3. Format text specifically for Text-to-Speech engines: avoid complex Markdown, bullet points, code blocks, or special symbols.")
            appendLine("4. Speak naturally in clear, engaging Hindi or English based on user input.")
            appendLine("5. Avoid unnecessary fillers or polite intros; provide answers immediately.")
            appendLine("6. Output ONLY the response text directly.")
        }

        val result = executeAiRequest(prompt, settings)
        if (result is AiResult.Success) {
            ConversationContextManager.getInstance().addTurn("user", userQuery)
            ConversationContextManager.getInstance().addTurn("assistant", result.text)
        }
        result
    }

    /**
     * Streams voice response chunks from active AI provider asynchronously as text tokens are generated.
     * Ensures immediate playback as soon as the first words/phrases are received.
     */
    fun streamMaxVoiceResponse(
        userQuery: String,
        settings: AppSettings
    ): Flow<String> = flow {
        val trimmedQuery = userQuery.trim()
        if (trimmedQuery.isBlank()) {
            emit("Query is empty.")
            return@flow
        }

        val contextHistory = ConversationContextManager.getInstance().getFormattedHistoryForPrompt()

        // Fast Local Rule Engine check (0ms latency response)
        FastLocalRuleEngine.evaluate(trimmedQuery)?.let { fastReply ->
            Log.i(tag, "Fast Local Rule Engine matched streaming voice query: \"$fastReply\" (0ms API latency saved)")
            ConversationContextManager.getInstance().addTurn("user", trimmedQuery)
            ConversationContextManager.getInstance().addTurn("assistant", fastReply)
            emit(fastReply)
            return@flow
        }

        val activeProvider = SecureKeyManager.getActiveProvider(context)
        val apiKey = SecureKeyManager.getApiKey(context)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            emit("${activeProvider.displayName} API key is not configured. Please set your key in Settings.")
            return@flow
        }

        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant.")
            if (contextHistory.isNotBlank()) {
                appendLine(contextHistory)
                appendLine()
            }
            appendLine("Current User Query: \"$trimmedQuery\"")
            appendLine()
            appendLine("SYSTEM RULES:")
            appendLine("1. Keep all responses concise, direct, and conversational (1-3 sentences max).")
            appendLine("2. Evaluate the current query relative to previous conversation history if provided (resolve pronouns like 'he', 'she', 'it', 'that', or follow-up questions).")
            appendLine("3. Format text specifically for Text-to-Speech engines: avoid complex Markdown, bullet points, code blocks, or special symbols.")
            appendLine("4. Speak naturally in clear, engaging Hindi or English based on user input.")
            appendLine("5. Avoid unnecessary fillers or polite intros; provide answers immediately.")
            appendLine("6. Output ONLY the response text directly.")
        }

        if (activeProvider == ApiProvider.GEMINI) {
            val modelName = settings.modelName.trim()
            val initialModel = when (modelName) {
                "", "gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-flash-latest" -> "gemini-3.5-flash"
                else -> modelName
            }

            val modelsToTry = listOf(initialModel, "gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-2.5-flash").distinct()

            for (resolvedModel in modelsToTry) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:streamGenerateContent?alt=sse&key=$apiKey"
                val jsonBody = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                val partObj = JSONObject().apply {
                                    put("text", prompt)
                                }
                                put(partObj)
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)

                    val genConfig = JSONObject().apply {
                        put("temperature", 0.3)
                        put("maxOutputTokens", 120)
                        put("topP", 0.8)
                        put("topK", 20)
                    }
                    put("generationConfig", genConfig)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                var streamSuccess = false
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val source = response.body?.source()
                            if (source != null) {
                                while (!source.exhausted()) {
                                    val line = source.readUtf8Line() ?: break
                                    if (line.startsWith("data: ")) {
                                        val jsonStr = line.removePrefix("data: ").trim()
                                        if (jsonStr == "[DONE]") break
                                        val chunkText = parseCandidateText(jsonStr)
                                        if (!chunkText.isNullOrBlank()) {
                                            streamSuccess = true
                                            emit(chunkText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (streamSuccess) {
                        return@flow
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Streaming failed for model $resolvedModel: ${e.message}. Trying fallback...")
                }
            }
        }

        // Fallback to non-streaming if streaming endpoints were unreachable or for non-Gemini providers
        when (val nonStreamRes = generateMaxVoiceResponse(trimmedQuery, settings)) {
            is AiResult.Success -> emit(nonStreamRes.text)
            is AiResult.Error -> emit("Error generating voice response: ${nonStreamRes.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Executes the AI prompt using the active provider (Gemini, Groq, OpenAI, Anthropic, etc.).
     */
    private suspend fun executeAiRequest(prompt: String, settings: AppSettings): AiResult = withContext(Dispatchers.IO) {
        val activeProvider = SecureKeyManager.getActiveProvider(context)
        val apiKey = SecureKeyManager.getApiKey(context)

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext AiResult.Error(
                "${activeProvider.displayName} API key is not configured. Please set your key in Settings or Keys Manager.",
                isQuotaOrAuth = true
            )
        }

        when (activeProvider) {
            ApiProvider.GROQ -> executeOpenAiCompatibleRequest(
                endpoint = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = apiKey,
                model = if (settings.modelName.isNotBlank()) settings.modelName else "llama-3.3-70b-versatile",
                prompt = prompt,
                providerName = "Groq"
            )
            ApiProvider.OPENAI -> executeOpenAiCompatibleRequest(
                endpoint = "https://api.openai.com/v1/chat/completions",
                apiKey = apiKey,
                model = if (settings.modelName.isNotBlank()) settings.modelName else "gpt-4o-mini",
                prompt = prompt,
                providerName = "OpenAI"
            )
            ApiProvider.OPENROUTER -> executeOpenAiCompatibleRequest(
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = apiKey,
                model = if (settings.modelName.isNotBlank()) settings.modelName else "deepseek/deepseek-r1",
                prompt = prompt,
                providerName = "OpenRouter"
            )
            ApiProvider.DEEPSEEK -> executeOpenAiCompatibleRequest(
                endpoint = "https://api.deepseek.com/v1/chat/completions",
                apiKey = apiKey,
                model = if (settings.modelName.isNotBlank()) settings.modelName else "deepseek-chat",
                prompt = prompt,
                providerName = "DeepSeek"
            )
            ApiProvider.PERPLEXITY -> executeOpenAiCompatibleRequest(
                endpoint = "https://api.perplexity.ai/chat/completions",
                apiKey = apiKey,
                model = if (settings.modelName.isNotBlank()) settings.modelName else "sonar-pro",
                prompt = prompt,
                providerName = "Perplexity"
            )
            ApiProvider.ANTHROPIC -> executeAnthropicRequest(
                apiKey = apiKey,
                model = if (settings.modelName.isNotBlank()) settings.modelName else "claude-3-7-sonnet-latest",
                prompt = prompt
            )
            else -> executeGeminiRequest(prompt, settings.modelName)
        }
    }

    /**
     * Executes an OpenAI-compatible chat completion request (Groq, OpenAI, OpenRouter, DeepSeek, Perplexity).
     */
    private suspend fun executeOpenAiCompatibleRequest(
        endpoint: String,
        apiKey: String,
        model: String,
        prompt: String,
        providerName: String
    ): AiResult = withContext(Dispatchers.IO) {
        return@withContext try {
            withTimeout(25_000L) {
                val jsonBody = JSONObject().apply {
                    put("model", model)
                    val messages = JSONArray().apply {
                        val sysMsg = JSONObject().apply {
                            put("role", "system")
                            put("content", "You are MAX, an intelligent and helpful voice AI assistant.")
                        }
                        val userMsg = JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                        put(sysMsg)
                        put(userMsg)
                    }
                    put("messages", messages)
                    put("temperature", 0.3)
                    put("max_tokens", 150)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val (code, responseBody) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299) {
                    val root = JSONObject(responseBody)
                    val choices = root.optJSONArray("choices")
                    val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                    if (!content.isNullOrBlank()) {
                        AiResult.Success(content, "$providerName: $model")
                    } else {
                        AiResult.Error("$providerName returned an empty response.")
                    }
                } else {
                    val errorMsg = parseErrorMessage(responseBody)
                    AiResult.Error("$providerName Error (HTTP $code): $errorMsg", isQuotaOrAuth = code in listOf(401, 403, 429))
                }
            }
        } catch (e: Exception) {
            AiResult.Error("$providerName execution error: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Executes an Anthropic Claude messages request.
     */
    private suspend fun executeAnthropicRequest(
        apiKey: String,
        model: String,
        prompt: String
    ): AiResult = withContext(Dispatchers.IO) {
        return@withContext try {
            withTimeout(25_000L) {
                val jsonBody = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 150)
                    put("system", "You are MAX, an intelligent and helpful voice AI assistant.")
                    val messages = JSONArray().apply {
                        val userMsg = JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                        put(userMsg)
                    }
                    put("messages", messages)
                }

                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val (code, responseBody) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299) {
                    val root = JSONObject(responseBody)
                    val contentArray = root.optJSONArray("content")
                    val text = contentArray?.optJSONObject(0)?.optString("text")?.trim()
                    if (!text.isNullOrBlank()) {
                        AiResult.Success(text, "Anthropic: $model")
                    } else {
                        AiResult.Error("Anthropic returned an empty response.")
                    }
                } else {
                    val errorMsg = parseErrorMessage(responseBody)
                    AiResult.Error("Anthropic Error (HTTP $code): $errorMsg", isQuotaOrAuth = code in listOf(401, 403, 429))
                }
            }
        } catch (e: Exception) {
            AiResult.Error("Anthropic execution error: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Executes the Gemini REST prompt with timeout and robust error classification.
     */
    private suspend fun executeGeminiRequest(prompt: String, modelName: String): AiResult = withContext(Dispatchers.IO) {
        val apiKey = SecureKeyManager.getApiKey(context)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext AiResult.Error(
                "Gemini API key is not configured. Please set your key in Settings or BuildConfig.",
                isQuotaOrAuth = true
            )
        }

        val initialModel = when (modelName.trim()) {
            "", "gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-flash-latest" -> "gemini-3.6-flash"
            else -> modelName.trim()
        }

        val modelsToTry = listOf(initialModel, "gemini-3.6-flash", "gemini-1.5-flash", "gemini-2.5-flash").distinct()

        return@withContext try {
            withTimeout(30_000L) {
                var lastResult: AiResult = AiResult.Error("Failed to contact Gemini API")

                for (resolvedModel in modelsToTry) {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:generateContent?key=$apiKey"

                    var code = 0
                    var responseBody = ""
                    val max503Retries = 2

                    for (attempt in 0..max503Retries) {
                        val jsonBody = JSONObject().apply {
                            val contentsArray = JSONArray().apply {
                                val contentObj = JSONObject().apply {
                                    val partsArray = JSONArray().apply {
                                        val partObj = JSONObject().apply {
                                            put("text", prompt)
                                        }
                                        put(partObj)
                                    }
                                    put("parts", partsArray)
                                }
                                put(contentObj)
                            }
                            put("contents", contentsArray)

                            val genConfig = JSONObject().apply {
                                put("temperature", 0.3)
                                put("maxOutputTokens", 120)
                                put("topP", 0.8)
                                put("topK", 20)
                            }
                            put("generationConfig", genConfig)
                        }

                        val request = Request.Builder()
                            .url(url)
                            .post(jsonBody.toString().toRequestBody(jsonMediaType))
                            .build()

                        val (resCode, resBody) = try {
                            httpClient.newCall(request).execute().use { response ->
                                Pair(response.code, response.body?.string() ?: "")
                            }
                        } catch (e: Exception) {
                            Pair(-1, e.message ?: "Network error")
                        }

                        code = resCode
                        responseBody = resBody

                        if (code == 503 && attempt < max503Retries) {
                            Log.w(tag, "Gemini API ($resolvedModel) 503 Service Unavailable on attempt ${attempt + 1}. Retrying in 2 seconds...")
                            kotlinx.coroutines.delay(2000L)
                            continue
                        }
                        break
                    }

                    if (code in 200..299) {
                        val parsedText = parseCandidateText(responseBody)
                        return@withTimeout if (parsedText.isNullOrBlank()) {
                            AiResult.Error("Gemini returned an empty response.")
                        } else {
                            val cleaned = parsedText.removeSurrounding("\"").removeSurrounding("'").trim()
                            AiResult.Success(cleaned, resolvedModel)
                        }
                    }

                    Log.e(tag, "Gemini API ($resolvedModel) failed with HTTP $code: $responseBody")
                    val isAuthOrQuota = code == 400 || code == 401 || code == 403 || code == 429
                    val parsedError = parseErrorMessage(responseBody)
                    lastResult = AiResult.Error("AI Error ($code): $parsedError", isQuotaOrAuth = isAuthOrQuota)

                    val isFallbackEligible = code == -1 || code == 404 || code in 500..599
                    if (!isFallbackEligible) {
                        // For non-recoverable client errors (e.g. 400 bad request, 401/403 auth error, 429 quota limit), stop and report directly
                        return@withTimeout lastResult
                    }
                }

                lastResult
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(tag, "Gemini API request timed out", e)
            AiResult.Error("Request timed out (30s exceeded). Check network connectivity.")
        } catch (e: Exception) {
            Log.e(tag, "Gemini API execution failed", e)
            val msg = e.localizedMessage ?: e.message ?: "Unknown error"
            val isAuthOrQuota = msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    msg.contains("403", ignoreCase = true) ||
                    msg.contains("401", ignoreCase = true) ||
                    msg.contains("429", ignoreCase = true)
            AiResult.Error("AI Error: $msg", isQuotaOrAuth = isAuthOrQuota)
        }
    }

    /**
     * Models to attempt during Gemini key validation in order of preference.
     */
    private val validationCandidateModels = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-pro-preview",
        "gemini-2.5-flash",
        "gemini-3.6-flash"
    )

    /**
     * Auto-detects the provider and triggers a validation check for the detected API upon entry.
     * Upon successful validation, securely saves the key in SharedPreferences, sets active provider,
     * and updates version matching in AppSettings.
     */
    suspend fun validateAndSaveApiKey(candidateKey: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        val trimmed = candidateKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (trimmed.isBlank()) {
            return@withContext GeminiKeyValidationResult.Error("API key cannot be empty.")
        }

        val detectedProvider = ApiProvider.detectProvider(trimmed)
        Log.i(tag, "Auto-detected provider: ${detectedProvider.displayName} (${detectedProvider.id}) for key prefix: ${trimmed.take(6)}")

        when (detectedProvider) {
            ApiProvider.GROQ -> validateGroqKey(trimmed)
            ApiProvider.OPENAI -> validateOpenAiKey(trimmed)
            ApiProvider.ANTHROPIC -> validateAnthropicKey(trimmed)
            ApiProvider.OPENROUTER -> validateOpenRouterKey(trimmed)
            ApiProvider.DEEPSEEK -> validateDeepSeekKey(trimmed)
            ApiProvider.PERPLEXITY -> validatePerplexityKey(trimmed)
            else -> validateGeminiKey(trimmed)
        }
    }

    private suspend fun validateGroqKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/models")
                    .addHeader("Authorization", "Bearer $trimmed")
                    .get()
                    .build()

                val (code, body) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299 || code == 429) {
                    SecureKeyManager.saveProviderApiKey(context, ApiProvider.GROQ, trimmed)
                    SecureKeyManager.setActiveProvider(context, ApiProvider.GROQ)
                    AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GROQ.id, ApiProvider.GROQ.defaultModel)
                    GeminiKeyValidationResult.Success(
                        message = "✓ Groq API Key verified & activated! Model version set to ${ApiProvider.GROQ.defaultModel}.",
                        provider = ApiProvider.GROQ,
                        suggestedModel = ApiProvider.GROQ.defaultModel
                    )
                } else if (code == 401) {
                    GeminiKeyValidationResult.Error("Invalid Groq API Key (gsk_...). Check your key at console.groq.com.")
                } else {
                    GeminiKeyValidationResult.Error("Groq validation failed (HTTP $code): ${parseErrorMessage(body)}")
                }
            }
        } catch (e: Exception) {
            GeminiKeyValidationResult.Error("Groq validation error: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun validateOpenAiKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val request = Request.Builder()
                    .url("https://api.openai.com/v1/models")
                    .addHeader("Authorization", "Bearer $trimmed")
                    .get()
                    .build()

                val (code, body) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299 || code == 429) {
                    SecureKeyManager.saveProviderApiKey(context, ApiProvider.OPENAI, trimmed)
                    SecureKeyManager.setActiveProvider(context, ApiProvider.OPENAI)
                    AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.OPENAI.id, ApiProvider.OPENAI.defaultModel)
                    GeminiKeyValidationResult.Success(
                        message = "✓ OpenAI API Key verified & activated! Model version set to ${ApiProvider.OPENAI.defaultModel}.",
                        provider = ApiProvider.OPENAI,
                        suggestedModel = ApiProvider.OPENAI.defaultModel
                    )
                } else if (code == 401) {
                    GeminiKeyValidationResult.Error("Invalid OpenAI API Key. Check your key at platform.openai.com.")
                } else {
                    GeminiKeyValidationResult.Error("OpenAI validation failed (HTTP $code): ${parseErrorMessage(body)}")
                }
            }
        } catch (e: Exception) {
            GeminiKeyValidationResult.Error("OpenAI validation error: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun validateAnthropicKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/models")
                    .addHeader("x-api-key", trimmed)
                    .addHeader("anthropic-version", "2023-06-01")
                    .get()
                    .build()

                val (code, body) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299 || code == 429) {
                    SecureKeyManager.saveProviderApiKey(context, ApiProvider.ANTHROPIC, trimmed)
                    SecureKeyManager.setActiveProvider(context, ApiProvider.ANTHROPIC)
                    AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.ANTHROPIC.id, ApiProvider.ANTHROPIC.defaultModel)
                    GeminiKeyValidationResult.Success(
                        message = "✓ Anthropic API Key verified & activated! Model version set to ${ApiProvider.ANTHROPIC.defaultModel}.",
                        provider = ApiProvider.ANTHROPIC,
                        suggestedModel = ApiProvider.ANTHROPIC.defaultModel
                    )
                } else if (code == 401) {
                    GeminiKeyValidationResult.Error("Invalid Anthropic API Key. Check your key at console.anthropic.com.")
                } else {
                    GeminiKeyValidationResult.Error("Anthropic validation failed (HTTP $code): ${parseErrorMessage(body)}")
                }
            }
        } catch (e: Exception) {
            GeminiKeyValidationResult.Error("Anthropic validation error: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun validateOpenRouterKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .addHeader("Authorization", "Bearer $trimmed")
                    .get()
                    .build()

                val (code, body) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299 || code == 429) {
                    SecureKeyManager.saveProviderApiKey(context, ApiProvider.OPENROUTER, trimmed)
                    SecureKeyManager.setActiveProvider(context, ApiProvider.OPENROUTER)
                    AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.OPENROUTER.id, ApiProvider.OPENROUTER.defaultModel)
                    GeminiKeyValidationResult.Success(
                        message = "✓ OpenRouter API Key verified & activated! Model version set to ${ApiProvider.OPENROUTER.defaultModel}.",
                        provider = ApiProvider.OPENROUTER,
                        suggestedModel = ApiProvider.OPENROUTER.defaultModel
                    )
                } else {
                    GeminiKeyValidationResult.Error("OpenRouter validation failed (HTTP $code): ${parseErrorMessage(body)}")
                }
            }
        } catch (e: Exception) {
            GeminiKeyValidationResult.Error("OpenRouter validation error: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun validateDeepSeekKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val request = Request.Builder()
                    .url("https://api.deepseek.com/models")
                    .addHeader("Authorization", "Bearer $trimmed")
                    .get()
                    .build()

                val (code, body) = try {
                    httpClient.newCall(request).execute().use { resp ->
                        Pair(resp.code, resp.body?.string() ?: "")
                    }
                } catch (e: Exception) {
                    Pair(-1, e.message ?: "Network error")
                }

                if (code in 200..299 || code == 429) {
                    SecureKeyManager.saveProviderApiKey(context, ApiProvider.DEEPSEEK, trimmed)
                    SecureKeyManager.setActiveProvider(context, ApiProvider.DEEPSEEK)
                    AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.DEEPSEEK.id, ApiProvider.DEEPSEEK.defaultModel)
                    GeminiKeyValidationResult.Success(
                        message = "✓ DeepSeek API Key verified & activated! Model version set to ${ApiProvider.DEEPSEEK.defaultModel}.",
                        provider = ApiProvider.DEEPSEEK,
                        suggestedModel = ApiProvider.DEEPSEEK.defaultModel
                    )
                } else {
                    GeminiKeyValidationResult.Error("DeepSeek validation failed (HTTP $code): ${parseErrorMessage(body)}")
                }
            }
        } catch (e: Exception) {
            GeminiKeyValidationResult.Error("DeepSeek validation error: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun validatePerplexityKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        SecureKeyManager.saveProviderApiKey(context, ApiProvider.PERPLEXITY, trimmed)
        SecureKeyManager.setActiveProvider(context, ApiProvider.PERPLEXITY)
        AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.PERPLEXITY.id, ApiProvider.PERPLEXITY.defaultModel)
        GeminiKeyValidationResult.Success(
            message = "✓ Perplexity API Key saved & activated! Model version set to ${ApiProvider.PERPLEXITY.defaultModel}.",
            provider = ApiProvider.PERPLEXITY,
            suggestedModel = ApiProvider.PERPLEXITY.defaultModel
        )
    }

    private suspend fun validateGeminiKey(trimmed: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        val looksLikeGoogleApiKey = trimmed.startsWith("AIzaSy") && trimmed.length >= 35

        try {
            withTimeout(20_000L) {
                var lastErrorMessage = ""
                var lastCode = 0

                for (model in validationCandidateModels) {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$trimmed"

                    var statusCode = 0
                    var bodyString = ""
                    val max503Retries = 2

                    for (attempt in 0..max503Retries) {
                        val jsonBody = JSONObject().apply {
                            val contentsArray = JSONArray().apply {
                                val contentObj = JSONObject().apply {
                                    val partsArray = JSONArray().apply {
                                        val partObj = JSONObject().apply {
                                            put("text", "ping")
                                        }
                                        put(partObj)
                                    }
                                    put("parts", partsArray)
                                }
                                put(contentObj)
                            }
                            put("contents", contentsArray)
                        }

                        val request = Request.Builder()
                            .url(url)
                            .post(jsonBody.toString().toRequestBody(jsonMediaType))
                            .build()

                        val (code, body) = try {
                            httpClient.newCall(request).execute().use { resp ->
                                Pair(resp.code, resp.body?.string() ?: "")
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Model $model validation network exception: ${e.message}")
                            Pair(-1, e.message ?: "Network error")
                        }

                        statusCode = code
                        bodyString = body

                        if (statusCode == 503 && attempt < max503Retries) {
                            Log.w(tag, "Gemini validation ($model) received 503 on attempt ${attempt + 1}. Retrying in 2 seconds...")
                            kotlinx.coroutines.delay(2000L)
                            continue
                        }
                        break
                    }

                    if (statusCode in 200..299) {
                        val parsedText = parseCandidateText(bodyString)
                        if (!parsedText.isNullOrBlank()) {
                            SecureKeyManager.saveProviderApiKey(context, ApiProvider.GEMINI, trimmed)
                            SecureKeyManager.setActiveProvider(context, ApiProvider.GEMINI)
                            AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GEMINI.id, model)
                            return@withTimeout GeminiKeyValidationResult.Success(
                                message = "✓ Google Gemini API Key validated & activated! Model version set to $model.",
                                provider = ApiProvider.GEMINI,
                                suggestedModel = model
                            )
                        }
                    }

                    lastCode = statusCode
                    lastErrorMessage = if (statusCode > 0) parseErrorMessage(bodyString) else bodyString
                    Log.w(tag, "Model $model validation attempt result (HTTP $statusCode): $lastErrorMessage")

                    if (statusCode == 429 || lastErrorMessage.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || lastErrorMessage.contains("rate limit", ignoreCase = true)) {
                        SecureKeyManager.saveProviderApiKey(context, ApiProvider.GEMINI, trimmed)
                        SecureKeyManager.setActiveProvider(context, ApiProvider.GEMINI)
                        AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GEMINI.id, model)
                        return@withTimeout GeminiKeyValidationResult.Success(
                            message = "✓ Gemini API Key verified & activated (Quota rate-limited). Model set to $model.",
                            provider = ApiProvider.GEMINI,
                            suggestedModel = model
                        )
                    } else if (statusCode == 400 || lastErrorMessage.contains("API_KEY_INVALID", ignoreCase = true)) {
                        return@withTimeout GeminiKeyValidationResult.Error("Invalid Gemini API key. Please check your key from Google AI Studio.")
                    } else if (statusCode == 403 || lastErrorMessage.contains("PERMISSION_DENIED", ignoreCase = true)) {
                        return@withTimeout GeminiKeyValidationResult.Error("Permission denied for this key. Ensure Gemini API is enabled.")
                    }
                }

                if (lastCode == 503 || lastErrorMessage.contains("UNAVAILABLE", ignoreCase = true) || lastErrorMessage.contains("overloaded", ignoreCase = true)) {
                    if (looksLikeGoogleApiKey) {
                        SecureKeyManager.saveProviderApiKey(context, ApiProvider.GEMINI, trimmed)
                        SecureKeyManager.setActiveProvider(context, ApiProvider.GEMINI)
                        AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GEMINI.id, "gemini-3.5-flash")
                        return@withTimeout GeminiKeyValidationResult.Success(
                            message = "✓ Gemini API key saved & activated! (Google servers overloaded, retry shortly).",
                            provider = ApiProvider.GEMINI,
                            suggestedModel = "gemini-3.5-flash"
                        )
                    } else {
                        return@withTimeout GeminiKeyValidationResult.Error("Google Gemini service is currently unavailable (HTTP 503). Please try again in a few moments.")
                    }
                }

                if (looksLikeGoogleApiKey) {
                    SecureKeyManager.saveProviderApiKey(context, ApiProvider.GEMINI, trimmed)
                    SecureKeyManager.setActiveProvider(context, ApiProvider.GEMINI)
                    AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GEMINI.id, "gemini-3.5-flash")
                    GeminiKeyValidationResult.Success(
                        message = "✓ Gemini API key saved & activated! (Network verification timed out).",
                        provider = ApiProvider.GEMINI,
                        suggestedModel = "gemini-3.5-flash"
                    )
                } else {
                    GeminiKeyValidationResult.Error("Validation failed (HTTP $lastCode): $lastErrorMessage")
                }
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(tag, "Gemini key validation network/runtime error: $msg", e)

            if (msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || msg.contains("429", ignoreCase = true)) {
                SecureKeyManager.saveProviderApiKey(context, ApiProvider.GEMINI, trimmed)
                SecureKeyManager.setActiveProvider(context, ApiProvider.GEMINI)
                AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GEMINI.id, "gemini-3.5-flash")
                GeminiKeyValidationResult.Success(
                    message = "✓ Gemini API key verified & activated (Quota rate-limited).",
                    provider = ApiProvider.GEMINI,
                    suggestedModel = "gemini-3.5-flash"
                )
            } else if (looksLikeGoogleApiKey || msg.contains("timeout", ignoreCase = true)) {
                SecureKeyManager.saveProviderApiKey(context, ApiProvider.GEMINI, trimmed)
                SecureKeyManager.setActiveProvider(context, ApiProvider.GEMINI)
                AppSettingsRepository.getInstance(context).setAiProvider(ApiProvider.GEMINI.id, "gemini-3.5-flash")
                GeminiKeyValidationResult.Success(
                    message = "✓ Gemini API key saved & activated! (Network verification timed out).",
                    provider = ApiProvider.GEMINI,
                    suggestedModel = "gemini-3.5-flash"
                )
            } else {
                GeminiKeyValidationResult.Error("Validation failed: $msg")
            }
        }
    }

    private fun parseCandidateText(jsonString: String): String? {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            val firstPart = parts.getJSONObject(0)
            firstPart.optString("text", null)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Gemini response JSON", e)
            null
        }
    }

    private fun parseErrorMessage(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            val errorObj = root.optJSONObject("error")
            errorObj?.optString("message", jsonString) ?: jsonString
        } catch (e: Exception) {
            jsonString
        }
    }
}
