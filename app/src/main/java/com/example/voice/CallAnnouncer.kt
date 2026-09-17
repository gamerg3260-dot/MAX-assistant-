package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Text-to-Speech engine for announcing incoming callers.
 * Handles audio parameters, repeat cadence, and UtteranceProgressListener to coordinate with speech recognition.
 */
class CallAnnouncer(private val context: Context) : TextToSpeech.OnInitListener {
    private val tag = "CallAnnouncer"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeechDoneCallback: (() -> Unit)? = null
    private var currentUtteranceId: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TextToSpeech: ${e.message}", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(tag, "Default language not supported for TTS, falling back to US English")
                    engine.language = Locale.US
                }
                isInitialized = true
                setupUtteranceListener()
                Log.d(tag, "TextToSpeech initialized successfully.")
            }
        } else {
            Log.e(tag, "TextToSpeech initialization failed with status: $status")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(tag, "TTS onStart: $utteranceId")
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                Log.d(tag, "TTS onDone: $utteranceId")
                _isSpeaking.value = false
                if (utteranceId == currentUtteranceId) {
                    val cb = onSpeechDoneCallback
                    onSpeechDoneCallback = null
                    cb?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(tag, "TTS onError: $utteranceId")
                _isSpeaking.value = false
                if (utteranceId == currentUtteranceId) {
                    val cb = onSpeechDoneCallback
                    onSpeechDoneCallback = null
                    cb?.invoke()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(tag, "TTS onError code: $errorCode for $utteranceId")
                _isSpeaking.value = false
                if (utteranceId == currentUtteranceId) {
                    val cb = onSpeechDoneCallback
                    onSpeechDoneCallback = null
                    cb?.invoke()
                }
            }
        })
    }

    fun announceCaller(
        callerNameOrNumber: String,
        template: String = "Incoming call from {name}",
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        repeatCount: Int = 1,
        onDone: (() -> Unit)? = null
    ) {
        val announcementText = buildAnnouncementText(callerNameOrNumber, template, repeatCount)
        speak(announcementText, speechRate, speechPitch, onDone)
    }

    fun speak(
        text: String,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS not ready, re-initializing and speaking delayed")
            initTts()
            onDone?.invoke()
            return
        }

        try {
            this.onSpeechDoneCallback = onDone
            val utteranceId = "max_tts_${UUID.randomUUID()}"
            this.currentUtteranceId = utteranceId

            tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
            tts?.setPitch(speechPitch.coerceIn(0.5f, 2.0f))

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_VOICE_CALL)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e(tag, "Exception during speak: ${e.message}", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            onSpeechDoneCallback = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS: ${e.message}")
        }
    }

    companion object {
        fun buildAnnouncementText(
            callerNameOrNumber: String,
            template: String = "Incoming call from {name}",
            repeatCount: Int = 1
        ): String {
            val single = if (template.contains("{name}")) {
                template.replace("{name}", callerNameOrNumber)
            } else {
                "$template: $callerNameOrNumber"
            }
            return if (repeatCount > 1) {
                (1..repeatCount).joinToString(". ") { single }
            } else {
                single
            }
        }
    }
}
