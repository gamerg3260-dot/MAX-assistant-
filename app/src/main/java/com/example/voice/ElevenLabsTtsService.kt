package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.security.SecureKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class ElevenLabsResult {
    data class Success(val audioFile: File) : ElevenLabsResult()
    data class Error(val message: String) : ElevenLabsResult()
}

/**
 * Service for generating natural multilingual speech audio using ElevenLabs Text-to-Speech API.
 */
class ElevenLabsTtsService(private val context: Context) {
    private val tag = "ElevenLabsTtsService"
    private val keyManager = ElevenLabsKeyManager(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    suspend fun generateSpeech(
        text: String,
        voiceId: String = "21m00Tcm4TlvDq8ikWAM",
        modelId: String = "eleven_multilingual_v2",
        apiKeyOverride: String? = null
    ): ElevenLabsResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyOverride ?: keyManager.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext ElevenLabsResult.Error("ElevenLabs API key is missing. Please configure and validate it in settings.")
        }

        try {
            val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"

            val voiceSettings = JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            }

            val jsonBody = JSONObject().apply {
                put("text", text)
                put("model_id", modelId)
                put("voice_settings", voiceSettings)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "audio/mpeg")
                .addHeader("Content-Type", "application/json")
                .addHeader("xi-api-key", apiKey)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "No error details"
                    Log.e(tag, "ElevenLabs API Error [${response.code}]: $errBody")
                    return@withContext ElevenLabsResult.Error("API Error ${response.code}: $errBody")
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext ElevenLabsResult.Error("Received empty audio response from ElevenLabs API")
                }

                val tempFile = File(context.cacheDir, "elevenlabs_output.mp3")
                FileOutputStream(tempFile).use { fos ->
                    fos.write(bytes)
                }

                Log.d(tag, "ElevenLabs audio file successfully generated: ${tempFile.length()} bytes")
                ElevenLabsResult.Success(tempFile)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to call ElevenLabs TTS API: ${e.message}", e)
            ElevenLabsResult.Error(e.localizedMessage ?: "Unknown network error")
        }
    }

    suspend fun playAudio(audioFile: File, onCompletion: (() -> Unit)? = null) = withContext(Dispatchers.Main) {
        try {
            stopAudio()
            _isPlayingAudio.value = true
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    _isPlayingAudio.value = false
                    onCompletion?.invoke()
                    stopAudio()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                    _isPlayingAudio.value = false
                    stopAudio()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            _isPlayingAudio.value = false
            Log.e(tag, "Error playing audio file: ${e.message}", e)
            onCompletion?.invoke()
        }
    }

    fun stopAudio() {
        try {
            _isPlayingAudio.value = false
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping media player: ${e.message}")
        }
    }
}
