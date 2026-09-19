package com.example.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import java.io.File

sealed class ElevenLabsResult {
    data class Success(val audioFile: File) : ElevenLabsResult()
    data class Error(val message: String) : ElevenLabsResult()
}

/**
 * Service for generating natural speech using Android's native MAX Native Text-to-Speech engine.
 * Maintained as a compatibility adapter over MAXNativeTTS.
 */
class ElevenLabsTtsService(private val context: Context) {
    private val tag = "MAXNativeTTSAdapter"
    private val nativeTts = MAXNativeTTS(context)

    val isPlayingAudio: StateFlow<Boolean> = nativeTts.isSpeaking

    fun speak(
        text: String,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onCompletion: (() -> Unit)? = null
    ) {
        nativeTts.speak(
            text = text,
            speechRate = speechRate,
            speechPitch = speechPitch,
            onDone = onCompletion
        )
    }

    suspend fun generateSpeech(
        text: String,
        voiceId: String = "hindi_voice",
        modelId: String = "native_tts",
        apiKeyOverride: String? = null
    ): ElevenLabsResult {
        // Direct speak with Android native TTS engine
        nativeTts.speak(text)
        val dummyFile = File(context.cacheDir, "native_tts.mp3")
        return ElevenLabsResult.Success(dummyFile)
    }

    suspend fun playAudio(audioFile: File, onCompletion: (() -> Unit)? = null) {
        onCompletion?.invoke()
    }

    fun stopAudio() {
        nativeTts.stop()
    }

    fun shutdown() {
        nativeTts.shutdown()
    }
}

