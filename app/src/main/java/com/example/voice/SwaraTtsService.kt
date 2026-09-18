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
 * Native Text-to-Speech service using Android's built-in TextToSpeech engine,
 * specifically configured to use the 'Swara' voice (or Hindi/English native voice fallback).
 * Supports immediate streaming playback of phrase/sentence chunks as Gemini generates responses.
 */
class SwaraTtsService(private val context: Context) : TextToSpeech.OnInitListener {
    private val tag = "SwaraTtsService"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onSpeechDoneCallback: (() -> Unit)? = null
    private var lastUtteranceId: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Swara TextToSpeech: ${e.message}", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                configureSwaraVoice(engine)
                isInitialized = true
                setupUtteranceListener()
                Log.i(tag, "Swara TextToSpeech service initialized successfully.")
            }
        } else {
            Log.e(tag, "Swara TextToSpeech initialization failed with status: $status")
        }
    }

    private fun configureSwaraVoice(engine: TextToSpeech) {
        val voices = try { engine.voices } catch (e: Exception) { null }
        val swaraVoice = voices?.firstOrNull { voice ->
            voice.name.lowercase(Locale.ROOT).contains("swara")
        } ?: voices?.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.ROOT)
            voice.locale.language == "hi" || name.contains("hi-in") || name.contains("hi_in")
        }

        if (swaraVoice != null) {
            try {
                engine.voice = swaraVoice
                Log.i(tag, "Configured Swara TTS voice: ${swaraVoice.name}")
            } catch (e: Exception) {
                Log.w(tag, "Failed to set Swara voice, falling back to hi-IN locale: ${e.message}")
                engine.language = Locale("hi", "IN")
            }
        } else {
            engine.language = Locale("hi", "IN")
            Log.i(tag, "Swara voice not explicitly listed, defaulting to hi-IN locale.")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) {
                    _isSpeaking.value = false
                    val cb = onSpeechDoneCallback
                    onSpeechDoneCallback = null
                    cb?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                if (utteranceId == lastUtteranceId) {
                    val cb = onSpeechDoneCallback
                    onSpeechDoneCallback = null
                    cb?.invoke()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                if (utteranceId == lastUtteranceId) {
                    val cb = onSpeechDoneCallback
                    onSpeechDoneCallback = null
                    cb?.invoke()
                }
            }
        })
    }

    /**
     * Speaks the provided text using Android native Swara TTS.
     * @param text Speech text content
     * @param queueMode TextToSpeech.QUEUE_FLUSH (interrupts current) or TextToSpeech.QUEUE_ADD (appends to stream)
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        if (!isInitialized || tts == null) {
            Log.w(tag, "Swara TTS not ready yet, re-initializing engine...")
            initTts()
            onDone?.invoke()
            return
        }

        try {
            if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                this.onSpeechDoneCallback = onDone
            }
            val utteranceId = "swara_tts_${UUID.randomUUID()}"
            this.lastUtteranceId = utteranceId

            tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
            tts?.setPitch(speechPitch.coerceIn(0.5f, 2.0f))

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            }

            _isSpeaking.value = true
            tts?.speak(cleanText, queueMode, params, utteranceId)
        } catch (e: Exception) {
            Log.e(tag, "Exception during Swara TTS speak: ${e.message}", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    /**
     * Queues a streaming text chunk to speak immediately as soon as words are generated.
     */
    fun speakChunk(chunk: String, isFirstChunk: Boolean = false) {
        val clean = chunk.trim()
        if (clean.isBlank()) return
        val mode = if (isFirstChunk) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        speak(text = clean, queueMode = mode)
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            onSpeechDoneCallback = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping Swara TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down Swara TTS: ${e.message}")
        }
    }
}
