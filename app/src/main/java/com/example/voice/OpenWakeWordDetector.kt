package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class OpenWakeWordState {
    UNINITIALIZED,
    READY,
    LISTENING,
    PAUSED,
    ERROR
}

/**
 * Data structure holding configuration for a target wake-word model.
 */
data class WakeWordModelConfig(
    val wakePhrase: String,
    val assetFileName: String
)

/**
 * OpenWakeWord Engine implementation for multi-wake-word detection ("Okay Max", "Backup Max", "Hey Max").
 * Runs concurrently in a lightweight background service using ONNX Runtime models.
 */
class OpenWakeWordDetector(private val context: Context) {
    private val tag = "OpenWakeWordDetector"

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 1280 // 80ms at 16kHz
        const val DETECTION_THRESHOLD = 0.50f
        const val REFRACTORY_DEBOUNCE_MS = 1500L

        // Target Wake Phrases monitored concurrently
        val TARGET_WAKE_WORDS = listOf(
            WakeWordModelConfig("Okay Max", "openwakeword/okay_max.onnx"),
            WakeWordModelConfig("Backup Max", "openwakeword/backup_max.onnx"),
            WakeWordModelConfig("Hey Max", "openwakeword/hey_max.onnx")
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()

    private val _state = MutableStateFlow(OpenWakeWordState.UNINITIALIZED)
    val state: StateFlow<OpenWakeWordState> = _state.asStateFlow()

    private val _rmsDbLevel = MutableStateFlow(-60f)
    val rmsDbLevel: StateFlow<Float> = _rmsDbLevel.asStateFlow()

    private val _lastDetectedPhrase = MutableStateFlow<String?>(null)
    val lastDetectedPhrase: StateFlow<String?> = _lastDetectedPhrase.asStateFlow()

    var onWakeWordDetected: ((wakePhrase: String) -> Unit)? = null
    var onError: ((errorMessage: String) -> Unit)? = null

    private val isListening = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // ONNX Runtime session & environment
    private var ortEnvironment: OrtEnvironment? = null
    private val ortSessions = mutableMapOf<String, OrtSession>()

    private var audioRecord: AudioRecord? = null
    private var fallbackSpeechRecognizer: SpeechRecognizer? = null
    private var lastTriggerTimestamp = 0L

    init {
        initializeEngine()
    }

    /**
     * Initializes the OpenWakeWord ONNX Runtime models for all three wake phrases.
     */
    fun initializeEngine() {
        scope.launch {
            try {
                Log.i(tag, "Initializing OpenWakeWord ONNX engine for multi-phrase monitoring...")
                ortEnvironment = OrtEnvironment.getEnvironment()

                var loadedOnnxCount = 0
                val openWakeWordDir = File(context.filesDir, "openwakeword")
                if (!openWakeWordDir.exists()) openWakeWordDir.mkdirs()

                for (config in TARGET_WAKE_WORDS) {
                    val modelFile = File(openWakeWordDir, File(config.assetFileName).name)
                    if (!modelFile.exists() || modelFile.length() == 0L) {
                        copyAssetToFile(config.assetFileName, modelFile)
                    }

                    if (modelFile.exists() && modelFile.length() > 0L) {
                        try {
                            val sessionOptions = OrtSession.SessionOptions().apply {
                                setIntraOpNumThreads(1)
                            }
                            val session = ortEnvironment?.createSession(modelFile.absolutePath, sessionOptions)
                            if (session != null) {
                                ortSessions[config.wakePhrase] = session
                                loadedOnnxCount++
                                Log.i(tag, "Loaded OpenWakeWord ONNX model for: ${config.wakePhrase}")
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Could not load ONNX model for ${config.wakePhrase}: ${e.message}")
                        }
                    }
                }

                _state.value = OpenWakeWordState.READY
                Log.i(tag, "OpenWakeWord Engine initialized ($loadedOnnxCount ONNX models active).")

                if (loadedOnnxCount == 0) {
                    Log.i(tag, "Activating native continuous speech engine for multi-wake-word detection (\"Okay Max\", \"Backup Max\", \"Hey Max\").")
                    setupAndroidSpeechFallback()
                }
            } catch (e: Exception) {
                Log.e(tag, "OpenWakeWord engine initialization exception: ${e.message}", e)
                _state.value = OpenWakeWordState.ERROR
                setupAndroidSpeechFallback()
            }
        }
    }

    /**
     * Copies an asset file to internal storage for ONNX Runtime loading.
     */
    private fun copyAssetToFile(assetPath: String, outputFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Asset $assetPath not present in APK assets: ${e.message}")
        }
    }

    /**
     * Starts continuous background wake-word detection for "Okay Max", "Backup Max", and "Hey Max".
     */
    fun startListening() {
        if (isListening.get()) return

        isListening.set(true)
        isPaused.set(false)
        _state.value = OpenWakeWordState.LISTENING

        if (ortSessions.isNotEmpty()) {
            executor.submit { runOnnxAudioLoop() }
        } else {
            startAndroidSpeechFallback()
        }
    }

    /**
     * Temporarily pauses wake-word detection (e.g. when assistant is speaking or recording user speech).
     */
    fun pauseListening() {
        isPaused.set(true)
        _state.value = OpenWakeWordState.PAUSED
        if (fallbackSpeechRecognizer != null) {
            try {
                fallbackSpeechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    /**
     * Resumes background wake-word detection.
     */
    fun resumeListening() {
        if (!isListening.get()) {
            startListening()
            return
        }
        isPaused.set(false)
        _state.value = OpenWakeWordState.LISTENING
        if (ortSessions.isEmpty()) {
            startAndroidSpeechFallback()
        }
    }

    /**
     * Stops listening and releases AudioRecord resources.
     */
    fun stopListening() {
        isListening.set(false)
        isPaused.set(false)
        _state.value = OpenWakeWordState.READY

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null

        stopAndroidSpeechFallback()
    }

    /**
     * Audio loop processing frames and running ONNX inference concurrently across loaded models.
     */
    private fun runOnnxAudioLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = Math.max(minBufferSize, FRAME_SIZE * 2 * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed.")
                setupAndroidSpeechFallback()
                startAndroidSpeechFallback()
                return
            }

            audioRecord?.startRecording()
            Log.i(tag, "AudioRecord started. OpenWakeWord monitoring ONNX models...")

            val pcmBuffer = ShortArray(FRAME_SIZE)
            val floatBuffer = FloatArray(FRAME_SIZE)

            while (isListening.get()) {
                if (isPaused.get()) {
                    Thread.sleep(100)
                    continue
                }

                val readCount = audioRecord?.read(pcmBuffer, 0, FRAME_SIZE) ?: -1
                if (readCount > 0) {
                    var sum = 0.0
                    for (i in 0 until readCount) {
                        val sample = pcmBuffer[i].toFloat() / 32768.0f
                        floatBuffer[i] = sample
                        sum += sample * sample
                    }

                    // Compute RMS dB
                    val rms = sqrt(sum / readCount)
                    val db = if (rms > 0) (20 * log10(rms)).toFloat().coerceIn(-80f, 0f) else -80f
                    _rmsDbLevel.value = db

                    // Run ONNX Inference for loaded models
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerTimestamp > REFRACTORY_DEBOUNCE_MS) {
                        evaluateOnnxModels(floatBuffer, readCount)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception in ONNX audio loop: ${e.message}", e)
            setupAndroidSpeechFallback()
            startAndroidSpeechFallback()
        }
    }

    /**
     * Feeds audio feature tensors into ONNX Runtime sessions for multi-wake-word detection.
     */
    private fun evaluateOnnxModels(floatBuffer: FloatArray, count: Int) {
        val env = ortEnvironment ?: return

        try {
            // Prepare FloatBuffer ONNX Tensor
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatBuffer), longArrayOf(1, count.toLong()))

            for ((wakePhrase, session) in ortSessions) {
                try {
                    val outputs = session.run(mapOf("input" to inputTensor))
                    val score = extractProbabilityFromOutput(outputs)

                    if (score >= DETECTION_THRESHOLD) {
                        // Speaker Verification Check
                        val verificationManager = com.example.biometrics.SpeakerVerificationManager.getInstance(context)
                        val verification = verificationManager.verifySpeaker(floatBuffer)

                        if (verification.isAuthorized) {
                            triggerWakeWordMatch(wakePhrase, score)
                            break
                        } else {
                            Log.w(tag, ">>> IGNORED WAKE-WORD (\"$wakePhrase\"): ${verification.message} <<<")
                        }
                    }
                } catch (_: Exception) {
                    // Ignore session run mismatch exceptions gracefully
                }
            }

            inputTensor.close()
        } catch (_: Exception) {}
    }

    /**
     * Extracts prediction probability from ONNX output tensor.
     */
    private fun extractProbabilityFromOutput(outputs: OrtSession.Result): Float {
        return try {
            if (outputs.count() > 0) {
                val value = outputs.get(0).value
                when (value) {
                    is Array<*> -> {
                        val floatArr = value as Array<FloatArray>
                        if (floatArr.isNotEmpty() && floatArr[0].isNotEmpty()) floatArr[0][0] else 0f
                    }
                    is FloatArray -> if (value.isNotEmpty()) value[0] else 0f
                    else -> 0f
                }
            } else 0f
        } catch (_: Exception) {
            0f
        }
    }

    /**
     * Handles positive wake-word detection.
     */
    private fun triggerWakeWordMatch(wakePhrase: String, score: Float = 1.0f) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < REFRACTORY_DEBOUNCE_MS) return

        lastTriggerTimestamp = now
        _lastDetectedPhrase.value = wakePhrase
        Log.i(tag, ">>> MULTI-WAKE-WORD DETECTED: \"$wakePhrase\" (score=$score) <<<")

        scope.launch(Dispatchers.Main) {
            onWakeWordDetected?.invoke(wakePhrase)
        }
    }

