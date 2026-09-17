package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

/**
 * Secure storage manager for Gemini API keys and sensitive credentials.
 * Implements persistent SharedPreferences with fallback to EncryptedSharedPreferences and BuildConfig.
 */
object SecureKeyManager {
    private const val TAG = "SecureKeyManager"
    private const val PREFS_FILE = "secure_gemini_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_LEGACY_KEY = "encrypted_gemini_api_key"
    private const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    private const val KEY_ELEVENLABS_LEGACY_KEY = "encrypted_elevenlabs_api_key"

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
     * Retrieves the active Gemini API Key.
     * Checks SharedPreferences first, then encrypted fallback, then BuildConfig.GEMINI_API_KEY.
     */
    fun getApiKey(context: Context): String {
        val stdPrefs = getStandardPrefs(context)
        var storedKey = stdPrefs.getString(KEY_GEMINI_API_KEY, null)?.trim()
        if (storedKey.isNullOrEmpty()) {
            storedKey = stdPrefs.getString(KEY_GEMINI_LEGACY_KEY, null)?.trim()
        }

        if (storedKey.isNullOrEmpty()) {
            storedKey = try {
                getEncryptedPrefs(context)?.getString(KEY_GEMINI_API_KEY, null)?.trim()
            } catch (e: Exception) {
                null
            }
        }

        if (!storedKey.isNullOrEmpty()) {
            return storedKey
        }

        // Fallback to BuildConfig if provided at compile/environment time
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }

        return if (buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }

    /**
     * Stores an updated API key into SharedPreferences with immediate commit.
     */
    fun saveApiKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        val stdPrefs = getStandardPrefs(context)
        stdPrefs.edit()
            .putString(KEY_GEMINI_API_KEY, trimmed)
            .putString(KEY_GEMINI_LEGACY_KEY, trimmed)
            .commit()

        try {
            getEncryptedPrefs(context)?.edit()?.putString(KEY_GEMINI_API_KEY, trimmed)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not mirror key to encrypted prefs: ${e.message}")
        }
        Log.i(TAG, "Gemini API key successfully saved to SharedPreferences.")
    }

    /**
     * Clears the custom API key stored in SharedPreferences.
     */
    fun clearCustomApiKey(context: Context) {
        val stdPrefs = getStandardPrefs(context)
        stdPrefs.edit()
            .remove(KEY_GEMINI_API_KEY)
            .remove(KEY_GEMINI_LEGACY_KEY)
            .commit()

        try {
            getEncryptedPrefs(context)?.edit()?.remove(KEY_GEMINI_API_KEY)?.apply()
        } catch (e: Exception) {
            // Ignore
        }
        Log.i(TAG, "Gemini API key cleared from SharedPreferences.")
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
