package com.example.voice

import android.content.Context
import android.util.Log
import com.example.security.SecureKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ElevenLabsKeyValidationResult {
    data class Success(val message: String) : ElevenLabsKeyValidationResult()
    data class Error(val message: String) : ElevenLabsKeyValidationResult()
}

/**
 * Manager class responsible for validating and securely persisting the ElevenLabs API Key.
 */
class ElevenLabsKeyManager(private val context: Context) {
    private val tag = "ElevenLabsKeyManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Gets the current saved API key from SecureKeyManager (SharedPreferences).
     */
    fun getApiKey(): String {
        return SecureKeyManager.getElevenLabsApiKey(context)
    }

    /**
     * Checks if a valid non-empty ElevenLabs API key is currently stored.
     */
    fun hasValidApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Validates the provided candidate API key by making a test API request to ElevenLabs API endpoint.
     * If valid (HTTP 200), securely stores it in SharedPreferences and returns Success.
     * If invalid or network fails, returns Error without saving.
     */
    suspend fun validateAndSaveApiKey(candidateKey: String): ElevenLabsKeyValidationResult = withContext(Dispatchers.IO) {
        val trimmedKey = candidateKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ElevenLabsKeyValidationResult.Error("API Key cannot be empty.")
        }

        try {
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/user")
                .addHeader("Accept", "application/json")
                .addHeader("xi-api-key", trimmedKey)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Key is valid! Store securely in SharedPreferences
                    SecureKeyManager.saveElevenLabsApiKey(context, trimmedKey)
                    Log.i(tag, "ElevenLabs API Key successfully validated and saved.")
                    ElevenLabsKeyValidationResult.Success("ElevenLabs API Key validated and saved successfully!")
                } else if (response.code == 401 || response.code == 403) {
                    Log.w(tag, "Validation failed with status ${response.code}")
                    ElevenLabsKeyValidationResult.Error("Invalid API Key. Please verify your ElevenLabs credentials.")
                } else {
                    val errorMsg = response.body?.string() ?: "Validation request failed with HTTP ${response.code}"
                    Log.w(tag, "Validation failed: $errorMsg")
                    ElevenLabsKeyValidationResult.Error("API Validation Failed [${response.code}]: $errorMsg")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error validating ElevenLabs API key: ${e.message}", e)
            ElevenLabsKeyValidationResult.Error("Network error validating key: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Clears the stored ElevenLabs API Key from SharedPreferences.
     */
    fun clearApiKey() {
        SecureKeyManager.saveElevenLabsApiKey(context, "")
    }
}
