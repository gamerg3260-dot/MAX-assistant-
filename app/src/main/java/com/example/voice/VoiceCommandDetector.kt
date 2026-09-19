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

enum class VoiceCommand {
    ACCEPT,
    REJECT,
    SILENCE,
    UNKNOWN
}

sealed class VoiceDetectorState {
    data object Idle : VoiceDetectorState()
    data object Preparing : VoiceDetectorState()
    data class Listening(val prompt: String = "Listening for voice command...") : VoiceDetectorState()
    data class Recognized(val command: VoiceCommand, val rawText: String) : VoiceDetectorState()
    data class Error(val message: String) : VoiceDetectorState()
}

/**
 * Real-time Speech-to-Text command detector for incoming call voice control.
 * Listens for triggers like "Accept", "Receive", "Reject", "Decline", "Silence".
 */
class VoiceCommandDetector(private val context: Context) {
    private val tag = "VoiceCommandDetector"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    private val _detectorState = MutableStateFlow<VoiceDetectorState>(VoiceDetectorState.Idle)
    val detectorState: StateFlow<VoiceDetectorState> = _detectorState.asStateFlow()

    private val _rmsDbLevel = MutableStateFlow(0f)
    val rmsDbLevel: StateFlow<Float> = _rmsDbLevel.asStateFlow()

    var onCommandListener: ((command: VoiceCommand, rawText: String) -> Unit)? = null
    var onErrorListener: ((errorMsg: String) -> Unit)? = null
    var onListeningStartedListener: (() -> Unit)? = null
    var onListeningEndedListener: (() -> Unit)? = null

