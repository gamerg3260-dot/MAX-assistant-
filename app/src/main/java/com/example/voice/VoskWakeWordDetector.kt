package com.example.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

sealed class WakeWordState {
    data object Uninitialized : WakeWordState()
    data object LoadingModel : WakeWordState()
    data object Listening : WakeWordState()
    data object Paused : WakeWordState()
    data class Triggered(val wakePhrase: String = "Hey Max") : WakeWordState()
    data class Error(val message: String) : WakeWordState()
}

/**
 * Continuous Offline Wake-Word ("Hey Max") Detector powered by Vosk.
 * Uses strict keyword grammar ["hey max", "[unk]"] with 16kHz Mono 16-bit PCM AudioRecord.
 */
class VoskWakeWordDetector(private val context: Context) {

    private val tag = "VoskWakeWordDetector"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null

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
     * Initializes the Vosk model from assets and prepares the recognizer with strict keyword grammar.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (voskModel != null && voskRecognizer != null) {
            return@withContext true
        }

        _state.value = WakeWordState.LoadingModel
        Log.i(tag, "Initializing Vosk model for 'Hey Max' wake-word spotting...")

        try {
            val modelDir = extractOrGetModelDir()
            if (modelDir == null || !modelDir.exists()) {
                val err = "Vosk model assets not found in app/src/main/assets/model-en"
                Log.e(tag, err)
                _state.value = WakeWordState.Error(err)
                onError?.invoke(err)
                return@withContext false
            }

            voskModel = Model(modelDir.absolutePath)
            voskRecognizer = Recognizer(voskModel, SAMPLE_RATE.toFloat(), WAKE_PHRASE_GRAMMAR)
            Log.i(tag, "Vosk Model and Recognizer successfully initialized with grammar: $WAKE_PHRASE_GRAMMAR")
            _state.value = WakeWordState.Listening
            true
        } catch (e: Exception) {
            val err = "Vosk initialization failed: ${e.localizedMessage ?: e.message}"
            Log.e(tag, err, e)
            _state.value = WakeWordState.Error(err)
            onError?.invoke(err)
            false
        }
    }

    /**
     * Unpacks assets/model-en into context.filesDir/model-en if not already unpacked.
     */
    private fun extractOrGetModelDir(): File? {
        val targetDir = File(context.filesDir, "model-en")
        val am = context.assets

        try {
            // Direct recursive copy of asset model-en to internal storage
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
        if (isRunning && recordingJob?.isActive == true) {
            isPaused = false
            _state.value = WakeWordState.Listening
            return
        }

        isRunning = true
        isPaused = false

        recordingJob = scope.launch {
            if (voskModel == null || voskRecognizer == null) {
                val ok = initialize()
                if (!ok) {
                    isRunning = false
                    return@launch
                }
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
                            // Compute RMS level for visualizers / telemetry
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

    private fun checkAndTriggerWakeWord(jsonResult: String): Boolean {
        if (jsonResult.isBlank()) return false
        return try {
            val json = JSONObject(jsonResult)
            val text = (json.optString("text", "") + " " + json.optString("partial", "")).lowercase().trim()

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
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping audioRecord on pause: ${e.message}")
        }
    }

    /**
     * Resumes listening after assistant interaction is finished.
     */
    fun resumeListening() {
        isPaused = false
        if (!isRunning || recordingJob?.isActive != true) {
            startListening()
        } else {
            _state.value = WakeWordState.Listening
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
