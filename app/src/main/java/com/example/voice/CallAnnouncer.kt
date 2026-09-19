package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.PhoneNumberUtils
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TtsSpan
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Text-to-Speech engine for announcing incoming callers.
 * Handles audio parameters, repeat cadence, dynamic phone number formatting using PhoneNumberUtils and TtsSpan,
 * and UtteranceProgressListener to coordinate with speech recognition and UI state.
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
        val announcement = buildAnnouncementCharSequence(callerNameOrNumber, template, repeatCount)
        speak(announcement, speechRate, speechPitch, onDone)
    }

    fun speak(
        text: CharSequence,
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
        /**
         * Formats a caller identifier (name or raw phone number).
         * If the input is a phone number, uses PhoneNumberUtils and TtsSpan.TelephoneBuilder
         * to produce natural spoken digit cadence and prevent choppy digit-by-digit reading.
         */
        fun formatPhoneNumberForSpeech(rawInput: String, defaultCountryIso: String? = null): CharSequence {
            val trimmed = rawInput.trim()
            val digitsCount = trimmed.count { it.isDigit() }
            val isPhoneLike = digitsCount >= 7 || trimmed.startsWith("+") ||
                    (digitsCount >= 3 && (trimmed.contains("-") || trimmed.contains("(") || trimmed.contains(")")))

            if (!isPhoneLike) {
                return trimmed
            }

            val country = defaultCountryIso ?: Locale.getDefault().country.ifEmpty { "US" }
            val formatted = PhoneNumberUtils.formatNumber(trimmed, country) ?: formatDigitsToSpokenGroups(trimmed)

            val spannable = SpannableString(formatted)
            try {
                val ttsSpan = TtsSpan.TelephoneBuilder()
                    .setNumberParts(formatted)
                    .build()
                spannable.setSpan(ttsSpan, 0, formatted.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } catch (e: Exception) {
                Log.w("CallAnnouncer", "Could not attach TtsSpan: ${e.message}")
            }
            return spannable
        }

        /**
         * Fallback grouping for phone numbers to ensure TTS reads them as natural number chunks (e.g. area code, prefix, line)
         * instead of robotic single-digit enumerations.
         */
        fun formatDigitsToSpokenGroups(number: String): String {
            val clean = number.filter { it.isDigit() || it == '+' }
            if (clean.length == 10 && !clean.startsWith("+")) {
                return "${clean.substring(0, 3)}-${clean.substring(3, 6)}-${clean.substring(6)}"
            } else if (clean.length == 11 && clean.startsWith("1")) {
                return "+1 ${clean.substring(1, 4)}-${clean.substring(4, 7)}-${clean.substring(7)}"
            } else if (clean.length > 6) {
                return clean.chunked(3).joinToString(" ")
            }
            return number
        }

        /**
         * Builds a styled CharSequence with natural telephone span formatting embedded in the announcement template.
         */
        fun buildAnnouncementCharSequence(
            callerNameOrNumber: String,
            template: String = "Incoming call from {name}",
            repeatCount: Int = 1
        ): CharSequence {
            val formattedCaller = formatPhoneNumberForSpeech(callerNameOrNumber)
            val singleAnnouncement = SpannableStringBuilder()

            if (template.contains("{name}")) {
                val parts = template.split("{name}")
                if (parts.isNotEmpty()) {
                    singleAnnouncement.append(parts[0])
                    singleAnnouncement.append(formattedCaller)
                    if (parts.size > 1) {
                        singleAnnouncement.append(parts[1])
                    }
                } else {
                    singleAnnouncement.append(formattedCaller)
                }
            } else {
                singleAnnouncement.append("$template: ")
                singleAnnouncement.append(formattedCaller)
            }

            val fullAnnouncement = SpannableStringBuilder()
            val totalRepeats = repeatCount.coerceAtLeast(1)
            for (i in 1..totalRepeats) {
                if (i > 1) {
                    fullAnnouncement.append(". ")
                }
                fullAnnouncement.append(singleAnnouncement)
            }

            return fullAnnouncement
        }

        fun buildAnnouncementText(
            callerNameOrNumber: String,
            template: String = "Incoming call from {name}",
            repeatCount: Int = 1
        ): String {
            return buildAnnouncementCharSequence(callerNameOrNumber, template, repeatCount).toString()
        }
    }
}
