package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-Decibel Theft Alarm, Motion Detection Sentinel,
 * and Silent Front Camera Snapshot Capture Manager for MAX Assistant.
 */
class IntruderSecurityManager private constructor(private val context: Context) : SensorEventListener {
    private val tag = "IntruderSecurityManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRepo = AppSettingsRepository.getInstance(context)

    private val _failedUnlockCount = MutableStateFlow(0)
    val failedUnlockCount: StateFlow<Int> = _failedUnlockCount.asStateFlow()

    private val _lastFailedTimestamp = MutableStateFlow<Long?>(null)
    val lastFailedTimestamp: StateFlow<Long?> = _lastFailedTimestamp.asStateFlow()

    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

    private val _isMotionArmed = MutableStateFlow(false)
    val isMotionArmed: StateFlow<Boolean> = _isMotionArmed.asStateFlow()

    private val _lastSecurityStatus = MutableStateFlow("All Security Systems Nominal")
    val lastSecurityStatus: StateFlow<String> = _lastSecurityStatus.asStateFlow()

    private val _capturedIntruderImages = MutableStateFlow<List<File>>(emptyList())
    val capturedIntruderImages: StateFlow<List<File>> = _capturedIntruderImages.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var sirenAudioTrack: AudioTrack? = null
    private var sirenJob: Job? = null
    private var vibratorJob: Job? = null

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastAcceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var accelerationMagnitude = 0f
    private var lastMotionTriggerTime = 0L

    init {
        loadCapturedIntruderImages()
    }

