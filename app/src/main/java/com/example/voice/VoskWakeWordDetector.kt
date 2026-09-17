package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

sealed class WakeWordState {
    data object Uninitialized : WakeWordState()
    data object LoadingModel : WakeWordState()
    data object Listening : WakeWordState()
    data object Paused : WakeWordState()
    data class Triggered(val wakePhrase: String = "Hey Max") : WakeWordState()
    data class Error(val message: String) : WakeWordState()
}

/**
 * Continuous Offline Wake-Word ("Hey Max") Detector powered by Vosk,
 * with automatic fallback to Android SpeechRecognizer if native model files are absent.
 */
class VoskWakeWordDetector(private val context: Context) {

    private val tag = "VoskWakeWordDetector"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null

    private var useSpeechRecognizerFallback = false
    private var fallbackRecognizer: SpeechRecognizer? = null

    private var recordingJob: Job? = null
    private var isPaused = false
    private var isRunning = false

    private val _state = MutableStateFlow<WakeWordState>(WakeWordState.Uninitialized)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val _rmsDbLevel = MutableStateFlow(0f)
    val rmsDbLevel: StateFlow<Float> = _rmsDbLevel.asStateFlow()

    var onWakeWordDetected: ((phrase: String) -> Unit)? = null
    var onError: ((errorMessage: String) -> Unit)? = null

    companion object {
        const val SAMPLE_RATE = 16000
        const val WAKE_PHRASE_GRAMMAR = "[\"hey max\", \"hey\", \"max\", \"[unk]\"]"
    }

    /**
     * Initializes the Vosk model from assets or activates Android SpeechRecognizer fallback.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (voskModel != null && voskRecognizer != null) {
            return@withContext true
        }
        if (useSpeechRecognizerFallback) {
            return@withContext true
        }

        _state.value = WakeWordState.LoadingModel
        Log.i(tag, "Initializing Vosk model for 'Hey Max' wake-word spotting...")

        try {
            val modelDir = extractOrGetModelDir()
            val hasModelBinaryFiles = modelDir != null && modelDir.exists() && (
                File(modelDir, "am/final.mdl").exists() ||
                File(modelDir, "final.mdl").exists() ||
                File(modelDir, "am").exists()
            )

            if (!hasModelBinaryFiles) {
                Log.w(tag, "Vosk offline model binary files ('am/final.mdl') not present in assets. Using Android SpeechRecognizer fallback for 'Hey Max' wake word detection.")
                useSpeechRecognizerFallback = true
                _state.value = WakeWordState.Listening
                return@withContext true
            }

            voskModel = Model(modelDir.absolutePath)
            voskRecognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat(), WAKE_PHRASE_GRAMMAR)
            Log.i(tag, "Vosk Model and Recognizer successfully initialized with grammar: $WAKE_PHRASE_GRAMMAR")
            _state.value = WakeWordState.Listening
            true
        } catch (e: Exception) {
            Log.w(tag, "Vosk native model load exception (${e.message}). Activating Android SpeechRecognizer fallback for 'Hey Max'.")
            useSpeechRecognizerFallback = true
            _state.value = WakeWordState.Listening
            true
        }
    }

    /**
     * Unpacks assets/model-en into context.filesDir/model-en if not already unpacked.
     */
    private fun extractOrGetModelDir(): File? {
        val targetDir = File(context.filesDir, "model-en")
        val am = context.assets

        try {
            copyAssetFolder(am, "model-en", targetDir)
            if (targetDir.exists() && (targetDir.listFiles()?.isNotEmpty() == true)) {
                return targetDir
            }
            return targetDir
        } catch (e: Exception) {
            Log.e(tag, "Failed copying model-en assets", e)
            return if (targetDir.exists()) targetDir else null
        }
    }