    // Configurable keywords
    var isAcceptEnabled: Boolean = true
    var isRejectEnabled: Boolean = true
    var acceptKeywords: List<String> = listOf("accept", "receive", "answer", "yes", "pickup", "take call", "pick up")
    var rejectKeywords: List<String> = listOf("reject", "decline", "disconnect", "no", "ignore", "hang up", "cut call", "drop")
    var silenceKeywords: List<String> = listOf("silence", "mute", "quiet", "stop")

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "SpeechRecognizer onReadyForSpeech")
            _detectorState.value = VoiceDetectorState.Listening("Say 'Accept' or 'Reject'")
            onListeningStartedListener?.invoke()
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "SpeechRecognizer onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsDbLevel.value = rmsdB.coerceAtLeast(0f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "SpeechRecognizer onEndOfSpeech")
            _rmsDbLevel.value = 0f
        }

        override fun onError(error: Int) {
            val errorMsg = getSpeechErrorString(error)
            Log.w(tag, "SpeechRecognizer onError: $errorMsg ($error)")
            isCurrentlyListening = false
            _rmsDbLevel.value = 0f
            _detectorState.value = VoiceDetectorState.Error(errorMsg)
            onErrorListener?.invoke(errorMsg)
            onListeningEndedListener?.invoke()
        }

        override fun onResults(results: Bundle?) {
            handleSpeechResults(results, isPartial = false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            handleSpeechResults(partialResults, isPartial = true)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleSpeechResults(results: Bundle?, isPartial: Boolean) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches.isNullOrEmpty()) {
            if (!isPartial) {
                isCurrentlyListening = false
                _detectorState.value = VoiceDetectorState.Idle
                onListeningEndedListener?.invoke()
            }
            return
        }

        Log.d(tag, "Speech matches (isPartial=$isPartial): $matches")
        val topMatch = matches[0].trim().lowercase(Locale.ROOT)
        val detectedCommand = evaluateCommand(matches)

        if (detectedCommand != VoiceCommand.UNKNOWN) {
            Log.i(tag, "Voice command identified: $detectedCommand from '$topMatch'")
            isCurrentlyListening = false
            _detectorState.value = VoiceDetectorState.Recognized(detectedCommand, topMatch)
            stopListening()
            onCommandListener?.invoke(detectedCommand, topMatch)
            onListeningEndedListener?.invoke()
        } else if (!isPartial) {
            Log.d(tag, "No known command matched in: $matches")
            isCurrentlyListening = false
            _detectorState.value = VoiceDetectorState.Recognized(VoiceCommand.UNKNOWN, topMatch)
            onCommandListener?.invoke(VoiceCommand.UNKNOWN, topMatch)
            onListeningEndedListener?.invoke()
        }
    }

    fun evaluateCommand(phrases: List<String>): VoiceCommand {
        for (rawPhrase in phrases) {
            val phrase = rawPhrase.lowercase(Locale.ROOT).trim()

            // Check Accept keywords
            if (isAcceptEnabled && acceptKeywords.any { phrase.contains(it) || it.contains(phrase) }) {
                return VoiceCommand.ACCEPT
            }

            // Check Reject keywords
            if (isRejectEnabled && rejectKeywords.any { phrase.contains(it) || it.contains(phrase) }) {
                return VoiceCommand.REJECT
            }

            // Check Silence keywords
            if (silenceKeywords.any { phrase.contains(it) || it.contains(phrase) }) {
                return VoiceCommand.SILENCE
            }
        }
        return VoiceCommand.UNKNOWN
    }

    private fun destroySpeechRecognizerInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(tag, "Error destroying speechRecognizer instance: ${e.message}")
        } finally {
            speechRecognizer = null
            isCurrentlyListening = false
        }
    }

    fun startListening(
        timeoutSeconds: Int = 12,
        customAcceptKeywords: String? = null,
        customRejectKeywords: String? = null,
        isAcceptEnabled: Boolean? = null,
        isRejectEnabled: Boolean? = null
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val errorMsg = "Microphone permission (RECORD_AUDIO) missing. Please grant microphone access."
            _detectorState.value = VoiceDetectorState.Error(errorMsg)
            onErrorListener?.invoke(errorMsg)
            return
        }

        isAcceptEnabled?.let { this.isAcceptEnabled = it }
        isRejectEnabled?.let { this.isRejectEnabled = it }

        customAcceptKeywords?.let {
            acceptKeywords = it.split(",").map { k -> k.trim().lowercase(Locale.ROOT) }.filter { k -> k.isNotEmpty() }
        }
        customRejectKeywords?.let {
            rejectKeywords = it.split(",").map { k -> k.trim().lowercase(Locale.ROOT) }.filter { k -> k.isNotEmpty() }
        }

        mainHandler.post {
            try {
                destroySpeechRecognizerInternal()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _detectorState.value = VoiceDetectorState.Error("Speech recognition service is not available on device")
                    onErrorListener?.invoke("Speech recognition service is not available on device")
                    return@post
                }

                _detectorState.value = VoiceDetectorState.Listening("Say 'Accept' or 'Reject'...")
                isCurrentlyListening = true

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, (timeoutSeconds * 1000).toLong())
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, (timeoutSeconds * 1000).toLong())
                }

                recognizer.startListening(intent)
                Log.d(tag, "SpeechRecognizer started listening immediately...")
            } catch (e: Exception) {
                Log.e(tag, "Error launching SpeechRecognizer: ${e.message}", e)
                destroySpeechRecognizerInternal()
                _detectorState.value = VoiceDetectorState.Error("Failed to start voice recognition: ${e.localizedMessage}")
                onErrorListener?.invoke("Failed to start voice recognition: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            destroySpeechRecognizerInternal()
            _detectorState.value = VoiceDetectorState.Idle
            _rmsDbLevel.value = 0f
            Log.d(tag, "SpeechRecognizer stopped and cleaned up")
        }
    }

    fun isListening(): Boolean = isCurrentlyListening

    private fun getSpeechErrorString(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network communication error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timed out"
            SpeechRecognizer.ERROR_NO_MATCH -> "No voice match recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (timeout)"
            else -> "Speech recognition error code: $errorCode"
        }
    }
}