    /**
     * Android SpeechRecognizer continuous fallback for "Okay Max", "Backup Max", and "Hey Max".
     */
    private fun setupAndroidSpeechFallback() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        scope.launch(Dispatchers.Main) {
            try {
                fallbackSpeechRecognizer?.destroy()
                fallbackSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {
                            _rmsDbLevel.value = rmsdB
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            if (isListening.get() && !isPaused.get()) {
                                executor.submit {
                                    Thread.sleep(500)
                                    scope.launch(Dispatchers.Main) { startAndroidSpeechFallback() }
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            processSpeechMatches(matches)
                            if (isListening.get() && !isPaused.get()) {
                                startAndroidSpeechFallback()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            processSpeechMatches(matches)
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } catch (e: Exception) {
                Log.w(tag, "SpeechRecognizer fallback error: ${e.message}")
            }
        }
    }

    private fun processSpeechMatches(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return
        for (match in matches) {
            val q = match.lowercase(Locale.ROOT)
            when {
                q.contains("okay max") || q.contains("ok max") || q.contains("ओके मैक्स") -> {
                    triggerWakeWordMatch("Okay Max")
                    return
                }
                q.contains("backup max") || q.contains("back up max") || q.contains("बैकअप मैक्स") -> {
                    triggerWakeWordMatch("Backup Max")
                    return
                }
                q.contains("hey max") || q.contains("hi max") || q.contains("हे मैक्स") -> {
                    triggerWakeWordMatch("Hey Max")
                    return
                }
            }
        }
    }

    private fun startAndroidSpeechFallback() {
        scope.launch(Dispatchers.Main) {
            if (!isListening.get() || isPaused.get()) return@launch
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                fallbackSpeechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(tag, "Start listening fallback error: ${e.message}")
            }
        }
    }

    private fun stopAndroidSpeechFallback() {
        scope.launch(Dispatchers.Main) {
            try {
                fallbackSpeechRecognizer?.stopListening()
                fallbackSpeechRecognizer?.destroy()
                fallbackSpeechRecognizer = null
            } catch (_: Exception) {}
        }
    }

    fun release() {
        stopListening()
        for (session in ortSessions.values) {
            try {
                session.close()
            } catch (_: Exception) {}
        }
        ortSessions.clear()

        try {
            ortEnvironment?.close()
        } catch (_: Exception) {}
        ortEnvironment = null

        executor.shutdown()
    }
}
