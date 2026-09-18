package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.repository.AppSettings
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
    data class Success(val message: String) : GeminiKeyValidationResult()
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

        executeGeminiRequest(prompt, settings.modelName)
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

        executeGeminiRequest(prompt, settings.modelName)
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

        executeGeminiRequest(prompt, settings.modelName)
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

        // Fast Local Rule Engine check (0ms latency response)
        FastLocalRuleEngine.evaluate(userQuery)?.let { fastReply ->
            Log.i(tag, "Fast Local Rule Engine matched instant voice response: \"$fastReply\" (0ms API latency saved)")
            return@withContext AiResult.Success(fastReply, "Fast Local Rule Engine")
        }

        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant.")
            appendLine("User Query: \"$userQuery\"")
            appendLine()
            appendLine("SYSTEM RULES:")
            appendLine("1. Keep all responses concise, direct, and conversational (1-3 sentences max).")
            appendLine("2. Format text specifically for Text-to-Speech engines: avoid complex Markdown, bullet points, code blocks, or special symbols.")
            appendLine("3. Speak naturally in clear, engaging Hindi or English based on user input.")
            appendLine("4. Avoid unnecessary fillers or polite intros; provide answers immediately.")
            appendLine("5. Output ONLY the response text directly.")
        }

        executeGeminiRequest(prompt, settings.modelName)
    }

    /**
     * Streams voice response chunks from Gemini API asynchronously as text tokens are generated.
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

        // Fast Local Rule Engine check (0ms latency response)
        FastLocalRuleEngine.evaluate(trimmedQuery)?.let { fastReply ->
            Log.i(tag, "Fast Local Rule Engine matched streaming voice query: \"$fastReply\" (0ms API latency saved)")
            emit(fastReply)
            return@flow
        }

        val apiKey = SecureKeyManager.getApiKey(context)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            emit("Gemini API key is not configured. Please set your key in Settings.")
            return@flow
        }

        val prompt = buildString {
            appendLine("You are MAX, an intelligent and helpful AI assistant.")
            appendLine("User Query: \"$trimmedQuery\"")
            appendLine()
            appendLine("SYSTEM RULES:")
            appendLine("1. Keep all responses concise, direct, and conversational (1-3 sentences max).")
            appendLine("2. Format text specifically for Text-to-Speech engines: avoid complex Markdown, bullet points, code blocks, or special symbols.")
            appendLine("3. Speak naturally in clear, engaging Hindi or English based on user input.")
            appendLine("4. Avoid unnecessary fillers or polite intros; provide answers immediately.")
            appendLine("5. Output ONLY the response text directly.")
        }

        val modelName = settings.modelName.trim()
        val initialModel = when (modelName) {
            "", "gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-flash-latest" -> "gemini-3.6-flash"
            else -> modelName
        }

        val modelsToTry = listOf(initialModel, "gemini-3.6-flash", "gemini-1.5-flash", "gemini-2.5-flash").distinct()

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

        // Fallback to non-streaming if streaming endpoints were unreachable
        when (val nonStreamRes = generateMaxVoiceResponse(trimmedQuery, settings)) {
            is AiResult.Success -> emit(nonStreamRes.text)
            is AiResult.Error -> emit("Error generating voice response: ${nonStreamRes.message}")
        }
    }.flowOn(Dispatchers.IO)

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
     * Models to attempt during key validation in order of preference.
     */
    private val validationCandidateModels = listOf(
        "gemini-3.6-flash",
        "gemini-1.5-flash",
        "gemini-2.5-flash"
    )

    /**
     * Validates a candidate Gemini API key by making a test request via native REST.
     * If validated successfully or if rate-limited / network slow, allows saving smoothly.
     */
    suspend fun validateAndSaveApiKey(candidateKey: String): GeminiKeyValidationResult = withContext(Dispatchers.IO) {
        val trimmed = candidateKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (trimmed.isBlank()) {
            return@withContext GeminiKeyValidationResult.Error("Gemini API key cannot be empty.")
        }

        // Quick sanity check for Google AI Studio API key format (typically AIzaSy...)
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
                            SecureKeyManager.saveApiKey(context, trimmed)
                            return@withTimeout GeminiKeyValidationResult.Success("Gemini API key validated and saved successfully!")
                        }
                    }

                    lastCode = statusCode
                    lastErrorMessage = if (statusCode > 0) parseErrorMessage(bodyString) else bodyString
                    Log.w(tag, "Model $model validation attempt result (HTTP $statusCode): $lastErrorMessage")

                    if (statusCode == 429 || lastErrorMessage.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || lastErrorMessage.contains("rate limit", ignoreCase = true)) {
                        // Key is valid and recognized by Google servers, but currently throttled/rate-limited
                        SecureKeyManager.saveApiKey(context, trimmed)
                        return@withTimeout GeminiKeyValidationResult.Success("Gemini API key verified & saved (Quota rate-limited).")
                    } else if (statusCode == 400 || lastErrorMessage.contains("API_KEY_INVALID", ignoreCase = true)) {
                        return@withTimeout GeminiKeyValidationResult.Error("Invalid Gemini API key. Please check your key from Google AI Studio.")
                    } else if (statusCode == 403 || lastErrorMessage.contains("PERMISSION_DENIED", ignoreCase = true)) {
                        return@withTimeout GeminiKeyValidationResult.Error("Permission denied for this key. Ensure Gemini API is enabled.")
                    }
                    // If 404 / 503, continue loop to next candidate model
                }

                if (lastCode == 503 || lastErrorMessage.contains("UNAVAILABLE", ignoreCase = true) || lastErrorMessage.contains("overloaded", ignoreCase = true)) {
                    if (looksLikeGoogleApiKey) {
                        SecureKeyManager.saveApiKey(context, trimmed)
                        return@withTimeout GeminiKeyValidationResult.Success("Gemini API key saved! (Google servers temporarily overloaded, retry shortly).")
                    } else {
                        return@withTimeout GeminiKeyValidationResult.Error("Google Gemini service is currently unavailable (HTTP 503). Please try again in a few moments.")
                    }
                }

                if (looksLikeGoogleApiKey) {
                    SecureKeyManager.saveApiKey(context, trimmed)
                    GeminiKeyValidationResult.Success("Gemini API key saved! (Network verification timed out).")
                } else {
                    GeminiKeyValidationResult.Error("Validation failed (HTTP $lastCode): $lastErrorMessage")
                }
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(tag, "Gemini key validation network/runtime error: $msg", e)

            if (msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || msg.contains("429", ignoreCase = true)) {
                SecureKeyManager.saveApiKey(context, trimmed)
                GeminiKeyValidationResult.Success("Gemini API key verified & saved (Quota rate-limited).")
            } else if (looksLikeGoogleApiKey || msg.contains("timeout", ignoreCase = true)) {
                SecureKeyManager.saveApiKey(context, trimmed)
                GeminiKeyValidationResult.Success("Gemini API key saved! (Network verification timed out).")
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
