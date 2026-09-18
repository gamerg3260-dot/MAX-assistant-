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
 * Service for generating natural speech using Android's native Swara Text-to-Speech engine.
 * Maintained as a compatibility adapter over SwaraTtsService.
 */
class ElevenLabsTtsService(private val context: Context) {
    private val tag = "SwaraTtsAdapter"
    private val swaraTts = SwaraTtsService(context)

    val isPlayingAudio: StateFlow<Boolean> = swaraTts.isSpeaking

    fun speak(
        text: String,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onCompletion: (() -> Unit)? = null
    ) {
        swaraTts.speak(
            text = text,
            speechRate = speechRate,
            speechPitch = speechPitch,
            onDone = onCompletion
        )
    }

    suspend fun generateSpeech(
        text: String,
        voiceId: String = "swara_voice",
        modelId: String = "native_swara",
        apiKeyOverride: String? = null
    ): ElevenLabsResult {
        // Direct speak with native Swara TTS
        swaraTts.speak(text)
        val dummyFile = File(context.cacheDir, "swara_tts.mp3")
        return ElevenLabsResult.Success(dummyFile)
    }

    suspend fun playAudio(audioFile: File, onCompletion: (() -> Unit)? = null) {
        onCompletion?.invoke()
    }

    fun stopAudio() {
        swaraTts.stop()
    }
}

