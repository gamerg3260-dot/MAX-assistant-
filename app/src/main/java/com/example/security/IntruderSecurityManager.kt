package com.example.security

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager handling Device Admin Security Monitoring, Loud Alarm System,
 * and Silent Background Front Camera Snapshot Capture on failed unlock attempts.
 */
class IntruderSecurityManager private constructor(private val context: Context) {
    private val tag = "IntruderSecurityManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _failedUnlockCount = MutableStateFlow(0)
    val failedUnlockCount: StateFlow<Int> = _failedUnlockCount.asStateFlow()

    private val _lastFailedTimestamp = MutableStateFlow<Long?>(null)
    val lastFailedTimestamp: StateFlow<Long?> = _lastFailedTimestamp.asStateFlow()

    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

    private val _capturedIntruderImages = MutableStateFlow<List<File>>(emptyList())
    val capturedIntruderImages: StateFlow<List<File>> = _capturedIntruderImages.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    init {
        loadCapturedIntruderImages()
    }

    /**
     * Refreshes list of intruder snapshot files saved in internal app storage.
     */
    fun loadCapturedIntruderImages() {
        val dir = File(context.filesDir, "security_intruders")
        if (!dir.exists()) dir.mkdirs()

        val files = dir.listFiles { file -> file.extension.lowercase(Locale.ROOT) == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        _capturedIntruderImages.value = files
    }

    /**
     * Handles failed unlock attempt triggered by DeviceAdminReceiver.
     * Triggers security actions immediately on the first failed attempt.
     */
    fun handleFailedUnlockAttempt() {
        val count = _failedUnlockCount.value + 1
        _failedUnlockCount.value = count
        _lastFailedTimestamp.value = System.currentTimeMillis()

        Log.w(tag, "!!! SECURITY ALERT: Failed unlock attempt #$count registered !!!")

        // 1. Silent Background Front Camera Capture
        captureSilentFrontCameraSnapshot()

        // 2. Play Continuous Loud Alarm (overriding silent mode)
        triggerLoudAlarm()
    }

    /**
     * Plays a loud continuous alarm sound at maximum volume, overriding silent/vibrate mode.
     */
    fun triggerLoudAlarm() {
        scope.launch {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                // Override silent / vibrate mode to Normal
                try {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                } catch (e: Exception) {
                    Log.w(tag, "Could not set ringer mode: ${e.message}")
                }

                // Set Alarm and Media streams to MAX volume
                val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)

                stopLoudAlarm() // Ensure previous player cleaned up

                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

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

                _isAlarmRinging.value = true
                Log.i(tag, "Loud alarm system activated at MAX volume.")
            } catch (e: Exception) {
                Log.e(tag, "Error triggering loud alarm system: ${e.message}", e)
            }
        }
    }

    /**
     * Stops the loud security alarm system.
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
        _isAlarmRinging.value = false
    }

    /**
     * Uses Android Camera2 API to silently capture a front-camera snapshot in the background.
     * No preview or flash is displayed. Image saved securely in internal storage.
     */
    fun captureSilentFrontCameraSnapshot() {
        scope.launch {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                var frontCameraId: String? = null

                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = id
                        break
                    }
                }

                if (frontCameraId == null) {
                    Log.e(tag, "Front camera not found on this device.")
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

                        saveIntruderSnapshot(bytes)
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing intruder snapshot image bytes: ${e.message}", e)
                    } finally {
                        reader.close()
                    }
                }, cameraHandler)

                cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        try {
                            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            captureBuilder.addTarget(imageReader.surface)
                            
                            // Ensure Flash is OFF for silent/invisible background capture
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
                                                    Log.i(tag, "Silent front camera snapshot capture completed!")
                                                    camera.close()
                                                    stopCameraThread()
                                                }
                                            }, cameraHandler)
                                        } catch (e: Exception) {
                                            Log.e(tag, "Failed session capture: ${e.message}", e)
                                            camera.close()
                                            stopCameraThread()
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        Log.e(tag, "Camera capture session configuration failed.")
                                        camera.close()
                                        stopCameraThread()
                                    }
                                },
                                cameraHandler
                            )
                        } catch (e: Exception) {
                            Log.e(tag, "Error setting up camera capture request: ${e.message}", e)
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
            } catch (e: Exception) {
                Log.e(tag, "Exception during silent camera capture: ${e.message}", e)
            }
        }
    }

    /**
     * Saves captured snapshot JPEG bytes into app's internal storage directory.
     */
    private fun saveIntruderSnapshot(bytes: ByteArray) {
        try {
            val dir = File(context.filesDir, "security_intruders")
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "intruder_$timestamp.jpg")

            FileOutputStream(file).use { out ->
                out.write(bytes)
            }

            Log.i(tag, "Intruder snapshot saved successfully: ${file.absolutePath}")
            loadCapturedIntruderImages()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save intruder snapshot: ${e.message}", e)
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
