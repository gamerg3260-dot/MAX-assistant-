package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.repository.AppSettings
import com.example.security.SecureKeyManager
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed class AiResult {
    data class Success(val text: String, val modelUsed: String) : AiResult()
    data class Error(val message: String, val isQuotaOrAuth: Boolean = false) : AiResult()
}

/**
 * Service handling Gemini AI logic using the official Google Gen AI SDK.
 */
class GeminiAutoResponderService(private val context: Context) {
    private val tag = "GeminiAutoResponder"

    /**
     * Initializes the official Google Gen AI SDK Client using the securely retrieved API key.
     */
    private fun createClient(): Client {
        val apiKey = SecureKeyManager.getApiKey(context)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please set your key in Settings or BuildConfig.")
        }
        return Client.builder().apiKey(apiKey).build()
    }

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

        val prompt = buildString {
            appendLine("You are an automated SMS assistant responding on behalf of the device owner.")
            appendLine("Owner Current Persona: ${settings.selectedPersona}")
            appendLine("Owner Context/Instructions: ${settings.customInstructions}")
            appendLine("Desired Tone: ${settings.responseTone}")
            appendLine("Sender Phone/ID: $senderNumber")
            appendLine("Incoming Message: \"$incomingMessage\"")
            appendLine()
            appendLine("TASK:")
            appendLine("Write a concise, natural, context-aware SMS reply to this incoming message.")
            appendLine("RULES:")
            appendLine("1. Keep it short (maximum 160 characters if possible).")
            appendLine("2. Address the sender's message politely according to the owner's status.")
            appendLine("3. Output ONLY the reply text directly. Do not include quotes, prefixes, or explanations.")
        }

        executeGeminiRequest(prompt, settings.modelName)
    }

    /**
     * Generates a context-aware SMS notification reply for a missed phone call.
     */
    suspend fun generateMissedCallReply(
        callerNumber: String,
        settings: AppSettings
    ): AiResult = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("You are an automated SMS assistant responding to a missed phone call on behalf of the device owner.")
            appendLine("Owner Current Persona: ${settings.selectedPersona}")
            appendLine("Owner Context/Instructions: ${settings.customInstructions}")
            appendLine("Desired Tone: ${settings.responseTone}")
            appendLine("Caller Number: $callerNumber")
            appendLine()
            appendLine("TASK:")
            appendLine("Write a polite, concise SMS notification to the caller explaining that their call was missed.")
            appendLine("RULES:")
            appendLine("1. Keep it very short and helpful (under 140 characters).")
            appendLine("2. Mention that the owner missed their call and will return it or ask them to leave a message.")
            appendLine("3. Output ONLY the reply text directly. Do not include quotes, prefixes, or explanations.")
        }

        executeGeminiRequest(prompt, settings.modelName)
    }

    /**
     * Executes the Gemini prompt with timeout and robust error classification.
     */
    private suspend fun executeGeminiRequest(prompt: String, modelName: String): AiResult {
        return try {
            withTimeout(25_000L) {
                val client = createClient()
                val config = GenerateContentConfig.builder()
                    .temperature(0.7f)
                    .build()

                val response = client.models.generateContent(
                    modelName,
                    prompt,
                    config
                )

                val generatedText = response.text()?.trim()
                if (generatedText.isNullOrEmpty()) {
                    AiResult.Error("Gemini returned an empty response.")
                } else {
                    // Clean up any surrounding quotes or artifact wrappers
                    val cleaned = generatedText.removeSurrounding("\"").removeSurrounding("'")
                    AiResult.Success(cleaned, modelName)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(tag, "Gemini API request timed out", e)
            AiResult.Error("Request timed out (25s exceeded). Check network connectivity.")
        } catch (e: IllegalStateException) {
            Log.e(tag, "Gemini configuration error", e)
            AiResult.Error(e.message ?: "Configuration error", isQuotaOrAuth = true)
        } catch (e: Exception) {
            Log.e(tag, "Gemini API execution failed", e)
            val msg = e.localizedMessage ?: e.message ?: "Unknown error"
            val isAuthOrQuota = msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                    msg.contains("403", ignoreCase = true) ||
                    msg.contains("401", ignoreCase = true)
            AiResult.Error("AI Error: $msg", isQuotaOrAuth = isAuthOrQuota)
        }
    }
}