    /**
     * Refreshes list of intruder snapshot files saved in internal app storage.
     */
    fun loadCapturedIntruderImages() {
        try {
            val dir = File(context.filesDir, "security_intruders")
            if (!dir.exists()) dir.mkdirs()

            val files = dir.listFiles { file -> file.extension.lowercase(Locale.ROOT) == "jpg" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            _capturedIntruderImages.value = files
        } catch (e: Exception) {
            Log.e(tag, "Failed to list intruder images: ${e.message}")
        }
    }

    /**
     * Handles failed unlock attempt triggered by DeviceAdminReceiver or Security System.
     */
    fun handleFailedUnlockAttempt() {
        val count = _failedUnlockCount.value + 1
        _failedUnlockCount.value = count
        _lastFailedTimestamp.value = System.currentTimeMillis()
        val settings = settingsRepo.settings.value

        Log.w(tag, "!!! SECURITY ALERT: Failed unlock attempt #$count registered (Threshold: ${settings.failedUnlockThreshold}) !!!")
        _lastSecurityStatus.value = "Unauthorized unlock attempt #$count detected!"

        // Check if count meets or exceeds the configured threshold
        if (count >= settings.failedUnlockThreshold) {
            if (settings.isIntruderSelfieCaptureEnabled) {
                captureSilentFrontCameraSnapshot(reason = "Failed Unlock ($count attempts)")
            }

            if (settings.isAntiTheftSirenEnabled) {
                triggerLoudAlarm(reason = "Failed Unlock ($count attempts)")
            }
        }
    }

    /**
     * Arm or Disarm Motion / Pickpocket Theft Detection.
     */
    fun setMotionDetectionArmed(armed: Boolean) {
        _isMotionArmed.value = armed
        if (armed) {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                Log.i(tag, "Anti-theft motion detector armed and active.")
                _lastSecurityStatus.value = "Anti-Theft Motion Sensor Armed"
            } ?: run {
                Log.w(tag, "Accelerometer sensor not available.")
            }
        } else {
            sensorManager?.unregisterListener(this)
            Log.i(tag, "Anti-theft motion detector disarmed.")
            _lastSecurityStatus.value = "Motion Sensor Disarmed"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isMotionArmed.value) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = currentAcceleration - lastAcceleration
        accelerationMagnitude = accelerationMagnitude * 0.9f + delta

        // Detect substantial sudden movement / grab
        if (accelerationMagnitude > 14f) {
            val now = System.currentTimeMillis()
            if (now - lastMotionTriggerTime > 5000) { // 5-sec cooldown
                lastMotionTriggerTime = now
                Log.w(tag, "!!! THEFT MOTION DETECTED: Device moved abruptly while armed! !!!")
                _lastSecurityStatus.value = "Theft motion detected while armed!"

                val settings = settingsRepo.settings.value
                if (settings.isIntruderSelfieCaptureEnabled) {
                    captureSilentFrontCameraSnapshot(reason = "Theft Motion Sensor")
                }
                if (settings.isAntiTheftSirenEnabled) {
                    triggerLoudAlarm(reason = "Theft Motion Sensor")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Plays a piercing, high-pitched, maximum-volume siren alarm overriding system silent/vibrate mode.
     */
    fun triggerLoudAlarm(reason: String = "Manual / Security Trigger") {
        scope.launch {
            try {
                _lastSecurityStatus.value = "SIREN ACTIVE: $reason"
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                // 1. Force Ringer Mode to NORMAL to override Silent/Vibrate
                try {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } catch (e: Exception) {
                    Log.w(tag, "Could not set ringer mode: ${e.message}")
                }

                // 2. Set ALL audio streams to Absolute Maximum Volume
                val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val maxRingVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRingVol, 0)

                stopLoudAlarm() // Clean up any active playback first

                _isAlarmRinging.value = true

                // 3. Start Dual-Frequency High-Decibel Synthesized Siren Tone
                startSynthesizedSirenAudioTrack()

                // 4. Start Media Player alarm sound as parallel fallback
                try {
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                    if (alarmUri != null) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, alarmUri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            isLooping = true
                            prepare()
                            start()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Ringtone player init warning: ${e.message}")
                }

                // 5. Start Urgent Siren Vibration Loop
                startUrgentVibration()

                Log.i(tag, "High-Decibel Siren Alarm engaged successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Error triggering loud alarm system: ${e.message}", e)
            }
        }
    }

    /**
     * Synthesizes an oscillating high-pitched dual-tone siren (1600Hz - 3200Hz warble)
     * via AudioTrack for guaranteed ear-piercing loudness regardless of system ringtones.
     */
    private fun startSynthesizedSirenAudioTrack() {
        sirenJob?.cancel()
        sirenJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, sampleRate / 2)

            val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_ALARM,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            sirenAudioTrack = audioTrack
            try {
                audioTrack.play()
                val buffer = ShortArray(bufferSize)
                var phase = 0.0

                while (isActive && _isAlarmRinging.value) {
                    val currentTimeMs = System.currentTimeMillis()
                    // Oscillating siren frequency between 1600Hz and 3200Hz every 400ms
                    val cycle = (currentTimeMs % 800) / 800.0
                    val currentFreq = if (cycle < 0.5) 1800.0 + (cycle * 2.0 * 1400.0) else 3200.0 - ((cycle - 0.5) * 2.0 * 1400.0)

                    val angularFreq = 2.0 * Math.PI * currentFreq / sampleRate
                    for (i in buffer.indices) {
                        buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.95).toInt().toShort()
                        phase += angularFreq
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.w(tag, "Siren audio track exception: ${e.message}")
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startUrgentVibration() {
        vibratorJob?.cancel()
        vibratorJob = scope.launch {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                } ?: return@launch

                val pattern = longArrayOf(0, 300, 100, 300, 100, 500)
                while (isActive && _isAlarmRinging.value) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                    delay(1300)
                }
            } catch (e: Exception) {
                Log.w(tag, "Vibration warning: ${e.message}")
            }
        }
    }

    /**
     * Stops the loud security alarm and reset ringing state.
     */
    fun stopLoudAlarm() {
        try {
            mediaPlayer?.run {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping alarm media player: ${e.message}")
        }
        mediaPlayer = null

        sirenJob?.cancel()
        sirenJob = null
        try {
            sirenAudioTrack?.stop()
            sirenAudioTrack?.release()
        } catch (_: Exception) {}
        sirenAudioTrack = null

        vibratorJob?.cancel()
        vibratorJob = null

        _isAlarmRinging.value = false
        _lastSecurityStatus.value = "Alarm Standby (Disarmed)"
        Log.i(tag, "Theft alarm system stopped.")
    }

    /**
     * Uses Android Camera2 API to silently snap a front-camera snapshot in the background.
     * Operates completely silently without showing a full-screen preview or flash.
     * Automatically corrects orientation and saves to internal storage.
     */
    fun captureSilentFrontCameraSnapshot(reason: String = "Intruder Alert", onComplete: ((File?) -> Unit)? = null) {
        scope.launch {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                var frontCameraId: String? = null
                var sensorOrientation = 270

                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = id
                        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                        break
                    }
                }

                if (frontCameraId == null) {
                    Log.e(tag, "Front camera not found on this hardware.")
                    onComplete?.invoke(null)
                    return@launch
                }

                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.CAMERA
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(tag, "Camera permission missing for silent background capture.")
                    onComplete?.invoke(null)
                    return@launch
                }

                startCameraThread()

                val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()

                        val savedFile = saveIntruderSnapshot(bytes, sensorOrientation, reason)
                        onComplete?.invoke(savedFile)
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing intruder snapshot bytes: ${e.message}", e)
                        onComplete?.invoke(null)
                    } finally {
                        reader.close()
                    }
                }, cameraHandler)

                cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            captureBuilder.addTarget(imageReader.surface)

                            // Silent background parameters - Flash OFF, auto focus
                            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                            camera.createCaptureSession(
                                listOf(imageReader.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        try {
                                            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: android.hardware.camera2.TotalCaptureResult
                                                ) {
                                                    Log.i(tag, "Silent intruder front camera snapshot captured!")
                                                    camera.close()
                                                    stopCameraThread()
                                                }
                                            }, cameraHandler)
                                        } catch (e: Exception) {
                                            Log.e(tag, "Failed capture session: ${e.message}", e)
                                            camera.close()
                                            stopCameraThread()
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        Log.e(tag, "Camera capture session configure failed.")
                                        camera.close()
                                        stopCameraThread()
                                    }
                                },
                                cameraHandler
                            )
                        } catch (e: Exception) {
                            Log.e(tag, "Error setting up camera request: ${e.message}", e)
                            camera.close()
                            stopCameraThread()
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        stopCameraThread()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(tag, "Camera open error: code $error")
                        camera.close()
                        stopCameraThread()
                    }
                }, cameraHandler)

            } catch (e: SecurityException) {
                Log.e(tag, "Camera permission missing for silent background capture: ${e.message}")
                onComplete?.invoke(null)
            } catch (e: Exception) {
                Log.e(tag, "Exception during silent camera capture: ${e.message}", e)
                onComplete?.invoke(null)
            }
        }
    }

    /**
     * Saves captured snapshot JPEG bytes with proper rotation and timestamp into app internal storage.
     */
    private fun saveIntruderSnapshot(bytes: ByteArray, sensorOrientation: Int, reason: String): File? {
        return try {
            val dir = File(context.filesDir, "security_intruders")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "intruder_${timestamp}.jpg")

            // Rotate bitmap if necessary for upright orientation
            var finalBytes = bytes
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (originalBitmap != null) {
                    val matrix = Matrix().apply {
                        postRotate(sensorOrientation.toFloat())
                        postScale(-1f, 1f) // Mirror front camera correctly
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                    val stream = ByteArrayOutputStream()
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    finalBytes = stream.toByteArray()
                    originalBitmap.recycle()
                    rotatedBitmap.recycle()
                }
            } catch (e: Exception) {
                Log.w(tag, "Bitmap rotation note: ${e.message}")
            }

            FileOutputStream(file).use { out ->
                out.write(finalBytes)
            }

            Log.i(tag, "Intruder snapshot saved successfully: ${file.name} (Reason: $reason)")
            loadCapturedIntruderImages()
            file
        } catch (e: Exception) {
            Log.e(tag, "Failed to save intruder snapshot: ${e.message}", e)
            null
        }
    }

    fun deleteIntruderImage(file: File) {
        try {
            if (file.exists()) {
                file.delete()
                loadCapturedIntruderImages()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete image ${file.name}: ${e.message}")
        }
    }

    private fun startCameraThread() {
        if (cameraThread == null) {
            cameraThread = HandlerThread("IntruderCameraThread").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)
        }
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
        } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
    }

    fun clearIntruderLogs() {
        _failedUnlockCount.value = 0
        _lastFailedTimestamp.value = null
        val dir = File(context.filesDir, "security_intruders")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
        loadCapturedIntruderImages()
    }

    companion object {
        @Volatile
        private var INSTANCE: IntruderSecurityManager? = null

        fun getInstance(context: Context): IntruderSecurityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IntruderSecurityManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
