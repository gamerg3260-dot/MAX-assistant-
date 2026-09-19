package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Real-time Voice Activity Detection (VAD) & Barge-In Controller for MAX Assistant.
 * Continuously monitors user speech during assistant voice generation and audio playback.
 * When user voice activity or speech onset is detected, it triggers instantaneous interruption (<20ms)
 * to halt audio playback, cancel AI response generation, and immediately activate listening mode.
 */
class RealtimeBargeInManager(
    private val context: Context,
    private val realtimeAudioPlayer: RealtimeAudioPlayer,
    private val maxNativeTTS: MAXNativeTTS,
    private val elevenLabsTtsService: ElevenLabsTtsService,
    private val callAnnouncer: CallAnnouncer
) {
    private val tag = "RealtimeBargeInManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_SAMPLES = 320 // 20ms per frame at 16kHz
        const val CONSECUTIVE_FRAMES_THRESHOLD = 3 // 60ms of speech energy to trigger
    }

    private val isMonitoringActive = AtomicBoolean(false)
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null

    // State flows
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isInterrupted = MutableStateFlow(false)
    val isInterrupted: StateFlow<Boolean> = _isInterrupted.asStateFlow()

    private val _bargeInCount = MutableStateFlow(0)
    val bargeInCount: StateFlow<Int> = _bargeInCount.asStateFlow()

    private val _currentMicDb = MutableStateFlow(0f)
    val currentMicDb: StateFlow<Float> = _currentMicDb.asStateFlow()

    private val _lastInterruptionReason = MutableStateFlow<String?>(null)
    val lastInterruptionReason: StateFlow<String?> = _lastInterruptionReason.asStateFlow()

    private val _isBargeInEnabled = MutableStateFlow(true)
    val isBargeInEnabled: StateFlow<Boolean> = _isBargeInEnabled.asStateFlow()

    private val _sensitivity = MutableStateFlow(0.75f) // 0.1 (low) to 1.0 (high)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    var onBargeInListener: ((reason: String) -> Unit)? = null
    var onSpeechOnsetDetected: (() -> Unit)? = null

    // Action cancellation hook for active AI generation coroutines / jobs
    var activeGenerationCancellationCallback: (() -> Unit)? = null

    fun setBargeInEnabled(enabled: Boolean) {
        _isBargeInEnabled.value = enabled
        if (!enabled && isMonitoringActive.get()) {
            stopMonitoring()
        }
    }

    fun setSensitivity(sens: Float) {
        _sensitivity.value = sens.coerceIn(0.1f, 1.0f)
    }

    /**
     * Starts low-latency real-time microphone VAD monitoring while assistant is generating or speaking.
     * If user begins speaking, interruption is fired immediately.
     */
    fun startMonitoring(
        onBargeIn: ((reason: String) -> Unit)? = null
    ) {
        if (!_isBargeInEnabled.value) {
            Log.d(tag, "Barge-in monitoring skipped (disabled by user settings)")
            return
        }

        if (onBargeIn != null) {
            this.onBargeInListener = onBargeIn
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(tag, "Cannot start barge-in VAD: RECORD_AUDIO permission not granted")
            return
        }

        if (isMonitoringActive.compareAndSet(false, true)) {
            _isMonitoring.value = true
            _isInterrupted.value = false
            recordJob?.cancel()
            recordJob = scope.launch {
                runVadAudioLoop()
            }
            Log.d(tag, "Real-time barge-in VAD monitoring active (Sensitivity: ${_sensitivity.value})")
        }
    }

    /**
     * Continuous low-latency audio processing loop for Voice Activity Detection (VAD).
     */
    private suspend fun runVadAudioLoop() {
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(FRAME_SIZE_SAMPLES * 2 * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Hardware acoustic echo canceller where supported
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufSize
            )

            val record = audioRecord ?: return
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(tag, "AudioRecord failed to initialize for Barge-In VAD")
                return
            }

            record.startRecording()

            val buffer = ShortArray(FRAME_SIZE_SAMPLES)
            var consecutiveSpeechFrames = 0
            var noiseFloorDb = 35.0f

            // Map sensitivity (0.1..1.0) to speech threshold delta above noise floor (e.g. 12dB at high sens, 24dB at low sens)
            val currentSens = _sensitivity.value
            val thresholdDeltaDb = (28.0f - (currentSens * 18.0f)).coerceIn(8.0f, 26.0f)

            while (scope.isActive && isMonitoringActive.get()) {
                val readSamples = record.read(buffer, 0, FRAME_SIZE_SAMPLES)
                if (readSamples > 0) {
                    var sumSq = 0.0
                    for (i in 0 until readSamples) {
                        sumSq += (buffer[i] * buffer[i]).toDouble()
                    }
                    val rms = sqrt(sumSq / readSamples)
                    val rmsDb = if (rms > 1.0) (20.0 * log10(rms)).toFloat() else 0f
                    _currentMicDb.value = rmsDb

                    // Track running dynamic noise floor
                    if (rmsDb < noiseFloorDb) {
                        noiseFloorDb = noiseFloorDb * 0.95f + rmsDb * 0.05f
                    } else {
                        noiseFloorDb = noiseFloorDb * 0.998f + rmsDb * 0.002f
                    }

                    // Check if voice energy exceeds threshold
                    val voiceThreshold = (noiseFloorDb + thresholdDeltaDb).coerceAtLeast(42.0f)
                    if (rmsDb > voiceThreshold) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= CONSECUTIVE_FRAMES_THRESHOLD) {
                            Log.i(tag, "⚡ User Speech Detected during assistant playback! (RMS: $rmsDb dB, NoiseFloor: $noiseFloorDb dB)")
                            triggerBargeIn("VAD_VOICE_ACTIVITY")
                            break
                        }
                    } else {
                        consecutiveSpeechFrames = (consecutiveSpeechFrames - 1).coerceAtLeast(0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Barge-in AudioRecord loop exception: ${e.message}")
        } finally {
            cleanupAudioRecord()
            _isMonitoring.value = false
            isMonitoringActive.set(false)
        }
    }

    private fun cleanupAudioRecord() {
        try {
            audioRecord?.let { record ->
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.stop()
                }
                record.release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.w(tag, "Error releasing AudioRecord: ${e.message}")
        }
    }

    /**
     * Immediately interrupts all audio playback and AI generation, then triggers the barge-in callback.
     */
    fun triggerBargeIn(reason: String = "USER_SPEECH") {
        if (!_isBargeInEnabled.value) return

        _isInterrupted.value = true
        _bargeInCount.value += 1
        _lastInterruptionReason.value = reason

        // Stop VAD monitoring loop
        stopMonitoring()

        // 1. Instantly halt all audio players
        try {
            realtimeAudioPlayer.interruptNow()
            maxNativeTTS.stop()
            elevenLabsTtsService.stopAudio()
            callAnnouncer.stop()
            Log.d(tag, "All TTS and PCM playback halted instantly for barge-in.")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping audio engines on barge-in: ${e.message}")
        }

        // 2. Cancel ongoing AI generation pipeline
        try {
            activeGenerationCancellationCallback?.invoke()
            activeGenerationCancellationCallback = null
        } catch (e: Exception) {
            Log.e(tag, "Error cancelling AI generation: ${e.message}")
        }

        // 3. Trigger subtle haptic confirmation
        triggerHapticFeedback()

        // 4. Notify listeners on Main Thread to immediately open microphone / STT
        mainHandler.post {
            onSpeechOnsetDetected?.invoke()
            onBargeInListener?.invoke(reason)
        }
    }

    /**
     * Manually simulates a barge-in event (for interactive testing / sandbox).
     */
    fun simulateBargeIn() {
        triggerBargeIn("TEST_SANDBOX_BARGE_IN")
    }

    private fun triggerHapticFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (e: Exception) {
            Log.w(tag, "Haptic feedback unavailable: ${e.message}")
        }
    }

    /**
     * Stops barge-in monitoring once assistant is completely idle or finished speaking.
     */
    fun stopMonitoring() {
        if (isMonitoringActive.compareAndSet(true, false)) {
            _isMonitoring.value = false
            recordJob?.cancel()
            recordJob = null
            scope.launch {
                cleanupAudioRecord()
            }
            Log.d(tag, "Real-time barge-in VAD monitoring stopped.")
        }
    }
}
