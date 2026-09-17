package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class MaxSttState {
    data object Idle : MaxSttState()
    data object Preparing : MaxSttState()
    data class Listening(val message: String = "Listening for Hindi / English voice input...") : MaxSttState()
    data class Recognized(val text: String) : MaxSttState()
    data class Error(val message: String) : MaxSttState()
}

/**
 * Native Speech-to-Text (STT) Manager supporting Hindi and English speech inputs using Android's SpeechRecognizer API.
 */
class MaxSttManager(private val context: Context) {
    private val tag = "MaxSttManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val _sttState = MutableStateFlow<MaxSttState>(MaxSttState.Idle)
    val sttState: StateFlow<MaxSttState> = _sttState.asStateFlow()

    private val _rmsDbLevel = MutableStateFlow(0f)
    val rmsDbLevel: StateFlow<Float> = _rmsDbLevel.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    var onSpeechRecognizedListener: ((recognizedText: String) -> Unit)? = null
    var onErrorListener: ((errorMsg: String) -> Unit)? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "SpeechRecognizer ready for speech input")
            _sttState.value = MaxSttState.Listening("Speaks in Hindi or English...")
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "User began speaking")
            _sttState.value = MaxSttState.Listening("Listening to speech...")
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsDbLevel.value = rmsdB.coerceAtLeast(0f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "User finished speaking")
            _rmsDbLevel.value = 0f
            _sttState.value = MaxSttState.Listening("Processing speech...")
        }

        override fun onError(error: Int) {
            val errorMsg = getSpeechErrorString(error)
            Log.w(tag, "SpeechRecognizer error: $errorMsg ($error)")
            isListening = false
            _rmsDbLevel.value = 0f
            _sttState.value = MaxSttState.Error(errorMsg)
            onErrorListener?.invoke(errorMsg)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            _rmsDbLevel.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0].trim()
                Log.i(tag, "Speech recognized successfully: $recognizedText")
                _sttState.value = MaxSttState.Recognized(recognizedText)
                _partialText.value = recognizedText
                onSpeechRecognizedListener?.invoke(recognizedText)
            } else {
                Log.w(tag, "No recognition results received")
                _sttState.value = MaxSttState.Error("No speech detected. Please try again.")
                onErrorListener?.invoke("No speech detected. Please try again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partial = matches[0].trim()
                _partialText.value = partial
                Log.d(tag, "Partial recognition: $partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Starts native speech recognition supporting Hindi (hi-IN) and English (en-US).
     */
    fun startListening(preferredLanguage: String = "hi-IN") {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val error = "RECORD_AUDIO permission missing. Please grant microphone access."
            Log.e(tag, error)
            _sttState.value = MaxSttState.Error(error)
            onErrorListener?.invoke(error)
            return
        }

        mainHandler.post {
            try {
                stopListening()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    val error = "Speech recognition service is not available on this device."
                    Log.e(tag, error)
                    _sttState.value = MaxSttState.Error(error)
                    onErrorListener?.invoke(error)
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguage)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-US", "en-IN"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                }

                _sttState.value = MaxSttState.Preparing
                _partialText.value = ""
                isListening = true
                speechRecognizer?.startListening(intent)
                Log.d(tag, "SpeechRecognizer started listening with language $preferredLanguage...")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize SpeechRecognizer: ${e.message}", e)
                isListening = false
                val errorMsg = "Speech recognition start failed: ${e.localizedMessage}"
                _sttState.value = MaxSttState.Error(errorMsg)
                onErrorListener?.invoke(errorMsg)
            }
        }
    }

    /**
     * Stops the active speech recognition session.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening || speechRecognizer != null) {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    isListening = false
                    _rmsDbLevel.value = 0f
                    Log.d(tag, "SpeechRecognizer stopped and resources released")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error stopping SpeechRecognizer: ${e.message}")
            }
        }
    }

    fun isCurrentlyListening(): Boolean = isListening

    private fun getSpeechErrorString(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client side error occurred."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions for speech recognition."
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error. Check internet connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timed out. Please try again."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found. Speak clearly into mic."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Retrying..."
            SpeechRecognizer.ERROR_SERVER -> "Server error from speech recognition service."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (timeout)."
            else -> "Speech recognition error code: $errorCode"
        }
    }
}
