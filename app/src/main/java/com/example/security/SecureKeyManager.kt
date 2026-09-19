package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.example.ai.ApiProvider

/**
 * Secure storage manager for multi-provider API keys (Gemini, Groq, OpenAI, etc.) and sensitive credentials.
 * Implements persistent SharedPreferences with fallback to EncryptedSharedPreferences and BuildConfig.
 */
object SecureKeyManager {
    private const val TAG = "SecureKeyManager"
    private const val PREFS_FILE = "secure_gemini_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_LEGACY_KEY = "encrypted_gemini_api_key"
    private const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    private const val KEY_ELEVENLABS_LEGACY_KEY = "encrypted_elevenlabs_api_key"
    private const val KEY_ACTIVE_PROVIDER = "active_ai_provider"

    private fun getStandardPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE + "_enc",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, relying on standard SharedPreferences: ${e.message}")
            null
        }
    }

    /**
     * Gets the currently active AI provider. Defaults to GEMINI if none set.
     */
    fun getActiveProvider(context: Context): ApiProvider {
        val stdPrefs = getStandardPrefs(context)
        val providerId = stdPrefs.getString(KEY_ACTIVE_PROVIDER, ApiProvider.GEMINI.id)
        return ApiProvider.fromId(providerId)
    }

    /**
     * Sets the currently active AI provider and commits to SharedPreferences.
     */
    fun setActiveProvider(context: Context, provider: ApiProvider) {
        val stdPrefs = getStandardPrefs(context)
        stdPrefs.edit()
            .putString(KEY_ACTIVE_PROVIDER, provider.id)
            .commit()
        Log.i(TAG, "Active AI provider set to ${provider.displayName} (${provider.id})")
    }

    /**
     * Retrieves the API key for a specific provider.
     */
    fun getProviderApiKey(context: Context, provider: ApiProvider): String {
        val stdPrefs = getStandardPrefs(context)
        val prefKey = "api_key_${provider.id}"
        var key = stdPrefs.getString(prefKey, null)?.trim()

        if (key.isNullOrEmpty() && provider == ApiProvider.GEMINI) {
            key = stdPrefs.getString(KEY_GEMINI_API_KEY, null)?.trim()
            if (key.isNullOrEmpty()) {
                key = stdPrefs.getString(KEY_GEMINI_LEGACY_KEY, null)?.trim()
            }
            if (key.isNullOrEmpty()) {
                val buildConfigKey = try {
                    BuildConfig.GEMINI_API_KEY.trim()
                } catch (e: Exception) {
                    ""
                }
                if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
                    key = buildConfigKey
                }
            }
        }

        return key ?: ""
    }

    /**
     * Saves an API key for a specific provider into SharedPreferences.
     */
    fun saveProviderApiKey(context: Context, provider: ApiProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        val stdPrefs = getStandardPrefs(context)
        val prefKey = "api_key_${provider.id}"

        stdPrefs.edit()
            .putString(prefKey, trimmed)
            .putString(KEY_ACTIVE_PROVIDER, provider.id)
            .apply {
                if (provider == ApiProvider.GEMINI) {
                    putString(KEY_GEMINI_API_KEY, trimmed)
                    putString(KEY_GEMINI_LEGACY_KEY, trimmed)
                }
            }
            .commit()

        try {
            getEncryptedPrefs(context)?.edit()?.putString(prefKey, trimmed)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not mirror key to encrypted prefs: ${e.message}")
        }
        Log.i(TAG, "API key for ${provider.displayName} successfully saved and activated.")
    }

    /**
     * Retrieves the active API Key based on current provider or legacy fallback.
     */
    fun getApiKey(context: Context): String {
        val activeProvider = getActiveProvider(context)
        val providerKey = getProviderApiKey(context, activeProvider)
        if (providerKey.isNotEmpty()) {
            return providerKey
        }

        // Fallback to standard Gemini key
        return getProviderApiKey(context, ApiProvider.GEMINI)
    }

    /**
     * Stores an updated API key into SharedPreferences with immediate auto-detection and activation.
     */
    fun saveApiKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        val detectedProvider = ApiProvider.detectProvider(trimmed)
        saveProviderApiKey(context, detectedProvider, trimmed)
    }

    /**
     * Clears the custom API key stored for the active provider.
     */
    fun clearCustomApiKey(context: Context) {
        val activeProvider = getActiveProvider(context)
        val stdPrefs = getStandardPrefs(context)
        val prefKey = "api_key_${activeProvider.id}"

        stdPrefs.edit()
            .remove(prefKey)
            .apply {
                if (activeProvider == ApiProvider.GEMINI) {
                    remove(KEY_GEMINI_API_KEY)
                    remove(KEY_GEMINI_LEGACY_KEY)
                }
            }
            .commit()

        try {
            getEncryptedPrefs(context)?.edit()?.remove(prefKey)?.apply()
        } catch (e: Exception) {
            // Ignore
        }
        Log.i(TAG, "API key for ${activeProvider.displayName} cleared from SharedPreferences.")
    }

    /**
     * Retrieves the active ElevenLabs API Key.
     */
    fun getElevenLabsApiKey(context: Context): String {
        val stdPrefs = getStandardPrefs(context)
        var storedKey = stdPrefs.getString(KEY_ELEVENLABS_API_KEY, null)?.trim()
        if (storedKey.isNullOrEmpty()) {
            storedKey = stdPrefs.getString(KEY_ELEVENLABS_LEGACY_KEY, null)?.trim()
        }
        return storedKey ?: ""
    }

    /**
     * Stores an updated ElevenLabs API Key into SharedPreferences with immediate commit.
     */
    fun saveElevenLabsApiKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        val stdPrefs = getStandardPrefs(context)
        stdPrefs.edit()
            .putString(KEY_ELEVENLABS_API_KEY, trimmed)
            .putString(KEY_ELEVENLABS_LEGACY_KEY, trimmed)
            .commit()

        try {
            getEncryptedPrefs(context)?.edit()?.putString(KEY_ELEVENLABS_API_KEY, trimmed)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not mirror ElevenLabs key to encrypted prefs: ${e.message}")
        }
    }

    /**
     * Returns true if a valid custom or build-configured key is available.
     */
    fun hasValidApiKey(context: Context): Boolean {
        val key = getApiKey(context)
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }
}