    private fun copyAssetFolder(am: android.content.res.AssetManager, srcFolder: String, destFolder: File) {
        val files = am.list(srcFolder) ?: return
        if (!destFolder.exists()) {
            destFolder.mkdirs()
        }

        for (filename in files) {
            val srcPath = "$srcFolder/$filename"
            val destFile = File(destFolder, filename)
            val subFiles = am.list(srcPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(am, srcPath, destFile)
            } else {
                if (!destFile.exists() || destFile.length() == 0L) {
                    try {
                        am.open(srcPath).use { inStream ->
                            FileOutputStream(destFile).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    } catch (e: IOException) {
                        Log.w(tag, "Error copying asset $srcPath: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Starts continuous background listening for "Hey Max".
     */
    fun startListening() {
        if (isRunning && (recordingJob?.isActive == true || fallbackRecognizer != null)) {
            isPaused = false
            _state.value = WakeWordState.Listening
            return
        }

        isRunning = true
        isPaused = false

        recordingJob = scope.launch {
            if (voskModel == null || voskRecognizer == null) {
                initialize()
            }

            if (useSpeechRecognizerFallback) {
                Log.i(tag, "Starting SpeechRecognizer fallback for 'Hey Max' wake-word detection...")
                startSpeechRecognizerFallback()
                return@launch
            }

            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val audioBuffer = ShortArray(bufferSize / 2)

            while (isActive && isRunning) {
                if (isPaused) {
                    delay(200)
                    continue
                }

                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.w(tag, "AudioRecord failed to initialize with VOICE_RECOGNITION. Retrying with MIC...")
                        audioRecord?.release()
                        audioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                    }

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(tag, "AudioRecord state not initialized. Retrying in 2 seconds...")
                        delay(2000)
                        continue
                    }

                    audioRecord?.startRecording()
                    _state.value = WakeWordState.Listening
                    Log.i(tag, "Wake-Word detector audio stream started (16kHz Mono 16-bit PCM)")

                    while (isActive && isRunning && !isPaused && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val readCount = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                        if (readCount > 0) {
                            var sum = 0.0
                            for (i in 0 until readCount) {
                                val s = audioBuffer[i]
                                sum += (s * s).toDouble()
                            }
                            val rms = Math.sqrt(sum / readCount)
                            val db = if (rms > 1) (20 * Math.log10(rms)).toFloat().coerceIn(0f, 90f) else 0f
                            _rmsDbLevel.value = db

                            val recognizer = voskRecognizer ?: break
                            val isAccepted = recognizer.acceptWaveForm(audioBuffer, readCount)

                            val resultJson = if (isAccepted) recognizer.result else recognizer.partialResult
                            if (checkAndTriggerWakeWord(resultJson)) {
                                break
                            }
                        } else if (readCount < 0) {
                            Log.w(tag, "AudioRecord read error code: $readCount")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "AudioRecord streaming error", e)
                } finally {
                    try {
                        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord?.stop()
                        }
                        audioRecord?.release()
                        audioRecord = null
                    } catch (e: Exception) {
                        Log.w(tag, "Error releasing AudioRecord: ${e.message}")
                    }
                }

                if (isRunning && !isPaused) {
                    delay(500)
                }
            }
        }
    }

    private fun startSpeechRecognizerFallback() {
        if (!isRunning || isPaused) return
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(tag, "Android SpeechRecognizer is not available on this device.")
                    return@post
                }

                fallbackRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                fallbackRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = WakeWordState.Listening
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsDbLevel.value = rmsdB.coerceAtLeast(0f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.d(tag, "Fallback SpeechRecognizer onError code: $error")
                        fallbackRecognizer?.destroy()
                        fallbackRecognizer = null
                        if (isRunning && !isPaused) {
                            mainHandler.postDelayed({ startSpeechRecognizerFallback() }, 1500L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        processFallbackSpeechResults(results)
                        restartFallbackAfterDelay()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        processFallbackSpeechResults(partialResults)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Error starting Fallback SpeechRecognizer", e)
            }
        }
    }

    private fun processFallbackSpeechResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (match in matches) {
            val lower = match.lowercase(Locale.ROOT)
            if (lower.contains("hey max") || lower.contains("max") || lower.contains("hey macs") || lower.contains("hey mark")) {
                Log.i(tag, "Wake-Word detected via SpeechRecognizer fallback: '$match'")
                pauseListening()
                _state.value = WakeWordState.Triggered("Hey Max")
                onWakeWordDetected?.invoke("Hey Max")
                break
            }
        }
    }

    private fun restartFallbackAfterDelay() {
        fallbackRecognizer?.destroy()
        fallbackRecognizer = null
        if (isRunning && !isPaused) {
            mainHandler.postDelayed({ startSpeechRecognizerFallback() }, 500L)
        }
    }

    private fun checkAndTriggerWakeWord(jsonResult: String): Boolean {
        if (jsonResult.isBlank()) return false
        return try {
            val json = JSONObject(jsonResult)
            val text = (json.optString("text", "") + " " + json.optString("partial", "")).lowercase(Locale.ROOT).trim()

            if (text.contains("hey max") || text.contains("max") || text.contains("hey")) {
                Log.i(tag, ">>> WAKE-WORD TRIGGERED: '$text' <<<")
                pauseListening()
                _state.value = WakeWordState.Triggered("Hey Max")
                onWakeWordDetected?.invoke("Hey Max")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Temporarily pauses wake-word detection (e.g. while Gemini is listening or TTS is speaking).
     */
    fun pauseListening() {
        isPaused = true
        _state.value = WakeWordState.Paused
        _rmsDbLevel.value = 0f

        if (useSpeechRecognizerFallback) {
            mainHandler.post {
                try {
                    fallbackRecognizer?.stopListening()
                    fallbackRecognizer?.destroy()
                    fallbackRecognizer = null
                } catch (e: Exception) {
                    Log.w(tag, "Error stopping fallback recognizer: ${e.message}")
                }
            }
        } else {
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
            } catch (e: Exception) {
                Log.w(tag, "Error stopping audioRecord on pause: ${e.message}")
            }
        }
    }

    /**
     * Resumes listening after assistant interaction is finished.
     */
    fun resumeListening() {
        isPaused = false
        if (!isRunning || (recordingJob?.isActive != true && fallbackRecognizer == null)) {
            startListening()
        } else {
            _state.value = WakeWordState.Listening
            if (useSpeechRecognizerFallback && fallbackRecognizer == null) {
                startSpeechRecognizerFallback()
            }
        }
    }

    /**
     * Stops listening completely and releases all resources.
     */
    fun stopListening() {
        isRunning = false
        isPaused = false
        _state.value = WakeWordState.Uninitialized
        recordingJob?.cancel()
        recordingJob = null

        mainHandler.post {
            try {
                fallbackRecognizer?.cancel()
                fallbackRecognizer?.destroy()
                fallbackRecognizer = null
            } catch (e: Exception) {
                Log.w(tag, "Error cleaning fallback recognizer: ${e.message}")
            }
        }

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(tag, "Error releasing audioRecord: ${e.message}")
        }

        try {
            voskRecognizer?.close()
            voskRecognizer = null
        } catch (e: Exception) {
            Log.w(tag, "Error closing recognizer: ${e.message}")
        }
    }
}
