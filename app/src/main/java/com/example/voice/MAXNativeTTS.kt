package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Native Text-to-Speech engine for MAX Assistant.
 * Initializes Android's native TextToSpeech engine, sets language to Hindi (India) [hi-IN],
 * and provides robust methods for speech synthesis, streaming chunk playback, and lifecycle shutdown.
 */
class MAXNativeTTS(private val context: Context) : TextToSpeech.OnInitListener {
    private val tag = "MAXNativeTTS"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var onSpeechDoneCallback: (() -> Unit)? = null
    private var lastUtteranceId: String? = null

    // Target locale: Hindi (India)
    val hindiLocale = Locale("hi", "IN")

    init {
        initializeTts()
    }

    /**
     * Initializes the Android Native TextToSpeech engine.
     */
    fun initializeTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Android Native TextToSpeech: ${e.message}", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                configureHindiLanguageAndVoice(engine)
                isInitialized = true
                _isReady.value = true
                setupUtteranceProgressListener()
                Log.i(tag, "MAX Native TTS initialized successfully with Hindi (India) [hi-IN].")
            }
        } else {
            Log.e(tag, "MAX Native TTS initialization failed with status code: $status")
            _isReady.value = false
        }
    }

    /**
     * Sets the language to Hindi (India) and selects the best matching Hindi voice if available.
     */
    private fun configureHindiLanguageAndVoice(engine: TextToSpeech) {
        val langResult = engine.setLanguage(hindiLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Hindi (India) [hi-IN] missing or not supported, trying generic Hindi ('hi')...")
            val fallbackHindi = engine.setLanguage(Locale("hi"))
            if (fallbackHindi == TextToSpeech.LANG_MISSING_DATA || fallbackHindi == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Hindi not supported on device, falling back to default locale: ${Locale.getDefault()}")
                engine.language = Locale.getDefault()
            }
        } else {
            Log.i(tag, "Language successfully set to Hindi (India) [hi-IN].")
        }

        // Try selecting a high quality Hindi voice if available in the engine's voice list
        try {
            val voices: Set<Voice>? = engine.voices
            val matchingVoice = voices?.firstOrNull { voice ->
                val name = voice.name.lowercase(Locale.ROOT)
                val lang = voice.locale.language.lowercase(Locale.ROOT)
                val country = voice.locale.country.lowercase(Locale.ROOT)
                (lang == "hi" && country == "in") || name.contains("hi-in") || name.contains("hi_in") || name.contains("hindi")
            } ?: voices?.firstOrNull { voice ->
                voice.locale.language.equals("hi", ignoreCase = true)
            }

            if (matchingVoice != null) {
                engine.voice = matchingVoice
                Log.i(tag, "Configured MAX Native Hindi voice: ${matchingVoice.name}")
            }
        } catch (e: Exception) {
            Log.w(tag, "Voice selection error, using standard locale: ${e.message}")
        }
    }

    /**
     * Sets up UtteranceProgressListener to monitor playback state and invoke completion callbacks.
     */
    private fun setupUtteranceProgressListener() {
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
     * Speaks the provided text using the native Android TTS engine with Hindi (India) configuration.
     *
     * @param text The text to be spoken.
     * @param queueMode TextToSpeech.QUEUE_FLUSH (replaces active speech) or TextToSpeech.QUEUE_ADD (queues speech).
     * @param speechRate Speech speed multiplier (0.5 to 2.0).
     * @param speechPitch Speech pitch multiplier (0.5 to 2.0).
     * @param onDone Optional callback invoked when speech finishes.
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        speechRate: Float = 1.0f,
        speechPitch: Float = 1.0f,
        onDone: (() -> Unit)? = null
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        if (!isInitialized || tts == null) {
            Log.w(tag, "MAX Native TTS not initialized yet. Re-initializing engine...")
            initializeTts()
            onDone?.invoke()
            return
        }

        try {
            if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                this.onSpeechDoneCallback = onDone
            }
            val utteranceId = "max_native_tts_${UUID.randomUUID()}"
            this.lastUtteranceId = utteranceId

            tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
            tts?.setPitch(speechPitch.coerceIn(0.5f, 2.0f))

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            }

            _isSpeaking.value = true
            tts?.speak(cleanText, queueMode, params, utteranceId)
        } catch (e: Exception) {
            Log.e(tag, "Exception during MAX Native TTS speak: ${e.message}", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    /**
     * Queues a streaming text chunk for real-time phrase-by-phrase speech synthesis.
     */
    fun speakChunk(chunk: String, isFirstChunk: Boolean = false) {
        val clean = chunk.trim()
        if (clean.isBlank()) return
        val mode = if (isFirstChunk) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        speak(text = clean, queueMode = mode)
    }

    /**
     * Stops current speech playback immediately.
     */
    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            onSpeechDoneCallback = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping MAX Native TTS: ${e.message}")
        }
    }

    /**
     * Shuts down and cleans up the Android native TextToSpeech engine.
     */
    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isReady.value = false
            Log.i(tag, "MAX Native TTS shut down successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down MAX Native TTS: ${e.message}")
        }
    }

    /**
     * Checks if the TTS engine is currently ready.
     */
    fun isReady(): Boolean = isInitialized && tts != null
}

/**
 * Compatibility alias for legacy references.
 */
typealias SwaraTtsService = MAXNativeTTS
