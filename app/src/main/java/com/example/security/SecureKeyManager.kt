package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

/**
 * Secure storage manager for Gemini API keys and sensitive credentials.
 * Implements AndroidX EncryptedSharedPreferences with fallback to BuildConfig.
 */
object SecureKeyManager {
    private const val TAG = "SecureKeyManager"
    private const val PREFS_FILE = "secure_gemini_prefs"
    private const val KEY_GEMINI_API_KEY = "encrypted_gemini_api_key"

    private fun getEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, using standard fallback", e)
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    /**
     * Securely retrieves the active Gemini API Key.
     * Checks EncryptedSharedPreferences first; falls back to BuildConfig.GEMINI_API_KEY.
     */
    fun getApiKey(context: Context): String {
        val storedKey = getEncryptedPrefs(context)?.getString(KEY_GEMINI_API_KEY, null)?.trim()
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
     * Securely stores an updated API key into EncryptedSharedPreferences.
     */
    fun saveApiKey(context: Context, apiKey: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_GEMINI_API_KEY, apiKey.trim())?.apply()
    }

    /**
     * Clears the custom API key stored in EncryptedSharedPreferences.
     */
    fun clearCustomApiKey(context: Context) {
        getEncryptedPrefs(context)?.edit()?.remove(KEY_GEMINI_API_KEY)?.apply()
    }

    /**
     * Returns true if a valid custom or build-configured key is available.
     */
    fun hasValidApiKey(context: Context): Boolean {
        val key = getApiKey(context)
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }
}
